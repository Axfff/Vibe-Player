import asyncio
import json
from dataclasses import replace

import httpx
import pytest

from discovery.albums import AlbumService, aggregate
from discovery.config import Settings
from discovery.errors import DiscoveryError
from discovery.models import ProviderStatus, VisionOutput
from discovery.vision import VisionService

from .test_vision import VALID, live_config, vision_payload

CONSENT = {"X-Cloud-Consent": "true"}
PHOTO_HEADERS = {**CONSENT, "Content-Type": "image/jpeg"}


def create(client, count=3):
    response = client.post("/v1/albums", json={"photoCount": count}, headers=CONSENT)
    assert response.status_code == 201
    return response.json()["albumId"]


def test_album_partial_results_and_no_images_in_state(client_factory, image_bytes, caplog):
    client = client_factory(Settings(vision_mode="mock"))
    identifier = create(client)
    for index, data in enumerate([image_bytes, b"invalid-image-PRIVATE", image_bytes]):
        response = client.post(
            f"/v1/albums/{identifier}/photos/{index}", content=data, headers=PHOTO_HEADERS
        )
        assert response.status_code == 200
    result = response.json()
    assert (result["total"], result["processed"], result["succeeded"], result["failed"]) == (
        3,
        3,
        2,
        1,
    )
    assert result["state"] == "completed_with_errors" and result["pending"] == 0
    assert result["vision"]["mode"] == "mock" and result["vision"]["requestsMade"] == 0
    assert result["frequencies"]["sceneTags"] == [{"tag": "other", "photos": 2}]
    assert result["intent"]["uncertainty"] == "high"
    assert result["items"][-1]["errorCode"] == "invalid_image"
    assert "PRIVATE" not in response.text + caplog.text
    album = client.app.state.albums.get(identifier)
    assert all(isinstance(output, VisionOutput) for output in album.results.values())
    assert not any(isinstance(value, bytes) for value in vars(album).values())


def test_album_consent_before_image_processing(client_factory, image_bytes):
    client = client_factory(Settings(vision_mode="mock"))
    assert client.post("/v1/albums", json={"photoCount": 1}).status_code == 403
    identifier = create(client, 1)
    assert client.post(f"/v1/albums/{identifier}/photos/0", content=image_bytes).status_code == 403
    assert client.get(f"/v1/albums/{identifier}").json()["processed"] == 0
    assert client.get("/v1/providers/status").json()["vision"]["state"] == "not_run"


def test_album_disconnected_upload_never_starts_model(client_factory, image_bytes, monkeypatch):
    client = client_factory(Settings(vision_mode="mock"))
    identifier = create(client, 1)

    async def disconnected(request):
        return True

    monkeypatch.setattr("starlette.requests.Request.is_disconnected", disconnected)
    response = client.post(
        f"/v1/albums/{identifier}/photos/0", content=image_bytes, headers=PHOTO_HEADERS
    )
    assert response.status_code == 499
    assert client.get("/v1/providers/status").json()["vision"]["state"] == "not_run"


def test_album_image_byte_limit_before_processing(client_factory, image_bytes):
    client = client_factory(Settings(vision_mode="mock", max_upload_bytes=100))
    identifier = create(client, 1)
    assert (
        client.post(
            f"/v1/albums/{identifier}/photos/0", content=image_bytes, headers=PHOTO_HEADERS
        ).status_code
        == 413
    )
    assert client.get(f"/v1/albums/{identifier}").json()["processed"] == 0


def test_album_image_pixel_limit_is_reported(client_factory, image_bytes):
    client = client_factory(Settings(vision_mode="mock", max_pixels=100))
    identifier = create(client, 1)
    result = client.post(
        f"/v1/albums/{identifier}/photos/0", content=image_bytes, headers=PHOTO_HEADERS
    ).json()
    assert result["items"][0]["errorCode"] == "image_pixel_limit"
    assert result["succeeded"] == 0 and result["failed"] == 1
    assert result["vision"]["state"] == "not_run"


@pytest.mark.parametrize("count", [0, 501, True, 1.5])
def test_album_count_limits(client_factory, count):
    client = client_factory(Settings(vision_mode="mock"))
    assert client.post("/v1/albums", json={"photoCount": count}, headers=CONSENT).status_code == 422


def test_unconfigured_and_live_budget_preflight(client_factory):
    client = client_factory()
    assert client.post("/v1/albums", json={"photoCount": 1}, headers=CONSENT).status_code == 503
    client = client_factory(live_config())
    response = client.post("/v1/albums", json={"photoCount": 2}, headers=CONSENT)
    assert response.status_code == 429 and response.json()["error"] == "album_budget_insufficient"
    assert response.json()["vision"]["requestsMade"] == 0


def test_duplicate_index_is_idempotent_and_live_remains_live(client_factory, network, image_bytes):
    sent = []

    def handler(request):
        sent.append(request)
        return httpx.Response(200, json=vision_payload(VALID))

    settings = live_config()
    vision = VisionService(settings, network(handler))
    client = client_factory(settings, vision=vision)
    identifier = create(client, 1)
    for _ in range(2):
        response = client.post(
            f"/v1/albums/{identifier}/photos/0", content=image_bytes, headers=PHOTO_HEADERS
        )
        assert response.json()["state"] == "completed"
        assert response.json()["vision"]["mode"] == "live"
    assert len(sent) == 1
    payload = json.loads(sent[0].content)
    assert len(payload["input"][0]["content"]) == 1
    assert "albumId" not in sent[0].content.decode() and "filename" not in sent[0].content.decode()


def test_live_failure_pauses_remaining_album(client_factory, network, image_bytes):
    settings = replace(live_config(), vision_call_budget=3)
    vision = VisionService(settings, network(lambda r: httpx.Response(403)))
    client = client_factory(settings, vision=vision)
    identifier = create(client)
    data = client.post(
        f"/v1/albums/{identifier}/photos/0", content=image_bytes, headers=PHOTO_HEADERS
    ).json()
    assert data["state"] == "paused" and data["pauseReason"] == "http_403"
    assert data["succeeded"] == 0 and data["pending"] == 2
    assert data["vision"]["mode"] == "live" and data["intent"] is None
    assert (
        client.post(
            f"/v1/albums/{identifier}/photos/1", content=image_bytes, headers=PHOTO_HEADERS
        ).status_code
        == 429
    )
    assert vision.status().requestsMade == 1


def test_local_failure_cancel_and_range(client_factory, image_bytes):
    client = client_factory(Settings(vision_mode="mock"))
    identifier = create(client)
    failed = client.post(
        f"/v1/albums/{identifier}/failures/0", json={"errorCode": "unsupported_image"}
    )
    assert failed.json()["failed"] == 1 and failed.json()["pending"] == 2
    assert (
        client.post(
            f"/v1/albums/{identifier}/failures/1", json={"errorCode": "PRIVATE"}
        ).status_code
        == 422
    )
    assert (
        client.post(
            f"/v1/albums/{identifier}/photos/3", content=image_bytes, headers=PHOTO_HEADERS
        ).status_code
        == 422
    )
    assert client.delete(f"/v1/albums/{identifier}").json()["state"] == "cancelled"
    assert (
        client.post(
            f"/v1/albums/{identifier}/photos/1", content=image_bytes, headers=PHOTO_HEADERS
        ).status_code
        == 409
    )


async def test_cancel_inflight_prevents_late_result_and_next_image(image_bytes):
    started = asyncio.Event()
    cancelled = asyncio.Event()

    class WaitingVision:
        def status(self):
            return ProviderStatus(
                provider="vision", mode="mock", configured=True, policyBasis="fixture"
            )

        async def analyze(self, jpeg):
            started.set()
            try:
                await asyncio.Event().wait()
            finally:
                cancelled.set()

    albums = AlbumService(WaitingVision(), Settings(vision_mode="mock"))
    identifier = albums.create(2)["albumId"]
    pending = asyncio.create_task(albums.process(identifier, 0, image_bytes, "image/jpeg"))
    await asyncio.wait_for(started.wait(), timeout=2)
    assert (await albums.cancel(identifier))["state"] == "cancelled"
    with pytest.raises(DiscoveryError, match="album_cancelled"):
        await pending
    assert cancelled.is_set() and albums.snapshot(identifier)["succeeded"] == 0
    with pytest.raises(DiscoveryError, match="album_cancelled"):
        await albums.process(identifier, 1, image_bytes, "image/jpeg")


def test_aggregate_counts_photos_not_duplicate_tags():
    a = VisionOutput(**{**VALID, "sceneTags": ["nature", "nature"], "uncertainty": "low"})
    b = VisionOutput(**{**VALID, "sceneTags": ["city"], "visualMoodSuggestions": ["energetic"]})
    intent, counts = aggregate([a, a, b])
    assert counts["sceneTags"] == [{"tag": "nature", "photos": 2}, {"tag": "city", "photos": 1}]
    assert intent.visualMoodSuggestions == ["calm", "energetic"]
    assert intent.uncertainty == "medium"
    assert aggregate([]) == (None, {})


def test_album_intent_user_override(client_factory, intent):
    client = client_factory()
    intent.update(inputMode="album", visualSuggestions=VALID)
    intent["intent"]["targetMood"] = "energetic"
    data = client.post("/v1/discover", json=intent).json()
    assert data["inputMode"] == "album" and data["effectiveIntent"]["targetMood"] == "energetic"


def test_expired_album_and_session_limit(client_factory):
    client = client_factory(Settings(vision_mode="mock"))
    identifiers = [create(client, 1) for _ in range(20)]
    assert client.post("/v1/albums", json={"photoCount": 1}, headers=CONSENT).status_code == 429
    client.app.state.albums.sessions[identifiers[0]].expires = 0
    assert client.get(f"/v1/albums/{identifiers[0]}").status_code == 410
    create(client, 1)

import asyncio
from dataclasses import replace

import httpx
import pytest

from discovery.app import connected
from discovery.config import Settings
from discovery.errors import DiscoveryError

from .conftest import itunes_row


async def test_disconnect_cancels_upstream_work():
    finished = asyncio.Event()

    class Request:
        async def is_disconnected(self):
            return True

    async def work():
        try:
            await asyncio.sleep(10)
        finally:
            finished.set()

    with pytest.raises(DiscoveryError, match="request_cancelled"):
        await connected(Request(), work())
    assert finished.is_set()


def test_image_byte_limit_on_interface(client_factory, image_bytes):
    client = client_factory(Settings(vision_mode="mock", max_upload_bytes=10))
    response = client.post(
        "/v1/photo-intent",
        content=image_bytes,
        headers={"Content-Type": "image/jpeg", "X-Cloud-Consent": "true"},
    )
    assert response.status_code == 413
    assert client.get("/v1/providers/status").json()["vision"]["requestsMade"] == 0


def test_chunked_image_has_byte_limit(client_factory, image_bytes):
    client = client_factory(Settings(vision_mode="mock", max_upload_bytes=10))
    response = client.post(
        "/v1/photo-intent",
        content=iter([image_bytes[:5], image_bytes[5:]]),
        headers={"Content-Type": "image/jpeg", "X-Cloud-Consent": "true"},
    )
    assert response.status_code == 413


def test_mime_spoof_on_interface(client_factory, image_bytes):
    client = client_factory(Settings(vision_mode="mock"))
    response = client.post(
        "/v1/photo-intent",
        content=image_bytes,
        headers={"Content-Type": "image/png", "X-Cloud-Consent": "true"},
    )
    assert response.status_code == 415


def test_missing_key_effective_status(client_factory):
    data = client_factory(Settings(jamendo_mode="live")).get("/v1/providers/status").json()
    assert data["catalogs"][0]["mode"] == "unconfigured"
    assert data["catalogs"][0]["configured"] is False


def test_same_domain_redirect_wrong_track_excluded(client_factory, intent):
    def handler(request):
        if request.method == "HEAD":
            if request.url.path == "/wrong":
                return httpx.Response(200)
            return httpx.Response(302, headers={"location": "https://music.apple.com/wrong"})
        return httpx.Response(200, json={"results": [itunes_row()]})

    data = (
        client_factory(Settings(itunes_mode="live"), handler)
        .post("/v1/discover", json=intent)
        .json()
    )
    assert data["returned"] == 0


async def test_api_redirect_cannot_cross_provider(network):
    http = network(
        lambda r: httpx.Response(302, headers={"location": "https://api.openai.com/v1/responses"})
    )
    with pytest.raises(DiscoveryError, match="unsafe_url"):
        await http.json("https://itunes.apple.com/search", source="itunes")
    assert http.count("itunes") == 1


def test_audio_is_never_proxied(client_factory):
    client = client_factory()
    paths = client.get("/openapi.json").json()["paths"]
    assert not any("audio" in p or "proxy" in p for p in paths)


def test_unknown_license_can_keep_valid_official_page(client_factory, live_settings, intent):
    from .conftest import jamendo_row

    def handler(request):
        if request.method == "HEAD":
            return httpx.Response(200)
        return httpx.Response(
            200,
            json={"headers": {"status": "success"}, "results": [jamendo_row(license_ccurl=None)]},
        )

    client = client_factory(replace(live_settings, itunes_mode="unconfigured"), handler)
    data = client.post("/v1/discover", json=intent).json()
    assert data["tracks"][0]["capability"]["mode"] == "UNKNOWN"
    assert data["tracks"][0]["officialUrl"] == "https://www.jamendo.com/track/201"

import base64
import io
import json
from dataclasses import replace

import httpx
import pytest
from PIL import Image

from discovery.config import Settings
from discovery.errors import DiscoveryError
from discovery.vision import VisionService, prepare_image


def test_consent_precedes_image_read_or_model(client_factory, image_bytes):
    client = client_factory(Settings(vision_mode="mock"))
    response = client.post(
        "/v1/photo-intent", content=image_bytes, headers={"Content-Type": "image/jpeg"}
    )
    assert response.status_code == 403 and response.json()["error"] == "cloud_consent_required"
    assert response.json()["vision"]["state"] == "not_run"


def test_mock_image_has_explicit_mode(client_factory, image_bytes):
    client = client_factory(Settings(vision_mode="mock"))
    response = client.post(
        "/v1/photo-intent",
        content=image_bytes,
        headers={"Content-Type": "image/jpeg", "X-Cloud-Consent": "true"},
    )
    assert response.status_code == 200
    data = response.json()
    assert data["vision"]["mode"] == "mock" and not data["vision"]["liveTested"]
    assert data["processedSize"] == [40, 20]
    assert data["vision"]["requestsMade"] == 0


def test_live_missing_configuration_does_not_fallback(client_factory, image_bytes):
    client = client_factory(Settings(vision_mode="live"))
    response = client.post(
        "/v1/photo-intent",
        content=image_bytes,
        headers={"Content-Type": "image/jpeg", "X-Cloud-Consent": "true"},
    )
    assert response.status_code == 503
    assert response.json()["vision"]["mode"] == "unconfigured"


def test_orientation_resize_and_exif_removal():
    original = Image.new("RGB", (180, 60), "red")
    exif = Image.Exif()
    exif[274] = 6
    exif[270] = "PRIVATE-METADATA"
    output = io.BytesIO()
    original.save(output, "JPEG", exif=exif)
    clean, size = prepare_image(output.getvalue(), "image/jpeg", Settings(max_image_edge=90))
    assert size == (30, 90)
    assert b"PRIVATE-METADATA" not in clean
    with Image.open(io.BytesIO(clean)) as image:
        assert not image.getexif()


@pytest.mark.parametrize(
    "case,code",
    [
        ("mime", "unsupported_image"),
        ("bytes", "image_byte_limit"),
        ("pixels", "image_pixel_limit"),
        ("garbage", "invalid_image"),
    ],
)
def test_image_validation(case, code, image_bytes):
    settings = Settings()
    mime, data = "image/jpeg", image_bytes
    if case == "mime":
        mime = "image/png"
    elif case == "bytes":
        settings = replace(settings, max_upload_bytes=10)
    elif case == "pixels":
        settings = replace(settings, max_pixels=100)
    else:
        data = b"garbage"
    with pytest.raises(DiscoveryError, match=code):
        prepare_image(data, mime, settings)


def test_animation_rejected():
    output = io.BytesIO()
    Image.new("RGB", (10, 10), "red").save(
        output, "PNG", save_all=True, append_images=[Image.new("RGB", (10, 10), "blue")]
    )
    with pytest.raises(DiscoveryError, match="unsupported_image"):
        prepare_image(output.getvalue(), "image/png", Settings())


def live_config():
    return Settings(
        vision_mode="live",
        vision_model="authorized-fixture-model",
        vision_key="fixture-key",
        cloud_authorized=True,
        vision_call_budget=1,
    )


def vision_payload(output):
    return {
        "status": "completed",
        "output": [
            {"type": "message", "content": [{"type": "output_text", "text": json.dumps(output)}]}
        ],
    }


VALID = {
    "sceneTags": ["nature"],
    "visualMoodSuggestions": ["calm"],
    "musicDescriptors": ["piano"],
    "uncertainty": "medium",
}


@pytest.mark.parametrize(
    "output",
    [
        {**VALID, "songs": ["invented song"]},
        {**VALID, "license": "approved"},
        {**VALID, "sceneTags": ["https://evil.test"]},
        {**VALID, "sceneTags": ["run shell command"]},
        {**VALID, "visualMoodSuggestions": ["diagnosed depression"]},
        {**VALID, "musicDescriptors": ["piano"] * 9},
        {**VALID, "uncertainty": 2},
    ],
)
async def test_model_output_rejected(output, network, image_bytes):
    vision = VisionService(
        live_config(), network(lambda r: httpx.Response(200, json=vision_payload(output)))
    )
    with pytest.raises(DiscoveryError, match="vision_invalid_output"):
        await vision.analyze(image_bytes)
    assert vision.status().mode == "live" and vision.status().state == "error"


async def test_live_contract_and_budget_without_real_http(network, image_bytes):
    sent = []

    def handler(request):
        sent.append(request)
        return httpx.Response(200, json=vision_payload(VALID))

    vision = VisionService(live_config(), network(handler))
    assert (await vision.analyze(image_bytes)).sceneTags == ["nature"]
    payload = json.loads(sent[0].content)
    assert payload["store"] is False and payload["max_output_tokens"] == 600
    assert "tools" not in payload
    assert payload["text"]["format"]["strict"] is True
    assert "catalog" not in json.dumps(payload)
    with pytest.raises(DiscoveryError, match="vision_call_budget_exhausted"):
        await vision.analyze(image_bytes)
    assert len(sent) == 1


def test_photo_bytes_not_echoed_or_logged(client_factory, image_bytes, caplog):
    client = client_factory(Settings(vision_mode="mock"))
    response = client.post(
        "/v1/photo-intent",
        content=image_bytes,
        headers={"Content-Type": "image/jpeg", "X-Cloud-Consent": "true"},
    )
    assert base64.b64encode(image_bytes).decode() not in response.text + caplog.text


async def test_live_403_stays_live_without_mock(network, image_bytes):
    vision = VisionService(live_config(), network(lambda r: httpx.Response(403)))
    with pytest.raises(DiscoveryError, match="http_403"):
        await vision.analyze(image_bytes)
    assert vision.status().mode == "live" and not vision.status().liveTested

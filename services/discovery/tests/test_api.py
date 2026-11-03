import json
from dataclasses import replace

import httpx
import pytest

from discovery.config import Settings

from .conftest import itunes_row, jamendo_row


def test_health_demo_and_origin_boundary(client_factory):
    client = client_factory()
    assert client.get("/health").json()["status"] == "ok"
    page = client.get("/demo/")
    assert page.status_code == 200 and "Vibe Discovery" in page.text
    assert "media-src https://prod-1.storage.jamendo.com" in page.headers["content-security-policy"]
    assert "Access-Control-Allow-Origin" not in page.headers
    assert client.get("/health", headers={"Host": "evil.test"}).status_code == 400
    assert (
        client.post("/v1/discover", headers={"X-Discovery-Token": ""}, json={}).status_code == 403
    )


def test_unconfigured_is_explicit(client_factory, intent):
    client = client_factory()
    response = client.post("/v1/discover", json=intent)
    assert response.status_code == 200
    data = response.json()
    assert data["returned"] == 0 and data["requested"] == 8
    assert data["shortageReason"]
    assert {p["mode"] for p in data["catalogs"]} == {"unconfigured"}
    assert data["inputMode"] == "manual" and data["vision"]["mode"] == "unconfigured"


def test_modes_separate_no_fake_catalog(client_factory, intent):
    client = client_factory(Settings(vision_mode="mock", jamendo_mode="mock", itunes_mode="live"))
    data = client.post("/v1/discover", json=intent).json()
    assert data["vision"]["mode"] == "mock"
    assert [p["mode"] for p in data["catalogs"]] == ["mock", "live"]
    assert data["returned"] == 1 and data["tracks"][0]["ref"]["provider"] == "itunes"
    assert data["catalogs"][0]["state"] == "mock_empty"


@pytest.mark.parametrize("code", [403, 404, 429, 500])
def test_partial_failure_keeps_other_source(client_factory, intent, live_settings, code):
    def handler(request):
        if request.method == "HEAD":
            return httpx.Response(200)
        if request.url.host == "api.jamendo.com":
            return httpx.Response(code, headers={"retry-after": "120"})
        return httpx.Response(200, json={"results": [itunes_row()]})

    data = client_factory(live_settings, handler).post("/v1/discover", json=intent).json()
    assert data["returned"] == 1
    assert data["catalogs"][0]["errorCode"] == f"http_{code}"
    assert data["catalogs"][0]["mode"] == "live" and data["catalogs"][0]["state"] == "error"
    assert data["catalogs"][1]["state"] == "ok"


def test_empty_and_bad_metadata(client_factory, intent):
    client = client_factory(
        Settings(itunes_mode="live"), lambda r: httpx.Response(200, json={"results": [{}, None]})
    )
    data = client.post("/v1/discover", json=intent).json()
    assert data["returned"] == 0 and data["shortageReason"]
    assert data["catalogs"][1]["state"] == "ok"


def test_intent_override_and_confirm_gate(client_factory, intent):
    requested = []

    def handler(request):
        requested.append(request)
        return httpx.Response(200, json={"results": []})

    client = client_factory(Settings(itunes_mode="live"), handler)
    intent.update(
        visualSuggestions={
            "sceneTags": ["night"],
            "visualMoodSuggestions": ["melancholic"],
            "musicDescriptors": ["acoustic"],
            "uncertainty": "high",
        }
    )
    intent["intent"] = {"targetMood": "energetic", "musicTags": ["dance"]}
    data = client.post("/v1/discover", json=intent).json()
    assert data["effectiveIntent"]["targetMood"] == "energetic"
    assert requested[0].url.params["term"] == "dance"
    intent["confirmed"] = False
    assert client.post("/v1/discover", json=intent).status_code == 422
    assert len(requested) == 1


def test_eight_target_and_artist_diversity(client_factory, intent):
    def handler(request):
        if request.method == "HEAD":
            return httpx.Response(200)
        return httpx.Response(
            200,
            json={
                "results": [
                    itunes_row(i, "same artist" if i < 105 else None) for i in range(101, 114)
                ]
            },
        )

    data = (
        client_factory(Settings(itunes_mode="live"), handler)
        .post("/v1/discover", json=intent)
        .json()
    )
    assert data["returned"] == 8
    assert sum(t["artist"] == "same artist" for t in data["tracks"]) == 2
    assert len({t["ref"]["providerTrackId"] for t in data["tracks"]}) == 8


def test_replace_exclusions_server_enforced(client_factory, intent):
    def handler(request):
        if request.method == "HEAD":
            return httpx.Response(200)
        return httpx.Response(200, json={"results": [itunes_row(i) for i in range(101, 105)]})

    client = client_factory(Settings(itunes_mode="live"), handler)
    intent["limit"] = 1
    first = client.post("/v1/discover", json=intent).json()
    original = first["tracks"][0]["ref"]
    refs = [original]
    for _ in range(3):
        body = {**intent, "sessionId": first["sessionId"], "replacing": refs[-1], "exclude": []}
        data = client.post("/v1/discover/replace", json=body).json()
        assert data["returned"] == 1 and data["tracks"][0]["ref"] not in refs
        refs.append(data["tracks"][0]["ref"])
    data = client.post("/v1/discover/replace", json=body).json()
    assert data["returned"] == 0
    assert data["shortageReason"]


def test_excluded_id_scoped_by_provider(client_factory, intent, live_settings):
    intent["exclude"] = [{"provider": "itunes", "providerTrackId": "201"}]
    data = client_factory(live_settings).post("/v1/discover", json=intent).json()
    assert {t["ref"]["provider"] for t in data["tracks"]} == {"itunes", "jamendo"}


def test_expired_session(client_factory, intent):
    client = client_factory()
    response = client.post(
        "/v1/discover/replace",
        json={
            **intent,
            "sessionId": "x" * 24,
            "replacing": {"provider": "itunes", "providerTrackId": "101"},
        },
    )
    assert response.status_code == 410


def test_capabilities_refresh_and_unknown_blocks(client_factory, intent, live_settings):
    rows = [jamendo_row()]

    def handler(request):
        if request.method == "HEAD":
            return httpx.Response(200, headers={"content-type": "audio/mpeg"})
        return httpx.Response(200, json={"headers": {"status": "success"}, "results": rows})

    settings = replace(live_settings, itunes_mode="unconfigured")
    client = client_factory(settings, handler)
    data = client.post("/v1/discover", json=intent).json()
    path = f"/v1/providers/jamendo/tracks/201/capabilities?sessionId={data['sessionId']}"
    cap = client.get(path).json()["track"]["capability"]
    assert cap["mode"] == "IN_APP_PREVIEW" and cap["previewUrl"] and cap["expiresAt"]
    assert cap["maxPreviewSeconds"] == 30
    rows[0]["license_ccurl"] = None
    cap = client.get(path).json()["track"]["capability"]
    assert cap["mode"] == "UNKNOWN" and cap["previewUrl"] is None


def test_audio_redirect_denied_retains_official_link(client_factory, intent, live_settings):
    def handler(request):
        if request.url.host == "prod-1.storage.jamendo.com":
            return httpx.Response(302, headers={"location": "https://evil.test/music.mp3"})
        if request.method == "HEAD":
            return httpx.Response(200)
        return httpx.Response(
            200, json={"headers": {"status": "success"}, "results": [jamendo_row()]}
        )

    client = client_factory(replace(live_settings, itunes_mode="unconfigured"), handler)
    data = client.post("/v1/discover", json=intent).json()
    fresh = client.get(
        f"/v1/providers/jamendo/tracks/201/capabilities?sessionId={data['sessionId']}"
    ).json()
    assert fresh["track"]["capability"]["mode"] == "EXTERNAL_ONLY"
    assert fresh["track"]["capability"]["previewUrl"] is None
    assert fresh["track"]["officialUrl"].startswith("https://www.jamendo.com/")


def test_unavailable_capability(client_factory, intent):
    failed = False

    def handler(request):
        if failed:
            return httpx.Response(404)
        if request.method == "HEAD":
            return httpx.Response(200)
        return httpx.Response(200, json={"results": [itunes_row()]})

    client = client_factory(Settings(itunes_mode="live"), handler)
    data = client.post("/v1/discover", json=intent).json()
    failed = True
    fresh = client.get(
        f"/v1/providers/itunes/tracks/101/capabilities?sessionId={data['sessionId']}"
    ).json()
    assert fresh["capability"]["mode"] == "UNAVAILABLE" and fresh["track"] is None


def test_untrusted_official_redirect_excluded(client_factory, intent):
    def handler(request):
        if request.method == "HEAD":
            return httpx.Response(302, headers={"location": "https://evil.test/"})
        return httpx.Response(200, json={"results": [itunes_row()]})

    data = (
        client_factory(Settings(itunes_mode="live"), handler)
        .post("/v1/discover", json=intent)
        .json()
    )
    assert data["returned"] == 0


def test_secrets_not_in_status_errors_or_logs(client_factory, intent, caplog):
    secret = "fixture-secret-never-expose"
    settings = Settings(jamendo_mode="live", jamendo_client_id=secret, vision_key=secret)
    client = client_factory(settings, lambda r: httpx.Response(403, text=secret))
    status = client.get("/v1/providers/status").text
    response = client.post("/v1/discover", json=intent).text
    invalid = client.post("/v1/discover", json={"secret": secret}).text
    assert secret not in status + response + invalid + caplog.text + repr(settings)
    assert json.loads(response)["catalogs"][0]["errorCode"] == "http_403"

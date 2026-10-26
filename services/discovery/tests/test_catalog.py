from dataclasses import replace

import httpx
import pytest

from discovery.catalog import ITunesCatalog, JamendoCatalog, query_for
from discovery.config import Settings
from discovery.errors import DiscoveryError
from discovery.models import DiscoveryIntent

from .conftest import itunes_row, jamendo_row


@pytest.mark.parametrize(
    "provider,row", [(ITunesCatalog, itunes_row()), (JamendoCatalog, jamendo_row())]
)
def test_normalization_and_duplicate_ids(provider, row, live_settings, network):
    source = provider(live_settings, network(lambda r: httpx.Response(200)))
    results = source.normalize(
        [row, row, {}, None, "bad", {**row, "id": False, "trackId": False}], "piano"
    )
    assert len(results) == 1
    assert results[0].track.title.startswith("Fixture")
    assert "piano" in results[0].track.recommendationReason
    assert results[0].track.artwork is None


@pytest.mark.parametrize(
    "field,value",
    [
        ("trackName", None),
        ("trackName", 123),
        ("artistName", ""),
        ("trackId", 1.5),
        ("trackId", True),
        ("trackViewUrl", "https://music.apple.com.evil.test/a/101"),
        ("trackViewUrl", "http://music.apple.com/us/song/101"),
        ("trackViewUrl", "https://music.apple.com/us/song/999"),
    ],
)
def test_bad_rows_rejected(field, value, network):
    source = ITunesCatalog(Settings(itunes_mode="live"), network(lambda r: httpx.Response(200)))
    assert source.normalize([{**itunes_row(), field: value}], "jazz") == []


def test_itunes_never_reads_preview(network):
    source = ITunesCatalog(Settings(itunes_mode="live"), network(lambda r: httpx.Response(200)))
    track = source.normalize([itunes_row()], "jazz")[0].track
    assert track.capability.mode == "EXTERNAL_ONLY"
    assert track.capability.previewUrl is None
    assert "forbidden.m4a" not in track.model_dump_json()


@pytest.mark.parametrize(
    "change,reason",
    [
        ({"jamendo_preview_enabled": False}, "deployment_review_required"),
        ({"jamendo_noncommercial": False}, "deployment_review_required"),
        ({"jamendo_terms_accepted": False}, "deployment_review_required"),
        ({"jamendo_review_id": ""}, "deployment_review_required"),
        ({"jamendo_client_id": ""}, "mock_no_playback"),
        ({"jamendo_mode": "mock"}, "mock_no_playback"),
    ],
)
def test_deployment_gates(change, reason, live_settings, network):
    source = JamendoCatalog(
        replace(live_settings, **change), network(lambda r: httpx.Response(200))
    )
    cap = source.normalize([jamendo_row()], "piano")[0].track.capability
    assert cap.mode != "IN_APP_PREVIEW"
    assert cap.reasonCode == reason


@pytest.mark.parametrize(
    "license_value,mode",
    [
        (None, "UNKNOWN"),
        ("", "UNKNOWN"),
        (True, "UNKNOWN"),
        ("https://creativecommons.org.evil.test/licenses/by/4.0/", "UNKNOWN"),
        ("http://creativecommons.org/licenses/by-nc-nd/3.0/", "EXTERNAL_ONLY"),
        ("https://creativecommons.org/licenses/by/4.0/", "IN_APP_PREVIEW"),
    ],
)
def test_track_license_evidence(license_value, mode, live_settings, network):
    source = JamendoCatalog(live_settings, network(lambda r: httpx.Response(200)))
    cap = source.normalize([jamendo_row(license_ccurl=license_value)], "piano")[0].track.capability
    assert cap.mode == mode
    assert cap.previewUrl is None


def test_audio_url_alone_is_insufficient(live_settings, network):
    source = JamendoCatalog(live_settings, network(lambda r: httpx.Response(200)))
    row = jamendo_row(license_ccurl=None)
    assert source.normalize([row], "piano")[0].track.capability.mode == "UNKNOWN"
    row = jamendo_row(audio="http://prod-1.storage.jamendo.com/audio")
    assert source.normalize([row], "piano")[0].track.capability.mode == "EXTERNAL_ONLY"
    row = jamendo_row(audio="https://prod-1.storage.jamendo.com/?trackid=999&format=mp31")
    assert source.normalize([row], "piano")[0].track.capability.mode == "EXTERNAL_ONLY"


async def test_search_mapping_and_explicit_user_intent(network, live_settings):
    requests = []

    def handler(request):
        requests.append(request)
        return httpx.Response(200, json={"headers": {"status": "success"}, "results": []})

    source = JamendoCatalog(live_settings, network(handler))
    intent = DiscoveryIntent(
        targetMood="energetic", musicTags=["jazz"], text="piano", energy="low", instrumental=True
    )
    await source.search(intent, "JP")
    assert requests[0].url.params["search"] == "piano"
    assert requests[0].url.params["speed"] == "low"
    assert requests[0].url.params["vocalinstrumental"] == "instrumental"
    assert query_for(DiscoveryIntent(targetMood="energetic")) == "dance"


@pytest.mark.parametrize(
    "body,code",
    [({"results": {}}, "invalid_schema"), ({"headers": {"status": "failed"}}, "provider_error")],
)
async def test_provider_schema_failure(body, code, live_settings, network):
    provider = JamendoCatalog if code == "provider_error" else ITunesCatalog
    source = provider(live_settings, network(lambda r: httpx.Response(200, json=body)))
    with pytest.raises(DiscoveryError, match=code):
        await source.search(DiscoveryIntent(targetMood="calm"), "US")
    assert source.status().state == "error"

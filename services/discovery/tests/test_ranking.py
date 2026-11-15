import httpx
import pytest

from discovery.catalog import ITunesCatalog, official_link
from discovery.config import Settings
from discovery.models import DiscoveryIntent
from discovery.ranking import rank_candidates

from .conftest import itunes_row, jamendo_row


def test_metadata_rank_and_tie_order_are_deterministic_without_mutation(network):
    source = ITunesCatalog(Settings(itunes_mode="live"), network(lambda r: httpx.Response(200)))
    rows = [
        {**itunes_row(101), "primaryGenreName": "Rock"},
        {**itunes_row(102), "trackName": "Piano Story", "primaryGenreName": "Rock"},
        {**itunes_row(103), "primaryGenreName": "Jazz"},
        {**itunes_row(104), "primaryGenreName": "Jazz"},
        {**itunes_row(105), "primaryGenreName": None},
    ]
    candidates = source.normalize(rows, "piano")
    original = candidates[0].track.recommendationReason
    intent = DiscoveryIntent(targetMood="calm", musicTags=["jazz"], text="piano")
    first = rank_candidates([candidates], intent)
    second = rank_candidates([candidates], intent)
    assert [c.track.ref.providerTrackId for c in first] == ["103", "104", "102", "101", "105"]
    assert [c.track for c in first] == [c.track for c in second]
    assert candidates[0].track.recommendationReason == original
    assert "目录标签匹配 1 项" in first[0].track.recommendationReason
    assert "标题/艺人词匹配 1 项" in first[2].track.recommendationReason


def test_rank_deduplicates_and_uses_mood_only_without_user_tags_or_text(network):
    source = ITunesCatalog(Settings(itunes_mode="live"), network(lambda r: httpx.Response(200)))
    candidates = source.normalize(
        [itunes_row(101), {**itunes_row(102), "primaryGenreName": "Ambient"}], "ambient"
    )
    result = rank_candidates([candidates + candidates], DiscoveryIntent(targetMood="calm"))
    assert [c.track.ref.providerTrackId for c in result] == ["102", "101"]
    assert len(rank_candidates([], DiscoveryIntent(targetMood="calm"))) == 0


def test_exclusions_and_normalized_artist_cap_apply_across_sources(
    client_factory, intent, live_settings
):
    def handler(request):
        if request.method == "HEAD":
            return httpx.Response(200)
        if request.url.host == "api.jamendo.com":
            rows = [jamendo_row(201 + i, artist_name=" SAME  artist ") for i in range(5)]
            return httpx.Response(200, json={"headers": {"status": "success"}, "results": rows})
        return httpx.Response(
            200, json={"results": [itunes_row(301 + i, "same artist") for i in range(5)]}
        )

    intent["exclude"] = [{"provider": "jamendo", "providerTrackId": "201"}]
    data = client_factory(live_settings, handler).post("/v1/discover", json=intent).json()
    assert data["returned"] == 2
    assert {t["ref"]["providerTrackId"] for t in data["tracks"]}.isdisjoint({"201"})
    assert data["shortageReason"]


@pytest.mark.parametrize("query", ["i=999", "i=101&i=999", "i=", "i=101&i=101"])
def test_conflicting_song_selectors_cannot_use_album_path_to_bypass_identity(query):
    with pytest.raises(ValueError):
        official_link(f"https://music.apple.com/us/album/101?{query}", "itunes", "101")

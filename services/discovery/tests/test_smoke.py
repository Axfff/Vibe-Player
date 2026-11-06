import httpx
import pytest

from discovery.catalog import ITunesCatalog
from discovery.config import Settings
from discovery.models import DiscoveryIntent, DiscoveryRequest
from discovery.service import DiscoveryService
from discovery.vision import VisionService
from smoke import exercise

from .conftest import itunes_row


@pytest.mark.parametrize(
    "budget, lookup_matches, succeeds", [(15, True, True), (13, True, False), (15, False, False)]
)
async def test_workflow_checks_real_identity_and_budget(network, budget, lookup_matches, succeeds):
    requests = []

    def handler(request):
        requests.append(request)
        if request.method == "HEAD":
            return httpx.Response(200)
        if request.url.path == "/lookup":
            identifier = int(request.url.params["id"]) if lookup_matches else 999
            return httpx.Response(200, json={"results": [itunes_row(identifier)]})
        return httpx.Response(200, json={"results": [itunes_row(i) for i in range(101, 115)]})

    settings = Settings(itunes_mode="live")
    http = network(handler, budget=budget)
    service = DiscoveryService([ITunesCatalog(settings, http)], VisionService(settings, http), http)
    result = await exercise(
        service,
        DiscoveryRequest(
            intent=DiscoveryIntent(targetMood="focused", text="piano"),
            confirmed=True,
        ),
        provider="itunes",
        workflow=True,
    )
    assert result["passed"] is succeeds
    assert result["initial"]["returned"] == 8
    assert len(result["replacements"]) == 2
    ids = [t["ref"]["providerTrackId"] for t in result["initial"]["tracks"]]
    ids += [t["ref"]["providerTrackId"] for r in result["replacements"] for t in r["tracks"]]
    assert len(ids) == len(set(ids)) == 10
    assert result["requestsMade"] == len(requests) <= budget
    assert all(r.url.host in {"itunes.apple.com", "music.apple.com"} for r in requests)
    assert result["checks"]["lookup_identity"] is succeeds


async def test_live_failure_remains_visible(network):
    http = network(lambda r: httpx.Response(403))
    settings = Settings(itunes_mode="live")
    service = DiscoveryService([ITunesCatalog(settings, http)], VisionService(settings, http), http)
    result = await exercise(
        service,
        DiscoveryRequest(
            intent=DiscoveryIntent(targetMood="focused"),
            confirmed=True,
        ),
        provider="itunes",
        workflow=True,
    )
    assert not result["passed"]
    assert result["initial"]["returned"] == 0
    assert result["replacements"] == []
    assert result["catalogs"][0]["mode"] == "live"
    assert result["catalogs"][0]["errorCode"] == "http_403"
    assert result["requestsMade"] == 1

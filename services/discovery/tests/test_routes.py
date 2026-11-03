import httpx
import pytest

from discovery.errors import DiscoveryError
from discovery.network import OutboundRoute, SafeHTTP


@pytest.mark.parametrize(
    "url",
    [
        "http://example.com:7897",
        "http://192.168.1.1:7897",
        "http://localhost:7897",
        "http://127.0.0.1",
        "https://127.0.0.1:7897",
        "http://127.0.0.1:0",
        "http://user:secret@127.0.0.1:7897",
        "http://127.0.0.1:7897/path",
        "http://127.0.0.1:7897?url=x",
        "http://127.0.0.1:7897#x",
        "http://127.0.0.1:7897\n",
        "http://127.0.0.1:99999",
    ],
)
def test_proxy_is_explicit_loopback_without_credentials(url):
    with pytest.raises(ValueError, match="Local proxy"):
        OutboundRoute(url)


@pytest.mark.parametrize("url", ["http://127.0.0.1:7897", "http://[::1]:7897"])
async def test_proxy_route_keeps_destination_and_redirect_checks(url):
    route = OutboundRoute(url)
    assert route.mode == "loopback_proxy"
    await route.resolve("itunes.apple.com")
    with pytest.raises(DiscoveryError, match="unsafe_url"):
        await route.resolve("127.0.0.1")
    requests = []

    def handler(request):
        requests.append(request)
        return httpx.Response(302, headers={"location": "https://127.0.0.1/private"})

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        http = SafeHTTP(client, budget=2, resolver=route.resolve)
        with pytest.raises(DiscoveryError, match="unsafe_url"):
            await http.resolve_page("https://music.apple.com/song/1", "itunes")
    assert len(requests) == 1


async def test_direct_route_still_validates_dns(monkeypatch):
    calls = []

    async def resolve(host):
        calls.append(host)
        raise DiscoveryError("private_address")

    monkeypatch.setattr("discovery.network.public_dns", resolve)
    route = OutboundRoute()
    assert route.mode == "direct"
    with pytest.raises(DiscoveryError, match="private_address"):
        await route.resolve("itunes.apple.com")
    assert calls == ["itunes.apple.com"]

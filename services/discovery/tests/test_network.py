import asyncio
import socket

import httpx
import pytest

from discovery.errors import DiscoveryError
from discovery.network import API_HOSTS, PAGE_HOSTS, public_dns, retry_delay, validate_url


@pytest.mark.parametrize(
    "url",
    [
        "http://music.apple.com/song/1",
        "javascript:alert(1)",
        "file:///etc/passwd",
        "https://music.apple.com.evil.test/song/1",
        "https://evil-music.apple.com/song/1",
        "https://user:secret@music.apple.com/song/1",
        "https://music.apple.com:8443/song/1",
        "https://music.apple.com./song/1",
        "https://127.0.0.1/song/1",
        "https://[::1]/song/1",
        "https://music.apple.com\\@evil.test/song/1",
        "https://music.apple.com/song/1\n",
        "https://music.apple.com/song/1#x",
        "//music.apple.com/song/1",
    ],
)
def test_bad_urls(url):
    with pytest.raises(DiscoveryError, match="unsafe_url"):
        validate_url(url, PAGE_HOSTS["itunes"])


@pytest.mark.parametrize("status,count", [(403, 1), (404, 1), (429, 2), (500, 2), (503, 2)])
async def test_http_errors_and_bounded_retry(status, count, network):
    calls = []

    def handler(request):
        calls.append(request)
        return httpx.Response(status, headers={"retry-after": "0"})

    http = network(handler)
    with pytest.raises(DiscoveryError, match=f"http_{status}"):
        await http.json("https://itunes.apple.com/search", source="itunes")
    assert len(calls) == count


async def test_long_retry_after_is_returned(network):
    http = network(lambda r: httpx.Response(429, headers={"retry-after": "120"}))
    with pytest.raises(DiscoveryError, match="http_429"):
        await http.json("https://itunes.apple.com/search", source="itunes")
    assert http.count("itunes") == 1
    assert retry_delay("invalid") is None


async def test_retry_then_success(network):
    responses = iter(
        [
            httpx.Response(503, headers={"retry-after": "0"}),
            httpx.Response(200, json={"results": []}),
        ]
    )
    http = network(lambda r: next(responses))
    assert await http.json("https://itunes.apple.com/search", source="itunes") == {"results": []}
    assert http.count("itunes") == 2


@pytest.mark.parametrize("body", [b"not JSON", b"[]", b"null", b"\xff"])
async def test_wrong_json(body, network):
    http = network(lambda r: httpx.Response(200, content=body))
    with pytest.raises(DiscoveryError, match="invalid_json"):
        await http.json("https://itunes.apple.com/search", source="itunes")


async def test_timeout(network):
    def handler(request):
        raise httpx.ReadTimeout("fixture secret must not escape", request=request)

    http = network(handler)
    with pytest.raises(DiscoveryError, match="^timeout$"):
        await http.json("https://itunes.apple.com/search", source="itunes")
    assert http.count("itunes") == 2


async def test_total_deadline(network):
    async def handler(request):
        await asyncio.sleep(0.1)
        return httpx.Response(200, json={})

    http = network(handler, timeout=0.01)
    with pytest.raises(DiscoveryError, match="timeout"):
        await http.json("https://itunes.apple.com/search", source="itunes")


@pytest.mark.parametrize(
    "destination", ["http://music.apple.com/a", "https://127.0.0.1/a", "https://evil.test/a"]
)
async def test_redirect_blocked_before_request(destination, network):
    http = network(lambda r: httpx.Response(302, headers={"location": destination}))
    with pytest.raises(DiscoveryError, match="unsafe_url"):
        await http.resolve_page("https://music.apple.com/song/1", "itunes")
    assert http.count("itunes") == 1


async def test_safe_redirect_and_loop(network):
    def handler(request):
        if request.url.path == "/old":
            return httpx.Response(302, headers={"location": "/new"})
        return httpx.Response(200)

    http = network(handler)
    assert (
        await http.resolve_page("https://music.apple.com/old", "itunes")
        == "https://music.apple.com/new"
    )
    http = network(lambda r: httpx.Response(302, headers={"location": "/loop"}))
    with pytest.raises(DiscoveryError, match="redirect_limit"):
        await http.resolve_page("https://music.apple.com/loop", "itunes")
    assert http.count("itunes") == 4


async def test_post_credentials_never_redirect(network):
    http = network(
        lambda r: httpx.Response(307, headers={"location": "https://itunes.apple.com/search"})
    )
    with pytest.raises(DiscoveryError, match="redirect_rejected"):
        await http.json(
            "https://api.openai.com/v1/responses", source="vision", payload={}, retries=0
        )
    assert http.count("vision") == 1


async def test_audio_separate_policy(network):
    http = network(lambda r: httpx.Response(200, headers={"content-type": "text/html"}))
    with pytest.raises(DiscoveryError, match="unsafe_url"):
        await http.resolve_audio("https://www.jamendo.com/track/1")
    with pytest.raises(DiscoveryError, match="unsupported_audio_type"):
        await http.resolve_audio("https://prod-1.storage.jamendo.com/?trackid=1")


@pytest.mark.parametrize("ip", ["127.0.0.1", "10.0.0.1", "169.254.169.254", "::1", "192.168.1.1"])
async def test_private_dns(ip, monkeypatch):
    monkeypatch.setattr(socket, "getaddrinfo", lambda *a, **k: [(2, 1, 6, "", (ip, 443))])
    with pytest.raises(DiscoveryError, match="private_address"):
        await public_dns("music.apple.com")


async def test_budget_and_body_limit(network):
    http = network(lambda r: httpx.Response(200, content=b"12345"), budget=1)
    with pytest.raises(DiscoveryError, match="response_too_large"):
        await http.request(
            "GET", "https://itunes.apple.com/search", API_HOSTS, source="itunes", max_bytes=4
        )
    with pytest.raises(DiscoveryError, match="call_budget_exhausted"):
        await http.json("https://itunes.apple.com/search", source="itunes")

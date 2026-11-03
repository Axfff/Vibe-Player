import asyncio
import ipaddress
import json
import socket
import time
from datetime import UTC, datetime
from email.utils import parsedate_to_datetime
from urllib.parse import urljoin, urlsplit

import httpx

from .errors import DiscoveryError

PAGE_HOSTS = {
    "jamendo": frozenset({"www.jamendo.com", "jamendo.com"}),
    "itunes": frozenset({"music.apple.com", "itunes.apple.com"}),
}
# Exact host observed in the official tracks documentation; additions require evidence/review.
AUDIO_HOSTS = frozenset({"prod-1.storage.jamendo.com"})
API_HOSTS = frozenset({"api.jamendo.com", "itunes.apple.com", "api.openai.com"})
REDIRECTS = {301, 302, 303, 307, 308}


def validate_url(url: str, hosts: frozenset[str]) -> str:
    try:
        if not isinstance(url, str) or len(url) > 2048:
            raise ValueError
        if any(ord(c) <= 32 for c in url) or "\\" in url:
            raise ValueError
        parts = urlsplit(url)
        if (
            parts.scheme != "https"
            or parts.hostname not in hosts
            or parts.username
            or parts.password
            or parts.port not in (None, 443)
            or parts.fragment
        ):
            raise ValueError
    except ValueError:
        raise DiscoveryError("unsafe_url") from None
    return url


async def public_dns(host: str):
    try:
        addresses = await asyncio.to_thread(socket.getaddrinfo, host, 443, type=socket.SOCK_STREAM)
        if not addresses or any(
            not ipaddress.ip_address(item[4][0]).is_global for item in addresses
        ):
            raise DiscoveryError("private_address")
    except OSError:
        raise DiscoveryError("dns_error") from None


class OutboundRoute:
    """Explicit loopback CONNECT proxy, or public DNS for the default direct route."""

    def __init__(self, proxy_url=""):
        self.proxy = None
        if proxy_url:
            try:
                parts = urlsplit(proxy_url)
                if (
                    parts.scheme != "http"
                    or parts.hostname not in {"127.0.0.1", "::1"}
                    or parts.port is None
                    or not 1 <= parts.port <= 65535
                    or parts.username is not None
                    or parts.password is not None
                    or parts.path not in {"", "/"}
                    or parts.query
                    or parts.fragment
                    or any(ord(c) <= 32 for c in proxy_url)
                    or "\\" in proxy_url
                ):
                    raise ValueError
            except ValueError:
                raise ValueError(
                    "Local proxy must be an HTTP loopback IP and port, without credentials"
                ) from None
            self.proxy = proxy_url

    @property
    def mode(self):
        return "loopback_proxy" if self.proxy else "direct"

    def client(self, **options):
        # HTTPS destinations use CONNECT plus the client's normal certificate verification.
        return httpx.AsyncClient(proxy=self.proxy, trust_env=False, **options)

    async def resolve(self, host):
        if self.proxy:
            # The configured local proxy resolves destination DNS. Every URL/redirect still
            # passes its purpose-specific allowlist before this method or the CONNECT request.
            if host not in API_HOSTS | AUDIO_HOSTS | PAGE_HOSTS["itunes"] | PAGE_HOSTS["jamendo"]:
                raise DiscoveryError("unsafe_url")
        else:
            await public_dns(host)


def retry_delay(value: str | None) -> float | None:
    """Never shorten a provider-requested wait. Long Retry-After returns to the user."""
    if not value:
        return 0.25
    try:
        wait = float(value)
    except ValueError:
        try:
            wait = (parsedate_to_datetime(value) - datetime.now(UTC)).total_seconds()
        except (TypeError, ValueError, OverflowError):
            return None
    return max(0, wait) if wait <= 2 else None


class SafeHTTP:
    def __init__(
        self, client: httpx.AsyncClient, budget: int, timeout: float = 8, resolver=public_dns
    ):
        self.client = client
        self.budget = budget
        self.timeout = timeout
        self.resolver = resolver
        self.calls: dict[str, int] = {}
        self._recent: dict[str, list[float]] = {}

    def count(self, name):
        return self.calls.get(name, 0)

    async def request(
        self,
        method,
        url,
        hosts,
        *,
        source,
        params=None,
        payload=None,
        headers=None,
        retries=1,
        max_bytes=1_000_000,
    ):
        # Total deadline includes DNS, all redirect hops, body reads and one retry.
        try:
            async with asyncio.timeout(self.timeout):
                return await self._request(
                    method, url, hosts, source, params, payload, headers, retries, max_bytes
                )
        except TimeoutError:
            raise DiscoveryError("timeout") from None

    async def _request(self, method, url, hosts, source, params, payload, headers, retries, cap):
        for attempt in range(retries + 1):
            current = url
            current_params = params
            retry = False
            for hop in range(4):
                validate_url(current, hosts)
                await self.resolver(urlsplit(current).hostname)
                if sum(self.calls.values()) >= self.budget:
                    raise DiscoveryError("call_budget_exhausted", 429)
                recent = [t for t in self._recent.get(source, []) if time.monotonic() - t < 60]
                if len(recent) >= 18:
                    raise DiscoveryError("local_rate_limit", 429)
                self._recent[source] = [*recent, time.monotonic()]
                self.calls[source] = self.count(source) + 1
                try:
                    async with self.client.stream(
                        method,
                        current,
                        params=current_params,
                        json=payload,
                        headers=headers,
                        follow_redirects=False,
                    ) as response:
                        if response.status_code in REDIRECTS:
                            if method != "GET" and method != "HEAD":
                                raise DiscoveryError("redirect_rejected")
                            if hop == 3 or not response.headers.get("location"):
                                raise DiscoveryError("redirect_limit")
                            current = urljoin(str(response.url), response.headers["location"])
                            current_params = None
                            continue
                        if response.status_code == 429 or response.status_code >= 500:
                            delay = retry_delay(response.headers.get("retry-after"))
                            if attempt < retries and delay is not None:
                                retry = True
                            else:
                                raise DiscoveryError(f"http_{response.status_code}")
                        elif response.status_code != 200:
                            raise DiscoveryError(f"http_{response.status_code}")
                        if not retry:
                            data = bytearray()
                            if method != "HEAD":
                                async for chunk in response.aiter_bytes():
                                    data.extend(chunk)
                                    if len(data) > cap:
                                        raise DiscoveryError("response_too_large")
                            return bytes(data), str(response.url), response.headers
                except httpx.TimeoutException:
                    if attempt == retries:
                        raise DiscoveryError("timeout") from None
                    delay, retry = 0.25, True
                except httpx.HTTPError:
                    raise DiscoveryError("network_error") from None
                if retry:
                    await asyncio.sleep(delay)
                    break
        raise DiscoveryError("network_error")

    async def json(self, url, *, source, params=None, payload=None, headers=None, retries=1):
        validate_url(url, API_HOSTS)
        body, _, _ = await self.request(
            "POST" if payload is not None else "GET",
            url,
            frozenset({urlsplit(url).hostname}),
            source=source,
            params=params,
            payload=payload,
            headers=headers,
            retries=retries,
        )
        try:
            parsed = json.loads(body)
            if not isinstance(parsed, dict):
                raise ValueError
            return parsed
        except (ValueError, UnicodeError):
            raise DiscoveryError("invalid_json") from None

    async def resolve_page(self, url, provider):
        _, final, _ = await self.request(
            "HEAD", url, PAGE_HOSTS[provider], source=provider, retries=0
        )
        return validate_url(final, PAGE_HOSTS[provider])

    async def resolve_audio(self, url):
        _, final, headers = await self.request(
            "HEAD", url, AUDIO_HOSTS, source="jamendo", retries=0
        )
        if headers.get("content-type", "").split(";")[0].lower() not in {
            "audio/mpeg",
            "audio/mp3",
        }:
            raise DiscoveryError("unsupported_audio_type")
        return validate_url(final, AUDIO_HOSTS)

import copy
import io

import httpx
import pytest
from fastapi.testclient import TestClient
from PIL import Image

from discovery.app import create_app
from discovery.config import Settings
from discovery.network import SafeHTTP
from discovery.vision import VisionService


def itunes_row(identifier=101, artist=None):
    # Synthetic schema fixture, restricted to tests. Never served by the mock demo.
    return {
        "kind": "song",
        "trackId": identifier,
        "trackName": f"Fixture song {identifier}",
        "artistName": artist or f"Fixture artist {identifier}",
        "primaryGenreName": "Jazz",
        "trackViewUrl": f"https://music.apple.com/us/album/fixture/900?i={identifier}",
        "previewUrl": "https://audio-ssl.itunes.apple.com/forbidden.m4a",
    }


def jamendo_row(identifier=201, **updates):
    return {
        "id": str(identifier),
        "name": f"Fixture song {identifier}",
        "artist_name": f"Fixture artist {identifier}",
        "shareurl": f"https://www.jamendo.com/track/{identifier}",
        "audio": f"https://prod-1.storage.jamendo.com/?trackid={identifier}&format=mp31",
        "license_ccurl": "https://creativecommons.org/licenses/by/4.0/",
        "musicinfo": {"tags": {"genres": ["jazz"], "instruments": ["piano"]}},
        **updates,
    }


async def resolved(host):
    pass


@pytest.fixture
def network():
    clients = []

    def build(handler, budget=100, timeout=2):
        client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
        clients.append(client)
        return SafeHTTP(client, budget=budget, timeout=timeout, resolver=resolved)

    return build


@pytest.fixture
def intent():
    return {
        "intent": {"targetMood": "calm", "musicTags": ["piano"], "text": ""},
        "confirmed": True,
        "market": "US",
    }


@pytest.fixture
def image_bytes():
    output = io.BytesIO()
    Image.new("RGB", (40, 20), "green").save(output, "JPEG")
    return output.getvalue()


@pytest.fixture
def client_factory(network):
    clients = []

    def make(settings=None, handler=None, vision=None):
        settings = settings or Settings()

        def default(request):
            if request.method == "HEAD":
                return httpx.Response(200, headers={"content-type": "audio/mpeg"})
            if request.url.host == "api.jamendo.com":
                return httpx.Response(
                    200, json={"headers": {"status": "success"}, "results": [jamendo_row()]}
                )
            return httpx.Response(200, json={"results": [itunes_row()]})

        http = network(handler or default)
        vision = vision or VisionService(settings, http)
        client = TestClient(
            create_app(settings, http=http, vision=vision), base_url="http://127.0.0.1"
        )
        client.__enter__()
        client.headers["X-Discovery-Token"] = client.get("/v1/providers/status").json()["demoToken"]
        clients.append(client)
        return client

    yield make
    for client in clients:
        client.__exit__(None, None, None)


@pytest.fixture
def live_settings():
    return Settings(
        itunes_mode="live",
        jamendo_mode="live",
        jamendo_client_id="fixture-only",
        jamendo_preview_enabled=True,
        jamendo_noncommercial=True,
        jamendo_terms_accepted=True,
        jamendo_review_id="test-fixture-review",
    )


@pytest.fixture
def copy_intent(intent):
    return copy.deepcopy(intent)

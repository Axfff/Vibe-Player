import asyncio
import hmac
import logging
import secrets
from contextlib import asynccontextmanager, suppress

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles
from starlette.middleware.trustedhost import TrustedHostMiddleware

from .albums import MAX_ALBUM_PHOTOS, AlbumService
from .catalog import ITunesCatalog, JamendoCatalog
from .config import ROOT, Settings
from .errors import DiscoveryError
from .models import (
    AlbumCreateRequest,
    AlbumFailureRequest,
    DiscoveryRequest,
    DiscoveryResponse,
    ReplaceRequest,
    TrackRef,
)
from .network import OutboundRoute, SafeHTTP
from .policy import REVIEW_DATE
from .service import DiscoveryService
from .vision import VisionService, prepare_image


async def connected(request, coroutine):
    task = asyncio.create_task(coroutine)
    try:
        while not task.done():
            await asyncio.wait({task}, timeout=0.2)
            if not task.done() and await request.is_disconnected():
                raise DiscoveryError("request_cancelled", 499)
        return await task
    finally:
        if not task.done():
            task.cancel()
            with suppress(asyncio.CancelledError):
                await task


def create_app(settings=None, http=None, vision=None, catalogs=None):
    settings = settings or Settings.load()
    logging.getLogger("httpx").setLevel(logging.WARNING)
    logging.getLogger("httpcore").setLevel(logging.WARNING)
    route = OutboundRoute(settings.local_proxy_url)
    client = route.client(timeout=settings.timeout_seconds)
    http = http or SafeHTTP(
        client, settings.catalog_call_budget, settings.timeout_seconds, resolver=route.resolve
    )
    vision_http = SafeHTTP(client, settings.vision_call_budget, timeout=30, resolver=route.resolve)
    vision = vision or VisionService(settings, vision_http)
    catalogs = catalogs or [JamendoCatalog(settings, http), ITunesCatalog(settings, http)]
    service = DiscoveryService(catalogs, vision, http)
    albums = AlbumService(vision, settings)
    token = secrets.token_urlsafe(32)

    @asynccontextmanager
    async def lifespan(app):
        yield
        await client.aclose()

    app = FastAPI(
        title="Vibe Discovery", version="0.1.0", lifespan=lifespan, docs_url=None, redoc_url=None
    )
    app.state.service = service
    app.state.albums = albums
    app.add_middleware(TrustedHostMiddleware, allowed_hosts=["127.0.0.1", "localhost"])

    @app.middleware("http")
    async def local_boundary(request, call_next):
        if request.url.path.startswith("/v1/") and request.url.path != "/v1/providers/status":
            if not hmac.compare_digest(request.headers.get("x-discovery-token", ""), token):
                return JSONResponse({"error": "local_token_required"}, 403)
        if request.method == "POST":
            value = request.headers.get("content-length")
            if value and (not value.isdecimal() or int(value) > settings.max_upload_bytes):
                return JSONResponse({"error": "request_byte_limit"}, 413)
        response = await call_next(request)
        response.headers.update(
            {
                "Cache-Control": "no-store",
                "Referrer-Policy": "no-referrer",
                "X-Content-Type-Options": "nosniff",
                "Content-Security-Policy": (
                    "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; "
                    "img-src 'self' blob:; media-src https://prod-1.storage.jamendo.com; "
                    "frame-ancestors 'none'; base-uri 'none'; form-action 'self'"
                ),
                "Permissions-Policy": "camera=(), microphone=(), geolocation=()",
            }
        )
        return response

    @app.exception_handler(DiscoveryError)
    async def known_error(request, error):
        return JSONResponse(
            {
                "error": error.code,
                "vision": vision.status().model_dump(),
                "catalogs": [p.status().model_dump() for p in catalogs],
            },
            error.status,
        )

    @app.exception_handler(RequestValidationError)
    async def validation_error(request, error):
        return JSONResponse(
            {
                "error": "invalid_request",
                "fields": [{"path": list(e["loc"]), "type": e["type"]} for e in error.errors()],
            },
            422,
        )

    @app.get("/health")
    async def health():
        return {"status": "ok", "service": "vibe-discovery", "version": "0.1.0"}

    @app.get("/v1/providers/status")
    async def status():
        return {
            "vision": vision.status(),
            "catalogs": [p.status() for p in catalogs],
            "cloudRecipient": "OpenAI" if vision.status().mode == "live" else "无云端接收者",
            "visionModel": settings.vision_model if vision.status().mode == "live" else None,
            "policyReviewedAt": REVIEW_DATE,
            "demoToken": token,
            "limits": {
                "uploadBytes": settings.max_upload_bytes,
                "pixels": settings.max_pixels,
                "imageEdge": settings.max_image_edge,
                "previewSeconds": 30,
                "albumPhotos": MAX_ALBUM_PHOTOS,
                "visionCallsRemaining": max(
                    0, settings.vision_call_budget - vision.status().requestsMade
                ),
            },
            "defaultMarket": "US",
            "marketBasis": "explicit_test_configuration",
            "networkRoute": route.mode,
        }

    def photo_consent(request):
        if request.headers.get("x-cloud-consent") != "true":
            raise DiscoveryError("cloud_consent_required", 403)
        if vision.status().mode == "unconfigured":
            raise DiscoveryError("vision_not_configured", 503)

    async def read_image(request):
        body = bytearray()
        async for chunk in request.stream():
            body.extend(chunk)
            if len(body) > settings.max_upload_bytes:
                raise DiscoveryError("image_byte_limit", 413)
        return bytes(body)

    @app.post("/v1/photo-intent")
    async def photo_intent(request: Request):
        photo_consent(request)
        body = await read_image(request)
        jpeg, size = await asyncio.to_thread(
            prepare_image,
            body,
            request.headers.get("content-type", ""),
            settings,
        )
        del body
        if await request.is_disconnected():
            raise DiscoveryError("request_cancelled", 499)
        output = await connected(request, vision.analyze(jpeg))
        return {
            "intent": output,
            "vision": vision.status(),
            "catalogs": [p.status() for p in catalogs],
            "processedSize": list(size),
        }

    def album_response(data):
        return {**data, "catalogs": [p.status() for p in catalogs]}

    @app.post("/v1/albums", status_code=201)
    async def create_album(body: AlbumCreateRequest, request: Request):
        photo_consent(request)
        return album_response(albums.create(body.photoCount))

    @app.get("/v1/albums/{album_id}")
    async def album_status(album_id: str):
        return album_response(albums.snapshot(album_id))

    @app.post("/v1/albums/{album_id}/photos/{index}")
    async def album_photo(album_id: str, index: int, request: Request):
        photo_consent(request)
        albums.ready(album_id, index)
        data = await read_image(request)
        if await request.is_disconnected():
            raise DiscoveryError("request_cancelled", 499)
        result = await connected(
            request, albums.process(album_id, index, data, request.headers.get("content-type", ""))
        )
        return album_response(result)

    @app.post("/v1/albums/{album_id}/failures/{index}")
    async def album_failure(album_id: str, index: int, body: AlbumFailureRequest):
        return album_response(await albums.record_failure(album_id, index, body.errorCode))

    @app.delete("/v1/albums/{album_id}")
    async def cancel_album(album_id: str):
        return album_response(await albums.cancel(album_id))

    @app.post("/v1/discover", response_model=DiscoveryResponse)
    async def discover(body: DiscoveryRequest, request: Request):
        return await connected(request, service.discover(body))

    @app.post("/v1/discover/replace", response_model=DiscoveryResponse)
    async def replace(body: ReplaceRequest, request: Request):
        return await connected(request, service.discover(body, replace=True))

    @app.get("/v1/providers/{provider}/tracks/{track_id}/capabilities")
    async def track_capabilities(provider: str, track_id: str, sessionId: str, request: Request):
        try:
            ref = TrackRef(provider=provider, providerTrackId=track_id)
        except ValueError:
            raise DiscoveryError("invalid_track_ref", 422) from None
        return await connected(request, service.capabilities(ref, sessionId))

    @app.get("/")
    async def index():
        return RedirectResponse("/demo/")

    app.mount("/demo", StaticFiles(directory=ROOT / "static", html=True), name="demo")
    return app


app = create_app()

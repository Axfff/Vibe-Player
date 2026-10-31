"""Sequential album analysis. Only validated labels and counters live in memory."""

import asyncio
import secrets
import time
from collections import Counter
from contextlib import suppress
from dataclasses import dataclass, field

from .errors import DiscoveryError
from .models import VisionOutput
from .vision import prepare_image

MAX_ALBUM_PHOTOS = 500
PAUSE_ERRORS = {
    "vision_call_budget_exhausted",
    "call_budget_exhausted",
    "local_rate_limit",
    "http_401",
    "http_403",
    "http_429",
    "vision_not_configured",
}


@dataclass
class Album:
    total: int
    expires: float
    results: dict[int, VisionOutput] = field(default_factory=dict)
    errors: dict[int, str] = field(default_factory=dict)
    cancelled: bool = False
    pause_reason: str | None = None
    lock: asyncio.Lock = field(default_factory=asyncio.Lock)
    active: asyncio.Task | None = None


def aggregate(outputs):
    """One vote per photo/tag; no second model call and no directory/audio inputs."""
    if not outputs:
        return None, {}
    counts = {
        name: Counter(tag for output in outputs for tag in set(getattr(output, name)))
        for name in ("sceneTags", "visualMoodSuggestions", "musicDescriptors")
    }
    ranked = {
        name: sorted(counter.items(), key=lambda pair: (-pair[1], pair[0]))
        for name, counter in counts.items()
    }
    mood_count = ranked["visualMoodSuggestions"][0][1]
    if any(output.uncertainty == "high" for output in outputs) or mood_count * 2 <= len(outputs):
        uncertainty = "high"
    elif all(output.uncertainty == "low" for output in outputs) and mood_count == len(outputs):
        uncertainty = "low"
    else:
        uncertainty = "medium"
    intent = VisionOutput(
        sceneTags=[tag for tag, _ in ranked["sceneTags"][:8]],
        visualMoodSuggestions=[tag for tag, _ in ranked["visualMoodSuggestions"][:3]],
        musicDescriptors=[tag for tag, _ in ranked["musicDescriptors"][:8]],
        uncertainty=uncertainty,
    )
    frequencies = {
        name: [{"tag": tag, "photos": count} for tag, count in values]
        for name, values in ranked.items()
    }
    return intent, frequencies


class AlbumService:
    def __init__(self, vision, settings):
        self.vision, self.settings = vision, settings
        self.sessions: dict[str, Album] = {}

    def prune(self):
        for key, album in list(self.sessions.items()):
            if album.expires <= time.monotonic() and album.active is None:
                del self.sessions[key]

    def create(self, total):
        self.prune()
        if self.vision.status().mode == "unconfigured":
            raise DiscoveryError("vision_not_configured", 503)
        if self.vision.status().mode == "live":
            remaining = self.settings.vision_call_budget - self.vision.status().requestsMade
            if total > remaining:
                raise DiscoveryError("album_budget_insufficient", 429)
        if len(self.sessions) >= 20:
            raise DiscoveryError("album_session_limit", 429)
        identifier = secrets.token_urlsafe(24)
        self.sessions[identifier] = Album(total=total, expires=time.monotonic() + 1200)
        return self.snapshot(identifier)

    def get(self, identifier):
        self.prune()
        if identifier not in self.sessions:
            raise DiscoveryError("album_expired", 410)
        return self.sessions[identifier]

    def ready(self, identifier, index):
        album = self.get(identifier)
        if not 0 <= index < album.total:
            raise DiscoveryError("album_index_out_of_range", 422)
        if album.cancelled:
            raise DiscoveryError("album_cancelled", 409)
        if album.pause_reason:
            raise DiscoveryError(album.pause_reason, 429)
        return album

    def snapshot(self, identifier):
        album = self.get(identifier)
        intent, frequencies = aggregate(list(album.results.values()))
        processed = len(album.results) + len(album.errors)
        state = "processing" if processed else "ready"
        if processed == album.total:
            state = "completed_with_errors" if album.errors else "completed"
        if album.pause_reason:
            state = "paused"
        if album.cancelled:
            state = "cancelled"
        if intent and len(album.results) < album.total:
            intent.uncertainty = "high"
        return {
            "albumId": identifier,
            "total": album.total,
            "processed": processed,
            "succeeded": len(album.results),
            "failed": len(album.errors),
            "pending": album.total - processed,
            "state": state,
            "pauseReason": album.pause_reason,
            "intent": intent,
            "frequencies": frequencies,
            "vision": self.vision.status(),
            "items": [
                {"index": index, "state": "succeeded", "intent": output}
                for index, output in sorted(album.results.items())
            ]
            + [
                {"index": index, "state": "failed", "errorCode": code}
                for index, code in sorted(album.errors.items())
            ],
        }

    async def process(self, identifier, index, data, mime):
        album = self.ready(identifier, index)
        async with album.lock:
            self.ready(identifier, index)
            if index in album.results or index in album.errors:
                return self.snapshot(identifier)
            album.active = asyncio.current_task()
            album.expires = time.monotonic() + 1200
            try:
                jpeg, _ = await asyncio.to_thread(prepare_image, data, mime, self.settings)
                output = await self.vision.analyze(jpeg)
                if not album.cancelled:
                    album.results[index] = output
            except DiscoveryError as error:
                album.errors[index] = error.code
                if error.code in PAUSE_ERRORS:
                    album.pause_reason = error.code
            except asyncio.CancelledError:
                album.errors[index] = "request_cancelled"
                if album.cancelled:
                    raise DiscoveryError("album_cancelled", 409) from None
                raise
            finally:
                album.active = None
            return self.snapshot(identifier)

    async def record_failure(self, identifier, index, error_code):
        album = self.ready(identifier, index)
        async with album.lock:
            self.ready(identifier, index)
            if index not in album.results and index not in album.errors:
                album.errors[index] = error_code
            album.expires = time.monotonic() + 1200
            return self.snapshot(identifier)

    async def cancel(self, identifier):
        album = self.get(identifier)
        album.cancelled = True
        if album.active is not None:
            active = album.active
            active.cancel()
            with suppress(asyncio.CancelledError, DiscoveryError):
                await active
        return self.snapshot(identifier)

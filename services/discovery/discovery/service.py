import asyncio
import secrets
import time
from collections import Counter
from dataclasses import dataclass, field
from datetime import UTC, datetime, timedelta
from itertools import zip_longest

from .catalog import official_link
from .errors import DiscoveryError
from .models import (
    CapabilityMode,
    DiscoveryResponse,
    PlaybackCapability,
)
from .policy import now_iso


@dataclass
class Session:
    expires: float
    market: str
    seen: set[str] = field(default_factory=set)
    tracks: dict = field(default_factory=dict)


class DiscoveryService:
    def __init__(self, catalogs, vision, http):
        self.catalogs, self.vision, self.http = catalogs, vision, http
        self.sessions: dict[str, Session] = {}
        # Serialize discovery/replacement mutations: exclusion and budget checks are atomic.
        self.lock = asyncio.Lock()

    def get_session(self, identifier):
        for key in list(self.sessions):
            if self.sessions[key].expires <= time.monotonic():
                del self.sessions[key]
        if identifier not in self.sessions:
            raise DiscoveryError("session_expired", 410)
        return self.sessions[identifier]

    async def discover(self, request, replace=False):
        async with self.lock:
            deadline = time.monotonic() + 35
            if replace:
                session_id = request.sessionId
                session = self.get_session(session_id)
                if request.replacing.key not in session.tracks:
                    raise DiscoveryError("track_not_in_session", 404)
                # Reserve before network I/O; failure still preserves the excluded identity.
                session.seen.add(request.replacing.key)
                if len(session.seen) >= 500:
                    raise DiscoveryError("session_exclusion_limit", 409)
            else:
                session_id = secrets.token_urlsafe(24)
                session = Session(time.monotonic() + 1200, request.market)
                if len(self.sessions) >= 100:
                    self.sessions.pop(next(iter(self.sessions)))
                self.sessions[session_id] = session
            excluded = session.seen | {ref.key for ref in request.exclude}
            if len(excluded) > 500:
                raise DiscoveryError("session_exclusion_limit", 409)
            session.seen.update(excluded)
            session.market = request.market
            limit = 1 if replace else request.limit
            results = await asyncio.gather(
                *(provider.search(request.intent, request.market) for provider in self.catalogs),
                return_exceptions=True,
            )
            groups = [value if isinstance(value, list) else [] for value in results]
            pool = [candidate for row in zip_longest(*groups) for candidate in row if candidate]
            tracks, artists = [], Counter()
            selection_issues = Counter()
            # Limit validation traffic as well as results. No unbounded pagination/fallback search.
            inspected = 0
            for candidate in pool:
                track = candidate.track
                artist_key = (track.ref.provider, track.artist.casefold())
                if track.ref.key in excluded or artists[artist_key] >= 2:
                    continue
                if inspected >= 12:
                    break
                if time.monotonic() >= deadline:
                    break
                inspected += 1
                try:
                    track.officialUrl = official_link(
                        await self.http.resolve_page(track.officialUrl, track.ref.provider),
                        track.ref.provider,
                        track.ref.providerTrackId,
                    )
                except (DiscoveryError, ValueError) as error:
                    code = (
                        error.code
                        if isinstance(error, DiscoveryError)
                        else "official_track_mismatch"
                    )
                    selection_issues[(track.ref.provider, code)] += 1
                    if code == "call_budget_exhausted":
                        break
                    continue
                tracks.append(track)
                excluded.add(track.ref.key)
                artists[artist_key] += 1
                session.tracks[track.ref.key] = track
                session.seen.add(track.ref.key)
                if len(tracks) == limit:
                    break
            statuses = [provider.status() for provider in self.catalogs]
            shortage = None
            if len(tracks) < limit:
                shortage = (
                    f"目标 {limit} 首，返回 {len(tracks)} 首。"
                    "已按来源状态、有效官方链接、排除列表及每来源同艺人最多两首筛选。"
                )
            return DiscoveryResponse(
                sessionId=session_id,
                inputMode=request.inputMode,
                effectiveIntent=request.intent,
                market=request.market,
                tracks=tracks,
                requested=limit,
                returned=len(tracks),
                shortageReason=shortage,
                vision=self.vision.status(),
                catalogs=statuses,
                selectionIssues=[
                    {"provider": provider, "reasonCode": code, "count": count}
                    for (provider, code), count in selection_issues.items()
                ],
            )

    async def capabilities(self, ref, session_id):
        session = self.get_session(session_id)
        if ref.key not in session.tracks:
            raise DiscoveryError("track_not_in_session", 404)
        provider = next(p for p in self.catalogs if p.name == ref.provider)
        try:
            candidate = await provider.lookup(ref.providerTrackId, session.market)
            if candidate is None:
                raise DiscoveryError("track_unavailable", 404)
            track = candidate.track
            try:
                track.officialUrl = official_link(
                    await self.http.resolve_page(track.officialUrl, ref.provider),
                    ref.provider,
                    ref.providerTrackId,
                )
            except ValueError:
                raise DiscoveryError("official_track_mismatch") from None
            cap = track.capability
            if cap.mode == CapabilityMode.IN_APP_PREVIEW:
                try:
                    cap.previewUrl = await self.http.resolve_audio(candidate.audio)
                    cap.expiresAt = (datetime.now(UTC) + timedelta(seconds=60)).isoformat()
                except DiscoveryError as error:
                    cap.mode, cap.reasonCode = CapabilityMode.EXTERNAL_ONLY, error.code
            session.tracks[ref.key] = track
            return {"track": track, "vision": self.vision.status(), "catalog": provider.status()}
        except DiscoveryError as error:
            return {
                "track": None,
                "capability": PlaybackCapability(
                    mode=CapabilityMode.UNAVAILABLE,
                    reasonCode=error.code,
                    checkedAt=now_iso(),
                ),
                "vision": self.vision.status(),
                "catalog": provider.status(),
            }

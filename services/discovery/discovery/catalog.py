import re
from dataclasses import dataclass
from typing import Protocol
from urllib.parse import parse_qs, urlsplit

from pydantic import ValidationError

from .config import Settings
from .errors import DiscoveryError
from .models import CatalogTrack, DiscoveryIntent, ProviderStatus, TrackRef
from .network import PAGE_HOSTS, SafeHTTP, validate_url
from .policy import APPLE_TERMS, JAMENDO_TERMS, capability

MOOD_TERMS = {
    "calm": "ambient",
    "bright": "pop",
    "energetic": "dance",
    "melancholic": "acoustic",
    "dreamy": "ambient",
    "focused": "instrumental",
}


def query_for(intent: DiscoveryIntent):
    # User-confirmed text/tags take precedence over the finite mood expansion.
    return intent.text or " ".join(dict.fromkeys(intent.musicTags)) or MOOD_TERMS[intent.targetMood]


@dataclass
class Candidate:
    track: CatalogTrack
    audio: str = ""
    license_value: str = ""


class CatalogProvider(Protocol):
    name: str

    def status(self) -> ProviderStatus: ...
    async def search(self, intent: DiscoveryIntent, market: str) -> list[Candidate]: ...
    async def lookup(self, track_id: str, market: str) -> Candidate | None: ...


def track_id(value):
    if isinstance(value, bool) or not isinstance(value, (str, int)):
        raise ValueError
    value = str(value)
    if not re.fullmatch(r"[1-9][0-9]{0,19}", value):
        raise ValueError
    return value


def text_field(value):
    if not isinstance(value, str) or not value.strip() or len(value) > 300:
        raise ValueError
    return value.strip()


def official_link(value, provider, identifier):
    validate_url(value, PAGE_HOSTS[provider])
    parsed = urlsplit(value)
    if provider == "jamendo":
        if not re.match(rf"^/track/{identifier}(?:/|$)", parsed.path):
            raise ValueError
    else:
        song_id = parse_qs(parsed.query).get("i", [None])[0]
        if song_id != identifier and not re.search(rf"/(?:id)?{identifier}/?$", parsed.path):
            raise ValueError
    return value


class BaseCatalog:
    name: str

    def __init__(self, settings: Settings, http: SafeHTTP):
        self.settings, self.http = settings, http
        self.mode = getattr(settings, f"{self.name}_mode")
        if self.name == "jamendo" and self.mode == "live" and not settings.jamendo_client_id:
            self.mode = "unconfigured"
        self.state, self.error, self.live_tested = "not_run", None, False

    def status(self):
        issues = []
        if getattr(self.settings, f"{self.name}_mode") == "unconfigured":
            issues.append("mode_not_enabled")
        if self.name == "jamendo" and not self.settings.jamendo_client_id:
            issues.append("project_client_id_required")
        return ProviderStatus(
            provider=self.name,
            mode=self.mode,
            configured=self.mode != "unconfigured",
            policyBasis=APPLE_TERMS if self.name == "itunes" else JAMENDO_TERMS,
            configurationIssues=issues,
            previewPolicyReady=(
                self.name == "jamendo"
                and self.mode == "live"
                and self.settings.jamendo_preview_enabled
                and self.settings.jamendo_terms_accepted
                and self.settings.jamendo_noncommercial
                and bool(self.settings.jamendo_review_id)
            ),
            state=self.state,
            errorCode=self.error,
            liveTested=self.live_tested,
            requestsMade=self.http.count(self.name),
        )

    async def fetch(self, url, params):
        if self.mode != "live":
            self.state = "mock_empty" if self.mode == "mock" else "not_configured"
            return []
        try:
            body = await self.http.json(url, source=self.name, params=params)
            if self.name == "jamendo":
                header = body.get("headers")
                if not isinstance(header, dict) or header.get("status") != "success":
                    raise DiscoveryError("provider_error")
            rows = body.get("results")
            if not isinstance(rows, list):
                raise DiscoveryError("invalid_schema")
            if len(rows) > 200:
                raise DiscoveryError("invalid_schema")
            self.state, self.error, self.live_tested = "ok", None, True
            return rows
        except DiscoveryError as error:
            self.state, self.error = "error", error.code
            raise

    def normalize(self, rows, query):
        results, seen = [], set()
        for row in rows:
            try:
                candidate = self.parse(row, query)
                key = candidate.track.ref.key
                if key not in seen:
                    results.append(candidate)
                    seen.add(key)
            except (KeyError, ValueError, TypeError, DiscoveryError, ValidationError):
                continue
        return results

    async def lookup(self, track_id, market):
        raise NotImplementedError


class ITunesCatalog(BaseCatalog):
    name = "itunes"

    async def search(self, intent, market):
        query = query_for(intent)
        rows = await self.fetch(
            "https://itunes.apple.com/search",
            {
                "term": query,
                "country": market,
                "media": "music",
                "entity": "song",
                "limit": 40,
                "explicit": "No",
            },
        )
        return self.normalize(rows, query)

    async def lookup(self, track_id, market):
        rows = await self.fetch(
            "https://itunes.apple.com/lookup",
            {
                "id": track_id,
                "country": market,
                "entity": "song",
            },
        )
        return next(
            (
                c
                for c in self.normalize(rows, "ID lookup")
                if c.track.ref.providerTrackId == track_id
            ),
            None,
        )

    def parse(self, row, query):
        if not isinstance(row, dict) or row.get("kind") != "song":
            raise ValueError
        identifier = track_id(row["trackId"])
        title, artist = text_field(row["trackName"]), text_field(row["artistName"])
        genre = row.get("primaryGenreName")
        tags = [genre] if isinstance(genre, str) and 0 < len(genre) <= 40 else []
        return Candidate(
            CatalogTrack(
                ref=TrackRef(provider=self.name, providerTrackId=identifier),
                title=title,
                artist=artist,
                officialUrl=official_link(row["trackViewUrl"], self.name, identifier),
                tags=tags,
                recommendationReason=(
                    f"iTunes 文本检索「{query}」；目录流派：{', '.join(tags) or '未提供'}。"
                ),
                capability=capability(
                    self.name, title, artist, None, None, self.settings, self.mode
                ),
            )
        )  # previewUrl and artwork fields are deliberately never read.


class JamendoCatalog(BaseCatalog):
    name = "jamendo"

    def params(self):
        return {
            "client_id": self.settings.jamendo_client_id,
            "format": "json",
            "limit": 40,
            "include": "musicinfo+licenses",
            "audioformat": "mp31",
            "type": "single albumtrack",
        }

    async def search(self, intent, market):
        query = query_for(intent)
        params = {**self.params(), "search": query, "order": "relevance"}
        if intent.energy != "any":
            params["speed"] = intent.energy
        if intent.instrumental is not None:
            params["vocalinstrumental"] = "instrumental" if intent.instrumental else "vocal"
        return self.normalize(
            await self.fetch("https://api.jamendo.com/v3.0/tracks/", params), query
        )

    async def lookup(self, track_id, market):
        rows = await self.fetch(
            "https://api.jamendo.com/v3.0/tracks/",
            {
                **self.params(),
                "id": track_id,
                "limit": 1,
            },
        )
        return next(
            (
                c
                for c in self.normalize(rows, "ID lookup")
                if c.track.ref.providerTrackId == track_id
            ),
            None,
        )

    def parse(self, row, query):
        if not isinstance(row, dict):
            raise ValueError
        identifier = track_id(row["id"])
        title, artist = text_field(row["name"]), text_field(row["artist_name"])
        tags = []
        info = row.get("musicinfo")
        if isinstance(info, dict) and isinstance(info.get("tags"), dict):
            for field in ("genres", "instruments", "vartags"):
                values = info["tags"].get(field, [])
                if isinstance(values, list):
                    tags += [v.strip() for v in values if isinstance(v, str) and 0 < len(v) <= 40]
        tags = list(dict.fromkeys(tags))[:30]
        license_value, audio = row.get("license_ccurl"), row.get("audio")
        # Tie the stream's documented track selector to the licensed catalog identity.
        if isinstance(audio, str):
            try:
                if parse_qs(urlsplit(audio).query).get("trackid") != [identifier]:
                    audio = ""
            except ValueError:
                audio = ""
        return Candidate(
            CatalogTrack(
                ref=TrackRef(provider=self.name, providerTrackId=identifier),
                title=title,
                artist=artist,
                officialUrl=official_link(row["shareurl"], self.name, identifier),
                tags=tags,
                recommendationReason=(
                    f"Jamendo 目录检索「{query}」；目录标签：{', '.join(tags) or '未提供'}。"
                ),
                capability=capability(
                    self.name, title, artist, license_value, audio, self.settings, self.mode
                ),
            ),
            audio if isinstance(audio, str) else "",
            license_value or "",
        )

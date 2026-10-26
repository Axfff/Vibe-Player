from enum import StrEnum
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, field_validator

Tag = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=40)]
Atmosphere = Literal["calm", "bright", "energetic", "melancholic", "dreamy", "focused"]
Descriptor = Literal[
    "ambient",
    "acoustic",
    "piano",
    "electronic",
    "jazz",
    "classical",
    "rock",
    "pop",
    "chillout",
    "instrumental",
    "folk",
    "dance",
]
SCENES = Literal[
    "nature",
    "water",
    "city",
    "indoors",
    "night",
    "daylight",
    "warm_colors",
    "cool_colors",
    "open_space",
    "abstract",
    "other",
]
Provider = Literal["jamendo", "itunes"]
Mode = Literal["unconfigured", "mock", "live"]


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)


class TrackRef(StrictModel):
    provider: Provider
    providerTrackId: str = Field(pattern=r"^[1-9][0-9]{0,19}$")

    @property
    def key(self):
        return f"{self.provider}:{self.providerTrackId}"


class DiscoveryIntent(StrictModel):
    targetMood: Atmosphere
    musicTags: list[Descriptor] = Field(default_factory=list, max_length=8)
    text: str = Field(default="", max_length=160)
    energy: Literal["any", "low", "medium", "high"] = "any"
    instrumental: bool | None = None

    @field_validator("text")
    @classmethod
    def text_only(cls, value):
        if any(ord(c) < 32 for c in value) or "://" in value:
            raise ValueError("Use plain search text")
        return value


class VisionOutput(StrictModel):
    # Closed vocabularies prevent image instructions or invented track data entering queries.
    sceneTags: list[SCENES] = Field(max_length=8)
    visualMoodSuggestions: list[Atmosphere] = Field(min_length=1, max_length=3)
    musicDescriptors: list[Descriptor] = Field(max_length=8)
    uncertainty: Literal["low", "medium", "high"]


class DiscoveryRequest(StrictModel):
    intent: DiscoveryIntent
    confirmed: Literal[True]
    sceneTags: list[SCENES] = Field(default_factory=list, max_length=8)
    visualSuggestions: VisionOutput | None = None
    inputMode: Literal["manual", "photo"] = "manual"
    market: Literal["US", "GB", "CN", "HK", "JP", "DE", "FR", "CA", "AU"] = "US"
    limit: int = Field(default=8, ge=1, le=8, strict=True)
    exclude: list[TrackRef] = Field(default_factory=list, max_length=500)


class ReplaceRequest(DiscoveryRequest):
    sessionId: str = Field(min_length=20, max_length=80)
    replacing: TrackRef


class CapabilityMode(StrEnum):
    EXTERNAL_ONLY = "EXTERNAL_ONLY"
    IN_APP_PREVIEW = "IN_APP_PREVIEW"
    UNAVAILABLE = "UNAVAILABLE"
    UNKNOWN = "UNKNOWN"


class PlaybackCapability(StrictModel):
    mode: CapabilityMode
    reasonCode: str
    policyEvidence: list[str] = Field(default_factory=list)
    checkedAt: str
    expiresAt: str | None = None
    previewUrl: str | None = None
    maxPreviewSeconds: int = 30
    licenseUrl: str | None = None
    attribution: str | None = None


class CatalogTrack(StrictModel):
    ref: TrackRef
    title: str = Field(min_length=1, max_length=300)
    artist: str = Field(min_length=1, max_length=300)
    officialUrl: str
    artwork: None = None
    tags: list[Tag] = Field(default_factory=list, max_length=30)
    recommendationReason: str
    capability: PlaybackCapability


class ProviderStatus(StrictModel):
    provider: str
    mode: Mode
    configured: bool
    policyBasis: str
    previewPolicyReady: bool = False
    configurationIssues: list[str] = Field(default_factory=list)
    state: str = "not_run"
    liveTested: bool = False
    requestsMade: int = 0
    errorCode: str | None = None


class DiscoveryResponse(StrictModel):
    sessionId: str
    inputMode: str
    effectiveIntent: DiscoveryIntent
    market: str
    tracks: list[CatalogTrack]
    requested: int
    returned: int
    shortageReason: str | None
    vision: ProviderStatus
    catalogs: list[ProviderStatus]

import re
from datetime import UTC, datetime

from .config import Settings
from .errors import DiscoveryError
from .models import CapabilityMode, PlaybackCapability
from .network import AUDIO_HOSTS, validate_url

JAMENDO_TERMS = "https://devportal.jamendo.com/api_terms_of_use"
JAMENDO_TRACKS = "https://developer.jamendo.com/v3.0/tracks"
APPLE_TERMS = "https://performance-partners.apple.com/search-api"
CC_BY_4 = "https://creativecommons.org/licenses/by/4.0/"
REVIEW_DATE = "2026-10-26"


def now_iso():
    return datetime.now(UTC).isoformat()


def license_url(value):
    # Normalize only the official historical HTTP CC identifier, never an audio/page URL.
    if isinstance(value, str) and re.fullmatch(
        r"https?://creativecommons\.org/licenses/(by|by-sa|by-nd|by-nc|by-nc-sa|by-nc-nd)/"
        r"(2\.0|2\.5|3\.0|4\.0)/",
        value,
    ):
        return "https:" + value.split(":", 1)[1]
    return None


def capability(provider, title, artist, license_value, audio, settings: Settings, mode):
    if provider == "itunes":
        return PlaybackCapability(
            mode=CapabilityMode.EXTERNAL_ONLY,
            reasonCode="itunes_external_only",
            policyEvidence=[APPLE_TERMS],
            checkedAt=now_iso(),
        )
    license_value = license_url(license_value)
    result = PlaybackCapability(
        mode=CapabilityMode.UNKNOWN,
        reasonCode="license_unknown",
        checkedAt=now_iso(),
        policyEvidence=[JAMENDO_TERMS, JAMENDO_TRACKS],
        licenseUrl=license_value,
        attribution=f"{title} — {artist} · Music provided by Jamendo",
    )
    if not license_value:
        return result
    result.mode = CapabilityMode.EXTERNAL_ONLY
    result.reasonCode = "license_outside_preview_policy"
    # This small MVP has reviewed CC BY 4.0 only; other licenses retain official links.
    if license_value != CC_BY_4:
        return result
    result.policyEvidence.append(CC_BY_4)
    if mode != "live":
        result.reasonCode = "mock_no_playback"
        return result
    if not (
        settings.jamendo_preview_enabled
        and settings.jamendo_noncommercial
        and settings.jamendo_terms_accepted
        and settings.jamendo_review_id
        and settings.jamendo_client_id
    ):
        result.reasonCode = "deployment_review_required"
        return result
    try:
        validate_url(audio, AUDIO_HOSTS)
    except DiscoveryError:
        result.reasonCode = "audio_url_unverified"
        return result
    result.mode = CapabilityMode.IN_APP_PREVIEW
    result.reasonCode = "cc_by_4_noncommercial_reviewed"
    # Stream URL is supplied only by the fresh capability route after network validation.
    return result

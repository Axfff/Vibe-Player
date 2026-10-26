from dataclasses import dataclass, field
from pathlib import Path

from dotenv import dotenv_values

ROOT = Path(__file__).resolve().parent.parent
MODES = {"live", "mock", "unconfigured"}


@dataclass(frozen=True)
class Settings:
    vision_mode: str = "unconfigured"
    itunes_mode: str = "unconfigured"
    jamendo_mode: str = "unconfigured"
    vision_model: str = ""
    vision_key: str = field(default="", repr=False)
    jamendo_client_id: str = field(default="", repr=False)
    cloud_authorized: bool = False
    vision_call_budget: int = 0
    catalog_call_budget: int = 80
    jamendo_preview_enabled: bool = False
    jamendo_noncommercial: bool = False
    jamendo_terms_accepted: bool = False
    # Operator review of this deployment, including territory/account and attribution.
    jamendo_review_id: str = ""
    max_upload_bytes: int = 5 * 1024 * 1024
    max_pixels: int = 20_000_000
    max_image_edge: int = 1280
    timeout_seconds: float = 8.0

    @classmethod
    def load(cls):
        import os

        values = {**dotenv_values(ROOT / ".env"), **os.environ}
        kwargs = {}
        for name, item in cls.__dataclass_fields__.items():
            value = values.get("DISCOVERY_" + name.upper())
            if value is not None:
                kind = type(item.default)
                if kind is bool:
                    if value.lower() not in {"true", "false"}:
                        raise ValueError(f"DISCOVERY_{name.upper()} must be true or false")
                    value = value.lower() == "true"
                else:
                    value = kind(value)
                kwargs[name] = value
        settings = cls(**kwargs)
        for mode in (settings.vision_mode, settings.itunes_mode, settings.jamendo_mode):
            if mode not in MODES:
                raise ValueError("Provider mode must be live, mock or unconfigured")
        if not 0 <= settings.vision_call_budget <= 100:
            raise ValueError("Vision budget must be 0..100 calls per process")
        if not 0 <= settings.catalog_call_budget <= 1000:
            raise ValueError("Catalog budget must be 0..1000 HTTP calls per process")
        if not 1 <= settings.max_upload_bytes <= 5 * 1024 * 1024:
            raise ValueError("Upload byte limit must be 1..5242880")
        if not 1 <= settings.max_pixels <= 20_000_000:
            raise ValueError("Decoded pixel limit must be 1..20000000")
        if not 1 <= settings.max_image_edge <= 1280:
            raise ValueError("Image edge limit must be 1..1280")
        if not 0 < settings.timeout_seconds <= 10:
            raise ValueError("Network timeout must be >0 and <=10 seconds")
        return settings

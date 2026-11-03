"""Exercise deployment validation without reading real credentials or making HTTP calls."""
import os

import pytest

from discovery.config import Settings


@pytest.fixture(autouse=True)
def isolated_configuration(monkeypatch):
    for name in list(os.environ):
        if name.startswith("DISCOVERY_"):
            monkeypatch.delenv(name)
    monkeypatch.setattr("discovery.config.dotenv_values", lambda _: {})


def test_environment_overrides_dotenv_and_converts_types(monkeypatch):
    monkeypatch.setattr("discovery.config.dotenv_values", lambda _: {
        "DISCOVERY_VISION_MODE": "mock", "DISCOVERY_CATALOG_CALL_BUDGET": "4"
    })
    for key, value in {
        "VISION_MODE": "unconfigured", "CATALOG_CALL_BUDGET": "17",
        "CLOUD_AUTHORIZED": "TRUE", "TIMEOUT_SECONDS": "2.5",
        "VISION_KEY": "fixture-private-key", "JAMENDO_CLIENT_ID": "fixture-private-id",
    }.items():
        monkeypatch.setenv("DISCOVERY_" + key, value)
    settings = Settings.load()
    assert settings.vision_mode == "unconfigured"
    assert settings.catalog_call_budget == 17
    assert settings.cloud_authorized is True
    assert settings.timeout_seconds == 2.5
    assert settings.vision_key == "fixture-private-key"
    assert "fixture-private" not in repr(settings)


@pytest.mark.parametrize("name,value,message", [
    ("CLOUD_AUTHORIZED", "yes", "true or false"),
    ("VISION_MODE", "silently-fallback", "Provider mode"),
    ("VISION_CALL_BUDGET", "101", "Vision budget"),
    ("CATALOG_CALL_BUDGET", "1001", "Catalog budget"),
    ("MAX_UPLOAD_BYTES", "0", "Upload byte"),
    ("MAX_PIXELS", "20000001", "Decoded pixel"),
    ("MAX_IMAGE_EDGE", "1281", "Image edge"),
    ("TIMEOUT_SECONDS", "0", "Network timeout"),
    ("TIMEOUT_SECONDS", "11", "Network timeout"),
])
def test_invalid_configuration_is_rejected_before_serving(monkeypatch, name, value, message):
    monkeypatch.setenv("DISCOVERY_" + name, value)
    with pytest.raises(ValueError, match=message):
        Settings.load()


def test_false_consent_and_zero_call_budgets_remain_disabled(monkeypatch):
    monkeypatch.setenv("DISCOVERY_CLOUD_AUTHORIZED", "false")
    monkeypatch.setenv("DISCOVERY_VISION_CALL_BUDGET", "0")
    monkeypatch.setenv("DISCOVERY_CATALOG_CALL_BUDGET", "0")
    settings = Settings.load()
    assert settings.cloud_authorized is False
    assert settings.vision_call_budget == settings.catalog_call_budget == 0

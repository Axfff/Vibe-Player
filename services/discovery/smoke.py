"""Explicit, bounded catalog smoke test. No model or audio requests."""

import argparse
import asyncio
import json
from dataclasses import replace
from datetime import UTC, datetime

from discovery.catalog import ITunesCatalog, JamendoCatalog
from discovery.config import Settings
from discovery.models import DiscoveryIntent, DiscoveryRequest, ReplaceRequest
from discovery.network import OutboundRoute, SafeHTTP
from discovery.service import DiscoveryService
from discovery.vision import VisionService


async def exercise(service, request, *, provider, workflow=False):
    """Use the same service path as the API; retain failed live outcomes in the report."""
    first = await service.discover(request)
    checks = {
        "real_results": first.returned > 0,
        "unique_initial_ids": len({t.ref.key for t in first.tracks}) == first.returned,
        "selected_provider_only": all(t.ref.provider == provider for t in first.tracks),
        "vision_disabled": first.vision.mode == "unconfigured",
    }
    report = {
        "checkedAt": datetime.now(UTC).isoformat(),
        "mode": "live",
        "provider": provider,
        "initial": first.model_dump(mode="json"),
        "replacements": [],
        "checks": checks,
    }
    seen = {t.ref.key for t in first.tracks}
    current = first.tracks[0] if first.tracks else None
    if workflow:
        for index in range(2):
            checks[f"replacement_{index + 1}_new_id"] = False
            if current is None:
                break
            response = await service.discover(
                ReplaceRequest(
                    **request.model_dump(exclude={"exclude"}),
                    sessionId=first.sessionId,
                    replacing=current.ref,
                    # Deliberately omit client exclusions: test server session memory.
                    exclude=[],
                ),
                replace=True,
            )
            report["replacements"].append(response.model_dump(mode="json"))
            current = response.tracks[0] if response.tracks else None
            checks[f"replacement_{index + 1}_new_id"] = bool(
                current and current.ref.key not in seen
            )
            if current:
                seen.add(current.ref.key)
        if provider == "itunes":
            checks["lookup_identity"] = False
            checks["external_only_without_preview"] = False
            if current:
                verified = await service.capabilities(current.ref, first.sessionId)
                track = verified.get("track")
                report["lookup"] = (
                    track.model_dump(mode="json") if track else verified["capability"].model_dump()
                )
                checks["lookup_identity"] = bool(track and track.ref == current.ref)
                checks["external_only_without_preview"] = bool(
                    track
                    and all(
                        t.capability.mode == "EXTERNAL_ONLY" and t.capability.previewUrl is None
                        for t in [*first.tracks, track]
                    )
                )
    report["requestsMade"] = service.http.count(provider)
    report["catalogs"] = [source.status().model_dump() for source in service.catalogs]
    report["passed"] = all(checks.values())
    return report


async def main(args):
    settings = Settings.load()
    if getattr(settings, args.provider + "_mode") != "live":
        raise SystemExit("Set the selected provider's mode to live in your local .env first.")
    settings = replace(
        settings, catalog_call_budget=args.max_http_calls, vision_mode="unconfigured"
    )
    audit = []

    async def record_request(request):
        request.extensions["smoke_audit_index"] = len(audit)
        # Never record query parameters (which may contain a Jamendo credential).
        audit.append(
            {
                "method": request.method,
                "host": request.url.host,
                "path": request.url.path,
                "status": None,
            }
        )

    async def record_response(response):
        audit[response.request.extensions["smoke_audit_index"]]["status"] = response.status_code

    route = OutboundRoute(settings.local_proxy_url)
    async with route.client(
        timeout=8,
        event_hooks={"request": [record_request], "response": [record_response]},
    ) as client:
        http = SafeHTTP(client, budget=args.max_http_calls, resolver=route.resolve)
        adapter = ITunesCatalog if args.provider == "itunes" else JamendoCatalog
        source = adapter(settings, http)
        if source.mode != "live":
            raise SystemExit("The selected provider is not configured.")
        service = DiscoveryService([source], VisionService(settings, http), http)
        report = await exercise(
            service,
            DiscoveryRequest(
                intent=DiscoveryIntent(targetMood="focused", text="piano"),
                confirmed=True,
                market=args.market,
                limit=args.limit,
            ),
            provider=args.provider,
            workflow=args.workflow,
        )
        report["httpBudget"] = args.max_http_calls
        report["networkRoute"] = route.mode
        report["httpRequests"] = audit
        print(json.dumps(report, ensure_ascii=False, indent=2))
        if not report["passed"]:
            raise SystemExit(1)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--provider", required=True, choices=["itunes", "jamendo"])
    parser.add_argument(
        "--authorized",
        required=True,
        action="store_true",
        help="Confirm authorization for this catalog test",
    )
    parser.add_argument("--max-http-calls", type=int, choices=range(1, 16), default=3)
    parser.add_argument("--limit", type=int, choices=range(1, 9), default=1)
    parser.add_argument(
        "--workflow",
        action="store_true",
        help="Also replace twice and, for iTunes, recheck the final ID via lookup",
    )
    parser.add_argument("--market", choices=["US", "GB", "CN", "HK", "JP"], default="US")
    asyncio.run(main(parser.parse_args()))

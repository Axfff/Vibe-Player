"""Explicit, bounded catalog smoke test. No model or audio requests."""

import argparse
import asyncio
import json
from dataclasses import replace

import httpx

from discovery.catalog import ITunesCatalog, JamendoCatalog
from discovery.config import Settings
from discovery.models import DiscoveryIntent, DiscoveryRequest
from discovery.network import SafeHTTP
from discovery.service import DiscoveryService
from discovery.vision import VisionService


async def main(args):
    settings = Settings.load()
    if getattr(settings, args.provider + "_mode") != "live":
        raise SystemExit("Set the selected provider's mode to live in your local .env first.")
    settings = replace(
        settings, catalog_call_budget=args.max_http_calls, vision_mode="unconfigured"
    )
    async with httpx.AsyncClient(timeout=8, trust_env=False) as client:
        http = SafeHTTP(client, budget=args.max_http_calls)
        adapter = ITunesCatalog if args.provider == "itunes" else JamendoCatalog
        source = adapter(settings, http)
        if source.mode != "live":
            raise SystemExit("The selected provider is not configured.")
        service = DiscoveryService([source], VisionService(settings, http), http)
        response = await service.discover(
            DiscoveryRequest(
                intent=DiscoveryIntent(targetMood="focused", text="piano"),
                confirmed=True,
                market=args.market,
                limit=1,
            )
        )
        print(
            json.dumps(
                {
                    "mode": "live",
                    "provider": args.provider,
                    "requestsMade": http.count(args.provider),
                    "results": response.model_dump(mode="json"),
                },
                ensure_ascii=False,
                indent=2,
            )
        )
        if response.returned == 0:
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
    parser.add_argument("--max-http-calls", type=int, choices=range(1, 6), default=3)
    parser.add_argument("--market", choices=["US", "GB", "CN", "HK", "JP"], default="US")
    asyncio.run(main(parser.parse_args()))

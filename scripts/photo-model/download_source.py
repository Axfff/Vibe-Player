"""Fetch only the pinned author's files; verify before promoting each local copy."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import urllib.request


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    manifest = json.loads(Path(__file__).with_name("source-manifest.json").read_text(encoding="utf-8"))
    args.output.mkdir(parents=True, exist_ok=True)
    for item in manifest["files"]:
        destination = args.output / item["name"]
        def valid(path):
            return path.is_file() and path.stat().st_size == item["size"] and hashlib.sha256(path.read_bytes()).hexdigest() == item["sha256"]
        if not valid(destination):
            url = f'https://huggingface.co/{manifest["repository"]}/resolve/{manifest["revision"]}/{item["name"]}'
            staging = destination.with_suffix(destination.suffix + ".part")
            try:
                with urllib.request.urlopen(url, timeout=60) as response, staging.open("wb") as output:
                    count = 0
                    while data := response.read(65536):
                        count += len(data)
                        if count > item["size"]:
                            raise ValueError("Source exceeds the pinned size")
                        output.write(data)
                if not valid(staging):
                    raise ValueError(f'Hash mismatch: {item["name"]}')
                os.replace(staging, destination)
            finally:
                staging.unlink(missing_ok=True)
        print("Verified", item["name"], flush=True)


if __name__ == "__main__":
    main()

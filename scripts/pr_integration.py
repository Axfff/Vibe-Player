#!/usr/bin/env python3
"""Require the exact, clean CI head to contain the PR's target base snapshot.

This is an integration precondition, not tests or approval. Full CI tests this
same head. Strict branch protection must reject a base that advances afterwards.
No branch, index or working file is modified by the inspection.
"""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import sys


class IntegrationError(ValueError):
    pass


def git(repo, *args):
    result = subprocess.run(["git", *args], cwd=repo, capture_output=True,
                            text=True, encoding="utf-8", check=False)
    if result.returncode:
        raise IntegrationError("Required Git commit/history unavailable; fetch the exact base and head with full history.")
    return result.stdout.strip()


def inspect(repo, base, head):
    for value in (base, head):
        if not re.fullmatch(r"[0-9a-f]{40}", value):
            raise IntegrationError("Base and head must be full 40-character commit SHAs.")
        if git(repo, "rev-parse", "--verify", value + "^{commit}") != value:
            raise IntegrationError("Base and head must identify commits, not tag objects.")
    if git(repo, "rev-parse", "--is-shallow-repository") != "false":
        raise IntegrationError("Shallow checkout cannot establish integration; use fetch-depth: 0.")
    actual = git(repo, "rev-parse", "HEAD")
    if actual != head:
        raise IntegrationError("Checkout does not match the expected PR head; do not reuse another revision's checks.")
    if git(repo, "status", "--porcelain=v1", "--untracked-files=all"):
        raise IntegrationError("Checkout is dirty; commit the complete candidate before verification.")
    ancestry = subprocess.run(["git", "merge-base", "--is-ancestor", base, head],
                              cwd=repo, capture_output=True, check=False)
    if ancestry.returncode == 1:
        raise IntegrationError("PR head does not include its target base. Integrate that base, resolve conflicts, regenerate coverage scope, and rerun full CI.")
    if ancestry.returncode:
        raise IntegrationError("Unable to verify target ancestry; fetch full base/head history.")
    return {"base_sha": base, "head_sha": head, "checkout_sha": actual,
            "base_is_ancestor": True, "candidate_tree": git(repo, "rev-parse", head + "^{tree}"),
            "candidate_is_tested_head": True}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True)
    parser.add_argument("--head", required=True)
    parser.add_argument("--output", type=Path, default=Path("artifacts/process/pr-integration.json"))
    args = parser.parse_args(argv)
    # CI uses the fixed ignored artifacts path; local callers may choose an
    # evidence path. Inspection completes before any report is written.
    record = {"schema": 1, "checked_at": datetime.now(timezone.utc).isoformat(),
              "status": "failure", "full_ci_required": True, "human_review_required": True}
    for key, value in (("base_sha", args.base), ("head_sha", args.head)):
        record[key] = value if re.fullmatch(r"[0-9a-f]{40}", value) else "invalid"
    try:
        record.update(inspect(Path.cwd(), args.base, args.head))
        record["status"] = "success"
        record["reason"] = "Exact clean head contains the target snapshot; full CI and current non-author approval are separate requirements."
    except IntegrationError as error:
        record["reason"] = str(error)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    summary = ("### PR integration: " + record["status"] + "\n\n"
               + f"Base: `{record['base_sha']}`  \nHead: `{record['head_sha']}`\n\n"
               + record["reason"] + "\n")
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a", encoding="utf-8") as stream:
            stream.write(summary)
    print(summary, file=sys.stdout if record["status"] == "success" else sys.stderr)
    return 0 if record["status"] == "success" else 1


if __name__ == "__main__":
    raise SystemExit(main())

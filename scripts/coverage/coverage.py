#!/usr/bin/env python3
"""Reproducible cumulative team-line scope; never infer coverage from test counts."""
import argparse
import hashlib
import html
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
BASE = "947709e4c798a103b9fcc5962172f2e85d349196"
MANIFEST = ROOT / "docs/testing/coverage-scope.json"
OUTPUT = ROOT / "artifacts/coverage"


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True, encoding="utf-8").strip()


def digest(path):
    return hashlib.sha256(path.read_bytes().replace(b"\r\n", b"\n")).hexdigest()


def module(path):
    if re.match(r"project/[^/]+/src/main/.*\.(kt|java)$", path):
        return "android"
    if re.match(r"services/[^/]+/discovery/.*\.py$", path):
        return "backend"
    if re.match(r"services/[^/]+/static/.*\.(m?js)$", path):
        return "web"
    return None


def ranges(numbers):
    result = []
    for number in sorted(set(numbers)):
        if result and number == result[-1][1] + 1:
            result[-1][1] = number
        else:
            result.append([number, number])
    return result


def expand(intervals):
    return {n for start, end in intervals for n in range(start, end + 1)}


def expected_scope():
    subprocess.run(["git", "merge-base", "--is-ancestor", BASE, "HEAD"], cwd=ROOT, check=True)
    changed = set(git("diff", "--name-only", "--no-renames", BASE, "--").splitlines())
    changed.update(git("ls-files", "--others", "--exclude-standard").splitlines())
    original = set(git("ls-tree", "-r", "--name-only", BASE).splitlines())
    files = []
    for path in sorted(changed):
        group = module(path)
        if group is None and path.startswith('services/') and Path(path).suffix in {'.py', '.js', '.mjs'}:
            if '/tests/' not in path and path != 'services/discovery/smoke.py':
                raise ValueError(f'Unclassified service executable source (declare and test its production root): {path}')
        if group is None and '/src/main/' in path and Path(path).suffix in {'.c', '.cpp', '.rs', '.js', '.mjs'}:
            raise ValueError(f'No coverage collector for new production language: {path}')
        if not group or not (ROOT / path).is_file():
            continue
        added = path not in original
        if added:
            lines = range(1, len((ROOT / path).read_text(encoding="utf-8").splitlines()) + 1)
        else:
            patch = git("diff", "--no-ext-diff", "--no-renames", "--unified=0", BASE, "--", path)
            lines = []
            for start, count in re.findall(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@", patch, re.M):
                lines.extend(range(int(start), int(start) + int(count or "1")))
        history = git("log", "--reverse", "--format=%H", f"{BASE}..HEAD", "--", path).splitlines()
        files.append({
            "path": path, "module": group, "change": "added" if added else "modified",
            "source_sha256": digest(ROOT / path),
            "line_policy": "all-executable" if added else "cumulative-diff-executable",
            "candidate_line_ranges": ranges(lines),
            "source_commits": history, "exception_reason": None,
        })
    return {"schema": 1, "baseline": BASE, "minimum_line_percent": 80,
            "non_production_exceptions": [{"path": 'services/discovery/smoke.py',
                "reason": 'Operator verification harness (exercised by tests/test_smoke.py); not a served backend module'}]
                if (ROOT / 'services/discovery/smoke.py').exists() else [], "files": files}


def validate_scope():
    expected = expected_scope()
    saved = json.loads(MANIFEST.read_text(encoding="utf-8"))
    # Attribution changes when a new commit is made, but paths, content and line ranges must match.
    def comparable(value):
        return {**value, "files": [{k: v for k, v in f.items() if k != "source_commits"} for f in value["files"]]}
    if comparable(saved) != comparable(expected):
        raise ValueError("Scope manifest is stale/incomplete; review `python scripts/coverage/coverage.py scope --write`")
    return expected


def source_fingerprint():
    paths = set(git("ls-files").splitlines()) | set(git("ls-files", "--others", "--exclude-standard").splitlines())
    relevant = [p for p in paths if (p.startswith("project/") or p.startswith("services/") or
                p.startswith("scripts/coverage/")) and (ROOT / p).is_file()]
    return hashlib.sha256("\n".join(f"{p}:{digest(ROOT / p)}" for p in sorted(relevant)).encode()).hexdigest()


def jacoco(path):
    root = ET.parse(path).getroot()
    result = {}
    for package in root.findall("package"):
        prefix = package.get("name") + "/"
        for item in package.findall("class"):
            if item.get("sourcefilename"):
                result.setdefault(prefix + item.get("sourcefilename"), {})
        for source in package.findall("sourcefile"):
            result[prefix + source.get("name")] = {
                int(line.get("nr")): int(line.get("ci")) > 0
                for line in source.findall("line")
                if int(line.get("ci")) + int(line.get("mi")) > 0
            }
    return result


def cobertura(path):
    result = {}
    for cls in ET.parse(path).getroot().findall(".//class"):
        result[cls.get("filename")] = {int(l.get("number")): int(l.get("hits")) > 0 for l in cls.findall("lines/line")}
    return result


def lcov(path):
    result, current = {}, None
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("SF:"):
            current = line[3:].replace("\\", "/")
            result[current] = {}
        elif line.startswith("DA:") and current:
            number, hits, *_ = line[3:].split(",")
            result[current][int(number)] = int(hits) > 0
    return result


def test_counts(paths):
    files = [p for pattern in paths for p in ROOT.glob(pattern)]
    if not files:
        raise ValueError(f"Required test XML missing: {paths}")
    counts = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    for path in files:
        root = ET.parse(path).getroot()
        # Node's native reporter emits testcase children directly under testsuites.
        # Count actual cases when available; do not double-count nested suite totals.
        cases = root.findall(".//testcase")
        if cases:
            counts["tests"] += len(cases)
            for name, tag in (("failures", "failure"), ("errors", "error"), ("skipped", "skipped")):
                counts[name] += sum(case.find(tag) is not None for case in cases)
            continue
        suites = [root] if root.tag == "testsuite" else root.findall(".//testsuite")
        suites = [suite for suite in suites if not suite.findall("testsuite")]
        for suite in suites:
            for name in counts:
                counts[name] += int(suite.get(name, 0))
    if counts["tests"] - counts["skipped"] <= 0:
        raise ValueError(f"Zero executed tests: {paths}")
    return counts


def fraction(covered, total):
    return {"covered": covered, "total": total,
            "percent": 100 * covered / total if total else None,
            "passed": total > 0 and covered * 100 >= total * 80}


def validate_execution_data(root, started_ns, unit_only=False):
    required = list(root.glob('project/*/build/outputs/unit_test_code_coverage/**/*.exec'))
    if not required:
        raise ValueError('Required JVM execution data missing')
    if not unit_only:
        device = list(root.glob('project/*/build/outputs/code_coverage/**/*.ec'))
        if not device:
            raise ValueError('Required Android execution data missing')
        required += device
    if any(p.stat().st_size == 0 or p.stat().st_mtime_ns < started_ns for p in required):
        raise ValueError('Empty or stale coverage execution data; clean and rerun')


def summarize(scope, reports):
    groups = {}
    for entry in scope["files"]:
        name, path = entry["module"], entry["path"]
        if name not in reports:
            raise ValueError(f"Required {name} coverage report missing for {path}")
        if name == "android":
            key = re.split(r"/src/main/(?:java|kotlin)/", path, maxsplit=1)[1]
        elif name == "backend":
            key = path.rsplit("/discovery/", 1)[1]
        else:
            key = path.split("/", 2)[2]
        matches = [v for k, v in reports[name].items() if k == key or k.endswith("/" + key)]
        if len(matches) != 1:
            raise ValueError(f"Missing/ambiguous production source in coverage: {path}")
        background = matches[0]
        selected = set(background) if entry["change"] == "added" else set(background) & expand(entry["candidate_line_ranges"])
        covered = sum(background[n] for n in selected)
        row = {**entry, **fraction(covered, len(selected)),
               "executable_line_ranges": ranges(selected),
               "uncovered_lines": sorted(n for n in selected if not background[n]),
               "whole_file": fraction(sum(background.values()), len(background))}
        groups.setdefault(name, []).append(row)
    return {name: {**fraction(sum(r["covered"] for r in rows), sum(r["total"] for r in rows)), "files": rows}
            for name, rows in groups.items()}


def write_reports(summary):
    OUTPUT.mkdir(parents=True, exist_ok=True)
    (OUTPUT / "summary.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    lines = ["<!-- vibe-team-coverage -->", "## Sprint 3 team line coverage",
             f"Tested SHA: `{summary['sha']}`; baseline: `{BASE}`.",
             f"Source fingerprint: `{summary['source_fingerprint']}`; uncommitted source: `{summary['dirty']}`.",
             "", "| Module | Covered / executable | Line coverage | >= 80% |", "|---|---:|---:|---|"]
    for name, group in summary["modules"].items():
        lines.append(f"| {name} | {group['covered']}/{group['total']} | {group['percent']:.4f}% | {'PASS' if group['passed'] else 'FAIL'} |")
    lines += ["", "Tests: `" + json.dumps(summary["tests"]) + "`.",
              "", "New files: all executable lines. Modified upstream files: cumulative changed executable lines; full-file context and uncovered lines are in the report.",
              "No UI/player/error-path exemptions. Generated bindings/resources/Moshi adapters only are excluded.",
              "Android Discovery is not present in this revision; integration must regenerate the scope manifest."]
    if summary.get("run_url"):
        lines.append(f"[Workflow and full HTML/XML report artifacts]({summary['run_url']})")
    (OUTPUT / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    body = ["<!doctype html><html><meta charset='utf-8'><title>Vibe-Player team coverage</title>",
            "<style>body{font:16px system-ui;margin:3rem;max-width:1200px}table{border-collapse:collapse;width:100%}td,th{border:1px solid #ccc;padding:.6rem;text-align:left}code{overflow-wrap:anywhere}.fail{color:#a00}.pass{color:#075}</style>",
            f"<h1>Team line coverage</h1><p>SHA <code>{summary['sha']}</code></p><p>Baseline <code>{BASE}</code></p>",
            "<p>All executable lines in new files; cumulative modified executable lines in upstream files. Whole-file context is also shown. Unexecuted files remain in the denominator.</p>"]
    for name, group in summary["modules"].items():
        body += [f"<h2>{name}: {group['covered']}/{group['total']} ({group['percent']:.4f}%)</h2>",
                 f"<p><a href='{name}/index.html'>Full line-by-line HTML</a> · <a href='{name}.xml'>XML</a></p>",
                 "<table><tr><th>Source</th><th>Team lines</th><th>Whole file</th><th>Uncovered team lines</th></tr>"]
        for row in group["files"]:
            whole = row["whole_file"]
            body.append(f"<tr><td>{html.escape(row['path'])}</td><td>{row['covered']}/{row['total']}</td><td>{whole['covered']}/{whole['total']}</td><td>{html.escape(str(row['uncovered_lines']))}</td></tr>")
        body.append("</table>")
    body.append("</html>")
    (OUTPUT / "index.html").write_text("\n".join(body), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["scope", "start", "report"])
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--measure-only", action="store_true", help="Local exploratory report; CI must not use this")
    parser.add_argument("--unit-only", action="store_true", help="Local JVM baseline only; CI must not use this")
    args = parser.parse_args()
    if args.command == "scope":
        if args.write:
            MANIFEST.parent.mkdir(parents=True, exist_ok=True)
            MANIFEST.write_text(json.dumps(expected_scope(), indent=2) + "\n", encoding="utf-8")
        print(f"Scope verified: {len(validate_scope()['files'])} production files from {BASE}")
        return
    scope = validate_scope()
    stamp_path = OUTPUT / "run-start.json"
    if args.command == "start":
        OUTPUT.mkdir(parents=True, exist_ok=True)
        stale = list(ROOT.glob('project/*/build/outputs/unit_test_code_coverage/**/*.exec'))
        stale += list(ROOT.glob('project/*/build/outputs/code_coverage/**/*.ec'))
        stale += list(ROOT.glob('project/*/build/test-results/**/*.xml'))
        if stale:
            raise ValueError('Old test/coverage outputs exist: run Gradle clean before starting collection')
        for name in ('summary.json', 'summary.md', 'index.html'):
            (OUTPUT / name).unlink(missing_ok=True)
        stamp_path.write_text(json.dumps({"sha": git("rev-parse", "HEAD"), "source_fingerprint": source_fingerprint(),
                                        "started_ns": time.time_ns()}), encoding="utf-8")
        return
    stamp = json.loads(stamp_path.read_text(encoding="utf-8"))
    if stamp['sha'] != git('rev-parse', 'HEAD') or stamp['source_fingerprint'] != source_fingerprint():
        raise ValueError("Source/commit changed during collection; rerun tests from a fresh build")
    if not args.measure_only:
        validate_execution_data(ROOT, stamp['started_ns'], args.unit_only)
    names = {f["module"] for f in scope["files"]}
    reports, tests = {}, {}
    def fresh(path):
        if not args.measure_only and path.stat().st_mtime_ns < stamp['started_ns']:
            raise ValueError(f'Stale required report: {path}')
        return path
    if "android" in names:
        xml = fresh(ROOT / "project/app/build/reports/coverage/android.xml")
        fresh(xml.parent / 'android-html/index.html')
        reports["android"] = jacoco(xml)
        shutil.copyfile(xml, OUTPUT / "android.xml")
        shutil.copytree(xml.parent / "android-html", OUTPUT / "android", dirs_exist_ok=True)
        tests["jvm"] = test_counts(["project/app/build/test-results/testDebugUnitTest/TEST-*.xml"])
        if not args.unit_only:
            tests["android"] = test_counts(["project/app/build/outputs/androidTest-results/connected/**/TEST-*.xml"])
    if "backend" in names:
        reports["backend"] = cobertura(fresh(OUTPUT / "backend.xml"))
        fresh(OUTPUT / 'backend/index.html')
        fresh(OUTPUT / 'backend-junit.xml')
        tests["backend"] = test_counts(["artifacts/coverage/backend-junit.xml"])
    if "web" in names:
        reports["web"] = lcov(fresh(OUTPUT / "web/lcov.info"))
        fresh(OUTPUT / 'web/index.html')
        shutil.copyfile(fresh(OUTPUT / 'web/cobertura-coverage.xml'), OUTPUT / 'web.xml')
        fresh(OUTPUT / 'web-junit.xml')
        tests["web"] = test_counts(["artifacts/coverage/web-junit.xml"])
    summary = {"schema": 1, **stamp, "baseline": BASE,
               "dirty": bool(git("status", "--porcelain", "--", "project", "services", "scripts/coverage")),
               "measurement_only": args.measure_only or args.unit_only,
               "modules": summarize(scope, reports), "tests": tests,
               "run_url": os.environ.get("COVERAGE_RUN_URL")}
    write_reports(summary)
    for name, group in summary["modules"].items():
        print(f"{name}: {group['covered']}/{group['total']} = {group['percent']:.6f}% ({'PASS' if group['passed'] else 'FAIL'})")
    failed = any(not g["passed"] for g in summary["modules"].values()) or any(t["failures"] or t["errors"] for t in tests.values())
    if failed and not args.measure_only:
        raise ValueError("Test/coverage gate failed; see artifacts/coverage/summary.json")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, ET.ParseError, subprocess.CalledProcessError) as error:
        print(f"COVERAGE ERROR: {error}", file=sys.stderr)
        sys.exit(1)

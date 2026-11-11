"""Post-inference only: python evaluate.py <frozen-fixture-root> [result-json]."""
import csv
import json
from pathlib import Path
import sys

fixture = Path(sys.argv[1])
result = Path(sys.argv[2]) if len(sys.argv) > 2 else Path(__file__).with_name("real-ranking-fresh.json")
raw = json.loads(result.read_text(encoding="utf-8"))
with (fixture / "evaluation/expected-relevance.csv").open(encoding="utf-8", newline="") as stream:
    labels = list(csv.DictReader(stream))
cases = []
for case in raw["cases"]:
    rows = {row["photo_id"]: row for row in labels if row["representative_track_id"] == case["audio_id"]}
    candidates = {item["photo_id"] for item in case["image_features"]}
    assert len(rows) == 20 and candidates == rows.keys(), "Frozen candidate set mismatch"
    selected = case["photo_ids"]
    assert len(selected) <= 5 and len(selected) == len(set(selected)) and set(selected) <= rows.keys()
    relevant = sum(int(rows[key]["expected_relevance"]) >= 2 for key in selected)
    cases.append({
        "case_id": case["case_id"], "returned": len(selected), "relevant": relevant,
        "precision_at_returned": relevant / len(selected) if selected else 0.0,
        "p_at_5_missing_as_zero": relevant / 5,
        "hard_distractors": sum(rows[key]["role"] == "hard_distractor" for key in selected),
        "random_expected_p5": sum(int(row["expected_relevance"]) >= 2 for row in rows.values()) / len(rows),
    })
print(json.dumps({"source_sha": raw["source_sha"],
    "labels": "Frozen provisional; separate post-inference evaluation; no human ground truth",
    "cases": cases}, indent=2))

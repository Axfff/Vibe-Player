#!/usr/bin/env python3
"""Toy ranking of human-described photo candidates from audio scores.

This is a transparent probe of the bridge between the two domains, not an
image analysis model, calibrated recommender, or production implementation.
The input must be the JSON from essentia_mood_smoke.py. Candidate descriptions
and vibe annotations are authored by a person, not inferred from photographs.
"""

import argparse
import json
import math
from pathlib import Path


# Manually chosen, deliberately narrow correspondences; each category's score
# is the arithmetic mean of its listed audio-model labels. These associations
# are hypotheses, not learned or validated mappings.
VIBES = {
    "adventurous": ("adventure",),
    "dramatic": ("drama", "dramatic"),
    "peaceful": ("relaxing", "calm", "meditative"),
    "joyful": ("happy", "positive", "uplifting"),
}


def rank(record, candidates, usable):
    """Return interpretable labels and candidate scores, or explicitly abstain."""
    if not usable:
        return {"status": "manual fallback", "reason": "audio declared unusable"}
    if "error" in record:
        return {"status": "manual fallback", "reason": record["error"]}
    scores = {item["label"]: float(item["score"]) for item in record["mean_scores_descending"]}
    if any(not math.isfinite(score) or not 0 <= score <= 1 for score in scores.values()):
        raise ValueError("Audio scores must be finite and between 0 and 1")
    missing = {label for names in VIBES.values() for label in names} - scores.keys()
    if missing:
        raise ValueError(f"Missing audio labels: {sorted(missing)}")
    profile = {
        vibe: sum(scores[label] for label in labels) / len(labels)
        for vibe, labels in VIBES.items()
    }
    ranked = []
    for candidate in candidates:
        vibes = candidate["human_assigned_vibes"]
        if any(vibe not in VIBES for vibe in vibes):
            raise ValueError(f"Unknown candidate vibe in {candidate['id']}")
        # Mean avoids automatically rewarding candidates for having more tags.
        match = sum(profile[vibe] for vibe in vibes) / len(vibes) if vibes else None
        ranked.append({"id": candidate["id"], "description": candidate["description"],
                       "human_assigned_vibes": vibes, "score": match})
    if not any(candidate["score"] is not None for candidate in ranked):
        return {"status": "manual fallback", "reason": "no annotated candidate matches the supported vibes"}
    ranked.sort(key=lambda item: (item["score"] is None, -(item["score"] or 0), item["id"]))
    return {"status": "illustrative ranking", "audio": record["audio"],
            "text_labels": sorted(profile, key=profile.get, reverse=True),
            "profile": profile, "candidates": ranked}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("results", type=Path, help="Output of essentia_mood_smoke.py")
    parser.add_argument("--index", type=int, required=True, help="Zero-based audio result")
    parser.add_argument("--usable-audio", required=True, choices=("yes", "no"),
                        help="Human-supplied gate for this experiment, not silence detection")
    parser.add_argument("--candidates", type=Path,
                        default=Path(__file__).with_name("caption_candidates.json"))
    args = parser.parse_args()
    with args.results.open(encoding="utf-8") as source:
        record = json.load(source)["results"][args.index]
    with args.candidates.open(encoding="utf-8") as source:
        candidates = json.load(source)
    print(json.dumps(rank(record, candidates, args.usable_audio == "yes"), indent=2))


if __name__ == "__main__":
    main()

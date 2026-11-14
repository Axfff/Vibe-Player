# Music/photo fixtures v2

Repairs the input-order and offline-attribution findings in Issue #51. Requires **Python 3.12+** (pinned PyAV requires it).

All 20 audio files, 33 photos, 80 case memberships, representative tracks, and both evaluation CSV files are copied unchanged from [frozen v1](../music-photo-v1/README.md). This is a development set already seen during model work, not a fresh held-out study. Provisional labels still need human listening and visual review.

Candidate order is SHA-256 of `music-photo-v2:{case_number}:{neutral_photo_id}`, sorted by digest. It does not use the order of relevance rows. Reversing a published shuffle cannot reconstruct the annotation row order. No label or source metadata goes into the neutral inference JSON. The HTML galleries and contact sheets use the new order.

The original Mayon JPEG copyright notice **Bien Bacarra Jr** and author metadata **Bien Orellana Bacarra Jr** are preserved in the manifest, per-item evidence and bundled ATTRIBUTION. Commons uploader Bien02 and the original CC BY 4.0 terms remain credited. Materialized exports include these offline records.

The old v1 package and its freeze/checksums are untouched. Copied evidence dated November 7 describes historical acquisition/decoding; it does not claim a v2 model result. The new freeze records the input-order repair and unchanged label hashes.

From the repository root, in a Python 3.12+ virtual environment:

```text
python -m pip install -r scripts/fixtures/requirements.txt
python -m unittest discover -s scripts/fixtures/tests -v
python scripts/fixtures/validate_music_photo_fixtures.py --output ../fixture-v2-validation.json
python scripts/fixtures/materialize_music_photo_fixtures.py --output ../music-photo-v2-export
python scripts/fixtures/evaluate_rankings.py ../actual-results.json --output ../actual-evaluation.json
```

Evaluation accepts 0–5 distinct in-scope results and always divides relevant returns by five for P@5. It reports returned count and precision among returned photos separately; the latter is null for an empty result. Missing slots are never silently discarded. Models, actual rankings, user media and APKs are not included in this package.

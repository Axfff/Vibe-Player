#!/usr/bin/env python3
"""Smoke-test the official Discogs-EffNet / Jamendo mood-theme chain.

This is an exploratory measurement, not a quality or Android benchmark.
Scores are means over overlapping audio patches, not calibrated probabilities
of listener mood or photo suitability.
"""

import argparse
import json
import time
from pathlib import Path

import numpy as np
from essentia.standard import MonoLoader, TensorflowPredict2D, TensorflowPredictEffnetDiscogs


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model-dir", type=Path, required=True)
    parser.add_argument("audio", type=Path, nargs="+")
    args = parser.parse_args()

    backbone_path = args.model_dir / "discogs-effnet-bs64-1.pb"
    head_path = args.model_dir / "mtg_jamendo_moodtheme-discogs-effnet-1.pb"
    metadata_path = args.model_dir / "mtg_jamendo_moodtheme-discogs-effnet-1.json"
    with metadata_path.open(encoding="utf-8") as f:
        labels = json.load(f)["classes"]
    if len(labels) != 56:
        raise ValueError(f"Expected 56 labels, got {len(labels)}")

    start = time.perf_counter()
    backbone = TensorflowPredictEffnetDiscogs(
        graphFilename=str(backbone_path), output="PartitionedCall:1"
    )
    head = TensorflowPredict2D(graphFilename=str(head_path))
    init_seconds = time.perf_counter() - start
    results = []
    failed = False
    for audio_path in args.audio:
        try:
            start = time.perf_counter()
            audio = MonoLoader(filename=str(audio_path), sampleRate=16000, resampleQuality=4)()
            decoded = time.perf_counter()
            if audio.ndim != 1 or not len(audio) or not np.isfinite(audio).all():
                raise ValueError("Decoded audio is empty, non-mono, or non-finite")
            embeddings = backbone(audio)
            embedded = time.perf_counter()
            if (embeddings.ndim != 2 or embeddings.shape[0] == 0
                    or embeddings.shape[1] != 1280 or not np.isfinite(embeddings).all()):
                raise ValueError(f"Invalid embeddings: shape={embeddings.shape}")
            predictions = head(embeddings)
            predicted = time.perf_counter()
            if (predictions.shape != (embeddings.shape[0], len(labels))
                    or not np.isfinite(predictions).all()
                    or (predictions < 0).any() or (predictions > 1).any()):
                raise ValueError(f"Invalid predictions: shape={predictions.shape}")
            scores = predictions.mean(axis=0)
            ranked = sorted(zip(labels, scores), key=lambda pair: -pair[1])
            results.append({
                "audio": str(audio_path),
                "duration_seconds": len(audio) / 16000,
                "embedding_shape": list(embeddings.shape),
                "prediction_shape": list(predictions.shape),
                "seconds": {
                    "decode": decoded - start,
                    "embedding": embedded - decoded,
                    "classification": predicted - embedded,
                    "total": predicted - start,
                },
                "mean_scores_descending": [
                    {"label": label, "score": float(score)} for label, score in ranked
                ],
            })
        except Exception as exc:
            failed = True
            results.append({"audio": str(audio_path), "error": f"{type(exc).__name__}: {exc}"})

    print(json.dumps({
        "sample_rate_hz": 16000,
        "aggregation": "arithmetic mean of patch scores; exploratory only",
        "model_initialization_seconds": init_seconds,
        "results": results,
    }, indent=2))
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())

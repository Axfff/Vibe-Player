#!/usr/bin/env python3
"""Generate a local Android inference fixture and compare official TF and ONNX models.

This development-only experiment requires numpy, onnxruntime, and the
TensorFlow-enabled Essentia Python package. It keeps the licensed models and
music out of Git. Supply a mono, 16-kHz, 16-bit PCM WAV of at least 128 frames
of 256 samples. The Android experiment supports this specific WAV format.
"""

import argparse
import hashlib
import importlib.metadata
import json
from pathlib import Path
import shutil
import wave

import numpy as np
import onnxruntime as ort
from essentia.standard import (
    FrameGenerator,
    MonoLoader,
    TensorflowInputMusiCNN,
    TensorflowPredict2D,
    TensorflowPredictEffnetDiscogs,
)


def digest(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def copy_if_needed(source, destination):
    if source.resolve() != destination.resolve():
        shutil.copyfile(source, destination)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--wav", type=Path, required=True)
    parser.add_argument("--tf-backbone", type=Path, required=True)
    parser.add_argument("--tf-head", type=Path, required=True)
    parser.add_argument("--onnx-backbone", type=Path, required=True)
    parser.add_argument("--onnx-head", type=Path, required=True)
    parser.add_argument("--metadata", type=Path, required=True, help="Jamendo head JSON with classes")
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()

    with wave.open(str(args.wav), "rb") as wav:
        if (wav.getnchannels(), wav.getsampwidth(), wav.getframerate(), wav.getcomptype()) != (1, 2, 16000, "NONE"):
            parser.error("WAV must contain mono, 16-bit PCM at 16 kHz")
        if wav.getnframes() < 128 * 256:
            parser.error("WAV must contain at least 32768 samples")

    args.output_dir.mkdir(parents=True, exist_ok=True)
    audio = MonoLoader(filename=str(args.wav), sampleRate=16000, resampleQuality=4)()
    features = TensorflowInputMusiCNN()
    frames = np.stack([features(frame) for frame in FrameGenerator(audio, frameSize=512, hopSize=256)])
    patch = np.asarray(frames[:128][None, :, :], dtype="<f4")
    if patch.shape != (1, 128, 96) or not np.isfinite(patch).all():
        raise ValueError(f"Invalid spectrogram patch: {patch.shape}")

    tf_backbone = TensorflowPredictEffnetDiscogs(graphFilename=str(args.tf_backbone), output="PartitionedCall:1")
    tf_head = TensorflowPredict2D(graphFilename=str(args.tf_head))
    tf_embeddings = tf_backbone(audio)
    tf_scores = tf_head(tf_embeddings)
    if (tf_embeddings.ndim != 2 or tf_embeddings.shape[0] == 0
            or tf_embeddings.shape[1] != 1280
            or tf_scores.shape != (tf_embeddings.shape[0], 56)
            or not np.isfinite(tf_embeddings).all() or not np.isfinite(tf_scores).all()):
        raise ValueError("Invalid TensorFlow reference output")

    options = ort.SessionOptions()
    options.intra_op_num_threads = 1
    backbone = ort.InferenceSession(str(args.onnx_backbone), sess_options=options, providers=["CPUExecutionProvider"])
    head = ort.InferenceSession(str(args.onnx_head), sess_options=options, providers=["CPUExecutionProvider"])
    embeddings = backbone.run(["embeddings"], {"melspectrogram": patch})[0]
    scores = head.run(["activations"], {"embeddings": embeddings})[0]
    if embeddings.shape != (1, 1280) or scores.shape != (1, 56):
        raise ValueError(f"Unexpected ONNX output shapes: {embeddings.shape}, {scores.shape}")
    if not np.isfinite(embeddings).all() or not np.isfinite(scores).all():
        raise ValueError("Non-finite ONNX output")
    labels = json.loads(args.metadata.read_text(encoding="utf-8"))["classes"]
    if len(labels) != 56:
        raise ValueError("Metadata must contain 56 labels")

    embedding_difference = float(np.max(np.abs(embeddings[0] - tf_embeddings[0])))
    score_difference = float(np.max(np.abs(scores[0] - tf_scores[0])))
    # This is a numerical agreement check for this fixture, not a model-quality target.
    if embedding_difference > 1e-4 or score_difference > 1e-4:
        raise ValueError(f"TF/ONNX parity failed: embedding={embedding_difference}, scores={score_difference}")

    for source, name in ((args.wav, "audio.wav"), (args.onnx_backbone, "backbone.onnx"),
                         (args.onnx_head, "head.onnx"), (args.metadata, "metadata.json")):
        copy_if_needed(source, args.output_dir / name)
    patch.tofile(args.output_dir / "patch.f32")
    np.asarray(embeddings, dtype="<f4").tofile(args.output_dir / "embedding.f32")
    np.asarray(scores, dtype="<f4").tofile(args.output_dir / "scores.f32")
    files = ("audio.wav", "backbone.onnx", "head.onnx", "metadata.json", "patch.f32", "embedding.f32", "scores.f32")
    report = {
        "audio": "16 kHz mono signed PCM16 WAV; first 128-frame Essentia mel patch",
        "patch_shape": list(patch.shape),
        "embedding_shape": list(embeddings.shape),
        "scores_shape": list(scores.shape),
        "float_files": "little-endian float32, C order",
        "tf_first_patch_embedding_max_abs_diff": embedding_difference,
        "tf_first_patch_scores_max_abs_diff": score_difference,
        "top_scores": sorted(
            [{"label": label, "score": float(score)} for label, score in zip(labels, scores[0])],
            key=lambda item: -item["score"],
        )[:10],
        "sha256": {name: digest(args.output_dir / name) for name in files},
        "versions": {name: importlib.metadata.version(name) for name in ("numpy", "onnxruntime", "essentia-tensorflow")},
    }
    (args.output_dir / "reference.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()

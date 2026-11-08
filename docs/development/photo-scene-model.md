# Preparing the local photo scene model

The photo slot now accepts `tinyclip-scenes-v2.onnx` (33,305,014 bytes), SHA-256
`425e98a2cf201054928c7a2d2c649ac59713636e1667fbc95eb50e1bdd2a0492`.
This is a **project-exported ONNX file**, not an upstream ONNX download. Existing
MobileNet imports require a photo-model update; the two music models, photo grants,
playlists and saved covers remain intact. The old file is preserved.

## Source and reproducibility

- Author: [TinyCLIP ViT-8M/16 Text-3M, YFCC15M](https://huggingface.co/wkcn/TinyCLIP-ViT-8M-16-Text-3M-YFCC15M).
- Pinned revision: `a2a8c6eaa2549ad66eb7c31b85022bf58273a26c`.
- Author checkpoint: safetensors, 93,812,468 bytes, SHA-256
  `9339ee3d736344d0ddcaa6c03edc9f89688f08caaea5401220885233da726fcc`.
- Model card license: MIT. Preserve [the upstream notice](../../scripts/photo-model/LICENSE-TinyCLIP.txt)
  when distributing the converted model. This does not change the music models'
  CC BY-NC-SA 4.0 license.
- [Source manifest](../../scripts/photo-model/source-manifest.json) pins every input file;
  the downloader and exporter verify them. No pickle checkpoint or remote Python code is loaded.

Use a separate Python 3.12 environment from the repository root:

```text
python -m pip install torch==2.5.1 --index-url https://download.pytorch.org/whl/cpu
python -m pip install -r scripts/photo-model/requirements.txt
python scripts/photo-model/download_source.py --output /path/to/tinyclip-source
python scripts/photo-model/export_model.py --source /path/to/tinyclip-source --output /path/to/tinyclip-scenes-v2.onnx
```

The exporter verifies Android's label ordering against the versioned vocabulary,
exports opset 17 / IR 8, and compares ONNX Runtime output against PyTorch. Two
independent exports in the recorded environment produced the SHA above; maximum
export difference on the seeded input was `2.682209e-7`. A different output hash
must be investigated, not bypassed in the app. The generated JSON records provenance.

Copy the verified ONNX file to the phone, open **封面照片来源 → 配置本地分析模型 →
导入照片内容模型**, and explicitly select it. Only a complete checksum-verified
file becomes active. No weights are committed to Git or bundled in the APK, and
the app does not download models or upload media. A public ready-made model
distribution URL has not been published; this export procedure and the local
verified artifact are the available preparation paths.

## Input, output and conservative use

The exported graph contains the image tower and 40 fixed text prototypes, each
averaged across three general photo prompts. The text encoder and tokenizer are
not needed on the phone. No test filenames, expected relevance labels, or fixture
images are inputs to export. Vocabulary and thresholds were fixed before comparing
the two candidate TinyCLIP variants. A later refinement groups overlapping scene
classes without changing these thresholds. The frozen relevance labels are read
only by the separate post-inference evaluator; these development comparisons are
not a blind held-out accuracy study.

The app applies EXIF orientation and bounded decoding, then supplies two RGB NCHW
224×224 views: a materialized central square and the aspect-preserving whole image
with neutral padding. Bilinear scaling is deliberate. CLIP channel means are
`[.48145466,.4578275,.40821073]`; standard deviations are
`[.26862954,.26130258,.27577711]`. The output is `[2,40]` **cosine similarity**, not
an object detection result or calibrated probability.

Both views must agree on the highest-scoring class, each score must reach `.20`,
and each top-two margin must reach `.02` for a specific label. When both winners
belong to the same predefined broad family (flowers/meadow, water bodies,
mountain scenery, insects, urban scenes, or rain/storm), the same margin must hold
against **every class outside that family**. Only the broad description is used;
the app explicitly says specific content is unconfirmed. Other ambiguous cases
have no asserted label. Unrelated classes (documents, screenshots, food, objects, etc.) compete
with mapped scenes and are not automatically recommended for a music mood.
Only accepted scene cues feed the versioned editorial music mapping. The UI
identifies descriptions as model guesses, explains uncertainty counts, returns
the real number of candidates, and retains manual selection.

These rules intentionally trade candidate coverage for caution. They do **not**
establish general recognition accuracy. The two flower regressions improved, but
the four-case ranking evaluation also regressed in Precision@5 with missing slots
counted as zero. See [the comparison and remaining limitations](../testing/photo-quality-and-picker-2026-11-08.md).

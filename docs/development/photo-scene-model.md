# Preparing the local photo atmosphere model

The photo slot accepts `tinyclip-atmospheres-v3.onnx`, **33,313,206 bytes**, SHA-256
`d7c4c554e61ce95c37c094669ce0dd0bda9f68e5471e37793c3d9ba046fc4e95`.
This project-exported ONNX file replaces the scenes-v2 experiment. Its additional
four atmosphere prototypes add only 8,192 bytes. Old model files remain intact;
music models, photo grants, playlists and saved covers do not change on import.

## Source and preparation

- Author checkpoint: [TinyCLIP ViT-8M/16 Text-3M, YFCC15M](https://huggingface.co/wkcn/TinyCLIP-ViT-8M-16-Text-3M-YFCC15M).
- Revision `a2a8c6eaa2549ad66eb7c31b85022bf58273a26c`; safetensors 93,812,468 bytes,
  SHA-256 `9339ee3d736344d0ddcaa6c03edc9f89688f08caaea5401220885233da726fcc`.
- MIT license; preserve [the upstream notice](../../scripts/photo-model/LICENSE-TinyCLIP.txt).
  Music models retain their separate CC BY-NC-SA 4.0 noncommercial terms.
- [Source manifest](../../scripts/photo-model/source-manifest.json) pins every input;
  no pickle or remote Python code is loaded. No weights enter Git or the production APK.

In a separate Python 3.12 environment, from the repository root:

```text
python -m pip install torch==2.5.1 --index-url https://download.pytorch.org/whl/cpu
python -m pip install -r scripts/photo-model/requirements.txt
python scripts/photo-model/download_source.py --output /path/to/tinyclip-source
python scripts/photo-model/export_model.py --source /path/to/tinyclip-source --output /path/to/tinyclip-atmospheres-v3.onnx
```

The exporter verifies source hashes and Android scene ordering, exports opset 17 /
IR 8, and compares ONNX Runtime output with PyTorch on seeded input. The recorded
maximum difference is `2.682209e-7`. A different artifact hash requires investigation,
not bypassing the app's check. The generated JSON records provenance and prompt hashes.

Copy the verified file to the phone, then select **封面照片来源 → 配置本地分析模型 →
导入照片内容模型**. Only a complete matching size/hash becomes active. The app has
no model downloader or photo upload. No public ready-made model distribution URL
has been published; reproducible export and the locally prepared artifact are available.

## What changed

V2 required a confidently named object/scene before ranking. This discarded 52/80
development-case inputs, even when broad picture atmosphere was usable. V3 keeps
the conservative object descriptions but ranks independently from four generic
picture-atmosphere prototypes: **轻快鲜活、宁静舒缓、沉静内省、强烈动势**.
Each averages four general text prompts from the
[versioned vocabulary](../../scripts/photo-model/atmosphere-vocabulary-v3.json).
No photo IDs, filenames, source descriptions, relevance labels or individual
fixture exceptions are used in model export or production scoring. The prompts
describe an image's overall impression, not the emotional state of a person.

The input remains two RGB NCHW 224×224 views: a materialized center square and an
aspect-preserving full image with neutral padding, after EXIF orientation and
bounded decoding. Bilinear scaling, means `[.48145466,.4578275,.40821073]` and
standard deviations `[.26862954,.26130258,.27577711]` are unchanged.
Output `[2,44]` contains 40 scene and four atmosphere **cosines**, not probabilities.

Specific scene descriptions retain v2's two-view agreement, `.20` cosine floor,
`.02` top-two margin and explicitly broad overlapping families. Ambiguous
flower/insect evidence cannot assert either specific object. For atmosphere,
each prototype takes the lower of its two view scores, then normalizes relative
support with temperature `.05`. The maximum raw cosine must reach `.20`, and
maximum relative support `.30`; otherwise the image is uncertain. A document or
screenshot winning either scene view is ineligible regardless of atmosphere scores.

`music-atmosphere-v3` groups finite audio scores ≥ `.05`, deduplicating repeated
labels by their maximum score. Groups are lively (happy/positive/upbeat/fun/summer),
peaceful (calm/relaxing/meditative/soft), reflective (sad/melancholic/dark), intense
(epic/action/energetic/powerful/adventure/sport). Group sums are normalized and
dotted with the four relative picture supports. Scores below `.26` are rejected;
uniform support would score `.25`. These are explicit heuristics, not calibrated
accuracy thresholds. Identical image contents are deduplicated, ties use neutral
photo ID, and only the current authorized scope can supply candidates.

Reasons name the contributing music and broad picture atmosphere. A specific
scene is supplementary, never required and never substituted for an uncertain
object. Manual atmosphere selection and manual cover choice remain available.
Native model creation/use/close share one coroutine ownership scope so cancellation
cannot lose a newly created ONNX session or skip its release.

## Evidence and limits

Both 8M and 40M desktop experiments reached mean provisional P@5 `.95`; 8M was
retained for its smaller on-device cost. Real Android API 33 inference reproduced
`1.00 / 1.00 / .80 / 1.00` versus v2 `.60 / .60 / .20 / .20` and old MobileNet
`1.00 / 1.00 / .40 / .80`. These are development comparisons on already-seen,
provisional labels, **not held-out generalization or human acceptance**.
An open, bright lake remains a difficult distractor in the reflective case.
See the [current report](../testing/photo-atmosphere-and-review-2026-11-10.md).

Hardware/provider edge cases and model/product/redistribution suitability still
require review. No SDK, runtime dependency or media permission was added.

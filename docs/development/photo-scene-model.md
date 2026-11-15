# Preparing the local photo atmosphere model

The photo slot accepts `tinyclip-atmospheres-v4.onnx`, **158,993,255 bytes**, SHA-256
`c6811b6a8127a3091e2fc21e0a5b48f0234de40afa30472028b15ab215092dd9`.
This project-exported ONNX file uses the stronger 40M image tower in place of v3's
8M tower (33,313,206 bytes). Its prompts remain unchanged. The current ranking
policy is `music-atmosphere-v4-valence`, described below; no new model import is
needed for this ranking/UI update. Plan
for the larger local file and temporary import copy. Old model files remain intact;
music models, photo grants, playlists and saved covers do not change on import.

## Source and preparation

- Author checkpoint: [TinyCLIP ViT-40M/32 Text-19M, LAION400M](https://huggingface.co/wkcn/TinyCLIP-ViT-40M-32-Text-19M-LAION400M).
- Revision `95ec8197b3f2fe7f747865c61ca556cf0768b2f7`; safetensors 336,859,508 bytes,
  SHA-256 `1b08131ac0ac54429aab216c397c550527d31f5682afb5d21d35091ad39538ae`.
- MIT license; preserve [the upstream notice](../../scripts/photo-model/LICENSE-TinyCLIP.txt).
  Music models retain their separate CC BY-NC-SA 4.0 noncommercial terms.
- [Source manifest](../../scripts/photo-model/source-manifest-v4.json) pins every input;
  no pickle or remote Python code is loaded. No weights enter Git or the production APK.

In a separate Python 3.12 environment, from the repository root:

```text
python -m pip install torch==2.5.1 --index-url https://download.pytorch.org/whl/cpu
python -m pip install -r scripts/photo-model/requirements.txt
python scripts/photo-model/download_source.py --output /path/to/tinyclip-source
python scripts/photo-model/export_model.py --source /path/to/tinyclip-source --output /path/to/tinyclip-atmospheres-v4.onnx
```

The exporter verifies source hashes and Android scene ordering, exports opset 17 /
IR 8, and compares ONNX Runtime output with PyTorch on seeded input. The recorded
maximum difference is `2.682209e-7`. A different artifact hash requires investigation,
not bypassing the app's check. The generated JSON records provenance and prompt hashes.

Both scripts accept `--variant 8m` to reproduce the previous v3 artifact with the
unchanged [v3 source manifest](../../scripts/photo-model/source-manifest.json).

Copy the verified file to the phone, then select **选择封面 → 更多 → 推荐设置 →
导入照片识别文件**. Only a complete matching size/hash becomes active. The app has
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

`music-atmosphere-v4-valence` keeps finite audio scores in `[.05, 1]`, deduplicating
repeated labels by their maximum score. It starts with the same four group sums:
lively (happy/positive/upbeat/fun/summer), peaceful (calm/relaxing/meditative/soft),
reflective (sad/melancholic/dark), intense
(epic/action/energetic/powerful/adventure/sport).

Relaxing describes pace and can coexist with sadness. Let `R` be relaxing support,
`N = max(0, sad + melancholic - lively)` and `S = N + peaceful - R`. If `S > 0`,
transfer `R * N / S` from peaceful to reflective; otherwise transfer zero.
Darkness alone cannot make that transfer, and positive cues counterbalance sadness.
Square each resulting group sum and normalize to emphasize the leading preference.
These are explicit editorial heuristics, **not calibrated music probabilities**.

If a normalized musical preference reaches `.65`, retain only photos whose support
for that atmosphere is tied for or greater than every other picture atmosphere.
Otherwise retain blended ranking. This rejects a calm-leading picture for clearly
reflective music without a butterfly/object blacklist. It applies to every group
and can return fewer than five photos, or none. Manual atmosphere and single-cover
selection remain available. A quiet photo is not automatically a sad photo.

The score is the dot product of these normalized music weights and the four image
supports. Reject scores below `.26` (uniform support scores `.25`). Object labels,
filenames, directories and evaluation labels do not participate. Identical contents
are deduplicated; ties use neutral photo ID. Only the authorized scope is eligible.
See the [ranking and UI follow-up](../testing/photo-ranking-refinement-2026-11-11.md)
for the fixed input comparisons and the lower candidate-count tradeoff.

Reasons name the contributing music and broad picture atmosphere. A specific
scene is supplementary, never required and never substituted for an uncertain
object. Manual atmosphere selection and manual cover choice remain available.
Native model creation/use/close share one coroutine ownership scope so cancellation
cannot lose a newly created ONNX session or skip its release.

## Evidence and limits

In the November 10 comparison, both 8M and 40M desktop experiments reached mean
provisional P@5 `.95`; 8M was then retained for its smaller file. V4 now uses 40M
after a separate 20-image phone review exposed action mismatches. Real Android
API 33 inference with v4 still reproduces
`1.00 / 1.00 / .80 / 1.00` versus v2 `.60 / .60 / .20 / .20` and old MobileNet
`1.00 / 1.00 / .40 / .80`. These are development comparisons on already-seen,
provisional labels, **not held-out generalization or human acceptance**.
That historical result used the v3 ranking rule. The current rule comparison is
recorded separately; historical evidence is preserved, not overwritten.
See the [v3 report](../testing/photo-atmosphere-and-review-2026-11-10.md) and
[v4 new-photo review](../testing/photo-quality-v4-2026-11-11.md).

Hardware/provider edge cases and model/product/redistribution suitability still
require review. No SDK, runtime dependency or media permission was added.

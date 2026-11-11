# Discogs-EffNet / Jamendo mood-theme evaluation

Investigation date: 2026-11-06. This is an exploratory audio-model assessment for [#41](https://github.com/Axfff/Vibe-Player/issues/41), not an implementation or acceptance test of that story.

## Feature fit

Issue #41 asks for photos from the user's local collection to be ranked using both a documented music signal and photo content. It also requires honest signal disclosure, preview/apply/cancel via the basic cover flow in [#40](https://github.com/Axfff/Vibe-Player/issues/40), manual fallback, local photos unless the user expressly consents to sending them, and a repeatable music-and-photo evaluation. Both are Release 2 stories; their sprint assignment is not yet confirmed. Issue [#36](https://github.com/Axfff/Vibe-Player/issues/36) is the opposite direction, photo-to-music discovery.

Discogs-EffNet plus the Jamendo head is a candidate **music signal** for #41. It does not analyze photos, compare an image to a track, decide which album track represents an album, or implement the UI and fallback. The existing `services/discovery` prototype is a separate photo-to-intent/catalog workflow; its mock vision labels are fixed examples, while its live vision path requires cloud authorization. It is not a validated on-device photo-content model for this story.

## Model and interpretation

The [official example](https://essentia.upf.edu/models.html#mtg-jamendo-mood-and-theme) loads mono audio at 16 kHz, runs `TensorflowPredictEffnetDiscogs` with `discogs-effnet-bs64-1.pb` and output `PartitionedCall:1`, then sends its 1,280-dimensional embeddings to `TensorflowPredict2D` with `mtg_jamendo_moodtheme-discogs-effnet-1.pb`. The head gives 56 independent scores for each patch, including useful labels such as `calm`, `energetic`, `happy`, and `sad`, as well as themes such as `advertising` and `documentary`. These labels do not directly describe an image or measure the listener's feelings. The script here averages patch scores for a simple track-level exploratory profile; this is a choice for the smoke test, not a validated aggregation, calibrated confidence, or established threshold.

The [head metadata](https://essentia.upf.edu/models/classification-heads/mtg_jamendo_moodtheme/mtg_jamendo_moodtheme-discogs-effnet-1.json) describes 18,486 full tracks and reports test PR-AUC 0.14 and ROC-AUC 0.76. These aggregate dataset metrics are not evidence of ranking accuracy for album covers or of accuracy on this app's listeners and music. The [backbone documentation](https://essentia.upf.edu/reference/std_TensorflowPredictEffnetDiscogs.html) describes 128-frame patches and default 62-frame hop; results may vary across time and clips. Validate suitable track and album aggregation, thresholds and language against the actual use case.

The two frozen graph files total 21,106,287 bytes (about 20.1 MiB); runtime and audio preprocessing add to the eventual application size and resource use. [Essentia's licensing page](https://essentia.upf.edu/licensing_information.html) states its models are CC BY-NC-SA 4.0 for non-commercial use, with proprietary licensing on request. The Essentia library and its third-party dependencies have separate licensing terms. Confirm compatibility with the intended distribution and use before packaging or shipping.

## Local smoke test

**Result: the complete chain ran locally.** On 2026-11-06, macOS 27.0.1 arm64, CPython 3.12.6, NumPy 1.26.4 and `essentia-tensorflow` 2.1b6.dev1173, the script loaded both graphs and returned finite, in-range scores for two recordings and a silence control. One run produced the following mean patch scores:

| Input | Decoded duration | Embeddings → predictions | Highest three mean scores | Measured audio processing |
| --- | ---: | --- | --- | ---: |
| Hungarian Dance No. 5 | 45.87 s | 45 × 1,280 → 45 × 56 | film 0.288, adventure 0.202, drama 0.174 | 0.894 s |
| Vibe Ace | 61.47 s | 60 × 1,280 → 60 × 56 | relaxing 0.110, documentary 0.091, film 0.082 | 0.879 s |
| Generated silence | 30.00 s | 29 × 1,280 → 29 × 56 | film 0.119, energetic 0.107, melodic 0.106 | 0.844 s |

Model initialization took 0.742 s. Processing times include decoding, embedding and classification, but exclude Python import, initialization and shutdown; these are single exploratory measurements, not benchmarks. The process emitted thousands of repeated `No network created, or last created network has been deleted` warnings during the run, despite producing results and exiting successfully; the cause and deployment implications remain uninvestigated. Silence still received nonzero labels, so scores alone must not be treated as evidence of usable music: a separate silence/invalid-input check and manual fallback are needed. There are no ground-truth mood labels here, so the output demonstrates execution, not accuracy. The test does not demonstrate Android deployment, battery use, latency on a phone, or recommendation quality.

The fetched model graphs and metadata, sample recordings, and generated results are stored under `artifacts/essentia-evaluation/`, which is ignored by Git. They are local evaluation artifacts and are not committed or shipped with the app. Recreate them with the commands below from the repository root. The commands target the tested Apple Silicon / CPython 3.12 wheel; other platforms need an appropriate TensorFlow-enabled Essentia build. The package's [installation documentation](https://essentia.upf.edu/machine_learning.html) and the [official wheel index](https://essentia.upf.edu/downloads/python-wheels/macosx/arm64/) describe available builds. An [upstream issue](https://github.com/MTG/essentia/issues/1486) discusses macOS ARM wheel compatibility. Install only the TensorFlow-enabled package in a clean environment; its inference algorithms are in `essentia.standard`.

```sh
mkdir -p artifacts/essentia-evaluation/models artifacts/essentia-evaluation/audio
curl -fL -o artifacts/essentia-evaluation/models/discogs-effnet-bs64-1.pb https://essentia.upf.edu/models/feature-extractors/discogs-effnet/discogs-effnet-bs64-1.pb
curl -fL -o artifacts/essentia-evaluation/models/discogs-effnet-bs64-1.json https://essentia.upf.edu/models/feature-extractors/discogs-effnet/discogs-effnet-bs64-1.json
curl -fL -o artifacts/essentia-evaluation/models/mtg_jamendo_moodtheme-discogs-effnet-1.pb https://essentia.upf.edu/models/classification-heads/mtg_jamendo_moodtheme/mtg_jamendo_moodtheme-discogs-effnet-1.pb
curl -fL -o artifacts/essentia-evaluation/models/mtg_jamendo_moodtheme-discogs-effnet-1.json https://essentia.upf.edu/models/classification-heads/mtg_jamendo_moodtheme/mtg_jamendo_moodtheme-discogs-effnet-1.json
curl -fL -o 'artifacts/essentia-evaluation/audio/Hungarian_Dance_number_5_-_Allegro_in_F_sharp_minor_(string_orchestra).ogg' 'https://librosa.org/data/audio/Hungarian_Dance_number_5_-_Allegro_in_F_sharp_minor_(string_orchestra).ogg'
curl -fL -o artifacts/essentia-evaluation/audio/Kevin_MacLeod_-_Vibe_Ace.ogg https://librosa.org/data/audio/Kevin_MacLeod_-_Vibe_Ace.ogg
python3.12 -m venv /tmp/vibe-essentia-evaluation/venv
curl -fL -o /tmp/vibe-essentia-evaluation/essentia_tensorflow-2.1b6.dev1173-cp312-cp312-macosx_11_0_arm64.whl https://essentia.upf.edu/downloads/python-wheels/macosx/arm64/essentia_tensorflow-2.1b6.dev1173-cp312-cp312-macosx_11_0_arm64.whl
/tmp/vibe-essentia-evaluation/venv/bin/pip install 'numpy<2' /tmp/vibe-essentia-evaluation/essentia_tensorflow-2.1b6.dev1173-cp312-cp312-macosx_11_0_arm64.whl
ffmpeg -v error -f lavfi -i anullsrc=r=16000:cl=mono -t 30 -c:a pcm_s16le artifacts/essentia-evaluation/audio/silence-30s.wav
/tmp/vibe-essentia-evaluation/venv/bin/python scripts/experiments/essentia_mood_smoke.py --model-dir artifacts/essentia-evaluation/models artifacts/essentia-evaluation/audio/*.ogg artifacts/essentia-evaluation/audio/silence-30s.wav > artifacts/essentia-evaluation/results.json
```

The [librosa example-recordings page](https://librosa.org/doc/main/recordings.html) identifies these as Brahms' Hungarian Dance No. 5 and Kevin MacLeod's *Vibe Ace*. They are illustrative recordings, not a labeled quality benchmark; check each recording's license before any redistribution. To identify the fetched copies, their SHA-256 hashes are:

| File | SHA-256 |
| --- | --- |
| `discogs-effnet-bs64-1.pb` | `3ed9af50d5367c0b9c795b294b00e7599e4943244f4cbd376869f3bfc87721b1` |
| `mtg_jamendo_moodtheme-discogs-effnet-1.pb` | `03f2b047020aee4ab39f8880da7bdae2a36d06a1508d656c6d424ad4d6de07a9` |
| Hungarian Dance sample | `919b48aa4cc66a0357d2cd5728664c5ab8f15c4b3469460df4b59470d35d3e49` |
| Vibe Ace sample | `6c23aed3dd5aa57f2b1652ecab68d15d9b82ad257f54e639eb2880ca09bc118a` |

The CLI also has isolated contract tests for score aggregation, missing files, malformed embeddings and non-finite predictions. With NumPy installed, run `python -m unittest discover -s scripts/experiments -p 'test_*.py' -v`. These tests use a stub runtime; the real-model run above is a separate smoke test.

## Android validation and next evaluation

On 2026-11-06 a standalone Android experiment passed on an Android 17/API 37 arm64 emulator. For the first patch of a five-second, 16 kHz mono PCM16 WAV, Android generated mel features and ran the official ONNX backbone and head using ONNX Runtime Android 1.20.0. Its maximum absolute error against the desktop Essentia mel reference was 2.48e-5; its 56 final scores differed from desktop ONNX by at most 7.15e-7. The first-patch top labels were `space`, `relaxing`, `film`, `soundscape` and `dream`. The [Android experiment README](../../scripts/experiments/android-onnx/README.md) records setup, hashes, exact commands and scope. This is a numerical parity check, not a mood-accuracy test.

The application is Android/Kotlin with minimum SDK 21, and has no current model runtime for this chain. ONNX Runtime Android 1.20.0 declares SDK 21 support, but this single emulator run does not verify API 21–23, other ABIs or a physical device. The experimental preprocessor accepts only a specific WAV format and one patch: Android decoding, resampling, full-track patching and aggregation have not been implemented. Measure model and runtime size, initialization, inference time, memory and battery on representative supported devices before committing to the approach. Analyze accessible local audio off the UI thread, handle unavailable/revoked files and cancellation, and cache versioned results with invalidation when audio changes. Album-level aggregation and minimum usable-audio handling need a documented decision.

To satisfy #41, separately choose and validate a local photo-content representation and a transparent way to compare it with music labels. Rank only accessible photos, surface the signal actually used, and preserve manual selection whenever analysis or matching is unavailable. Choose a small set of appropriately licensed music and consented photos, spanning contrasting and ambiguous moods. Before running, record each album/track, candidate photos, expected suitable and unsuitable matches and why; then record the actual ranked results, selected model versions, timing, missing-data outcomes, and disagreements or limitations. Include matching and non-matching cases plus no access, no usable audio, no suitable match, cancellation and model failure, and exercise #40's preview/apply flow on Android. The team should agree the examples and evaluation approach; the smoke test does not complete these criteria.

## Exploratory text-label and caption bridge

The companion `scripts/experiments/label_caption_ranking.py` tests whether the actual audio outputs can be converted into short, intelligible labels and used in a transparent ranking of *descriptions*. It uses four manually chosen hypotheses: adventure → adventurous; the mean of drama and dramatic → dramatic; the mean of relaxing, calm and meditative → peaceful; and the mean of happy, positive and uplifting → joyful. It then averages the profile scores associated with each candidate's manually assigned vibes. These scores are relative experimental quantities, not calibrated confidence or a quality threshold. A description without supported vibe annotations is unscored, and if none have annotations the probe falls back to manual selection.

The [candidate fixture](../../scripts/experiments/caption_candidates.json) consists of four authored descriptions: a hiker on a stormy mountain (adventurous, dramatic), a quiet shore (peaceful), a celebration (joyful), and a receipt (no supported annotation). These are **not photographs**, and a person supplied their vibe labels; no image was processed or independently understood. The expectations declared before running the ranking were mountain first for Hungarian Dance and shore first for Vibe Ace. However, the audio scores had already been inspected before the mapping and expectations were selected. This is selection-biased exploration, not a blinded or independent evaluation.

| Input and hypothesis | Top text labels by profile | Expected first | Actual ordered candidates and experimental scores |
| --- | --- | --- | --- |
| Hungarian Dance | adventurous, dramatic | mountain | mountain 0.153; celebration 0.025; shore 0.020; receipt unscored |
| Vibe Ace | peaceful, joyful | shore | shore 0.065; celebration 0.052; mountain 0.035; receipt unscored |

The silence case produced manual fallback when the caller **declared** it unusable; the script does not detect silence. A candidate set containing only the unannotated receipt, and an audio-analysis error, also produced fallback. This illustrates that audio labels can form human-readable search terms and produce different rankings over *pre-annotated descriptions*. It does not show that they retrieve useful photographs, that the subjective matches reflect listeners' preferences, or that the feature has a reliable no-suitable-result decision. Validate actual image content, mapping and thresholds on held-out music and photos with independently agreed expectations.

Reproduce after running the audio smoke test above:

```sh
python3 scripts/experiments/label_caption_ranking.py artifacts/essentia-evaluation/results.json --index 0 --usable-audio yes
python3 scripts/experiments/label_caption_ranking.py artifacts/essentia-evaluation/results.json --index 1 --usable-audio yes
python3 scripts/experiments/label_caption_ranking.py artifacts/essentia-evaluation/results.json --index 2 --usable-audio no
python3 -m unittest discover -s scripts/experiments -p 'test_label_caption_ranking.py'
```

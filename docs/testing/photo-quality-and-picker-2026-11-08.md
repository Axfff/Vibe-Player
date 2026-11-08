# Picker investigation and conservative scene-model experiment

The Xiaomi album selection gap was resolved in the observed session, and the two
flower fixtures no longer produce a butterfly explanation with the new model.
**The new model is not accepted as an overall ranking-quality improvement.** It
returns fewer candidates, and the four frozen provisional ranking cases regress.
PR #50 remains a draft; formal acceptance criteria remain unchecked.

## System search and Xiaomi Gallery

The same Redmi remains Android 16 / T extension 17; the public keyword-highlighting
interface requires extension 19. A real update check returned “系统已是最新版本”.
The inspected security page did not expose a Google Play module update, and the
standard module-update settings intent had no handler. Updating this app cannot
raise a system extension. The app now displays the actual version, the requirement,
and the system/Google Play update paths while retaining ordinary selection.

The six fixture images were indexed in Android MediaStore, readable as files, and
had no `.nomedia` ancestor. After opening Xiaomi Gallery, they appeared in
**影集 → 相册 → 其他相册**. The app's ordinary picker then showed the same folder in
**影集 → 相册 (horizontal scroll) → 其他相册**. One sunflower was selected, returned
to the explicit QA1108 confirmation, and became a readable one-photo source.
Removing that temporary source retained the original six-photo directory and cover.
Gallery startup and elapsed indexing time were not isolated, so the exact internal
refresh cause is unproven. No gallery database or personal album setting was changed.

![Test folder in Xiaomi Gallery](evidence/photo-quality-2026-11-08/gallery-qa-six.png)
![The same six fixtures in the ordinary picker](evidence/photo-quality-2026-11-08/picker-qa-found.png)

The new **找不到相册？从文件选择照片** action provides a separate public SAF image
multi-select route with the same target/grant confirmation. More than 100 selected
URIs are rejected without adding a partial source. Emulator UI tests verified
launch and cancellation without changing the existing source. Physical-device
verification of this new route is pending the user's screen unlock.

## Model and explanation changes

The old MobileNet ImageNet classifier has a narrow scene vocabulary: its flower
cue is principally the daisy class. The replacement experiment uses a pinned
TinyCLIP image tower with 40 general scene prototypes; the model stays outside
Git and the APK. See [preparation, license, hash and preprocessing](../development/photo-scene-model.md).

Central-crop and whole-frame predictions must agree, with a cosine floor of `.20`
and a margin of `.02`. Overlapping classes can instead support an explicitly
broad family description, provided both views also beat every outside class by
the same margin. Flower/insect disagreement cannot become a broad flower label.
The decoder tests caught and corrected boundary-pixel bleed from an unmaterialized
crop. Unrelated image classes remain in the competition; they are not all forced
into a mood-associated scene.

The UI uses Chinese music/scene descriptions, marks them as model guesses and
editorial associations, identifies broad descriptions, reports uncertainty and
read failures separately, and explains that the ranking score is not accuracy.
Manual mood and cover choices remain available. Existing music associations are
retained where their scene classes still exist; new scenes extend the mapping.
Old photo-model imports get an update-required state; music models and saved covers
remain intact, and failed imports preserve the previous files.

Two author checkpoints were compared on the same 33 unique licensed images using
the same initial vocabulary and thresholds. TinyCLIP 8M and 40M each initially
accepted 9/33 specific labels. The 8M version accepted both flower regressions;
40M initially abstained on the pink flower. Broad families subsequently increased
accepted cues to 11/33 (8M) and 14/33 (40M) in desktop comparison, but did not establish
an overall ranking improvement. The 8M experimental artifact is 33,305,014 bytes.
This is development/model selection on a small known set, not a blind accuracy study.

Actual Android inference now reports `flowers` for both img_0001 (sunflower) and
img_0002 (pink meadow saffron), cosine scores approximately `.369` and `.385`.
The latter previously received a butterfly explanation. These are two regression
examples, not evidence that all flower/butterfly errors are solved.

## Four-case actual inference and frozen post-evaluation

All four original representative audio files and all 20 neutral image inputs per
case were processed by production MoodAnalyzer, OnnxPhotoFeatureExtractor and
PhotoRanker on the disposable API 33 emulator. No expected relevance file was
available to the Android test. The separate unchanged evaluator read the frozen
provisional labels only after inference. Candidate identifiers/hashes, actual
music scores, scene cues, rules and timings are retained in
[raw inference](evidence/photo-quality-2026-11-08/real-ranking.json) and
[evaluation](evidence/photo-quality-2026-11-08/evaluation.json).

| Case | Old returned / relevant | New returned / relevant | Old P@5 | New P@5 | Old / new hard distractors |
|---|---:|---:|---:|---:|---:|
| 01 | 5 / 5 | 3 / 3 | 1.00 | 0.60 | 0 / 0 |
| 02 | 5 / 5 | 3 / 3 | 1.00 | 0.60 | 0 / 0 |
| 03 | 4 / 2 | 4 / 1 | 0.40 | 0.20 | 2 / 2 |
| 04 | 5 / 4 | 1 / 1 | 0.80 | 0.20 | 1 / 0 |

Missing Top-5 slots count as zero: mean P@5 falls **0.80 → 0.40**. Precision among
returned candidates is **16/19 → 8/11**; case 03 still has two hard distractors.
The smaller hard-distractor total partly reflects returning fewer images and is
not a general precision improvement. The first overly strict pass had mean P@5
0.35; preserving existing associations and allowing broad descriptions improved
that to 0.40, still below the old baseline. Labels were not changed, and no
per-image or per-case exception was introduced to improve these numbers.

All 80 per-case image extractions completed without errors. Cold extraction of
20 images took 2.30–2.58 seconds per case; cached extraction took 0.21–0.28 seconds.
These are emulator timings, not measured Redmi performance. The final APK used
for this real-inference pass has SHA-256
`69c892da4fe92911f8aed3e8156c78ec1a6240c5f6ee18b64dd7cc53c720a3a7`.
The run occurred before commit; the evidence records its base and a digest of the
complete production source tree rather than pretending it was a clean-HEAD run.

## Verification boundaries

- JVM tests cover score ordering/deduplication, no-match behavior, version messaging,
  wrong/ambiguous scene outputs, broad-family boundaries and Chinese explanations.
- Real Android tests cover image preprocessing, import recovery/migration and
  picker cancellation. The initial combined real-inference/UI run passed 8 tests;
  the final real-inference run passed again after the family/mapping refinements.
- Local UTP aborted before running any tests due to a protobuf `IllegalAccessError`.
  The documented legacy Gradle runner was used locally; CI settings and assertions
  were not weakened. An existing CI race at PhotoUiTest's immediate window check
  was replaced by waiting for the actual app window. DocumentsUI Back can traverse
  parent directories; cancellation now waits for the real return with a bounded loop.
- Full-suite, exact-HEAD coverage and CI status are recorded on PR #50 after this
  change. Test execution does not constitute subjective recommendation acceptance.
- Original phone playlists, ten seeded files and saved cover were verified
  unchanged after ordinary picker selection/removal. The phone subsequently locked.
  A new model file was copied into the dedicated QA Models directory, but new-model
  import, new file-picker success and the new recommendation UI on the physical
  device require the user's unlock and are not reported as passed.

The screenshot fixtures retain complete creator/license/source/change information
in [attribution](evidence/photo-quality-2026-11-08/fixture-attribution.json). The full
frozen dataset and original evaluation remain linked in the earlier
[evidence README](evidence/photo-scopes-2026-11-07/README.md). No personal photos,
device serial, raw phone UI dumps, private backups, credentials or weights are
included in this evidence.

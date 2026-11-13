# Music to photo ranking v1

This documents the original MobileNet baseline. The current branch ranks broad
[TinyCLIP picture atmosphere](photo-scene-model.md), independently of specific
object naming, with Chinese explanations and uncertainty handling. The
[November 10 comparison](../testing/photo-atmosphere-and-review-2026-11-10.md)
records recovery beyond the old baseline; human quality acceptance remains open.
The [November 11 v4 review](../testing/photo-quality-v4-2026-11-11.md) compares the
stronger image tower on the same new-photo pool and documents the remaining overlap.
Historical v1 results below are not results for the current model.

Use the existing `MoodAnalyzer` from PR #46. The user chooses a representative track within the target (first available is the displayed default). It decodes at most the initial 30 seconds, skips silent patches and reports decoded seconds, effective patch seconds and patch count. No lyrics, whole-song or playlist aggregation is claimed. Missing/invalid models, silence, short/unreadable audio and inference errors remain explicit. Manual atmosphere input is labelled 用户选择.

Runtime: existing ONNX Runtime Android 1.20.0, no new dependency or SDK upgrade. minSdk 21, compile/target SDK 33, Kotlin 1.8.21, AGP 8.0.2/Gradle 8 remain unchanged. Actual AAR manifest/merged manifest and API 33 execution are compatibility evidence; API 21–23 device behavior is still pending. ML Kit's [current labeler](https://developers.google.com/ml-kit/vision/image-labeling/android) requires API 23 and was not introduced.

Photo extractor: [official ONNX Model Zoo MobileNet v2-7](https://github.com/onnx/models/tree/main/validated/vision/classification/mobilenet), Apache 2.0; 14,246,826 bytes; SHA-256 `c1c513582d56afceff8516c73804e484c81c6a830712ab6d682253f4a3cd042f`. Input is oriented RGB, centered square crop resized to 224x224, CHW [1,3,224,224], [0,1] normalized by means [.485,.456,.406] and standard deviations [.229,.224,.225]. Output is 1,000 ImageNet logits; stable softmax yields classifier confidence, not calibrated photo relevance. A verified limited synset subset provides alp/cliff/lakeside/seashore/valley/volcano/daisy/butterfly/canoe/volleyball cues. This small object vocabulary cannot reliably distinguish action intensity, melancholy or nuanced composition; it is an experimental semantic baseline, not a general atmosphere recognizer. No identity/health/psychological inference.

The two existing music graph hashes/official links are in [mood-inference.md](mood-inference.md). [MTG model terms](https://essentia.upf.edu/licensing_information.html) specify CC BY-NC-SA 4.0 for noncommercial use. All weights are externally provisioned for this educational experiment, never committed or bundled in the app APK. Intended redistribution remains for human review. The optional test APK may contain locally supplied test assets and must not be published with weights.

Use **配置本地分析模型** from the photo-source or recommendation screen to import the three official files through Android DocumentsUI. Each file is copied to app-private `no_backup/photo-models/` only after its exact length and SHA-256 match the evaluated version. Existing developer-provisioned files are recognized after the same validation. The app has no automatic network/model downloader and its production Manifest has no INTERNET permission. See [model setup](photo-model-setup.md) for the user flow, atomic installation and recovery behavior.

`PhotoFeatureExtractor` is replaceable. Content is hashed on each use, including providers without modification timestamps. The bounded in-memory cache keys photo identity/content hash/model version; it never authorizes candidates. Only already enumerated current-scope photos are queried, before any feature lookup. Cache holds at most 256 entries and is cleared on scope mutations; no sensitive feature backup/persistent thumbnails. Cache reconstruction after process death is intentional. Identical content is deduplicated in ranking.

Rule `music-visual-v1`: documented associations live in `PhotoRanker.mapping`. Keep mapped music scores >=.05, sort score descending/label ascending and use top 5. For each music label select its strongest associated image cue >=.10. Score is the sum of music score / retained music-score sum × that cue confidence. Reject combined score <.05. Tie order is neutral photoId ascending. Same input/model/rule is deterministic. Return up to 5 initially and show more on demand; never pad from another scope. Explanations list actual music scores, visual cue confidences and the association. These are editorial associations, not learned emotional truths. No filename/directory/expected label is read by production inference.

Final candidates carry photo reference, fingerprint/model/rule versions, score and reasons; request identity/revision accompanies the result UI and crop. Saving calls existing `setCover(..., RECOMMENDED)` only after revalidation and user confirmation. Playback/Now Playing cover semantics remain those of the foundation branch.

Real evaluation uses only frozen neutral `candidates/case_01..04.json` and their actual media from fixture PR #49. `RealPhotoRankingTest` writes scores/features/timing/failures without private URIs. Evaluation labels are consumed only by the separate post-run evaluator; no labels change after results. Actual results and limitations belong in [results](../testing/music-photo-recommendation-results.md).

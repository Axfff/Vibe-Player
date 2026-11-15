# Model setup and newer-picker verification — 2026-11-08

Implementation: `5a87ae6017dafdb41b0e959f93971d39d62e0598`, on `feat/scoped-photo-recommendations` / [Draft PR #50](https://github.com/Axfff/Vibe-Player/pull/50). No personal phone was connected or operated. Human acceptance remains pending.

## Model configuration

Both the source screen and recommendation screen now expose **配置本地分析模型**. Users can review required files and official sources, import each model through Android DocumentsUI, see progress and validation, cancel, and recheck installed files. See [implementation and recovery details](../development/photo-model-setup.md).

Actual UI verification used a fresh, task-owned AVD `PhotoSearch_API_36_Ext19`, Android 16 / API 36, actual T extension 19, Google APIs x86_64 image revision 1. This AVD is separate from the existing API 33 regression emulator and contains only licensed test fixtures and official externally supplied models.

| Action | Observed result |
|---|---|
| Open setup on a fresh installation | 0/3 ready; all three models missing, with actionable import buttons |
| Choose each official ONNX file through actual DocumentsUI | Readiness advances 1/3 → 2/3 → all ready; app-private SHA-256 values match the pinned official hashes |
| Choose the music backbone in the mood-model slot | Oversized/wrong-model rejection; the previously imported mood model remains valid with the same hash |
| Force-stop and reopen the app | All three models recognized as ready; no developer-private-file provisioning was used |
| Ordinary picker → select two fixture photos → explicit target confirmation | Only those two photos are bound to `ModelSearchProbe`; model import itself had added no photo sources |
| Choose actual Carefree representative and analyze | 30.0 decoded seconds, 29 patches / 29.824 effective seconds; two ranked photos, zero failed reads |

The last result uses the freshly imported real music and image graphs. Scores were 0.622553 and 0.5205355; these are ranking values, not accuracy. The image baseline still describes one flower using a butterfly cue, as documented in the earlier experiment; this follow-up changes setup, not model quality or frozen evaluation labels. No cover was changed by importing models or running the recommendation.

Screenshots: [missing](evidence/photo-model-setup-2026-11-08/models-missing.png), [ready](evidence/photo-model-setup-2026-11-08/models-ready.png), [wrong file preserves model](evidence/photo-model-setup-2026-11-08/wrong-model-preserved.png), [after app restart](evidence/photo-model-setup-2026-11-08/models-after-restart.png), [real recommendations](evidence/photo-model-setup-2026-11-08/imported-model-recommendations.png). Media attribution is unchanged from [fixture PR #49](https://github.com/Axfff/Vibe-Player/pull/49) and the [prior attribution record](evidence/photo-scopes-2026-11-07/image-attribution.json).

## System search: request observed, filtering not available

The installed official image is `x86_64-36-ext19_r01.zip`, SHA-1 `a641ca5992fef209c23e36a999cd7b3c12758ff6`, verified against Google's SDK image catalog before extraction. Device fingerprint: `google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.E1/13823249:userdebug/dev-keys`.

- `com.google.android.photopicker` and `com.google.android.providers.media.module`: version name 16, version code 36. Bundled Google Photos: 7.8.0.697873850. No Google account was signed in; cloud-provider behavior was not tested.
- The app correctly reports that the system allows a search-enhancement request. A user-entered `beach` query launches the actual system picker; the app reports **已请求搜索高亮**.
- The picker instead shows the ordinary Photos / Collections grid, including flowers and unrelated images. It displays neither the requested query nor a highlighted result section; its menu exposes Settings only. This is not a successful keyword/semantic-filtering claim.
- Read-only `device_config list mediaprovider` shows `com.android.providers.media.flags.highlight_search_results_feature=false`, despite `enable_picker_highlight_search_results_apis=true` and the available extension API. This is consistent with the observed ignored request; SDK extension availability alone is insufficient. No feature flags were changed to manufacture a passing result.
- Cancelling returns to the source screen without adding photos. The explicit **普通选图** route succeeds: actual two-photo selection, explicit target confirmation and subsequent in-app recommendation work.

Evidence: [request](evidence/photo-model-setup-2026-11-08/search-request.png), [ordinary grid returned by provider](evidence/photo-model-setup-2026-11-08/search-provider-result.png), [read-only provider flags](evidence/photo-model-setup-2026-11-08/provider-flags.txt). Actual keyword highlighting therefore remains **UNVERIFIED / unavailable in this image's default provider configuration**; it is no longer merely an unattempted test due to missing SDK-extension hardware. A provider with the feature enabled is still needed. No Xiaomi/Redmi Gallery private API, whole-library access, cloud upload or search-result scraping was introduced.

## Automated regression and coverage

Clean local collection at `5a87ae60`, one dedicated disposable API 33 emulator, no optional licensed test assets; `dirty=false`, `measurement_only=false`. The cumulative inventory now includes 66 production files, including Release 1 and both new setup files. No exclusions or threshold changes.

| Suite | Result | Cumulative line coverage |
|---|---|---|
| JVM | 100 passed | Included below |
| Android instrumentation | 66 total: 62 passed, 4 skipped | 2506 / 3103 = **80.760554%** |
| Backend (Python 3.14.5) | 159 passed | 882 / 920 = **95.869565%** |
| Web | 22 passed | 812 / 825 = **98.424242%** |

JaCoCo class-ID audit passed for 495 classes. The four skips are the existing three optional audio-reference tests plus the optional four-case real-ranking test; none is counted as a pass. Earlier real inference remains documented in [the original report](music-photo-recommendation-results.md); the new UI-driven real-model run above is separate evidence.

New installer tests exercise valid atomic replacement, wrong hashes, truncation, oversize, provider read failure, cancellation and orphan-staging recovery using isolated known byte sequences. UI tests cover both entry points, model explanations, recreation, actual document-picker cancellation, unreadable source errors and cancellation state recovery.

An initial full run exposed a cancellation finalization bug and an over-eager test Back sequence; both were fixed and the four targeted tests then passed, followed by the clean full run above. Failed runs are not represented as passing evidence.

Production APK: 40,511,691 bytes, SHA-256 `1f21b7438499bbc12380eb537272ef8edb7b35100eeb1a5b2ff478f03e3d3fd0`. Model weights remain outside Git and the production APK. Machine-readable [coverage summary](evidence/photo-model-setup-2026-11-08/coverage-5a87ae60.json).

Still pending: human AC/product review, model redistribution suitability, API 21–23 runtime, OEM phone behavior, cloud-provider outages, and real search highlighting on a supporting provider. Student ID (CroissantXinny): 50027204.

## CI follow-up

At documentation head `6688a36c`, [push CI](https://github.com/Axfff/Vibe-Player/actions/runs/42468516641) passed all executed tests and the exact gate (Android 2505/3103 = 80.728327%, backend 919/957 = 96.029258%, web 812/825 = 98.424242%). [PR CI](https://github.com/Axfff/Vibe-Player/actions/runs/42468520820) had two unsuccessful attempts, retained as failures:

- Attempt 1: an existing, unchanged playback-position assertion reported before=6389 ms, after=5952 ms, elapsed=754 ms. All new model tests and the coverage percentages passed. No playback tolerance or player code was changed.
- Attempt 2: three photo UI tests timed out or observed package `android` instead of the expected app/picker. Logcat showed DocumentsUI displayed and its storage-root directory loaded while the accessibility-root checks failed. This run's Android coverage was 73.960683%, so its gate correctly failed.

The photo test helpers now inspect Android's public user-interactable windows, refresh the nodes, and identify the top application window including its descendants instead of relying solely on `rootInActiveWindow`. Background application windows cannot satisfy the return-to-app check, and modal consent is still exercised through actual UI. Timeouts, grant assertions and coverage thresholds are unchanged; failures include window diagnostics in logcat. All seven affected local tests passed after this test-only adjustment. The latest PR checks record full-run status; the production application and the API 36 verification above are unchanged.

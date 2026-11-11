# Music/photo recommendation verification — 2026-11-07

Historical MobileNet baseline. For the current atmosphere model, unchanged-label
four-case comparison and Xiaomi route, see the [November 10 follow-up](photo-atmosphere-and-review-2026-11-10.md).
The older scores and GitHub permission status below describe that earlier run.

Status is evidence-specific, not human acceptance. Integration branch `feat/scoped-photo-recommendations` uses main `844caf7c`, cover `25849a42` and PR #46 `4776e4e1`. Real inference below ran from `e27c4398c3d67ab9d4188421f3ccbc2c0035f437`. App APK SHA-256: `a7c24120896b858759737e0fb21f07167565a047728e95a10e44af99a2dc88ae`. Human Review/Story acceptance remain pending.

## Real inference, separate from UI authorization tests

Dedicated disposable emulator: PhotoScopes_API_33, Android 13/API 33, x86_64, T extension 5. Runtime ONNX 1.20.0 AAR declares minSdk 21; SHA-256 `07a8f71ef890afed8c6087a56220e6d558a492804276ee2dd7cb7f6262242027`. Three official model hashes verified; weights kept outside Git and app APK. Model/rule/preprocessing documented in [ranking](../development/music-photo-ranking.md).

Inputs reuse [fixture PR #49](https://github.com/Axfff/Vibe-Player/pull/49), frozen `music-photo-v1` at `ef6dae8d`. Optional test assets contain ONLY neutral candidate JSON, actual referenced audio/images, and model files. Test inference never reads evaluation/manifest/gallery/expected labels. The separate post-run evaluation compares fixed results against unchanged provisional CSV. No model-dependent relabeling occurred.

| Case / representative | Actual returned IDs (score order) | Relevant / returned | P@5, missing slots = 0 | Difficult distractors | Audio / cold image / cached image ms |
|---|---|---:|---:|---:|---:|
| case_01 / Carefree | img_0001, img_0002, img_0006, img_0005, img_0010 | 5/5 | 1.00 | 0 | 3604 / 1301 / 316 |
| case_02 / Thinking of You | img_0015, img_0010, img_0009, img_0011, img_0002 | 5/5 | 1.00 | 0 | 2839 / 1160 / 336 |
| case_03 / Heartbreaking | img_0015, img_0011, img_0010, img_0009 | 2/4 | 0.40 | 2 | 3367 / 1207 / 279 |
| case_04 / Volatile Reaction | img_0039, img_0040, img_0013, img_0028, img_0026 | 4/5 | 0.80 | 1 | 2987 / 1137 / 300 |

Each group has 20 images; all 80 analysis attempts succeeded. Four cold runs clear the feature cache independently; subsequent cached passes still read/hash actual bytes. Audio is the first at-most-30-second excerpt; measured decoded/effective seconds and all 56 scores are in the raw JSON. No full-track/playlist or lyric analysis. The initial exploratory run produced identical rankings, but its inter-case cache was not cleared; its timings are retained locally and not used as cold measurements.

case_03 has only four results above the predefined threshold; no fifth image is fabricated. P@returned is 0.50 there; the table's P@5 penalizes the missing fifth slot. The existing fixtures evaluator requires exactly five results, so it is not falsely reported as passing; the explicit calculation above supports the legitimate fewer-than-five behavior. The uniform random mathematical baseline for five of twenty is 0.60 relevance and 1 difficult distractor. These small provisional samples are not generalization accuracy or agreed acceptance thresholds. The melancholy case is a clear limitation of simple object/scene associations and remains a product/model-review concern.

## Search capability matrix

| System | T extension | Picker | Request highlights | Actually saw query highlights | Ordinary fallback |
|---|---:|---|---|---|---|
| Android 13/API 33 emulator | 5 | com.google.android.providers.media.module, version 13-9832127 | Correctly not requested | Not supported / not claimed | Real UI launch/cancel test; source binding unchanged |
| Injected supported capability in instrumentation | simulated >=19 | Intent construction only | Public bundle asserted | Not a provider/device test | Launch exception asserted |
| Actual supporting system/provider | >=19 required | Not available in this task | Pending | **BLOCKED: no supporting device** | Not substituted by ordinary launch |

## Test and environment limits

Initial build/JVM suite passed 100 tests, zero failures/errors/skips. Initial full run used a read-only copy of an older Pixel_7 AVD and failed nine existing empty-library/search checks because eight pre-existing tracks violated its clean-library precondition. The original AVD was not wiped. A brand-new task-specific AVD was created for subsequent checks. First failure log is retained; it is not hidden or counted as a pass.

Three existing MoodAnalyzer optional reference tests require the separately supplied reference WAVs, which were not available; they are skipped, not passed. The new four-case test uses real music plus real models and is distinct from those reference-parity tests. Model weights are not added to ordinary CI.

The first complete local e27 collection passed tests but failed the exact Android gate: 2330/2921 = 79.767203%. Adding EXIF transformation and stale/unbound candidate assertions at `2b9cbbab` produced 2342/2921 = 80.178021% with the optional real-model suite (100 JVM; 60 Android, 3 skips; 159 backend; 22 web; zero failures). Ordinary cloud [e27 run](https://github.com/Axfff/Vibe-Player/actions/runs/42376919123) passed build/tests but correctly failed coverage at 2183/2921 = 74.734680% without model assets. These are historical measurements, not substitutes for the final head. No UI, permission or Release 1 files were excluded. All 64 production files remain in the cumulative inventory from `947709e4`.

`PhotoAuthorizedFlowTest` was subsequently added to exercise actual DocumentsUI consent, a real persisted tree grant, direct versus nested images, limits, exclusions, sharing/removal, recreated preview, confirmation, revocation before save and default restoration without any model download. Its colored fixture images test access/crop mechanics only. Early development runs exposed test-side view-layout and DocumentsUI navigation synchronization errors; those failed logs are retained. The final targeted run passed after waiting for visible navigation state and tapping observed accessibility bounds.

## Device authorization and confirmation evidence

The following observations use the same e27 app installation/APK stated above on the dedicated API 33 emulator; no personal phone was used. ScopeA has the actual Carefree representative. Its directory contains fixture img_0001, img_0002, img_0005, img_0006 and img_0010. ScopeB first selects only img_0015 and img_0039 through the real multi-photo picker. Selection opens the explicit target/local-purpose confirmation before adding a source.

| Action / expected boundary | Actual observation | Evidence |
|---|---|---|
| Authorize only A's directory, default nonrecursive | 5 available, 5 examined; no global gallery scan | [A authorization](evidence/photo-scopes-2026-11-07/scope-a-authorized.png) |
| Give B a disjoint fixed selection | 2 available, 2 examined; A's directory not inherited | [B selection](evidence/photo-scopes-2026-11-07/scope-b-selected.png) |
| Explicitly share A's source with B | B has the 2-image selection plus 5-image directory | [B sharing](evidence/photo-scopes-2026-11-07/scope-b-shared.png) |
| Remove the directory from A | A has no sources; B still reads both sources; originals and saved cover retained | [A removed](evidence/photo-scopes-2026-11-07/scope-a-removed.png) |
| Force-stop/reopen, then actual `adb reboot` | After boot completion B revalidates both 2 and 5 readable images; A's removed binding stays absent | [After device reboot](evidence/photo-scopes-2026-11-07/scope-b-reboot.png) |
| Run real representative against A's authorized 5 photos | 30.0 decoded seconds, 29 effective patches / 29.824 s, 5 qualifying images, zero image failures; actual music/visual scores shown | [Ranked candidates](evidence/photo-scopes-2026-11-07/music-ranked.png) |
| Candidate → existing crop → cancel → reopen → confirm | Cancel leaves default; confirmation creates a custom cover through the existing repository | [Confirmed cover](evidence/photo-scopes-2026-11-07/confirmed-cover.png) |
| Restore default, then explicitly rebind A | Custom cover removed; separately re-shared directory reads 5 again | Observed UI and repository-backed automated assertions |

The device procedure proves app-level removal and reattachment, not OS-level complete revocation or a cloud-provider outage. Controlled stale/deleted request failures and the real-grant crop-save revocation test supplement it. A physical-device run, API 21–23 run, actual supported search-highlighting provider, and external cloud/offline/provider permission changes remain pending. Do not infer those from successful API 33 selection. Album identity/snapshot isolation is tested in code; the initial two-target session above used Playlists.

The e722 APK (SHA-256 `86837d30c904dce9cbc377d7823d433441f89df0e4953b9e361f123e34b8087d`) was then installed as an update on the same dedicated device. Existing playlists, bindings and grants survived. The [final entry layout](evidence/photo-scopes-2026-11-07/final-entry.png) stacks cover actions so their labels are readable. A real representative analysis was started and immediately closed with Back; the source screen returned with the same five authorized images and no cover change. No weights are in this APK.

The actual Royalty Free music Album was then opened through its overflow menu. Its initially empty scope was explicitly linked to the shared five-image source. The representative chooser showed both real songs; selecting Thinking of You produced [four above-threshold candidates](evidence/photo-scopes-2026-11-07/album-ranked.png), based on 30 decoded / 29.824 effective seconds. The first was previewed, cropped and confirmed as the [Album cover](evidence/photo-scopes-2026-11-07/album-cover.png). Returning to [ScopeA](evidence/photo-scopes-2026-11-07/playlist-after-album.png) still showed its restored default and “Set custom cover”, confirming the Album save did not overwrite that Playlist. The images' original authors/licenses and fixture modifications are in [image-attribution.json](evidence/photo-scopes-2026-11-07/image-attribution.json).

One later local regression run while both emulators were active failed an existing playback-position tolerance check (before 5688 ms, after 5135 ms, elapsed 227 ms). The failure is retained, not counted as a pass; the QA emulator was shut down before the final clean collection. No playback tolerance or test was weakened. The e722 cloud run passed runtime tests but its exact Android gate was still 2334/2921 = 79.904142%; editable-search/blank-query assertions were then added, keeping the same production denominator.

Raw inference is [real-ranking-fresh.json](evidence/photo-scopes-2026-11-07/real-ranking-fresh.json); separate provisional calculations are [evaluation-fresh.json](evidence/photo-scopes-2026-11-07/evaluation-fresh.json). Only licensed fixture IDs, image hashes, scores and timings are published, with no full content URI. The model can misclassify flowers as butterfly cues; displayed explanations faithfully report classifier output, not verified scene truth. Human photo-label and product review remains necessary.

## Reproduction

Final clean local collection: `c185ba7541caa5c817815782ec00277781bb0493`, one dedicated API 33 emulator, no optional model assets and no retained model files. Build, all executed tests, JaCoCo class-ID audit and the unmodified exact gate **PASS**. [Machine-readable full scope/counts](evidence/photo-scopes-2026-11-07/cumulative-coverage-c185ba75.json) records `dirty=false`, `measurement_only=false` and the production/test source fingerprint.

| Module / suite | Executed result | Cumulative line coverage |
|---|---|---:|
| Android JVM | 100 passed, 0 failures/errors/skips | Included with Android |
| Android instrumentation | 62 total: 58 passed, 0 failures/errors, 4 explicit optional skips | 2341/2921 = **80.143786%** |
| Discovery backend | 159 passed, 0 failures/errors/skips | 882/920 = **95.869565%** |
| Discovery web | 22 passed, 0 failures/errors/skips | 812/825 = **98.424242%** |
| Coverage contract tests | 12 passed | Gate behavior, not app coverage |

The four instrumentation skips are the three old reference tests plus the optional real four-case test in this no-assets configuration; the latter's successful separate real inference is documented above. No skipped test is counted as passed. Local backend Python is 3.14.5; cloud uses 3.12 and has a different executable-line denominator, so its percentage must be read from its own exact-head report. The production APK remains SHA-256 `86837d30c904dce9cbc377d7823d433441f89df0e4953b9e361f123e34b8087d` after these test-only changes. Documentation/evidence follow in a separate commit; they do not change the measured application/tests.

Phase status: (1) IMPLEMENTED, AUTOMATED_TESTED and DEVICE_VERIFIED for the documented source operations; (2) IMPLEMENTED, AUTOMATED_TESTED and DEVICE_VERIFIED for the actual models/ranking/crop flow; (3) IMPLEMENTED and AUTOMATED_TESTED, DEVICE_VERIFIED for ordinary selection/fallback, but actual search highlights are BLOCKED by unavailable supporting hardware/provider. All phases remain HUMAN_REVIEW_PENDING. These labels do not imply blanket certification of the explicitly listed device/provider edge cases.

Use JDK 17, SDK 33, existing Gradle wrapper. Supply optional test assets using existing `-PmoodTestAssetsDir=<external-directory>`: `photo-models/<three official onnx filenames>`, `photo-cases/case_01.json` through `case_04.json`, and `photo-cases/media/...` paths referenced by those neutral files. No answers or private data. The real test copies model files to app-private noBackup storage; its test APK is local-only and must not be published with weights.

Run `:app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest :app:teamCoverageReport` on a disposable emulator only; existing regression fixtures reset test preferences. Then `python scripts/coverage/coverage.py report` after current-source backend/web collections. Never run the resetting suite on a personal phone. For local-only real inference, select `com.iven.musicplayergo.photos.RealPhotoRankingTest` via the runner; output is `files/photo-ranking-results.json` in the debug app. Pull through `run-as`, not from a private user gallery. The app production Manifest has no INTERNET permission.

Normal Git branch push succeeded. GitHub integration rejected both Issue update and Draft PR creation with HTTP 403; there is no PR or updated Issue claim. The branch can be reviewed directly, and the pending Issue payload is retained. No main push, force push, merge or tag mutation.

Student ID (CroissantXinny): 50027204

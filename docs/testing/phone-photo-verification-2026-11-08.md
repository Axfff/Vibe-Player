# Photo/model flow on a physical Redmi — 2026-11-08

The local model import → explicitly authorized photo directory → real music/photo inference → crop and saved cover flow passed on the user's **REDMI K90 Pro Max**. Public system-search highlighting is unsupported on this device's current SDK extension. Ordinary picker launch/cancellation passed; fixture selection through that picker remains unverified because its album list did not expose the new test folder during this session.

Production implementation: `5a87ae60`; tested APK SHA-256 `1f21b7438499bbc12380eb537272ef8edb7b35100eeb1a5b2ff478f03e3d3fd0`. Test-helper head: `1b076946`. This follow-up adds verification evidence only, with no application, model, SDK, permission, or CI changes. Related: [Draft PR #50](https://github.com/Axfff/Vibe-Player/pull/50), [Issue #41](https://github.com/Axfff/Vibe-Player/issues/41).

## Device and scope

| Item | Observed value |
|---|---|
| Manufacturer / model | Xiaomi / 25102RKBEC; DocumentsUI identifies REDMI K90 Pro Max |
| OS | Android 16, API 36, `OS3.0.310.0.WPMCNXM` |
| ABI / T extension | arm64-v8a / **17** |
| Picker | `com.android.photopicker`, version name 16 / code 36 |
| File providers | Xiaomi `com.android.fileexplorer` for model OpenDocument; `com.google.android.documentsui` for SAF directory authorization |

The user authorized phone operation and enabled Xiaomi's additional USB debugging security setting after the initial input-injection refusal. The existing debug APK and app data were backed up locally before `adb install -r`. Existing preferences were byte-identical immediately after upgrade, and both original playlist records remained structurally identical after the complete session. No reset-oriented emulator instrumentation suite ran on the phone.

Only a new `QA1108` playlist, six licensed fixture images, the licensed Carefree track and the three pinned model files were used. Test directories are `Pictures/VibePlayerQA_20261108`, `Music/VibePlayerQA_20261108`, and `Download/VibePlayerQA_20261108/Models`. Private backups, device serial, raw UI dumps, unrelated media and personal-library screenshots are excluded from Git evidence.

## Verified actions

| Action | Actual result |
|---|---|
| Open model setup | Initially 0/3 ready |
| Import three files through Xiaomi's visible file picker, confirming each selection | 1/3 → 2/3 → all ready; private-copy SHA-256 matches all three pinned versions |
| Select the backbone file for the mood-head slot | Wrong/oversized file rejected; previous mood-head hash unchanged |
| Force-stop/relaunch the app | All three models remain ready |
| Select only the dedicated image directory through SAF | OS permission confirmation followed by explicit `QA1108` target confirmation; six readable candidates, recursion off |
| Analyze Carefree using the newly imported models | 30.0 decoded seconds, 29 patches / 29.824 effective seconds; six candidate images processed, four above threshold, zero read failures |
| Select first recommended image, drag crop, save | Real cropped sunflower cover saved with source `RECOMMENDED` |
| Force-stop/relaunch again | Saved cover visible; persisted directory grant still reads six images |
| Remove source from this target, then request manual-mood recommendations | No readable photos message, no candidates; saved cover and original files unchanged |
| Explicitly reauthorize the same test directory | Six readable images restored; phone left on `QA1108` with its saved cover |

First-run observed latency was **8.195 seconds**, including ADB tap preparation and accessibility/UI sampling. This is one functional observation, not an isolated inference benchmark, battery study or stress test. Top two scores were 0.62255305 and 0.5205357. The baseline still labels the pink flower with a butterfly cue; the result verifies real execution, not recommendation accuracy or product suitability.

The saved cover is 91,824 bytes, SHA-256 `79670aba3f9e37cae5fdc0d76f5b63e240611d3a0be3cd9afc80122b71227335`. Its hash was unchanged after source removal. All ten seeded files (three models, six JPEGs, one MP3) retained their original hashes. Source removal here is **application-scope removal**, not OS persisted-URI revocation.

## Search and OEM picker limitation

`build.version.extensions.t=17`, below the public search-highlight contract's extension 19 requirement. The app accurately displays that search enhancement is unsupported. Entering `beach` and choosing the search action opens the ordinary picker through the fallback path. Cancelling adds no source and retains the keyword; the explicit ordinary-selection action also opens the picker. Read-only provider configuration reported `enable_photopicker_search=false`. No provider flags, OS version, permissions or firmware were changed to manufacture search support.

The system MediaStore contains exactly six rows in the dedicated `Pictures/VibePlayerQA_20261108/` directory. However, the Xiaomi picker's Albums and Other albums views did not expose this new folder during the session. The underlying cause was not established. No personal photo was substituted. Consequently, **ordinary-picker selection/persisted grants for a returned image URI remain unverified on this phone**; directory selection, persistent directory authorization and complete in-app recommendation were verified separately. This observation does not mean all user albums are unavailable.

A supporting provider/device is still needed to verify actual keyword highlighting. This result does not claim direct access to Xiaomi Gallery's private semantic-search API or its internal AI labels.

## Evidence and remaining review

- [Models ready](evidence/phone-photo-2026-11-08/models-ready.png), [wrong replacement rejected](evidence/phone-photo-2026-11-08/wrong-model-preserved.png), [ready after relaunch](evidence/phone-photo-2026-11-08/models-after-restart.png).
- [Search capability and keyword](evidence/phone-photo-2026-11-08/search-unsupported.png), [six authorized images](evidence/phone-photo-2026-11-08/authorized-folder-six.png).
- [Actual recommendations](evidence/phone-photo-2026-11-08/real-recommendation.png), [cover after relaunch](evidence/phone-photo-2026-11-08/cover-after-restart.png), [no candidates after removal](evidence/phone-photo-2026-11-08/no-recommendation-after-removal.png), [final usable playlist](evidence/phone-photo-2026-11-08/final-ready-playlist.png).
- [Inference observation](evidence/phone-photo-2026-11-08/real-recommendation.json), [preservation checks](evidence/phone-photo-2026-11-08/after-source-removal.json), [model hashes](evidence/phone-photo-2026-11-08/models-hashes-after-ui-import.txt).

Fixture image IDs: `img_0001`, `img_0002`, `img_0010`, `img_0019`, `img_0027`, `img_0039`; track `audio_0001` / Carefree. All seven fixture attribution records and transformations are preserved in the [phone fixture attribution](evidence/phone-photo-2026-11-08/fixture-attribution.json), with hashes matched to the seeded files. Additional context is in the [existing fixture attribution](evidence/photo-scopes-2026-11-07/image-attribution.json), [four-case report](music-photo-recommendation-results.md), and [fixture PR #49](https://github.com/Axfff/Vibe-Player/pull/49). Model licenses and exact versions are unchanged from [model setup](../development/photo-model-setup.md).

Automated suites and the exact cumulative coverage gate passed at unchanged production/test head `1b076946`: [push CI](https://github.com/Axfff/Vibe-Player/actions/runs/42473149005), [PR CI](https://github.com/Axfff/Vibe-Player/actions/runs/42473152636). Phone verification does not replace automated tests or human AC acceptance. Remaining: human recommendation-quality/UX review, the OEM picker visibility discrepancy and selected-image grants, real supported search highlighting, OS-level grant revocation on this phone, other OS/device versions, cloud/offline cases, long-run performance and model licensing review. All formal AC remain pending human acceptance.

Student ID (CroissantXinny): 50027204.

# Pending Discovery fixtures and source attribution — November 15, 2026

Scope: [PR #62's P2/P3 findings](https://github.com/Axfff/Vibe-Player/pull/62#issuecomment-6874699922), Refs [Story #36](https://github.com/Axfff/Vibe-Player/issues/36). The Story records the dated pre-work plan and estimate. Shiyuan LIN / CroissantXinny authorized this repair and unlocked the phone; Codex implemented and executed the checks.

## Fixture lifecycle

API29+ JPEG fixtures remain `IS_PENDING=1` from insertion through teardown. The target application's resolver can read their exact URIs without publishing them. Normal teardown deletes only the URIs inserted by that fixture manager, including incomplete copies; failed deletions remain tracked and fail the test. Legacy API<29 read-only provider behavior and production permissions are unchanged.

If instrumentation cannot execute teardown, pending rows remain private until Android expires them. Android documents owner-only file access and automatic pending expiration/deletion; the actual expiry time and cleanup schedule belong to the platform. This repair does not claim immediate deletion on process death. [Android MediaStore reference](https://developer.android.com/reference/android/provider/MediaStore.MediaColumns#IS_PENDING), [expiration semantics](https://developer.android.com/reference/android/provider/MediaStore.MediaColumns#DATE_EXPIRES).

`PendingDiscoveryPhotosTest` runs without models or network and checks:

- Owner-side JPEG MIME, byte and bitmap decoding while pending, followed by exact-URI removal and idempotent cleanup.
- Loss of the in-memory teardown registry: the retained row stays pending, has a future expiry, and is absent from the published-media set. The regression's outer cleanup removes that exact row.
- Copy failure after insertion: the partial row is tracked and removed, while another independently owned test row keeps its bytes and pending state.
- An escaping interruption during copying: the row remains private and cleanup can remove it.

These are actual MediaStore tests with injected failure/skipped teardown, not a physical kill/power-loss experiment or a wait until automatic expiry. The stand-in for pre-existing media also remains pending; the regression never publishes test pictures.

## Coverage provenance

Regeneration restores commit `1379dffede6b36bb49a4b085193fd4a6c0e0c37b` for `RecyclerSheet.kt` and `NotificationActionsAdapter.kt`. No production source hash, executable range, baseline, exclusion or threshold changes. The cumulative manifest still contains 84 production files for this stack.

The existing scope gate now validates `source_commits` against generated Git history. Clearing, inventing, reordering or duplicating history fails. Only the commit that last wrote the manifest may omit its own hash, because a source-and-manifest commit cannot contain its own SHA. This exception never permits removing earlier history; generated runtime reports include the full history. Tests cover the gate wiring and actual temporary Git repositories, including a later documentation-only commit.

The new gate rejected the original #62 manifest at `RecyclerSheet.kt`; after regeneration it passed. Changes: fixture helper/direct tests `414288a2`, attribution guard/tests `d76d4b9f`, additional real-Git regression `ca222600`.

## Local and phone verification

The four fixture tests on the phone/emulator used Android test sources from `414288a26474400d3643a6ab9720501663b4a3de`. Their helper/cases remain unchanged in the subsequent CI timing correction below. Build used JDK 17.0.20.1, the pinned wrapper/AGP and the existing offline dependency cache:

```text
project/gradlew.bat -p project --offline --no-daemon --console=plain -I <local-cache-init> :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
python scripts/test_coverage_contract.py
python scripts/coverage/coverage.py scope
adb -s <serial> shell am instrument -w -r -e class com.iven.musicplayergo.discovery.PendingDiscoveryPhotosTest com.iven.musicplayergo.debug.test/androidx.test.runner.AndroidJUnitRunner
```

- Build and **127 JVM tests passed**; **17 coverage-contract tests passed**, no failures/errors/skips.
- Disposable `CoverReview_API_33`, serial `emulator-5560`: **4 tests passed**, 0.546 seconds.
- User-unlocked Redmi 25102RKBEC, serial `7512baaf`, API37: **4 tests passed**, 0.117 seconds. Only the test package was updated through normal `adb install -r`; the main application was not replaced.
- Phone main APK SHA-256 remained `db9eea492bfe6ed15b2a94565d48ce1ab5552f63dba4c98071a5aad0c904ddd1` (previously verified #69 integration `a99ad4e6`). All **8** existing files under `files`, `shared_prefs` and `databases` retained their SHA-256; no persistent files were added. Each regression asserted removal of its exact fixture URIs and preservation of the separate row where applicable. Private snapshots are not committed or published.
- Local test APK SHA-256: `df67c5a529e0bf20aed82cc815ce2b623e102536d66bd081db496ab4171e1c36`. Local main APK (emulator only): `2341cc5bf008012c0836713951909f07ce9a6eabfd23d1ab9148d029d1bbc2a0`.

The phone result certifies this model-independent fixture lifecycle against the installed integration. The whole opt-in real-model/catalog/UI test was not rerun this time; earlier device/model results keep their original SHAs and limits.

## First CI failure and targeted test correction

[Run 43704839946](https://github.com/Axfff/Vibe-Player/actions/runs/43704839946) on `57fba9c4f300dc26c32592935c031a23432e66ad` **failed**, with one Android assertion: `DiscoveryFlowTest.confirmRealTransportResultsOpenSaveRestartRestoreAndExclude`, line 52, expected one discover request but observed two. All four new pending-photo cases passed in that run. The original failed ZIP is retained locally with SHA-256 `5778e4ec3ddf39b6aeca998dc5df7d703bd5996be890957970d236c41e10019d`; it is not a passing verification record.

The duplicate-click check had assumed the first loopback response was still in flight during the second `performClick`. A fast response can finish before the first call returns, making the second request legitimate. Commit `e876b9c86fe7138df1eaedd9b48d890c66c4dda3` holds the fixture response with a bounded latch until both click attempts complete, retaining the busy-state and exactly-one-request assertions. It does not relax assertions, add blanket retries, or change application behavior.

The updated test APK built successfully. All **7 DiscoveryFlowTest cases passed**, 20.9 seconds, on the disposable API33 emulator. This revised emulator-only test APK SHA-256 is `853d5f9e6982cff6f03cb4bf2a921a59225eda19a5a1df110a6ddec10f78a4b5`. The phone's previously tested package and main application were not replaced again. Final full CI on the corrected head is linked in the dated PR update; the first failure remains part of its history.

## Integration and review boundary

This remains a focused stacked PR targeting `test/discovery-phone-navigation-20261112`, exact base `8dc3aa9c6e90513bfd4eb1a4dfe050ce82340911`. Current main `99bc4264` and #61's later repair were inspected; the shared parent and sibling PRs were not changed. No pending production work is silently retargeted to main. Main/dependency integration must be tested separately after the normal review sequence.

The existing workflow still filters automatic PR CI to main; #72's all-base process change is separately awaiting review. Therefore this stack uses full exact-head workflow dispatch, retaining all build/test/coverage gates. Its PR publisher is normally skipped without a pull-request event. Final CI, raw-report audit and re-review request are recorded on [PR #62](https://github.com/Axfff/Vibe-Player/pull/62). No human approval, merge, AC checkoff, Story closure, model/permission reset or release-tag change is supplied by this report.

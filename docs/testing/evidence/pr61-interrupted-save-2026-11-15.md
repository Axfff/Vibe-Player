# Interrupted cover-save recovery — November 15, 2026

Scope: repair the [PR #61 P2 finding](https://github.com/Axfff/Vibe-Player/pull/61#issuecomment-6874699939), supporting [Story #40](https://github.com/Axfff/Vibe-Player/issues/40). The dated, pre-implementation owner/estimate is recorded on that Story. Implementation/testing: Shiyuan LIN / CroissantXinny with Codex assistance. Prior PR evidence and failed runs retain their original boundaries.

## Decision and implementation

After an interrupted replacement, metadata may still reference the old image while an uncommitted immutable WebP remains in private storage. A retry previously generated another filename without reclaiming that abandoned copy.

Recovery now takes the whole successfully decoded and validated metadata document. It restores referenced legacy `.bak`/`.delete` copies when necessary, then reclaims only recognized unreferenced importer filenames directly inside the album/playlist directories. Referenced images and their recovery copies, unknown names, nested directories and redirected files/directories are retained. Invalid or unavailable metadata prevents cleanup. No metadata is rewritten by recovery; failed file cleanup can be retried at the next open/save/remove operation.

Recovery and image/metadata transactions share an in-process lock across repository instances. This prevents reopening from treating an active writer's not-yet-committed image as an orphan. The app uses one process for this store; this is not a multi-process storage protocol.

Changes retain main `99bc4264f55c2de2ec4c3fdfdeba6fb974f0a4b9` through merge `11f133cc`; production and direct tests are commit `b596f3eb33d5b1e24f05b4d951d8601abc3ca70d`. No personal-phone installation, clearing, preference migration or media replacement was performed in this repair.

## Local verification

Tested production/test source: `b596f3eb33d5b1e24f05b4d951d8601abc3ca70d`. JDK 17.0.20.1, pinned Gradle wrapper/AGP, offline cached dependencies. From repository root:

```text
project/gradlew.bat -p project --offline --no-daemon --console=plain -I <local-cache-init> :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
python scripts/test_coverage_contract.py
python scripts/coverage/coverage.py scope
adb -s emulator-5560 shell am instrument -w -r -e class com.iven.musicplayergo.covers.CoverStorageRegressionTest,com.iven.musicplayergo.covers.CustomCoverInstrumentationTest com.iven.musicplayergo.debug.test/androidx.test.runner.AndroidJUnitRunner
```

- Build and **131 JVM tests passed**, zero failures/errors/skips.
- All **12 coverage-contract tests passed**; 85 cumulative production files are inventoried from the original baseline, with unchanged per-module 80% gates.
- **16 Android tests passed** on the disposable `CoverReview_API_33` emulator, serial `emulator-5560`, API 33. Each storage case uses its own temporary directory.
- The four before/after-commit replacement/removal cases now check directory contents after reopening and retrying, plus byte-for-byte preservation of another album and a playlist cover.
- Additional cases verify corrupt/unavailable metadata, retry after metadata becomes readable, recovery concurrent with a paused save, and symlink isolation. JVM filesystem cases cover orphan/temporary files, unknown/nested files, referenced backups, idempotence and unavailable directories.
- Interruption is injected at storage commit boundaries using an escaping `Error` and real image files; these tests do not claim a physical process-kill or power-loss experiment.

APK SHA-256:

| Artifact | SHA-256 |
|---|---|
| Debug app | `5123bf06d5efa781c0b260a8aa2c2a4d705da88b69796ece2f27473b644e8bbd` |
| Debug Android tests | `ea47c275b26b0e5d13d977619648a2f59a0171c55c7553676eb87350fc2e0855` |

Full exact-head CI, raw report audit and non-author review are recorded on [PR #61](https://github.com/Axfff/Vibe-Player/pull/61). Local results above do not substitute for that CI or teammate review. No acceptance boxes, release tags or Story state are changed by this report.

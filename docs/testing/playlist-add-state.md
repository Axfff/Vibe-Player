# Playlist picker: disabled Add for existing members

Verified 2026-10-27 (Asia/Shanghai). Implements [issue #8](https://github.com/Axfff/Vibe-Player/issues/8); supports Story #2 / AC3. This record does not replace formal team acceptance in #17.

## Change

The song picker previously displayed an active Add button even for tracks marked “Already in this playlist.” The button now uses the existing Material disabled appearance and cannot add that member. The listener also checks the current adapter row before invoking Add. Unadded tracks remain enabled when the library and storage are ready; existing repository duplicate and ordering checks remain in place.

TC03 in the [acceptance plan](custom-playlists.md#tc03--添加共享成员及同一歌单去重) now describes the disabled-button interaction. Its formal result remains Pending, and historical acceptance records are preserved.

## Tested source and environment

- Working tree based on `dc17523be591a72f9fefa98536c61689e4d99ad1`, with the `PlaylistAdapters.kt` change in this commit. Adapter SHA-256: `eceefd7d7f0faa252089fd2e6e1d71a041abc59d95194703ac14cac7080b7dd3`.
- macOS arm64, JDK 17.0.14, compile SDK 33; Android 17 / API 37 arm64 emulator, `Medium_Phone_API_37.0`, read-only with snapshots disabled.
- Debug APK SHA-256: `8e82c1d11cbe968f45273d6f144052f10abdb2676ea453c68d8e37c93941f908`.

## Verification

| Check | Actual result | Evidence |
| --- | --- | --- |
| `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` | Build successful; 30 JVM tests, zero failures/errors | [Build log](evidence/2026-10-27-playlist-add-state/build.txt), [suite counts](evidence/2026-10-27-playlist-add-state/summary.json) |
| Existing `additionOrderSurvivesRestartAndDuplicatesKeepTheirPosition` JVM test | Pass; duplicate additions preserve stored bytes and order | Included in the 24-test repository suite above |
| Existing Android `PlaylistLibraryRegressionTest#rg07_hiddenTrackRemainsPlayableAndOldControlsStillWork` | `OK (1 test)`; picker filtering, saved playback, ordinary Songs, Favorites, Queue and controls | [Instrumentation output](evidence/2026-10-27-playlist-add-state/instrumentation.txt) |
| Add B then A to a new playlist through the UI | A/B Add disabled, membership messages visible; C Add enabled | [Screenshot](evidence/2026-10-27-playlist-add-state/added.png), [observed states](evidence/2026-10-27-playlist-add-state/summary.json) |
| Tap disabled B; search for B; clear search and reopen picker | Search result and reopened A/B stay disabled; stored order remains `[B, A]` | Observed states and saved order in summary |
| Remove B through the detail UI, reopen picker, add B again | B becomes enabled; A stays disabled; readding B saves `[A, B]` | Observed states and saved order in summary |
| Independent AI review | No actionable findings in adapter/TC03 diff | Current row lookup, DiffUtil rebinding and storage safeguards inspected; no human approval implied |

Commands ran from `project/` using `bash ./gradlew --no-daemon --console=plain`. The existing Android test ran with `adb -s emulator-5560 shell am instrument -w -r -e class 'com.iven.musicplayergo.regression.PlaylistLibraryRegressionTest#rg07_hiddenTrackRemainsPlayableAndOldControlsStillWork' com.iven.musicplayergo.debug.test/androidx.test.runner.AndroidJUnitRunner`.

UI smoke checks used three generated WAV fixtures (`Issue8-A/B/C`) and a new `Issue8` playlist. Android UI hierarchy dumps established enabled state and membership text; the screenshot was visually inspected for gray Add controls. Read-only inspection of the debug app's saved playlist JSON confirmed both recorded orders.

## Limits

This was a light-theme portrait emulator check. Physical devices, TalkBack, large text and landscape remain outside this small change's verification; the broader matrix stays in #18. An emulator System UI ANR was dismissed before the smoke checks. An overlapping `uiautomator dump` attempt was rejected while instrumentation owned UiAutomation; all recorded smoke observations were collected after the test finished. Neither event was an application failure or a passing product test.

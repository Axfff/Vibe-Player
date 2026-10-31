# PR draft — Add persistent custom playlists and preserve playback on rebind

PR body source for [Draft PR #3](https://github.com/Axfff/Vibe-Player/pull/3). Target: `feat/custom-playlists` → `main`. Updated 2026-10-26; integration regression checks, feedback fixes and teammate review remain in progress.

## Problem

Listeners need several named collections of local music, each retaining its membership and order after an app restart. Favorites and the temporary Queue currently serve different playback needs.

## Changes and resulting behavior

- Add playlist list/detail views and create, rename, delete, search/add, and remove actions. Names are trimmed and unique without regard to case; duplicate additions retain their original position.
- Save each accepted mutation with Moshi and the dedicated SharedPreferences key `custom_playlists_v1`. Preserve unavailable memberships and resolve against the full successful scan, including hidden tracks.
- Play an independent ordered snapshot from its first available track, clearing the previous temporary Queue. Saved edits take effect on the next Play. Runtime file failures shrink the snapshot and terminate after finite attempts.
- Keep playlist management available for empty libraries and scan failures, and refresh existing library/detail views.
- Preserve the prepared player and media session when an Activity rebinds to the same running service. Route explicit Play/Pause commands to their corresponding operations. Honor hardware Pause/Stop during failed-file recovery.
- Keep Kotlin, XML, ViewBinding, MediaPlayer, JDK 17, SDK 33 and the existing Gradle/dependency versions.

## Related Issue

Refs #2 — [Sprint 2 Story and AC1–AC9](https://github.com/Axfff/Vibe-Player/issues/2).

## Verification

| AC / change | Check or test | Result | Evidence |
| --- | --- | --- | --- |
| AC1–AC5, AC7, AC9 data behavior | PlaylistRepositoryTest: names, isolation, deduplication/order, JSON reload, storage errors and resolution | 24 passed | [JUnit XML](../testing/evidence/2026-10-25-windows/TEST-com.iven.musicplayergo.playlists.PlaylistRepositoryTest.xml) |
| AC6 / AC9 recovery decisions | PlaylistFailurePlanTest: skip order, list end, bounded exhaustion and stale failures | 5 passed | [JUnit XML](../testing/evidence/2026-10-25-windows/TEST-com.iven.musicplayergo.player.PlaylistFailurePlanTest.xml) |
| Compilation / APK | `:app:testDebugUnitTest :app:assembleDebug` | BUILD SUCCESSFUL; 43 tasks, 17 executed / 26 up-to-date | [Build log](../testing/evidence/2026-10-25-windows/windows-build-retry.log) |
| Redmi device acceptance | CroissantXinny; Android 16 / API 36; TC01–TC10 | 10 passed; TC11–TC13 pending on this device | [Device report, screenshots and restart logs](../testing/custom-playlists-redmi-results.md) |
| Emulator acceptance | 曾沛涵; Android 17 / API 37; TC01–TC13 | 13 passed as recorded by the tester | [Report at 6e92066](https://github.com/Axfff/Vibe-Player/blob/6e92066dbadbd1d6abf5db2fc6b632b975aafa26/docs/testing/custom-playlists-manual-results.md) |
| Rotation, media commands, old playback/Favorites/Queue | RG01–RG07, prioritizing RG03/RG05/RG07 | Pending | [Test plan](../testing/custom-playlists.md) |
| GitHub CI on tested commit | Android CI push and PR runs for `785e0863` | Both passed; latest-head runs appear in PR checks | [Push run](https://github.com/Axfff/Vibe-Player/actions/runs/40286562346), [PR run](https://github.com/Axfff/Vibe-Player/actions/runs/40286566889) |
| Teammate code review / AC sign-off | PR Review and Story #2 | Pending | Review on this Draft PR; sign-off on the Story |

- Both device reports tested `785e0863e1efa8f44574cec1444b649a4016c911`. Local build evidence records `2f660d11647ac5cc3e77a3c39c05c02224584d41`, whose file tree matches source commit `03e85dd7d000aded436c6f3d5c756721e7e1ffbb` after the attribution update. The evidence publication updates documentation and attachments; Android source, Gradle and CI configuration retain the tested versions.
- Environment: Windows, Temurin 17.0.20.1+1, SDK 33 revision 3, Build Tools 30.0.3, Gradle Wrapper 8.0. Run from `project/`; exact command and APK hash are in [summary.json](../testing/evidence/2026-10-25-windows/summary.json).
- Initial build stopped on a DNS failure while resolving aapt2; retry succeeded. Both logs are retained. Existing resource-format, Manifest package, kapt and deprecated-API warnings are recorded in the logs.
- Redmi checks used three distinct 24-second test tones and independent fixtures. TC07–TC10 used force-stop/cold-launch on the same installation, with PID checks and tester confirmation of restored names, membership and order. The report includes 15 screenshots, UI snapshots and audio-file hashes.
- The emulator report remains on the teammate's `test/playlist-acceptance` branch at `6e92066`; this PR links that report and the Redmi results separately.

## Acceptance feedback

- [#4](https://github.com/Axfff/Vibe-Player/issues/4): newly copied audio waits for system media indexing; improve discovery and empty-library feedback.
- [#5](https://github.com/Axfff/Vibe-Player/issues/5): make the playlist entry easier to find on the main screen.
- [#6](https://github.com/Axfff/Vibe-Player/issues/6): a temporary blank white bar covers Refresh/Done when adding tracks.

Track fixes in these Issues and rerun the affected checks against each fix commit.

## Review focus and known limitations

Review same-service rebinding, idempotent media Play/Pause, and Stop cancelling pending file-failure recovery. Also review persistence write failure handling, full versus visible library resolution, Queue clearing, saved-order playback and details after refresh. Complete RG03, RG05 and RG07 on Android first, then the remaining plan. Active playback snapshots are session-only; saved names, membership and order persist across app restart.

## Before merge

- [x] Link the formal Story Issue and check scope against AC1–AC9.
- [x] Record local build and unit-test results for the tested source.
- [x] Record Redmi TC01–TC10 and the teammate's emulator TC01–TC13 results.
- [ ] Complete RG01–RG07 and record results and evidence.
- [x] Publish the feature branch and Draft PR linked to Issue #2.
- [x] Record successful push/PR CI for tested commit `785e0863`.
- [ ] Confirm CI for the final candidate commit.
- [ ] Address acceptance feedback #4/#5/#6 and retest affected behavior.
- [ ] Obtain a teammate's review and address feedback.
- [x] Update Sprint 2 / Release 1 evidence with the two acceptance reports.
- [ ] Complete AC sign-off and let the team decide merge readiness.

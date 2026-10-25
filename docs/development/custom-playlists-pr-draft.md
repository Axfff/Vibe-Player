# PR draft — Add persistent custom playlists and preserve playback on rebind

Local PR preparation. Target: `feat/custom-playlists` → `main`. Publish as **Draft** while device acceptance and teammate review are pending.

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
| AC1–AC9 on Android | TC01–TC13 | Pending; device environment Blocked | [Test plan](../testing/custom-playlists.md) |
| Rotation, media commands, old playback/Favorites/Queue | RG01–RG07, especially RG03/RG05/RG07 for this fix | Pending; device environment Blocked | Test plan |
| GitHub CI and teammate review | Remote branch/PR publication and review | Pending | Add actual run/review URLs after publication |

- Tested source: `2f660d11647ac5cc3e77a3c39c05c02224584d41`.
- Environment: Windows, Temurin 17.0.20.1+1, SDK 33 revision 3, Build Tools 30.0.3, Gradle Wrapper 8.0. Run from `project/`; exact command and APK hash are in [summary.json](../testing/evidence/2026-10-25-windows/summary.json).
- Initial build stopped on a DNS failure while resolving aapt2; retry succeeded. Both logs are retained. Existing resource-format, Manifest package, kapt and deprecated-API warnings are recorded in the logs.
- `adb devices -l` returned no devices and no AVD was configured. Device checks use the same installed app, independent fixtures and force-stop/reopen for persistence.

## Review focus and known limitations

Review same-service rebinding, idempotent media Play/Pause, and Stop cancelling pending file-failure recovery. Also review persistence write failure handling, full versus visible library resolution, Queue clearing, saved-order playback and details after refresh. Complete RG03, RG05 and RG07 on Android first, then the remaining plan. Active playback snapshots are session-only; saved names, membership and order persist across app restart.

## Before merge

- [x] Link the formal Story Issue and check scope against AC1–AC9.
- [x] Record local build and unit-test results for the tested source.
- [ ] Record TC01–TC13 and RG01–RG07 device results and evidence.
- [ ] Publish the feature branch and Draft PR through an authorized team channel, and record the real CI run.
- [ ] Obtain a teammate's review and address feedback.
- [ ] Update Sprint 2 / Release 1 evidence and let the team decide merge readiness.

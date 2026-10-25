# PR draft — Add custom local playlists

Status: local draft only; no remote PR has been created. Replace the related-Issue and evidence fields with real links before publishing.

## Problem

Listeners need multiple named collections of local music for different activities. The existing Favorites and temporary Queue do not provide independent, persistently saved playlists with their own membership and order.

## Changes and resulting behavior

- Add playlist list/detail screens with create, rename, delete, search-and-add, and remove actions.
- Validate trimmed, non-empty, case-insensitive unique names; preserve per-playlist insertion order and prevent duplicate membership.
- Persist playlist data separately from existing preferences using Moshi and a dedicated SharedPreferences key.
- Start an independent ordered playback snapshot from the first available track, clearing the previous temporary queue.
- Retain unavailable entries after a successful library refresh, skip unavailable tracks, and handle actual file-open failures with bounded recovery.
- Support playlist management when the device library is empty and repair refresh/lifecycle interactions in existing views.
- Add 29 unit tests and a CI workflow that builds the debug APK and uploads test results.

## Related Issue

The formal Story Issue has not been created. Until it exists, use [the Story and AC draft](../thinking/02-custom-playlists-user-story.md). Replace this paragraph with the actual Issue link; do not invent a number or use Sprint 1 Issue #1 as this feature's story.

## Verification

| Area | Check | Result | Evidence |
| --- | --- | --- | --- |
| Baseline | Unmodified base commit assembleDebug | Pass | Implementation report / handoff baseline log |
| Repository behavior | PlaylistRepositoryTest | 24 passed | Unit test XML / HTML report |
| Runtime recovery decisions | PlaylistFailurePlanTest | 5 passed | Unit test XML / HTML report |
| Compilation and packaging | Final :app:testDebugUnitTest :app:assembleDebug after removing generated build outputs | Pass, all 43 tasks executed | Final build log and APK |
| AC1–AC9 on device | TC01–TC13 | Pending | [Manual test plan](../testing/custom-playlists.md) |
| Integration regressions | RG01–RG07 | Pending | Manual test plan |
| GitHub Actions | Remote workflow execution | Not run | Add the real run URL after pushing |

- Tested source commit: `6ec486928175d448140fbdf05fa63ed64515b787` (local build and unit tests). Later changes are documentation only. Add the real CI run and device-tested commit when available.
- Build environment: complete JDK 17, Android SDK 33, project Gradle Wrapper.
- Device / Android API: not yet tested.
- This is an implementation candidate, not a claim that all acceptance criteria are satisfied.

## Review focus and known limitations

Please review playlist identity and persistence, raw versus filtered library resolution, clearing old queue state, failed-file recovery, paused rotation, and existing detail views after files disappear. Active playback uses a snapshot; saved edits take effect on the next Play. Persisting the complete active snapshot across process death, manual ordering, import/export, and AI are outside this change.

## Before merge

- [ ] Link the real Story Issue and confirm that its AC match this implementation.
- [ ] Record device acceptance and integration regression results for the tested commit.
- [ ] Record the real GitHub CI run.
- [ ] Obtain a teammate's review and address blocking feedback.
- [ ] Update Sprint 2 and Release 1 evidence links.

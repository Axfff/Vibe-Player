# Draft PR: Verify playlist regressions and fix media commands and denied access

Base: `feat/custom-playlists` · Head: `test/playlist-regressions`

Published as [Draft PR #12](https://github.com/Axfff/Vibe-Player/pull/12) on
2026-10-26 through the owner's explicitly authorized local GitHub CLI, after the
connected GitHub tool rejected creation with HTTP 403. Review and merge are pending.

## Problem

Playlist integration regressions lacked device evidence. Execution reproduced two
problems: explicit media Pause could resume a paused player, and denied audio
access could make a still-existing saved track appear unavailable.

## Changes and resulting behavior

- Separate explicit Play/Pause from headset toggles and double-click skipping.
  Repeated explicit commands preserve their requested state and clear stale click
  timing; existing toggle/headset behavior remains separate.
- Check audio permission before a MediaStore scan. Denied access keeps availability
  unknown, disables Play/Add and preserves saved members and playlist management.
  Restoring permission and refreshing makes the same external track playable.
- Add real Android regression fixtures, 24 instrumentation tests covering
  RG01–RG07, and a runner that rebuilds/installs both APKs and rejects failed or
  zero-test runs. Preserve before/after evidence for both defects.

## Related Issue

Refs #2 — [playlist Story and AC1–AC9](https://github.com/Axfff/Vibe-Player/issues/2).
This contribution targets the feature branch from
[feature PR #3](https://github.com/Axfff/Vibe-Player/pull/3).
Collaborators own TC01–TC13 on `test/playlist-acceptance`.

Refs [#9](https://github.com/Axfff/Vibe-Player/issues/9) (RG01–RG07 tracking),
[#10](https://github.com/Axfff/Vibe-Player/issues/10) (explicit media commands), and
[#11](https://github.com/Axfff/Vibe-Player/issues/11) (denied media access).
The issues were drafted during execution and published afterward; that timing is
disclosed in the tracking issue. The optional CI trigger/test-APK proposal remains
separate and is not included in this contribution.

## Verification

| AC / change | Check or test | Result | Evidence |
| --- | --- | --- | --- |
| RG01–RG07; AC6/AC9 integration and existing playback | Full Android instrumentation suite | 24 passed, 0 failures; 333.224 s | [Results and case mapping](../testing/custom-playlists-regression-results.md) |
| Playlist domain, persistence and failure-plan logic | Existing JVM suite | 29 passed, 0 failures | Same result record |
| Explicit media Pause defect | Same device reproducer before/after the fix | Failed before; passed after | [Before](../testing/evidence/2026-10-26-regressions/rg05-before-fix.txt), [after](../testing/evidence/2026-10-26-regressions/rg05-after-fix.txt) |
| Denied external-media access | Actual permission revoke/grant and live UI checks | Unknown while denied; same saved track playable after grant/refresh | [Permission evidence](../testing/evidence/2026-10-26-regressions/rg06-permission-after-fix.txt) |

- Tested source: `24beffad`; final documentation commits do not change that tested
  application/test source.
- Device: disposable Android 17 / API 37 arm64 emulator, headless with host audio
  disabled; JDK 17, Gradle 8.0 and existing SDK 33 build configuration.
- Command: `bash scripts/check-playlist-regressions.sh` builds
  `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`, installs
  both APKs and invokes AndroidJUnitRunner. See
  [reproduction instructions](../testing/playlist-regressions-automation.md).

## Review focus and known limitations

Review command routing, denied-access versus successful-empty scans, and the
preservation of saved membership and existing playback paths. RG05 asserts the
actual unavailable-files Toast and controls pending recovery within one main-loop
turn. Its transient focus test invokes the production callback; it does not verify
OS focus arbitration. Player assertions establish real `MediaPlayer.isPlaying` and
advancing position, not audible host output. Only API 37 was executed; physical
devices, other Android versions and hardware headset behavior remain unverified.

Prepared and reviewed with Codex assistance. AI review does not replace the
required human teammate review. These RG results do not sign off collaborators'
TC cases or declare Release 1 complete.

## Before merge

- [x] Publish and link the RG tracking task and both verified defect issues.
- [x] Record actual verification and its limits.
- [ ] Obtain a human teammate review and resolve blocking feedback.
- [ ] Reconcile relevant Sprint/release evidence with the team's final acceptance.

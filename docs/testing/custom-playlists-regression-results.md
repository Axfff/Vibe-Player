# Custom playlist integration regressions

## Scope and ownership

This contribution covers RG01–RG07 from the [playlist test plan](custom-playlists.md),
starting from PR #3 at `785e0863e1efa8f44574cec1444b649a4016c911`.
The separate `test/playlist-acceptance` branch records the collaborators' TC01–TC13
work. This record does not repeat or replace those results.

Execution and fixes are being prepared by Axfff with Codex assistance. AI assistance
is used for source inspection, test implementation, debugging, and evidence drafting.
Actual commands and observations determine the results; automated assistance is not
the required human teammate review.

## Execution plan

Use an isolated read-only Android emulator instance and disposable audio fixtures.
Record the tested source revision, APK hash, device/API, test output, and observations.
Each test starts with independently prepared data. Only test audio is removed.

| Case | Required observation | Status |
| --- | --- | --- |
| RG01 | Starting a saved playlist clears the old Queue and its restoration state; playback ends without returning to the old Queue. | Pending |
| RG02 | Editing saved membership preserves the active snapshot; the next explicit playlist Play uses the edited membership. | Pending |
| RG03 | Playing and paused rotation, plus background/return, preserve track, position, and subsequent playlist order. | Pending |
| RG04 | Repeated successful scans safely refresh an open vanished album/folder detail and the empty-library screen, without duplicate controls/rows. | Pending |
| RG05 | Runtime file failures recover finitely; explicit Play/Pause are idempotent; Pause/Stop cancel unwanted playback during recovery. | Pending |
| RG06 | Failed/unknown scan state differs from a successful empty scan; saved membership and playlist management remain accessible. | Pending |
| RG07 | Hidden media remains usable in saved playlists; ordinary playback, Favorites, Queue, and repeated refresh still work. | Pending |

Priority is RG03, RG05, and RG07, then the remaining cases. The existing 29 JVM tests
remain useful supporting checks but do not establish Android integration results.

## Setup details that affect validity

- RG03 uses actual Activity recreation while the same playback service lives. A
  process force-stop is a different scenario and does not promise restoration of an
  entire active playlist snapshot.
- RG04 distinguishes a dedicated album/folder detail from an artist's album
  selector. The latter can legitimately select another surviving album.
- RG05 invalidates audio after a successful scan, without refreshing the app before
  playback attempts. List repeat means the existing end-of-list Continue setting.
  Recovery-command tests must demonstrate that a command arrived while recovery
  was pending; otherwise record that subcase as unverified.
- RG07 gives B its own album/folder because existing Hide actions filter containers.
  Saved members resolve against the complete successful scan; the add picker uses
  the visible filtered library.

## Initial source finding

At the starting revision, `PlayerService.handleMediaIntent` groups explicit Play and
Pause with toggle/headset commands. Its MediaSession media-key path can therefore
toggle an already paused player on Pause, or skip on rapid repeated explicit
commands. `MediaPlayerHolder.handleMediaButton` already separates these commands.
This is an inherited inconsistency identified by source inspection. A regression
test must reproduce it before a fix is recorded as verified.

## Results and evidence

### RG05: reproduced before the fix

On 2026-10-26, the targeted Android test
`PlaylistRecoveryRegressionTest#mediaSessionPauseDoesNotResumePausedPlaylist`
failed against production source `785e0863`:

> Unexpected real MediaPlayer audio: explicit MediaSession Pause while paused

The app played a real fixture WAV, paused it, then received an explicit media Pause
through the service entry used by `MediaSession.Callback.onMediaButtonEvent`.
The assertion sampled the actual Android `MediaPlayer.isPlaying`, rather than a
mock player or only the app's displayed state. The emulator has host audio disabled;
the result establishes real decoder/player state, not an audible host recording.

Environment: macOS 27.0.1 arm64, JBR 17.0.14, Gradle 8.0, emulator 37.2.12,
Android 17 / API 37 arm64, Medium Phone read-only temporary overlay. Baseline
`:app:testDebugUnitTest :app:assembleDebug` passed with the existing 29 JVM tests.

The Gradle device runner initially aborted before running any tests. Explicitly
installing both newly built APKs and invoking AndroidJUnitRunner directly allowed
the test to execute against the correct application. That setup failure is not
counted as an application regression result.

[Actual pre-fix runner output](evidence/2026-10-26-regressions/rg05-before-fix.txt)
is retained. All other RG results remain Pending until their device runs complete.

### Focused media-command fix

`PlayerService.handleMediaIntent` now handles explicit Play and Pause separately
from the toggle/headset double-click branch. Each explicit command clears previous
headset-click timing and calls the matching player operation.

The same device reproducer now passes: **1 Android test, 0 failures**. Both APKs
were freshly built and installed by `scripts/check-playlist-regressions.sh` before
the run. [Actual post-fix runner output](evidence/2026-10-26-regressions/rg05-after-fix.txt)
is retained alongside the failing run. This targeted pass establishes the original
Pause fix; it does not yet mark the complete RG05 or other RG cases as passed.

### RG06: actual permission revocation found a second regression

An initial full instrumentation run passed 22 tests, but a separate real Android
permission check exposed an additional gap. The fixture audio for this check was
ADB-created and MediaStore-owned by `com.android.shell`, not owned by the app.
After saving that track in `RG06Permission`, revoking `READ_MEDIA_AUDIO`, reopening
the app and denying its rationale, opening Playlists triggered an automatic scan.
Android returned a permission-filtered empty result instead of throwing.

**Observed failure:** the still-existing external track was shown as Unavailable,
with a no-playable-files explanation. RG06 requires unknown availability when
access is denied. The saved member and its audio file were not deleted.

- [Before-fix screen](evidence/2026-10-26-regressions/rg06-permission-before-fix.png)
- [UI hierarchy](evidence/2026-10-26-regressions/rg06-permission-before-fix.xml)
- [Actual permission/file/MediaStore evidence and reproduction](evidence/2026-10-26-regressions/rg06-permission-before-fix.txt)

The original app-owned test WAVs would remain readable under scoped storage and
would not establish this external-media behavior. The scanner now needs an
explicit permission guard, and the live UI tests are being extended accordingly.
RG06 remains unverified until the same device check passes after the fix.

### RG06: same external-media check passes after the guard

The scanner now checks the existing audio permission helper before querying
MediaStore. A permission-limited result cannot become a successful empty library.
The helper accepts a Context so both Activity callers and the scanner Application
use the same permission rule.

The same saved playlist and external MediaStore ID were retained when installing
the fixed APK. With access still revoked, the live UI now shows **Availability not
checked**, disables Play/Add, enables Rename/Delete, and keeps the saved member.
After an actual `pm grant` and UI Refresh, the same member becomes available and
its Play action starts the real media session (`PLAYING(3)`).

- [Denied access after fix](evidence/2026-10-26-regressions/rg06-permission-after-fix.png)
- [Restored access](evidence/2026-10-26-regressions/rg06-permission-restored.png)
- [Asserted UI state, APK hash and observations](evidence/2026-10-26-regressions/rg06-permission-after-fix.txt)

The complete strengthened instrumentation suite still needs its final run. An
independent AI review requested stronger RG03 position bounds, live RG06 failure
rendering, and RG05 unavailable-feedback assertions; these are included in that
run. AI review does not replace a human teammate's review.

## Issue tracking and scope

The GitHub connector rejected issue and PR creation with HTTP 403. Prepared task
and defect issue bodies are in [the issue drafts](playlist-regression-issue-drafts.md).
There are no fabricated issue or PR numbers. Git commits and pushes succeeded.
The optional CI trigger/test-APK change was separated and is not part of this
contribution; it needs its own agreed issue before implementation.

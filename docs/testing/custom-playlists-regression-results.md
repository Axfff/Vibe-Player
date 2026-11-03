# Custom playlist integration regressions

## Scope and ownership

This contribution covers RG01–RG07 from the [playlist test plan](custom-playlists.md),
starting from PR #3 at `785e0863e1efa8f44574cec1444b649a4016c911`.
The separate `test/playlist-acceptance` branch records the collaborators' TC01–TC13
work. This record does not repeat or replace those results.

RG01–RG07 passed on 2026-10-26 on the Android 17 / API 37 arm64 emulator:
**24 Android integration tests and 29 supporting JVM tests, no failures**.
The tested source is `24beffad3c0c89739b13420d2a07cedb947770c0`.
Two reproduced defects were fixed: explicit media-command toggling and denied
media access being mistaken for a successful empty scan.

Execution and fixes were prepared by Axfff with Codex assistance. AI assistance
is used for source inspection, test implementation, debugging, and evidence drafting.
Actual commands and observations determine the results; automated assistance is not
the required human teammate review.

## Final execution

The run used an isolated read-only Android emulator instance and disposable audio
fixtures. Each test started with independently prepared data. Only test audio was
removed. The code and script had no tracked changes during the final execution.

| Case | Required observation | Status |
| --- | --- | --- |
| RG01 | Starting a saved playlist clears the old Queue and its restoration state; playback ends without returning to the old Queue. | Pass — 1 Android test |
| RG02 | Editing saved membership preserves the active snapshot; the next explicit playlist Play uses the edited membership. | Pass — 1 Android test |
| RG03 | Playing and paused rotation, plus background/return, preserve track, position, and subsequent playlist order. | Pass — 3 Android tests |
| RG04 | Repeated successful scans safely refresh an open vanished album/folder detail and the empty-library screen, without duplicate controls/rows. | Pass — 3 Android tests |
| RG05 | Runtime file failures recover finitely; explicit Play/Pause are idempotent; Pause/Stop cancel unwanted playback during recovery. | Pass — 12 Android tests |
| RG06 | Failed/unknown scan state differs from a successful empty scan; saved membership and playlist management remain accessible. | Pass — 3 Android tests plus actual permission revoke/restore |
| RG07 | Hidden media remains usable in saved playlists; ordinary playback, Favorites, Queue, and repeated refresh still work. | Pass — 1 Android test |

All Pass results above apply to this emulator. The existing 29 JVM tests remain
supporting checks; they do not establish Android integration results by themselves.

The final Android run took 333.224 seconds. The final Gradle build succeeded and
reused the up-to-date JVM results from the same source at 13:24:48 UTC. The Android
runner executed all 24 tests after both current APKs were installed.

- [Summary, environment and limitations](evidence/2026-10-26-regressions/final/summary.json)
- [Complete Android runner output](evidence/2026-10-26-regressions/final/instrumentation.txt)
- [Per-case device observations](evidence/2026-10-26-regressions/final/device-logcat.txt)
- [Build output](evidence/2026-10-26-regressions/final/build.txt) and [APK SHA-256 hashes](evidence/2026-10-26-regressions/final/apk-sha256.txt)
- [JVM failure-plan results (5 tests)](evidence/2026-10-26-regressions/final/TEST-com.iven.musicplayergo.player.PlaylistFailurePlanTest.xml) and [repository results (24 tests)](evidence/2026-10-26-regressions/final/TEST-com.iven.musicplayergo.playlists.PlaylistRepositoryTest.xml)
- [Repeatable run instructions](playlist-regressions-automation.md)

The summary lists 16 unmodified screenshots selected only from fixture sessions
present in the final device log. Screenshots are supporting UI evidence; the
state, position, ordering, controls and cancellation assertions are in the tests.

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
- Only Android 17 / API 37 arm64 was executed. Other supported APIs and physical
  devices remain unverified. Host audio was disabled; real player state and
  advancing positions were asserted without claiming audible host verification.
- The audio-focus test invokes the production focus listener directly. It verifies
  pause/resume handling, not Android's full OS focus arbitration.
- RG01/RG02 prepare and edit membership through the repository fixture, and RG07
  prepares the Hide preference directly. These integration checks do not replace
  the collaborators' TC UI acceptance work or claim the Hide popup was exercised.

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
is retained. At that stage the other RG device results were still Pending.

### Focused media-command fix

`PlayerService.handleMediaIntent` now handles explicit Play and Pause separately
from the toggle/headset double-click branch. Each explicit command clears previous
headset-click timing and calls the matching player operation.

The same device reproducer now passes: **1 Android test, 0 failures**. Both APKs
were freshly built and installed by `scripts/check-playlist-regressions.sh` before
the run. [Actual post-fix runner output](evidence/2026-10-26-regressions/rg05-after-fix.txt)
is retained alongside the failing run. That targeted pass established the original
Pause fix; the final complete run above subsequently verified the remaining cases.

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
would not establish this external-media behavior. This observation prompted an
explicit scanner permission guard and extended live UI tests. RG06 was kept
unverified until the same external-media device check passed after the fix.

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

The final strengthened suite passed all 24 tests. An independent AI review
requested RG03 position bounds tied to elapsed time, live Activity-owned RG06
failure rendering and disabled controls, and an actual RG05 unavailable Toast
assertion. These checks were included in the passing run. Focused AI re-review
found no remaining blocking findings. A human teammate's review remains pending.

## Issue tracking and scope

The GitHub connector initially rejected issue and PR creation with HTTP 403.
On 2026-10-26, the owner explicitly authorized publication through the separately
authenticated local GitHub CLI. The prepared records are now published:

- [RG01–RG07 tracking task #9](https://github.com/Axfff/Vibe-Player/issues/9)
- [Explicit media-command defect #10](https://github.com/Axfff/Vibe-Player/issues/10), fixed in `734cf90c`
- [Denied audio-access defect #11](https://github.com/Axfff/Vibe-Player/issues/11), fixed in `24beffad`
- [Draft PR #12](https://github.com/Axfff/Vibe-Player/pull/12), `test/playlist-regressions` into `feat/custom-playlists`

The issues were drafted during execution and published afterward; their timestamps
do not imply they existed before implementation. The original final-run summary
retains the publication blocker as it stood when evidence was captured. Human
teammate review and merge remain pending. Prepared bodies are retained in
[the issue records](playlist-regression-issue-drafts.md). The optional CI
trigger/test-APK change is separate and unimplemented; it needs its own agreed
issue before implementation.

## Follow-up PR review fixes

After the original 24-test RG contribution merged through PR #12, PR #3 review found
additional search/Shuffle, Title/Filename presentation and external equalizer-session
defects. They were tracked as issues #13–#15 and fixed in `2ffeff62` / `8ff9e2f2`.
The [follow-up verification record](playlist-review-fixes.md) preserves the actual
failing reproductions and the passing 29-test Android suite (24 original RG checks
plus five new checks) at `7b0da65c`. The original evidence above remains a record of
its tested revision; it did not cover those additional interactions.

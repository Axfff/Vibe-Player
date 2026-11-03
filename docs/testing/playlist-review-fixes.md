# Playlist PR review fixes

The review of [PR #3](https://github.com/Axfff/Vibe-Player/pull/3) at
`dd5878537731aacad76e64b0a57abc10f4b1a025` found three additional defects after
the original RG contribution was merged through PR #12. Each defect was published
as an issue before the fix was implemented.

All three fixes are verified at `7b0da65c27c89c1d0eacdc5cdf2d00457bf9e0c5`:
**29 Android tests passed, 0 failures, in 135.59 seconds**, including the original
24 RG tests and five new review checks. The 29 supporting JVM tests also pass.
The final build reused their up-to-date results from 15:34:38 UTC on the identical
application source; subsequent changes affected Android test data/assertions only.

| Issue | Required observation | Result |
| --- | --- | --- |
| [#13 — Shuffle after search and refresh](https://github.com/Axfff/Vibe-Player/issues/13) | Clear a no-match query after an actual library refresh: the restored Songs list has the correct Shuffle count, an enabled button, and working playback. | Pass — focused and final Android runs |
| [#14 — External audio-effect sessions](https://github.com/Axfff/Vibe-Player/issues/14) | Actual player sessions receive balanced OPEN/CLOSE broadcasts through manual Next, natural completion, failed-file recovery and service teardown. | Pass — focused and final Android runs |
| [#15 — Title/Filename presentation](https://github.com/Axfff/Vibe-Player/issues/15) | Change the real settings preference in both directions during playlist playback: titles and existing Songs rows update while the current player, track, position and saved playback order remain intact. | Pass — focused and final Android runs |

Verification uses the existing disposable Android emulator and real MediaStore WAV
fixtures. First execute the new tests against unchanged production code, retaining
the failing output. Then apply the narrow fixes, rerun the affected checks and run
the full RG suite with the 29 supporting JVM tests. Record the tested commit, APK
hashes, environment, raw runner output and relevant observations.

## Reproduction and focused verification

- `60b7afc8`: the initial run reproduced Shuffle remaining at `0` and missing effect
  session broadcasts in all three transition tests. Its fifth failure was a test
  setup failure: the WAV scanner normalized metadata titles to filenames. This is
  not counted as a reproduction of the title preference defect.
- `1fc2dce7`: trying to update the scanned TITLE column returned zero changed rows.
  That second fixture setup attempt is retained as diagnostic history, not an
  application failure. The test now uses generated MP3 tones with embedded ID3
  metadata, read through actual MediaStore scans; other RG tests keep their WAVs.
- `069eb46a`: the corrected title reproducer reached the real settings dialog,
  persisted the new preference and timed out waiting for the mini-player title to
  change to `Regression display title C`. The UI production code was still unfixed.
- `8ff9e2f2`: all **5 focused Android tests passed**, 0 failures, in 12.882 seconds.
  The runner rebuilt and installed both APKs. Actual Next, natural completion and
  failed-file recovery now produce balanced session OPEN/CLOSE events.
- `5d21268b`: the final test uses `[C, B, A]` and verifies Next goes to B, then A.
  This differs from ascending library order even when playback wraps. The first
  complete run executed 29 tests: 28 passed; RG07 timed out opening Now Playing.
  Its screenshot shows an emulator System UI ANR, and its fast Favorites/Queue
  sequence could also send the next click inside the app's shared 500 ms debounce.
  The failed output and ANR screenshot/XML are retained. They are not counted as
  a passing full run.
- `7b0da65c`: the RG07 test now waits 550 ms before sending a Now Playing click.
  Production click behavior is unchanged. The emulator's System UI dialog was
  closed, and a subsequent screenshot confirmed it was gone. The full suite is
  now passed against this committed source: 29 tests, 0 failures, 135.59 seconds.

Evidence: [initial failing run](evidence/2026-10-26-review-fixes/before-initial/instrumentation.txt),
[rejected metadata setup](evidence/2026-10-26-review-fixes/setup-title-update-rejected/instrumentation.txt),
[actual title regression](evidence/2026-10-26-review-fixes/before-title-tagged/instrumentation.txt),
and [focused passing run](evidence/2026-10-26-review-fixes/after-focused/instrumentation.txt).
Each evidence folder includes the exact source revision, APK hashes and device log.
The [first full attempt](evidence/2026-10-26-review-fixes/full-first-attempt/instrumentation.txt)
and its [System UI ANR screen](evidence/2026-10-26-review-fixes/full-first-attempt/system-ui-anr-at-rg07-timeout.png)
are retained separately from the final rerun.

## Final evidence

The final run used Android 17 / API 37 arm64 on a read-only Medium Phone emulator,
JBR 17.0.14, Gradle 8.0 and the existing SDK 33 build configuration. Both APKs were
rebuilt/installed by `bash scripts/check-playlist-regressions.sh`. Application,
test and script files had no uncommitted changes during execution.

- [Summary and limits](evidence/2026-10-26-review-fixes/final/summary.json)
- [Complete Android output](evidence/2026-10-26-review-fixes/final/instrumentation.txt)
- [Device observations and real effect-session events](evidence/2026-10-26-review-fixes/final/device-logcat.txt)
- [Build output](evidence/2026-10-26-review-fixes/final/build.log) and [APK SHA-256 hashes](evidence/2026-10-26-review-fixes/final/apk-sha256.txt)
- [JVM failure-plan results (5 tests)](evidence/2026-10-26-review-fixes/final/TEST-com.iven.musicplayergo.player.PlaylistFailurePlanTest.xml) and [repository results (24 tests)](evidence/2026-10-26-review-fixes/final/TEST-com.iven.musicplayergo.playlists.PlaylistRepositoryTest.xml)
- [Shuffle restored and playing](evidence/2026-10-26-review-fixes/final/PR3-search-cleared-shuffle-restored-and-playing.png)
- [Title mode](evidence/2026-10-26-review-fixes/final/PR3-playlist-presentation-title.png), [Filename mode](evidence/2026-10-26-review-fixes/final/PR3-playlist-presentation-filename.png) and [preserved C → B → A order](evidence/2026-10-26-review-fixes/final/PR3-presentation-keeps-snapshot-C-B-A.png)
- [RG07 after emulator recovery and click pacing](evidence/2026-10-26-review-fixes/final/RG07-library-favorites-queue-and-controls.png)

Screenshots were selected only from fixture sessions present in the final device
log and were visually checked for the absence of the System UI ANR overlay.

## Fixes

- `2ffeff62` fixes #14: track the opened effect session, close it before replacing or
  abandoning a player, open each new real session, and avoid duplicate CLOSE events
  during service shutdown. Stale playback callbacks remain isolated.
- `8ff9e2f2` fixes #13 and #15: update Shuffle through the shared list-data path and
  allow title/Songs presentation updates while guarding playlist snapshot mutation.

Independent AI review of the final changes found no additional blocking findings.
That review did not execute the device tests and does not substitute for a human
teammate's review of these new commits.

The external-effect tests check actual broadcast delivery and real player session
IDs. Host audio is disabled; they do not claim audible verification with a vendor
or third-party equalizer. Only the emulator/API actually executed will be reported.

Prepared with Codex assistance for Axfff. AI implementation/review, automated tests
and human teammate review are separate records. The new fixes require teammate
review before merge under the team's documented workflow.

# Playlist PR review fixes

The review of [PR #3](https://github.com/Axfff/Vibe-Player/pull/3) at
`dd5878537731aacad76e64b0a57abc10f4b1a025` found three additional defects after
the original RG contribution was merged through PR #12. Each defect was published
as an issue before the fix was implemented.

| Issue | Required observation | Result |
| --- | --- | --- |
| [#13 — Shuffle after search and refresh](https://github.com/Axfff/Vibe-Player/issues/13) | Clear a no-match query after an actual library refresh: the restored Songs list has the correct Shuffle count, an enabled button, and working playback. | Pending |
| [#14 — External audio-effect sessions](https://github.com/Axfff/Vibe-Player/issues/14) | Actual player sessions receive balanced OPEN/CLOSE broadcasts through manual Next, natural completion, failed-file recovery and service teardown. | Pending |
| [#15 — Title/Filename presentation](https://github.com/Axfff/Vibe-Player/issues/15) | Change the real settings preference in both directions during playlist playback: titles and existing Songs rows update while the current player, track, position and saved playback order remain intact. | Pending |

Verification uses the existing disposable Android emulator and real MediaStore WAV
fixtures. First execute the new tests against unchanged production code, retaining
the failing output. Then apply the narrow fixes, rerun the affected checks and run
the full RG suite with the 29 supporting JVM tests. Record the tested commit, APK
hashes, environment, raw runner output and relevant observations.

The external-effect tests check actual broadcast delivery and real player session
IDs. Host audio is disabled; they do not claim audible verification with a vendor
or third-party equalizer. Only the emulator/API actually executed will be reported.

Prepared with Codex assistance for Axfff. AI implementation/review, automated tests
and human teammate review are separate records. The new fixes require teammate
review before merge under the team's documented workflow.

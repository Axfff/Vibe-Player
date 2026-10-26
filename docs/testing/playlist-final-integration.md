# Playlist final integration — 2026-10-27

PR #16 adds the acceptance-feedback changes for #4–#6 to the feature in PR #3. The integrated application and regression tests were verified at `8f2e46160cf4fa2c2942d38421855ec05fa53784`, which includes the `f659ff7e` fixes for #13–#15. This record was prepared with Codex assistance. AI source reviews do not constitute human teammate approval.

## Build and automated execution

| Check | Actual result | Evidence |
| --- | --- | --- |
| JDK17 / SDK33 application and instrumentation build | Pass, 37 seconds | [Build output](evidence/2026-10-27-final-integration/build.txt) |
| JVM tests | 30 passed, zero failures/errors: repository 24, failure planning 5, scan policy 1 | [Summary and suite counts](evidence/2026-10-27-final-integration/summary.json), three JUnit XML files in the same folder |
| Android integration | 29 passed, zero failures, 215.954 seconds | [Raw runner output](evidence/2026-10-27-final-integration/instrumentation.txt) |
| Tested source and installed artifacts | Source diff empty; both current APKs installed | [Environment](evidence/2026-10-27-final-integration/environment.txt), [APK SHA-256](evidence/2026-10-27-final-integration/apk-sha256.txt), [source diff](evidence/2026-10-27-final-integration/source-diff.patch) |

Run from the repository root with `JAVA_HOME`, `ANDROID_HOME`, and a disposable `ANDROID_SERIAL` set:

```bash
REGRESSION_EVIDENCE_DIR=/path/to/evidence bash scripts/check-playlist-regressions.sh
```

The suite includes RG01–RG07 and the five follow-up checks for Shuffle, Title/Filename presentation, and balanced external effect sessions. It uses real MediaStore audio, Activity/service lifecycle and Android MediaPlayer. Device: disposable read-only Medium_Phone_API_37.0 emulator, Android 17/API37 arm64; host audio disabled, networking disabled, animation scales zero. The command, exact source, raw log and APK hashes are retained. Later documentation-only commits and integration merges must retain the same `project/`, `scripts/` and workflow content; verify the source diff before reusing this result.

## Actual UI smoke checks

The same installed build was exercised through ADB input and UIAutomator observations. Fixtures were two generated MP3 files from the existing regression assets; debug preferences were reset only before this independent smoke scenario. No preferences reset or reinstall occurred between accepting the playlist addition and the cold-restart check.

| Scenario | Observed result |
| --- | --- |
| Empty library, stopped player | The labelled Playlists button is visible and opens playlist management. |
| Scan with no accessible audio | A readable no-candidates message appears; Missing songs? remains available. |
| Import guidance | The dialog explains audio permission, system Files → Music copying, indexing and hidden media. |
| Scan accessible audio | After shell publication/indexing of two owned test files, the app reports scanning two files and refreshes the library. This is re-indexing evidence, not wholly unindexed-file discovery. |
| Add and duplicate | Adding A updates the membership row; repeating A produces readable inline feedback, leaves Refresh/Done enabled and unobscured, and retains exactly one saved member. |
| Rotation | The duplicate explanation survives portrait → landscape → portrait recreation. |
| Actual media permission denial/restoration | The Playlists entry remains usable, scan explains permission denial, saved membership stays Unknown with Play/Add disabled, and granting access plus relaunch restores scanning. |
| Cold restart on the same installation | After `am force-stop` and relaunch, FinalSmoke still has its one accepted member. |

Screenshots, UI XML and the exact command log are in [the smoke evidence folder](evidence/2026-10-27-final-integration/smoke/). They cover affected behaviors, not a new complete execution of collaborators' TC01–TC13. Storage write errors, full accessibility coverage and the scan edge-case matrix remain follow-up #18.

The emulator displayed a **System UI** ANR dialog after the automated suite. It was captured as `01-empty-library.png`, then closed before app UI checks. The successful runner output is retained; the dialog is not presented as an application failure or hidden from the evidence.

## Review and acceptance traceability

Three independent AI source checks covered the scan/lifecycle path, picker and main-screen UI, and player/permission/presentation fixes. They found no remaining concrete merge blocker in the reviewed source. No new human approval is claimed.

| AC | Existing evidence retained | Current integration support |
| --- | --- | --- |
| AC1–AC2 | Collaborators' TC01/TC02 and TC07/TC08; repository naming/rename tests | Repository tests pass; create and cold-restart smoke checked. |
| AC3 | TC03/TC07; repository membership/order/deduplication tests | Tests pass; actual picker addition, duplicate explanation and persistence checked. |
| AC4–AC5 | TC04/TC05 and TC09/TC10; repository isolation/removal/deletion tests | Unchanged repository behavior passes current JVM tests. |
| AC6 | TC06/TC12; saved-order and file-recovery integration tests | Current real-player tests pass, including the order differing from library sort. |
| AC7 | TC07–TC10; repository reload tests | Current JVM reload tests and same-install cold-restart smoke pass. |
| AC8–AC9 | Teammate emulator TC11–TC13; empty, unavailable, failed/denied query and bounded recovery tests | Current Android regression suite passes. |

Historical acceptance: [Redmi TC01–TC10](custom-playlists-redmi-results.md) and [teammate emulator TC01–TC13](https://github.com/Axfff/Vibe-Player/blob/6e92066dbadbd1d6abf5db2fc6b632b975aafa26/docs/testing/custom-playlists-manual-results.md) tested `785e0863`. Their version-specific results have not been relabelled as a complete new manual run.

Formal AC1–AC9 sign-off remains in Story #2 and follow-up #17. The owner explicitly authorized merging after available verification and moving external review/extra work into future issues on 2026-10-27. This is a recorded exception to the team's suggested review-before-merge order; it does not complete human review, formal team acceptance, a retrospective or a release.

## Follow-up work

- [#17 — Human teammate review and formal AC sign-off](https://github.com/Axfff/Vibe-Player/issues/17): review the merged changes and record the team decision; Story #2 stays open until acceptance.
- [#18 — Additional device/accessibility/scan verification](https://github.com/Axfff/Vibe-Player/issues/18): current physical-device acceptance, audible playback/vendor EQ, TalkBack/large text, timeout/partial/row-limit and in-progress rotation cases.
- [#19 — Team retrospective and release preparation](https://github.com/Axfff/Vibe-Player/issues/19): actual team/course records and release/demo decisions.
- [#4 — Discovery limitation](https://github.com/Axfff/Vibe-Player/issues/4): the scanner only sees existing accessible MediaStore candidates. Wholly unindexed files may still require system Files/Music indexing; no unsupported storage access is added.
- [#8 — Optional duplicate Add styling](https://github.com/Axfff/Vibe-Player/issues/8): duplicate membership is prevented; disabling its Add control remains separate.

## Integration status

[PR #16](https://github.com/Axfff/Vibe-Player/pull/16) merged into `feat/custom-playlists` as `a489a243895d6e39aea56198eacb89f448b7f17b` at 2026-10-27 00:41:50 Asia/Shanghai. Its `project/` tree is `4428850dffd9c784929c1522d88b20ed15064e3d`, exactly the tested tree; `git diff 8f2e4616 a489a243 -- project scripts .github/workflows` is empty. The subsequent integration-status commit also changes documentation only.

The owner authorized [PR #3 → main](https://github.com/Axfff/Vibe-Player/pull/3) after final candidate CI. The PR records the actual final head, CI runs and main merge commit; follow its GitHub state and merge-completion comment for the final result. Formal human review/AC acceptance and release preparation remain separate follow-ups.

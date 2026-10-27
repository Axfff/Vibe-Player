# Playlist final integration — 2026-10-27

PR #16 adds the acceptance-feedback changes for #4–#6 to the feature in PR #3. The integrated application and regression tests were verified at `8f2e46160cf4fa2c2942d38421855ec05fa53784`, which includes the `f659ff7e` fixes for #13–#15. This execution record was prepared with Codex assistance. The merged release is `release1` / `81b22ee5`; human review and formal acceptance remain Pending in #17. The [closeout matrix](../releases/release-1-evidence-matrix.md) links each AC to its version-specific evidence.

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
| Scan accessible audio | After shell publication/indexing of two owned test files, the app reports scanning two existing accessible candidates and refreshes the library. Wholly unindexed-file discovery remains in #4. |
| Add and duplicate | Adding A updates the membership row; repeating A produces readable inline feedback, leaves Refresh/Done enabled and unobscured, and retains exactly one saved member. |
| Rotation | The duplicate explanation survives portrait → landscape → portrait recreation. |
| Actual media permission denial/restoration | The Playlists entry remains usable, scan explains permission denial, saved membership stays Unknown with Play/Add disabled, and granting access plus relaunch restores scanning. |
| Cold restart on the same installation | After `am force-stop` and relaunch, FinalSmoke still has its one accepted member. |

Screenshots, UI XML and the exact command log are in [the smoke evidence folder](evidence/2026-10-27-final-integration/smoke/). This run covers the scenarios listed above; full manual TC01–TC13 results retain their historical revision in the collaborator reports below. Storage write-error UI checks, full accessibility coverage and the scan edge-case matrix remain Pending in #18.

The emulator displayed a **System UI** ANR dialog after the automated suite. It was captured as `01-empty-library.png`, then closed before app UI checks. The successful runner output and the system dialog screenshot are retained together.

## Review and acceptance traceability

Three independent AI source checks covered the scan/lifecycle path, picker and main-screen UI, and player/permission/presentation fixes. They found no remaining concrete merge blocker in the reviewed source. Human teammate review of these changes remains Pending in #17.

| AC | Existing evidence retained | Current integration support |
| --- | --- | --- |
| AC1–AC2 | Collaborators' TC01/TC02 and TC07/TC08; repository naming/rename tests | Repository tests pass; create and cold-restart smoke checked. |
| AC3 | TC03/TC07; repository membership/order/deduplication tests | Tests pass; actual picker addition, duplicate explanation and persistence checked. |
| AC4–AC5 | TC04/TC05 and TC09/TC10; repository isolation/removal/deletion tests | Unchanged repository behavior passes current JVM tests. |
| AC6 | TC06/TC12; saved-order and file-recovery integration tests | Current real-player tests pass, including the order differing from library sort. |
| AC7 | TC07–TC10; repository reload tests | Current JVM reload tests and same-install cold-restart smoke pass. |
| AC8–AC9 | Teammate emulator TC11–TC13; empty, unavailable, failed/denied query and bounded recovery tests | Current Android regression suite passes. |

Historical acceptance: [Redmi TC01–TC10](custom-playlists-redmi-results.md) and [teammate emulator TC01–TC13](https://github.com/Axfff/Vibe-Player/blob/6e92066dbadbd1d6abf5db2fc6b632b975aafa26/docs/testing/custom-playlists-manual-results.md) tested `785e0863` on 2026-10-26. Their results remain associated with that source; the current J/I/S results are indexed separately in the closeout matrix.

Formal AC1–AC9 sign-off remains Pending in Story #2 and follow-up #17. The owner explicitly authorized merging after available verification and tracking external review/extra work separately on 2026-10-27. This is the recorded exception to the team's suggested review-before-merge order. Human review and acceptance use the [review packet](../releases/release-1-human-review.md); contribution confirmation and a short retrospective use the [#19 template](../releases/release-1-retrospective.md) and [team guide](../releases/release-1-team-next-steps.md). CroissantXinny clarified on 2026-10-27 that Release 1 and Release 2 require no classroom presentation; that item is N/A and an internal Demo is optional.

## Follow-up work

- [#17 — Human teammate review and formal AC sign-off](https://github.com/Axfff/Vibe-Player/issues/17): review the merged changes and record the team decision; Story #2 stays open until acceptance.
- [#18 — Additional device/accessibility/scan verification](https://github.com/Axfff/Vibe-Player/issues/18): current physical-device acceptance, audible playback/vendor EQ, TalkBack/large text, timeout/partial/row-limit and in-progress rotation cases.
- [#19 — Short team retrospective and closeout](https://github.com/Axfff/Vibe-Player/issues/19): actual contribution/date confirmation, a short retrospective, 1–2 improvement actions and links to acceptance/follow-up decisions; classroom presentation N/A, internal Demo optional. Release publication is complete.
- [#4 — Discovery limitation](https://github.com/Axfff/Vibe-Player/issues/4): the scanner only sees existing accessible MediaStore candidates. Wholly unindexed files may still require system Files/Music indexing; no unsupported storage access is added.
- [#8 — Optional duplicate Add styling](https://github.com/Axfff/Vibe-Player/issues/8): duplicate membership is prevented; disabling its Add control remains separate.

## Integration status

[PR #16](https://github.com/Axfff/Vibe-Player/pull/16) merged into `feat/custom-playlists` as `a489a243895d6e39aea56198eacb89f448b7f17b` at 2026-10-27 00:41:50 Asia/Shanghai. Its `project/` tree is `4428850dffd9c784929c1522d88b20ed15064e3d`, exactly the tested tree; `git diff 8f2e4616 a489a243 -- project scripts .github/workflows` is empty. The subsequent integration-status commit also changes documentation only.

[PR #3](https://github.com/Axfff/Vibe-Player/pull/3) merged into `main` as `81b22ee5868e0cd5ac6a75472296caab3753a7b5` at 2026-10-27 00:51:01 Asia/Shanghai. Its final feature head was `2501c95f8257c5ba7ccdc7484c09747307f78d36`. Both final-head CI runs passed, followed by [main CI 40363054583](https://github.com/Axfff/Vibe-Player/actions/runs/40363054583), which ran 30 JVM tests and built the evaluation APK.

[Release `release1`](https://github.com/Axfff/Vibe-Player/releases/tag/release1) was published at 2026-10-27 01:00:15 Asia/Shanghai and points to the main merge above. Its CI APK has SHA256 `81c6fde3da1909ff3fb86efe099f265a967af5f8284b40f15f33251b1d3cfd92`; the local Android-tested APK hash remains in the original execution record. `git diff 8f2e4616 release1 -- project scripts .github/workflows` is empty. The subsequent main commit `dc17523b` records publication in documentation. These merge/publication facts were reconciled during doc-only closeout; the build and device results above retain their original execution dates and revisions.

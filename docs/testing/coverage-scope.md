# Sprint 3 cumulative team coverage

## Baseline and attribution

The baseline is `947709e4c798a103b9fcc5962172f2e85d349196`, upstream Music Player GO's 2025-01-04 merge of PR #568 (configuration change). Its README identifies `enricocid/Music-Player-GO`; it is an ancestor of this fork's main. The first production change after it is `fd00c099243b6f846e1128f1521f7676a3a0ee7b` (persistent custom playlists). Main was fetched and audited at `dc17523be591a72f9fefa98536c61689e4d99ad1` on 2026-10-31. The annotated `release1` tag peels to `81b22ee5868e0cd5ac6a75472296caab3753a7b5` and is unchanged.

This baseline includes all Release 1 changes. Release 1 is **not** the comparison baseline. `coverage-scope.json` inventories every added/modified production source, its normalized content hash, source commits, and cumulative candidate line ranges. Runtime `summary.json` adds the executable ranges, covered/total, whole-file context and individual uncovered lines from the actual coverage tool.

New source files use all executable lines. Modified upstream files use executable lines intersecting `git diff --unified=0 --no-renames BASE` additions/replacements, cumulatively through the tested tree. This is a transparent implementation choice for the course's “new or modified executable code”; the PDF does not prescribe this algorithm and no instructor approval is claimed. Deleted code cannot execute and is not counted. Each changed file also includes **whole-file** counts so the remaining upstream context is visible. Renames are treated conservatively as additions.

The generated JaCoCo report analyzes all compiled production classes, including never-executed classes. Only generated R, BuildConfig, view binding and Moshi JSON adapters are removed. No source UI, adapter, player, error handling, or device-only path is exempted. Abstract interfaces with no executable lines remain listed with 0/0; they do not create a false 100% module result. Resource XML and tests are not production executable line coverage.

Android, Python backend and web modules have separate gates. One module's result cannot compensate for another. A new production file, changed source hash, missing report/source, zero executed required suite, incompatible JaCoCo class IDs, changed source during collection, missing device data or stale execution data fails validation. Counts use integer comparison `covered * 100 >= total * 80`; 79.96% fails.

## Audited adjacent work

| Work | Audited head | Relationship to this branch |
| --- | --- | --- |
| PR #21 Add button membership state | `2093970ad0169c8be57a725681e573999ad4c566` | Pending separate PR; regenerate manifest after integration |
| PR #26 legacy null DATA path | `aca7ae7cacf04d594bc42660711ab352c26dd581` | Pending separate PR; has four JVM tests |
| PR #28 null metadata search | `bfd201cc3484c64324778c07ce2a9fe971dc64e2` | Pending separate PR; has six JVM tests |
| PR #30 missing album lookup | `d7535bfd7f8ffd90848a9b8c804ad593147acd22` | Pending separate PR; has five JVM tests |
| PR #20 / #24 | Release closeout / README | Documentation; no executable source |
| Local Discovery | `fa86950d0f8955a8b0718de95f7a0509dedee832` | Backend + web, not yet on main; measured in independent validation worktree |

Android Discovery is absent from these audited revisions. The user is arranging its implementation separately. No imaginary Android Discovery report is generated. Its eventual source must be integrated, inventoried and tested before claiming the **whole Sprint 3 feature** is complete. Historical Redmi evidence and other PRs' test counts are not reused as coverage inputs.

## Reproduce

Keep JDK 17, compile/target SDK 33, min SDK 21, Kotlin 1.8.21, AGP 8.0.2 and Gradle 8.0. Coverage uses JaCoCo 0.8.10 with AGP's separate unit/device switches. No unified `testAllSuites` task or toolchain migration is required.

From the repository root, with a fresh disposable API 33 emulator:

```sh
python scripts/test_coverage_contract.py
python scripts/coverage/coverage.py scope
cd project
./gradlew --no-daemon --console=plain clean
cd ..
python scripts/coverage/coverage.py start
export ANDROID_SERIAL=emulator-5554 DISPOSABLE_EMULATOR=1
python scripts/require-disposable-emulator.py
cd project
./gradlew --no-daemon --console=plain :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest :app:teamCoverageReport
cd ..
python scripts/coverage/coverage.py report
```

Windows uses `gradlew.bat` and PowerShell environment assignments. `start` requires clean test/coverage outputs. `report --unit-only --measure-only` is for a clearly labeled exploratory JVM baseline only; CI never uses either bypass. After changing production source, review and commit `python scripts/coverage/coverage.py scope --write`. CI checks the manifest; it never silently regenerates it.

For an integrated Python Discovery package, run `pytest --cov=discovery` with explicit HTML, XML and JUnit destinations in `artifacts/coverage`. The source option counts unimported files. Browser production modules are detected too; their LCOV and JUnit results must exist before that combined scope can pass. Do not silently exclude the existing web application when integrating Discovery.

## Publication and failure behavior

`Android CI` checks out the **PR head SHA**, builds/uploads the debug APK, runs JVM and disposable-emulator Android tests, verifies class IDs, writes full HTML/XML, then enforces the scope gate. Gradle `--continue` allows independent APK work to finish but preserves a nonzero exit for any failed task. No test/gate uses `continue-on-error` or `|| true`.

Reports, JUnit XML, execution data, Android diagnostics and APK artifacts are retained 90 days. The PR bot comment and `Team line coverage` check include the tested SHA, baseline, test counts, exact counts/percentage, threshold result and direct artifact link. A failed/missing report is described as not generated/rejected, never a fabricated zero or success. Upload/publication steps run after failures and do not turn failed build jobs green.

Tests have `contents: read`. The separate publisher gets only `actions: read`, `checks: write` and `pull-requests: write`, never checks out code, and treats report artifacts as bounded validated JSON. It verifies repository, workflow path, run, PR and current head SHA and skips stale heads. Same-repository PRs invoke it directly. Fork PRs retain read-only test tokens and use the trusted `workflow_run` publisher **after it has been reviewed and merged onto main**. This activation requirement is not permission to merge automatically. No `pull_request_target` executes PR code.

Pinned action commits were resolved against the upstream tag refs with `git ls-remote`: checkout v7, setup-java v6, setup-python v6, upload-artifact v7, download-artifact v8, github-script v8 and ReactiveCircus/android-emulator-runner v2. Pins are recorded inline in the workflows.

## Public integration contract

This CI branch owns `.github/workflows/`, `project/gradle/coverage.gradle`, app Gradle integration and `scripts/coverage/`. Feature authors can add production files and behavior tests in their own worktrees. Request shared Gradle/dependency changes from the CI owner, regenerate the manifest after integration, then run the same fresh collection pipeline. Data from different source/compiled-class revisions must never be averaged or combined.

References: [AGP 8.0 coverage switches](https://developer.android.com/reference/tools/gradle-api/8.0/com/android/build/api/dsl/BuildType), [JaCoCo class identity and unexecuted classes](https://www.jacoco.org/jacoco/trunk/doc/classids.html), [Gradle JaCoCo reporting](https://docs.gradle.org/current/userguide/jacoco_plugin.html).

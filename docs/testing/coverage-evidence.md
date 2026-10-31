# Sprint 3 coverage evidence index

Public CI/Gradle implementation: [PR #31](https://github.com/Axfff/Vibe-Player/pull/31).
Integration evidence (Discovery backend/web and PRs #21/#26/#28/#30):
[PR #32](https://github.com/Axfff/Vibe-Player/pull/32). This validation branch is not a request to merge the feature PRs.
The single bot comment on each PR records the latest tested full SHA, module counts, test counts and direct HTML/XML artifact link. Reports and APKs are retained for 90 days; download them before expiry for longer course archiving.

## Measured starting points

All Android counts use baseline `947709e4c798a103b9fcc5962172f2e85d349196`, including Release 1.
On main `dc17523be591a72f9fefa98536c61689e4d99ad1`, the existing 30 JVM tests and APK passed before coverage was enabled. Instrumenting those tests measured 155/1375 team lines (11.272727%). Adding the existing 29 Android tests on a clean disposable API 33 emulator measured 1005/1375 (73.090909%).

The first test additions at `8188733497ce8ac0f73137a06ddba8181c6504c6` measured 1170/1375 (85.090909%) locally, with 31 JVM and 36 Android tests passing. The [actual cloud run](https://github.com/Axfff/Vibe-Player/actions/runs/41218941842) measured 1177/1375 (85.600000%) with the same test counts and a successful APK. Different runtime paths can change hits; these measurements are not averaged. Its first publisher incorrectly labeled the result because GitHub Script overwrote an output named `result`; the subsequent fix uses `build_result` and makes publication validation failure fail the job.

Original Discovery `fa86950d0f8955a8b0718de95f7a0509dedee832`: backend 905/957 (94.566353%), 148 tests; web 216/825 (26.181818%), 17 tests. The web denominator includes previously unexecuted `app.js` and `images.mjs`. After settings and DOM/image behavior tests: backend 919/957 (96.029258%), 159 tests; web 812/825 (98.424242%), 22 tests. The combined branch must collect its own fresh Android data and pass all three gates. Android Discovery is still absent and must be tested after its separate implementation.

## Failure evidence

- [Assertion injection](https://github.com/Axfff/Vibe-Player/actions/runs/41219146717), SHA `04be535fccab94e0ba24402b22c35d0b9c0b9875`: 32 JVM tests, one deliberately failed assertion; CI failed, APK and diagnostics uploaded.
- [Low coverage injection](https://github.com/Axfff/Vibe-Player/actions/runs/41219153018), SHA `d7182a70fad7e959482fd1f96c2ea866187cae0d`: all 36 Android tests passed; a clearly labeled synthetic zero-hit report made the production gate fail at 0/1375. This is a failure probe, not a real coverage measurement.
- [Missing XML injection](https://github.com/Axfff/Vibe-Player/actions/runs/41219159668), SHA `8fbf6e33107d8659fb9f8bc48097884f29713e33`: required report deletion produced an explicit missing-file gate failure. An independent UI synchronization failure also occurred; the isolated branch is rerun after the fixture fix to obtain clean prerequisite evidence.

Failure probes live only on `ci/coverage-probe/*`, never in the delivery branches. Do not merge them. The Node native JUnit format and RecyclerView/decoder synchronization issues discovered during cloud verification are covered by the follow-up fixes. Full commands, scope semantics, exclusions and feature-author integration interface are in [coverage-scope.md](coverage-scope.md).

## Remaining integration and review

Review PR #31 before merging. The fork-PR `workflow_run` publisher activates only after the trusted workflow is on main and still needs a real fork acceptance run then. Configure required branch checks if desired; no repository administration policy or release tag is changed by this task. Android Discovery and later feature changes require a regenerated manifest and fresh tests; the current results do not claim completion of that missing feature.

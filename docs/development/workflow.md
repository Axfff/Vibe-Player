# Development workflow

For new work from 2026-11-11 (Asia/Singapore). Historical evidence keeps its original date, revision and review status. This is the single process source; [AGENTS.md](../../AGENTS.md) is the coding-agent entry point.

## Issue to merge

Issue → Implementation → Automated tests → Agent technical review → PR → Teammate review → Fix comments → Merge.

1. Plan one Issue with **User Story, testable AC, 3–6 main implementation tasks, Owner, Reviewer, Estimate, Sprint and Verification**. Use the [Story form](../../.github/ISSUE_TEMPLATE/user_story.yml). Bugs/setup work may use the existing Issue, adding the planning fields before implementation. Do not rewrite historical Issues merely to fit a template.
2. Use **hours** consistently for new estimates, dated before implementation with assumptions. Preserve the original when scope changes; actual effort is separate. Missing historical estimates stay “Not recorded before work”. Reviewer may initially be “Team to confirm”; identify a non-author teammate before formal review.
3. Inspect latest main, open PRs and local changes; use an independent branch/worktree. One PR has one coherent scope. A commit may contain behavior plus its direct tests; unrelated CI, documentation or features belong in separate changes. Never rewrite shared history or move historical release tags.
4. Run relevant checks, review the actual diff against Issue/AC, fix technical blockers and rerun affected checks. A Draft PR may obtain CI before formal peer review; request teammate review when technical preparation and current-SHA checks are complete.
5. Use the [PR template](../../.github/PULL_REQUEST_TEMPLATE.md): Why, What, Verification, Related Issues, Review focus and concise contribution disclosure. Link commits, commands/results and full reports. Use `Refs #N` for partial work; `Closes #N` only when all Issue requirements are met and closure is intended.
6. At least **one team member who did not author the change** reviews Story/AC, design, boundaries, test evidence, regressions and maintainability, then submits genuine comments or approval through GitHub Review. The author fixes comments; resolutions and updated approvals must reflect the latest material changes.
7. Merge only with the required non-author approval, resolved blocking conversations, passing current-SHA checks and satisfied in-scope AC. Test an integration candidate after combining branches; separate branch passes do not certify it. Do not bypass protection or use an agent to approve for a teammate.

For an operational task changing no repository content, **PR: N/A** is valid: link the Issue's actual command/result record. Do not create empty PRs. Documentation changes still use a branch/PR and relevant document checks.

## Technical review

Report actual defects, security/permission risks, AC mismatches, missing/failing tests, compatibility problems, concrete maintenance risks and technical merge blockers. Each finding has **severity (P0 critical / P1 high / P2 medium / P3 low), file/line, trigger, cause and suggested fix**. Fix in-scope blockers before peer review; link independent nonblocking work once.

“Human review pending”, “human approval not completed”, “waiting for peer review” and “manual sign-off pending” are not technical defects. GitHub tracks that stage. Do not append generic disclaimers or duplicate review TODOs. Real failures, unmet AC and technical blockers must remain visible.

After required checks, use four lines plus any concrete findings/evidence:

```text
Technical review: Pass / Issues found
Tests: Passed / Failed
CI: Passed / Failed
Ready for peer review: Yes / No
```

Before execution, use **Not run / Pending** (or justified N/A), never Passed. Ready for peer review means technical preparation is complete, not approval or permission to merge.

## Planning, evidence and contribution

Use a short update only when there is meaningful progress:

```text
Scope: goal for this change
Implemented: principal completed work
Verified: checks actually run and results, with tested SHA
Next: at most 1–3 specific actions
Links: Issue / PR / CI
```

Issue owns requirements, owner and estimate; PR owns change/verification and review discussion; CI owns automated results; Sprint records summarize planned increments, linked delivery and the actual retrospective. Do not duplicate GitHub states in status tables. Track durable independent work in an Issue; preserve failures and unmet AC at their primary source.

Maintain **Issue → Owner → PR → Commit → Verification**. Each Sprint's existing work table links Issue and PR; the PR supplies commits and evidence. Keep planned estimate and actual effort distinct. Link the [team registry](../../README.md#team-members); do not infer identity from commit email. Record actual human design, implementation, review and device work, distinguishing AI-assisted implementation/execution. Disclose tools, assistance scope and human validation once in the PR or linked evidence, plus any course-required submission; do not describe agent output as independently hand-written by a member.

For decisions affecting architecture, user behavior or scope, add **Decision / Reason / Action (Issue/PR) / Owner / Verification** to the relevant existing [development or research record](../thinking/README.md). Routine edits need no separate record. For example, isolating playlist photo grants must link its scope-management Issue/PR and a two-playlist isolation test; discussion alone is not completion. Do not invent historical decision owners or meetings.

## Setup acceptance criteria

A setup Issue records **prerequisites/versions, baseline SHA, exact commands, expected artifacts, installation/run steps, success/failure conditions and evidence location**. Use the entry below; record deviations instead of silently upgrading the project.

- Given pinned tools and a recorded clean checkout, when build/unit-test commands run, then both exit successfully and the debug APK and test reports exist.
- Given an authorized test device and that APK, when it is installed/launched and audio access granted, then the library opens, an indexed test track plays and Next selects the expected next track without a crash. Record player-state checks separately from audible verification if sound was not observed.
- When the same installation is force-stopped and reopened, saved test playlists remain. Record SHA, APK hash, device/API, steps and outcomes. Build success alone does not satisfy runtime AC.

## From source to a running app

This entry reflects main audited at `844caf7cd1cfb3348cdd130136763528c3274573`; **use the latest agreed main or PR revision and record its full SHA**, not a forced historical checkout. Open `project/` in Android Studio. Keep **JDK 17, SDK platform 33, Build Tools 30.0.3, Gradle Wrapper 8.0, AGP 8.0.2, Kotlin 1.8.21**, compile/target SDK 33 and min SDK 21. Gradle files/wrapper are authoritative. Install SDK command-line/platform tools, accept required SDK licenses and configure the SDK path in Android Studio or untracked `project/local.properties`. Never commit machine paths. Python 3.12 and Node 24/pnpm 11.25.0 are additional full-CI tools, not prerequisites just to launch the APK.

From repository root on Windows PowerShell:

```powershell
git status --short
git rev-parse HEAD
java -version
Set-Location project
.\gradlew.bat --version
if ($LASTEXITCODE -ne 0) { throw 'Gradle version check failed' }
.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest :app:assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'Build or unit tests failed' }
Get-FileHash app/build/outputs/apk/debug/app-debug.apk -Algorithm SHA256
Set-Location ..
```

On macOS/Linux, use `cd project`, `bash ./gradlew` with the same arguments and `shasum -a 256 app/build/outputs/apk/debug/app-debug.apk`; stop on nonzero exit. Expected outputs: `project/app/build/outputs/apk/debug/app-debug.apk` and `project/app/build/reports/tests/testDebugUnitTest/index.html`. Record versions, full SHA, exit statuses and APK SHA-256 in the setup Issue or linked evidence.

Start an Android **API 33** test emulator in Android Studio Device Manager (x86_64 on an x86 host, arm64 on an ARM host). Use only an authorized device; never clear a real phone or uninstall an existing app for this check. Put SDK `platform-tools` on PATH. Replace the example serial below with the authorized serial from `adb devices -l`:

```sh
adb devices -l
adb -s emulator-5554 install -r project/app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n com.iven.musicplayergo.debug/com.iven.musicplayergo.ui.MainActivity
```

Require installation `Success` and a visible, crash-free library. For signing/version conflicts, use a fresh test emulator instead of deleting someone's installation. Import two legally usable tracks through normal device media import, wait for indexing (or use the app's scan action) and grant audio access in UI. Create a test playlist with those two tracks in a recorded order, save it, play the first and use Next to check the second. Force-stop and reopen the same installation; confirm its name, tracks and order remain. Record unavailable device checks as Not run. Store evidence once in the Issue/PR or existing `docs/testing/evidence/` location; [older setup evidence](../sprints/sprint-1.md) remains historical.

## Tests and coverage

Preserve existing [Android CI](../../.github/workflows/android.yml): APK build, JVM/Android/backend/web tests, full HTML/XML artifacts and [PR publisher](../../.github/workflows/coverage-publish.yml). Relevant behavior tests must actually run. Failures, missing reports and low coverage cannot be swallowed; skipped tests are not passes.

The [coverage specification](../testing/coverage-scope.md) owns the cumulative inventory/calculation: upstream baseline `947709e4c798a103b9fcc5962172f2e85d349196`, **including Release 1**, and **at least 80% line coverage per module**. Include new/modified executable code; never omit UI, players, error paths or unexecuted classes to improve the number. Measure real misses, then add tests. Explain genuine testability constraints in the PR without silently shrinking scope or disabling the gate.

For full verification follow all Android, backend and web steps in the workflow; the historical Android-only command block in the coverage specification is not sufficient once those modules exist. Instrumentation uses a disposable emulator. A documentation PR checks links/templates and unchanged scope locally; normal PR CI still runs. The bot summary links full Actions reports. Record tested SHA and `covered/total`, not just a rounded percentage; do not paste raw logs into the PR.

## Repository protection

The owner/admin should activate the existing `main branch protection` ruleset for main: require PRs, at least one non-author approval, renewed approval after reviewable changes, conversation resolution, and successful `build` plus `Team line coverage` checks. Keep force pushes/deletion disallowed and routine bypass actors absent. Require a publisher check only when its exact name exists for every allowed PR source; fork publication uses a separate workflow. See [GitHub's protection settings](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches).

On 2026-11-11, main was unprotected, the existing ruleset was disabled, and this account lacked admin permission. Written policy does not enable protection. Verify activation by observing that an existing PR with missing approval or failed required checks cannot merge; do not merge or force-push to test it.

## Course alignment

The supplied **Guidelines for DSAA2044 Sprint 3 (2026–27 Fall), pages 1–2** require Story/supporting requirements/AC/tasks in GitHub, a working testable increment with later work assigned to Sprint 4, retained APK builds, tests/coverage for team changes including Release 1, automatic PR summaries/full reports, failed CI for failed builds/tests and at least 80% line coverage. The cumulative per-module gate is the team's implementation, not an instructor-prescribed diff algorithm.

This guide addresses the supplied Release 1 feedback with pre-work estimates, linked contributions/decisions, concrete setup AC and genuine peer review. Hours, 3–6 tasks and one required non-author approval are team execution rules, not invented course quotations. Existing [Sprint records](../README.md#sprint-and-release-evidence) preserve actual history; historical exceptions do not authorize future bypasses.

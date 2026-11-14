# Coding agents

Follow the [repository development workflow](docs/development/workflow.md). It owns the process; old feature handoffs are historical context, not instructions to restart from an old SHA.

## Before implementation

- Check latest main, applicable instructions, related Issue/AC, owner, estimate and open PRs. Missing historical estimates stay not recorded; never backdate one.
- Keep the agreed scope. Use an independent branch/worktree, preserve others' work and do not rewrite shared history or move release tags.

## During implementation

- Make focused commits. Keep behavior and direct tests together; separate unrelated feature, CI and documentation changes.
- Add meaningful tests for changed behavior. Preserve APK builds and cumulative coverage of team changes, including Release 1; never exclude difficult or unexecuted code to reach 80%.
- Link important technical decisions to their implementation Issue/PR and verification.

## Before peer review

- Run relevant checks and perform technical self-review. Fix blocking defects in scope and rerun affected checks before requesting teammate review.
- Findings must describe actual defects, security/permission risks, AC mismatches, testing gaps/failures, compatibility or concrete maintenance problems. Include severity, file/line, trigger, cause and suggested fix.
- Do not report “human review pending”, “human approval not completed”, “waiting for peer review” or “manual sign-off pending” as technical findings. Do not append generic disclaimers or duplicate review TODOs.
- Summarize Technical review: Pass / Issues found; Tests: Passed / Failed; CI: Passed / Failed; Ready for peer review: Yes / No. If a check has not run, use Not run / Pending (or justified N/A), never Passed.
- Provide concise evidence and review focus. A Draft PR may run CI before formal peer review.
- Never impersonate or claim teammate approval, dismiss a required review to unblock yourself, or bypass an unsatisfied required review. GitHub owns the peer-review gate.

## Reporting and history

- Use Scope / Implemented / Verified / Next / Links; keep Next to at most three concrete actions.
- Avoid duplicate TODO lists, repeated status paragraphs and copied logs. Link the primary Issue, PR or CI evidence.
- Do not conceal real test failures, unmet AC or merge blockers. Preserve accurate historical reviews and limitations.
- Attribute human work and AI assistance accurately. Never fabricate estimates, meetings, reviewers, test runs or approvals.

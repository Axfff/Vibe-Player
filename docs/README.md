# Vibe-Player documentation

[Development workflow](development/workflow.md) is the single source for current planning, technical review, peer review and evidence rules. [AGENTS.md](../AGENTS.md) directs coding agents to it.

- [From source to a running app](development/workflow.md#from-source-to-a-running-app)
- [Story form](../.github/ISSUE_TEMPLATE/user_story.yml) · [PR template](../.github/PULL_REQUEST_TEMPLATE.md)
- [Team members](../README.md#team-members) · [Administrator handoff](development/github-admin-handoff.md)
- [Coverage scope and calculation](testing/coverage-scope.md) · [Coverage evidence](testing/coverage-evidence.md)

## Where each fact belongs

| Primary source | Contents |
| --- | --- |
| Issue | Story, AC, owner, estimate, Sprint, tasks and verification plan |
| PR | Change, commits, actual verification and teammate review |
| CI | Build/test status, APK, concise coverage summary and full reports |
| Sprint record | Planned increment, Issue-to-PR links, actual contributions and retrospective |
| Relevant development/research record | Important decision, reason, action Issue/PR, owner and verification |

Link the primary record instead of creating parallel TODO or status tables. AI assistance is disclosed with the actual contribution; approval is recorded by the real reviewer on GitHub.

## Sprint and release evidence

These documents preserve revision-specific history. They do not replace the current workflow or authorize a future exception to review requirements.

| Record | Purpose |
| --- | --- |
| [Sprint 1](sprints/sprint-1.md) | Environment, build and device setup evidence for Issue #1 |
| [Sprint 2](sprints/sprint-2.md) | Story #2, delivered work, contributors and actual events |
| [Release 1](releases/release-1.md) | Published revision, APK, evidence and historical follow-ups |
| [AC evidence matrix](releases/release-1-evidence-matrix.md) | Historical/manual and final integration evidence by AC |
| [Review packet](releases/release-1-human-review.md) | Existing Issue #17 material; keep actual review records |
| [Retrospective](releases/release-1-retrospective.md) | Existing Issue #19 contribution and retrospective entry |
| [Research](thinking/README.md) | Original feature selection and requirements links |
| [Playlist test plan](testing/custom-playlists.md) | Cases corresponding to the formal Story |
| [Regression results](testing/custom-playlists-regression-results.md) | RG01–RG07, failures and fixes |
| [Review fixes](testing/playlist-review-fixes.md) | Issues #13–#15 and their before/after verification |
| [Final integration](testing/playlist-final-integration.md) | Integrated application and test revision evidence |
| [REDMI acceptance](testing/custom-playlists-redmi-results.md) | Historical TC01–TC10 execution |
| [REDMI #13/#15 checks](testing/playlist-redmi-review-13-15.md) | Later device verification |
| [#4/#5/#6 fixes](development/acceptance-fixes/2026-10-26-issues-4-5-6.md) | Implementation and targeted checks |
| [Collaborator emulator acceptance](https://github.com/Axfff/Vibe-Player/blob/6e92066dbadbd1d6abf5db2fc6b632b975aafa26/docs/testing/custom-playlists-manual-results.md) | Peihan ZENG's historical TC01–TC13 report |
| [Implementation record](development/custom-playlists-implementation.md) | Original feature implementation and evidence |
| [Historical agent handoff](development/custom-playlists-agent-guide.md) | Sprint 2 context, not a mandated checkout or current process |

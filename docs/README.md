# Vibe-Player documentation

[Development workflow](development/workflow.md) is the single source for current planning, technical review, peer review and evidence rules. [AGENTS.md](../AGENTS.md) directs coding agents to it.

- [From source to a running app](development/workflow.md#from-source-to-a-running-app)
- [Story form](../.github/ISSUE_TEMPLATE/user_story.yml) · [PR template](../.github/PULL_REQUEST_TEMPLATE.md)
- [Team members](../README.md#team-members)
- [Coverage scope and calculation](testing/coverage-scope.md) · [Coverage evidence](testing/coverage-evidence.md)

## Where each fact belongs

| Primary source | Contents |
| --- | --- |
| [Sprint 1](sprints/sprint-1.md) | #1 结项、环境、构建和安装运行证据 |
| [Sprint 2](sprints/sprint-2.md) | 目标、Story 链接、工作拆分、进度与回顾 |
| [Sprint 3 Discovery](sprints/sprint-3.md) | [正式 Story #36](https://github.com/Axfff/Vibe-Player/issues/36)、Sprint 3/4 AC/任务、Android 可运行增量与未通过项 |
| [Discovery 实测交付](development/discovery/sprint3-delivery-2026-11-03.md) | APK/源码身份、真实目录、模拟器截图、测试和照片授权/歌曲页地区限制 |
| [Release 1](releases/release-1.md) | 两个 Sprint 的交付和审阅证据索引 |
| [Release 1 evidence matrix](releases/release-1-evidence-matrix.md) | AC1–AC9 对应的历史 TC、最终集成测试、环境和源码版本 |
| [Human-review packet](releases/release-1-human-review.md) | #17 的复审范围、合并提交、证据及真人填写模板 |
| [简短 Retro 模板](releases/release-1-retrospective.md) | #19 的真实分工、日期、回顾与行动项；课堂展示 N/A、内部 Demo 可选 |
| [Thinking](thinking/README.md) | 调研、选择理由、未决问题 |
| [多歌单测试计划](testing/custom-playlists.md) | AC 对应的操作、期望及实际执行结果 |
| [RG01–RG07 回归结果](testing/custom-playlists-regression-results.md) | 原始 24 项 Android 回归与 PR #12 的复现、修复及证据 |
| [PR 审阅修复验证](testing/playlist-review-fixes.md) | #13–#15 的失败复现、完整 29 项 Android 测试、JVM 支持结果与限制 |
| [最终集成验证](testing/playlist-final-integration.md) | PR #16 与功能分支集成后的最新源码、构建、测试、合并状态及限制；最终结果以此为准 |
| [REDMI 真机验收](testing/custom-playlists-redmi-results.md) | CroissantXinny 的 TC01–TC10、截图、文件校验和强停重启证据 |
| [REDMI #13/#15 真人复测](testing/playlist-redmi-review-13-15.md) | 2026-10-27；与发布源码对应的本地 APK；Shuffle、Title/Filename 和连续 C→B→A 均 Pass |
| [#4/#5/#6 修复与复测](development/acceptance-fixes/2026-10-26-issues-4-5-6.md) | 候选补丁审阅、实际提交、新版构建/API 33 设备证据与待复测项 |
| [协作者模拟器验收](https://github.com/Axfff/Vibe-Player/blob/6e92066dbadbd1d6abf5db2fc6b632b975aafa26/docs/testing/custom-playlists-manual-results.md) | 曾沛涵的 TC01–TC13 与代表性截图 |
| [实现与验证记录](development/custom-playlists-implementation.md) | 本地实现范围、实际自动检查与待执行验收 |
| [Coding-agent 指南](development/custom-playlists-agent-guide.md) | 接手步骤、实施约定与可复制提示词 |
| [User Story 模板](../.github/ISSUE_TEMPLATE/user_story.yml) | 在同一个 Issue 填写 Story、AC、范围与验证计划 |
| [PR 模板](../.github/PULL_REQUEST_TEMPLATE.md) | 说明修改、关联需求、记录审阅与验证 |

## 一项需求只维护一份正式标准

**一个 User Story 对应一个 GitHub Issue；其 Acceptance Criteria 放在同一个 Issue 中。**

- User Story：谁希望完成什么，以及为什么。
- Acceptance Criteria：这个具体需求有哪些可观察、可验证的完成条件。
- Implementation tasks：为了实现需求需要完成的技术工作。
- Definition of Done：每项交付共同遵守的完成检查，如测试记录和同伴审阅。

Story 与 AC 是同一需求的两个小节，不分别开两个 Issue。需要分工时可在该 Issue 中列任务；范围过大时拆成可独立交付的多个 Story，每个 Story 保留自己的 AC。

[多歌单需求入口](thinking/02-custom-playlists-user-story.md)已指向正式 Issue #2。Story 与 AC 在该 Issue 统一维护；Sprint、测试、PR 和 Release 文档引用 Issue 与 AC 编号。

## 从需求到合并

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

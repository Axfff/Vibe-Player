# Vibe-Player 课程项目工作流

记录日期：2026-10-25（Asia/Shanghai）。本文件规定本项目采用的文档与协作方式；它不是对课程评分细则的补充。

## 课程要求与当前状态

根据老师提供的说明：Sprint 1 无需单独提交；代码、提交历史和项目文档持续保存在 GitHub；Sprint 2 增加一个基础功能并定义 User Story 与 Acceptance Criteria；Sprint 2 结束后的 Release 1 评审覆盖两个 Sprint 的进展、Agile 过程、代码审阅、代码质量与测试。

邮件没有指定模板格式、测试覆盖率、PR 数量、审阅人数或必须使用 GitHub Projects / Releases。以下结构是团队的执行方案。截止日期与具体分工由团队按课程安排补充。

当前沿用 [Axfff/Vibe-Player](https://github.com/Axfff/Vibe-Player)。自定义多歌单初版已在本地 `feat/custom-playlists` 实现，文档基础提交为 `2372cac`。功能源码提交 `6ec4869` 的最终本地重建成功，29 个单元测试通过；设备验收、人工 Review 和 AC 签收均为 Pending。实际实现与验证结果见[实施记录](development/custom-playlists-implementation.md)。

GitHub 集成仍返回 403；本功能的远端分支、正式 Story Issue 和 PR 尚未建立。本地代码与文档的存在不代表已经推送或发布。

## 文档入口

| 位置 | 用途 |
| --- | --- |
| [Sprint 1](sprints/sprint-1.md) | 汇总已有环境与构建记录，标明尚缺证据 |
| [Sprint 2](sprints/sprint-2.md) | 目标、Story 链接、工作拆分、进度与回顾 |
| [Release 1](releases/release-1.md) | 两个 Sprint 的交付和审阅证据索引 |
| [Thinking](thinking/README.md) | 调研、选择理由、未决问题 |
| [多歌单测试计划](testing/custom-playlists.md) | AC 对应的操作、期望及实际执行结果 |
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

正式 Issue 建立前，[多歌单需求草稿](thinking/02-custom-playlists-user-story.md) 是待发布正文。建立后，将草稿正文替换为正式 Issue 链接；之后只更新 Issue 中的需求。Sprint、测试和 Release 文档引用 Issue / AC 编号，不维护另一套需求。

## 从需求到合并

1. 在本仓库建立 Story Issue，记录同一需求的 Story 与 AC。当前远端授权受阻，已按用户要求基于唯一需求草稿进行本地实现；授权恢复后再发布正式 Issue 并更新链接，不猜编号。
2. 新功能从合适的最新基线建功能分支；本轮 `feat/custom-playlists` 已在本地存在，接手时检查当前分支和 diff，保留已有改动。
3. 做有意义的提交并推送分支；在本仓库创建目标为 `main` 的 PR。
4. 在 PR 中链接 Story Issue，并填写 `AC ID -> 验证方法 -> 结果 -> 证据`。继续向同一分支推送可更新同一个 PR。
5. 由队友进行真实审阅，记录并处理反馈；按实际情况运行构建和测试。
6. 范围完成且相关检查通过后合并，更新 Sprint 与 Release 的证据链接。

部分实现或文档搭建 PR 使用 `Refs #N`。只有完成对应需求、准备在合并到默认分支时关闭 Issue 的 PR 才使用 `Closes #N`。搭建文档不关闭多歌单 Story 或 Sprint 1 Issue。

模板合并到默认分支 `main` 后，GitHub 才会在新建 Issue / PR 时提供它们。模板文件、真实 Issue、功能实现是三个不同的交付。

可选的管理方式：用 `type:story`、`sprint:2` 标签和 `Release 1` milestone 分类；需要时再加 Todo / In Progress / In Review / Done 看板。这里没有宣称这些远端资源已经建立，也不把它们作为开始开发的前提。

## 团队采用的 Definition of Done

- [ ] 对应 Issue 的 AC 已逐条验证，留下实际结果与证据。
- [ ] 与修改相关的构建、自动化或手动测试已运行；失败与未执行项目已说明。
- [ ] 至少有一名队友完成审阅，阻碍合并的意见已处理。
- [ ] 需求、PR、提交和测试记录能够相互追踪。
- [ ] 合并到 `main`，相关 Sprint / Release 记录同步。

上面的同伴审阅约定是建议采用的团队实践，不是邮件明确规定的人数门槛。文档修改的功能验收项目可以写 N/A 并说明原因。

## 证据记录规则

使用真实日期、实际提交 SHA、执行环境、操作/命令、预期与实际结果，以及日志或录屏链接。`Pending` 表示尚未验证，不能等同于失败或通过。协作者提交的历史日志注明来源；构建成功、APP 能运行和某条功能 AC 通过分别记录。补整理历史时注明整理日期，不补造早先的会议、审阅、测试或提交。

本轮已有 coding agents 完成初版数据、UI、播放与扫描集成，并完成最终本地构建与 29 个单元测试。AI 审查、单元测试、设备验收和协作者在 GitHub 上的真实 Review 分别记录；人工审阅与 AC 签收仍待完成。最新命令、被测版本、结果和限制统一见[实施记录](development/custom-playlists-implementation.md)。

## 参考

- [GitHub Flow](https://docs.github.com/en/get-started/using-github/github-flow)
- [Issue 与 PR 模板](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/about-issue-and-pull-request-templates)
- [PR 与 Issue 的关联及关闭](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue)
- [User Story 的角色、目标与价值表达](https://agilealliance.org/glossary/user-story-template/)

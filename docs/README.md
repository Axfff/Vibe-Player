# Vibe-Player 课程项目工作流

更新日期：2026-10-27（Asia/Shanghai）。本文件规定本项目采用的文档与协作方式。

## 课程要求与当前状态

根据老师提供的说明：Sprint 1 无需单独提交；代码、提交历史和项目文档持续保存在 GitHub；Sprint 2 增加一个基础功能并定义 User Story 与 Acceptance Criteria；Sprint 2 结束后的 Release 1 评审覆盖两个 Sprint 的进展、Agile 过程、代码审阅、代码质量与测试。

邮件没有指定模板格式、测试覆盖率、PR 数量、审阅人数或必须使用 GitHub Projects / Releases。以下结构是团队的执行方案。截止日期与具体分工由团队按课程安排补充。

2026-10-27，CroissantXinny 补充确认 Release 1、Release 2 均无课堂展示要求，并采用精简收尾流程：课堂展示标为 **N/A**，内部 Demo 可选；三人可通过群聊或 Issue 回复完成贡献确认、简短 Retro 和 1–2 个改进行动。真人代码 Review、正式 AC 签收与后续验证继续按原范围跟踪。操作步骤见[三人收尾指南](releases/release-1-team-next-steps.md)。

当前 [Axfff/Vibe-Player](https://github.com/Axfff/Vibe-Player) 的 [PR #3](https://github.com/Axfff/Vibe-Player/pull/3) 已于 2026-10-27 合入 `main`，合并提交 `81b22ee5` 包含 PR #12、PR #16 与 #13–#15 修复。[Release `release1`](https://github.com/Axfff/Vibe-Player/releases/tag/release1) 已发布并指向该提交。文档整理起点为 `main` 的 `dc17523b`；Sprint 1 的 [Issue #1](https://github.com/Axfff/Vibe-Player/issues/1) 已依据环境、构建、运行和文档证据结项。

验证按版本索引：`785e0863` 的 REDMI TC01–TC10 与队友模拟器 TC01–TC13 为历史手工结果；`8f2e4616` 的 29 项 Android / 30 项 JVM 及指定 UI 冒烟为最终集成记录；`81b22ee5` 的发布 CI 完成 30 项 JVM 和 Debug APK 构建。最终集成与发布版本的应用、测试、脚本和 CI 文件一致。环境、测试提交、APK 哈希与逐项 AC 证据统一见 [Release 1 evidence matrix](releases/release-1-evidence-matrix.md)。历史修复过程见[设备反馈修复记录](development/acceptance-fixes/2026-10-26-issues-4-5-6.md)。

2026-10-27，仓库所有者授权先完成集成合并，将后续工作分别跟踪：[真人队友复审与正式 AC1–AC9 签收 #17](https://github.com/Axfff/Vibe-Player/issues/17)、[真机、可访问性与扫描边界验证 #18](https://github.com/Axfff/Vibe-Player/issues/18)、[贡献确认与简短回顾 #19](https://github.com/Axfff/Vibe-Player/issues/19)，均为 Pending。完全未进入系统索引的文件发现 [#4](https://github.com/Axfff/Vibe-Player/issues/4) 和可选 Add 样式 [#8](https://github.com/Axfff/Vibe-Player/issues/8) 按各自 Issue 继续跟踪。

正式需求为 [Story Issue #2](https://github.com/Axfff/Vibe-Player/issues/2)，其 AC 等待真人签收。原 GitHub 集成的 403 与后续获授权使用已有 Git / GitHub CLI 的处理记录保留在 Sprint 2。

## 文档入口

| 位置 | 用途 |
| --- | --- |
| [Sprint 1](sprints/sprint-1.md) | #1 结项、环境、构建和安装运行证据 |
| [Sprint 2](sprints/sprint-2.md) | 目标、Story 链接、工作拆分、进度与回顾 |
| [Release 1](releases/release-1.md) | 两个 Sprint 的交付和审阅证据索引 |
| [Release 1 evidence matrix](releases/release-1-evidence-matrix.md) | AC1–AC9 对应的历史 TC、最终集成测试、环境和源码版本 |
| [Human-review packet](releases/release-1-human-review.md) | #17 的复审范围、合并提交、证据及真人填写模板 |
| [简短 Retro 模板](releases/release-1-retrospective.md) | #19 的真实分工、日期、回顾与行动项；课堂展示 N/A、内部 Demo 可选 |
| [三人收尾操作指南](releases/release-1-team-next-steps.md) | 各自下一步、可复制回复、真人 Review / AC 签收及文档提交方法 |
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

1. 在正式 [Story Issue #2](https://github.com/Axfff/Vibe-Player/issues/2) 维护多歌单需求与 AC；本地入口、测试与 PR 材料保持链接一致。
2. 新功能从合适的最新基线建功能分支；本轮 `feat/custom-playlists` 已在本地存在，接手时检查当前分支和 diff，保留已有改动。
3. 做有意义的提交并推送分支；在本仓库创建目标为 `main` 的 PR。
4. 在 PR 中链接 Story Issue，并填写 `AC ID -> 验证方法 -> 结果 -> 证据`。继续向同一分支推送可更新同一个 PR。
5. 由队友进行真实审阅，记录并处理反馈；按实际情况运行构建和测试。
6. 范围完成且相关检查通过后合并，更新 Sprint 与 Release 的证据链接。

### 分支使用建议

三人协作建议长期保留 `main`，按任务创建短期分支，例如 `feat/功能名`、`fix/问题名`、`docs/文档任务`、`test/验证任务`。这些前缀用于说明任务类型；每个分支对应一个明确范围。同一 PR 的反馈和补充证据继续推到原分支。

任务合并后，确认全部所需提交已进入 `main`、本地改动已保存、相关 worktree 已交接，再清理完成的分支。下一项任务从更新后的 `main` 新开分支。PR 与提交历史继续保留协作和审阅记录。只有需要并行维护已发布版本时，再增加 `release/...` 维护分支；`release1` tag 继续标识已发布提交。流程参考 [GitHub Flow](https://docs.github.com/en/get-started/using-github/github-flow)。

部分实现或文档搭建 PR 使用 `Refs #N`。只有完成对应需求、准备在合并到默认分支时关闭 Issue 的 PR 才使用 `Closes #N`。搭建文档不关闭多歌单 Story 或 Sprint 1 Issue。

模板合并到默认分支 `main` 后，GitHub 才会在新建 Issue / PR 时提供它们。模板文件、真实 Issue、功能实现是三个不同的交付。

可选的管理方式：用 `type:story`、`sprint:2` 标签和 `Release 1` milestone 分类；需要时再加 Todo / In Progress / In Review / Done 看板，由团队按实际需要设置。

## 团队采用的 Definition of Done（通用模板）

- [ ] 对应 Issue 的 AC 已逐条验证，留下实际结果与证据。
- [ ] 与修改相关的构建、自动化或手动测试已运行；失败与未执行项目已说明。
- [ ] 至少有一名队友完成审阅，阻碍合并的意见已处理。
- [ ] 需求、PR、提交和测试记录能够相互追踪。
- [ ] 合并到 `main`，相关 Sprint / Release 记录同步。

上面的同伴审阅约定是建议采用的团队实践，不是邮件明确规定的人数门槛。文档修改的功能验收项目可以写 N/A 并说明原因。

本轮顺序例外（2026-10-27）：用户授权在可执行验证通过后先完成合并，真人队友复审与正式 AC 签收继续由 [#17](https://github.com/Axfff/Vibe-Player/issues/17) 跟踪。Release 1 的实际完成清单见 [Release 索引](releases/release-1.md)；此处作为后续工作的通用模板。

## 证据记录规则

使用真实日期、实际提交 SHA、执行环境、操作/命令、预期与实际结果，以及日志或录屏链接。`Pending` 表示等待实际验证。协作者提交的历史日志注明来源；构建成功、APP 能运行和某条功能 AC 通过分别记录。补整理历史时分别注明执行日期和整理日期。

初版数据、UI、播放与扫描集成及其当时的 29 项单元测试保留在[实施记录](development/custom-playlists-implementation.md)。AI 审查、自动化测试、设备验收和协作者在 GitHub 上的真实 Review 分别记录；历史通过结果不替代新集成版本的复测。最新命令、被测版本、结果、合并状态和限制统一见[最终集成验证](testing/playlist-final-integration.md)，真人队友复审与正式 AC 签收由 #17 跟踪。

## 参考

- [GitHub Flow](https://docs.github.com/en/get-started/using-github/github-flow)
- [Issue 与 PR 模板](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/about-issue-and-pull-request-templates)
- [PR 与 Issue 的关联及关闭](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue)
- [User Story 的角色、目标与价值表达](https://agilealliance.org/glossary/user-story-template/)

# Vibe-Player 项目讨论

这里保存调研、选择理由与需求发布前的草稿。正式 Story Issue 建立后，User Story 与 Acceptance Criteria 统一维护在该 Issue 中。

## 当前文档与决定

- [01 — 基础功能调研](01-basic-feature-research.md)：代码基线、已有功能、候选功能及后续 AI 接点。
- [02 — 自定义多歌单需求入口](02-custom-playlists-user-story.md)：正式 Issue 发布前的唯一正文草稿，包含 Story 与 AC；发布后改为 Issue 链接。
- 后续讨论 AI 时再增加 `03-ai-feature-options.md`；当前没有承诺实现任何 AI 功能。

2026-10-25：沿用 [Axfff/Vibe-Player](https://github.com/Axfff/Vibe-Player) 作为课程仓库，选择自定义多歌单作为当前需求方向。选择理由是：原有收藏和播放队列无法表达多个独立命名、长期保存的集合；多歌单同时具有明确的用户价值、可验证的持久化与播放行为，也能为未来 AI 生成歌单提供容器。

首轮 v0.1 采用添加顺序；手动拖动排序、导入导出、云同步与 AI 生成留到后续。初版已按需求入口中的规则在本地 `feat/custom-playlists` 实现，仍待设备验收和人工审阅。正式 Story Issue、远端功能分支和 PR 因 GitHub 集成 403 尚未建立。

## 内容放在哪里

| 内容 | 位置 |
| --- | --- |
| 候选功能、调研与选择理由 | `docs/thinking/` |
| 一个具体功能的 Story、AC、任务和讨论 | 同一个 GitHub Issue |
| 本轮目标、进度与回顾 | [Sprint 文档](../sprints/sprint-2.md) |
| 代码修改、测试结果和同伴审阅 | 对应 PR，引用 Issue 与 AC 编号 |
| 评审材料索引 | [Release 1](../releases/release-1.md) |
| 实现状态、验证证据与接手方法 | [实施记录](../development/custom-playlists-implementation.md)、[Agent 指南](../development/custom-playlists-agent-guide.md) |

分支隔离尚未合并的修改；文件夹让调研资料在合并后仍有固定位置。完整步骤见 [课程工作流](../README.md)。

## 研究与验证边界

基础调研对应 `main` 的 [947709e4c798a103b9fcc5962172f2e85d349196](https://github.com/Axfff/Vibe-Player/commit/947709e4c798a103b9fcc5962172f2e85d349196)。开始功能实现时应重新检查最新代码。调研中的其他候选、历史验收例子不是本轮额外承诺。

前期调研与课程文档已形成本地基础提交 `2372cac`；随后 coding agents 完成多歌单初版代码。功能源码提交 `6ec4869` 已完成最终本地构建与 29 个单元测试；设备验收与人工 Review 的实际状态见[实施记录](../development/custom-playlists-implementation.md)。所有 AC 的验收签收仍为 Pending。协作者 2026-10-17 的构建证据另见 [Sprint 1](../sprints/sprint-1.md)，不混同为本轮执行结果。

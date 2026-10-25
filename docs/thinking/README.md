# Vibe-Player 项目讨论

这里保存调研、选择理由与需求发布前的草稿。正式 Story Issue 建立后，User Story 与 Acceptance Criteria 统一维护在该 Issue 中。

## 当前文档与决定

- [01 — 基础功能调研](01-basic-feature-research.md)：代码基线、已有功能、候选功能及后续 AI 接点。
- [02 — 自定义多歌单需求入口](02-custom-playlists-user-story.md)：正式 Issue 发布前的唯一正文草稿，包含 Story 与 AC；发布后改为 Issue 链接。
- 后续讨论 AI 时再增加 `03-ai-feature-options.md`；当前没有承诺实现任何 AI 功能。

2026-10-25：沿用 [Axfff/Vibe-Player](https://github.com/Axfff/Vibe-Player) 作为课程仓库，选择自定义多歌单作为当前需求方向。选择理由是：原有收藏和播放队列无法表达多个独立命名、长期保存的集合；多歌单同时具有明确的用户价值、可验证的持久化与播放行为，也能为未来 AI 生成歌单提供容器。

首轮 v0.1 采用添加顺序；手动拖动排序、导入导出、云同步与 AI 生成留到后续。详细规则以需求入口为准，仍需团队审阅。

## 内容放在哪里

| 内容 | 位置 |
| --- | --- |
| 候选功能、调研与选择理由 | `docs/thinking/` |
| 一个具体功能的 Story、AC、任务和讨论 | 同一个 GitHub Issue |
| 本轮目标、进度与回顾 | [Sprint 文档](../sprints/sprint-2.md) |
| 代码修改、测试结果和同伴审阅 | 对应 PR，引用 Issue 与 AC 编号 |
| 评审材料索引 | [Release 1](../releases/release-1.md) |

分支隔离尚未合并的修改；文件夹让调研资料在合并后仍有固定位置。完整步骤见 [课程工作流](../README.md)。

## 研究与验证边界

基础调研对应 `main` 的 [947709e4c798a103b9fcc5962172f2e85d349196](https://github.com/Axfff/Vibe-Player/commit/947709e4c798a103b9fcc5962172f2e85d349196)。开始功能实现时应重新检查最新代码。调研中的其他候选、历史验收例子不是本轮额外承诺。

本轮由 ChatGPT 辅助静态源码核查、公开资料调研和文档起草；未修改 APP 功能代码、未运行 Android 构建或功能测试。仓库中协作者已有的构建证据另见 [Sprint 1](../sprints/sprint-1.md)，不混同为本轮执行结果。

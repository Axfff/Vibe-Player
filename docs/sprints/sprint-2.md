# Sprint 2 — 自定义多歌单

更新日期：2026-10-25（Asia/Shanghai）。状态：**本地初版已实现；最终本地构建与单元测试通过，设备验收与人工审阅待完成。**

## Sprint Goal

让用户按学习、通勤、跑步等用途保存多个命名的本地歌单，并能重新打开和播放这些集合。

## 需求入口

- 正式 Story Issue：[Issue #2](https://github.com/Axfff/Vibe-Player/issues/2)，2026-10-25 22:28 由 CroissantXinny 建立。
- 本地[需求入口](../thinking/02-custom-playlists-user-story.md)指向 Issue #2；Story 与 AC 统一在 Issue 维护。
- 选择依据：[基础功能调研](../thinking/01-basic-feature-research.md)。当前 APP 已有收藏和播放队列，新增价值是多个独立命名、长期保存的歌单。
- 手工验收：[多歌单测试计划](../testing/custom-playlists.md)，通过 AC 编号关联需求，设备执行结果仍为 Pending。
- 实现与自动验证：[实施记录](../development/custom-playlists-implementation.md)；接手方法见 [Agent 指南](../development/custom-playlists-agent-guide.md)。

首轮已按需求正文中的 v0.1 范围和产品规则完成本地初版。不在此复制完整 AC；如调整范围，先更新需求记录，再同步相关测试。代码实现状态与验收签收分开记录，当前不勾选 AC。

## 工作拆分与当前进度

| 工作项 | 当前执行 / 后续责任 | 状态 | 证据 / 后续动作 |
| --- | --- | --- | --- |
| User Story 与 AC | CroissantXinny 发布；团队验收人待定 | Issue #2 open | 正式需求已核实；AC 均待签收 |
| 歌单数据与本地保存 | Coding agents；团队接手人待定 | Local implementation | UUID、独立保存与保守曲目解析；见实施记录 |
| 歌单列表、编辑与歌曲管理 UI | Coding agents；团队接手人待定 | Local implementation | 已有界面和交互代码；设备验收 Pending |
| 播放集成与失效文件处理 | Coding agents；团队接手人待定 | Local implementation | 独立 snapshot、扫描和失效处理已接入；运行验收 Pending |
| 自动检查 | Codex | Local checks passed | 本轮修复 `2f660d11` Windows 构建成功，29 项单元测试通过；见实施记录 |
| 手工验收与人工 Review | 团队待分配 | Pending | TC01–TC13 和 RG01–RG07 尚待设备执行；没有真实同伴 Review |
| 远端发布与合并 | 团队待处理授权 | Pending | Story #2 已建立；交接记录有集成写入 403；功能分支与 PR 待发布 |

上述数据、UI、播放是同一用户故事内的技术任务。若团队评估超出 Sprint 容量，再拆成可独立验收的故事；每个故事都要有自己的 AC。

## 分支与 PR

- 本地文档基础提交：`2372cac`。
- 本地功能分支：`feat/custom-playlists`，已有初版实现提交；接手时检查状态并保留后续改动。
- 远端功能分支与功能 PR 待发布；正式 Story 已核实为 Issue #2。本轮准备 Draft PR，关联 Refs #2。
- 功能初版 `6ec4869`，本轮修复和验证源码 `2f660d11`，详见[实施记录](../development/custom-playlists-implementation.md)。
- 团队接手分工、Sprint 起止日期：待按课程安排补充。

文档搭建不代表多歌单功能已交付，也不会自动关闭 Story Issue。正式实现 PR 要链接需求并提供 AC 对应的验证结果。

## 实施约定与待验证事项

1. **曲目关联与保存**：已采用 UUID 歌单、Moshi + SharedPreferences 的 `custom_playlists_v1` key，以及 MediaStore ID 加 displayName/duration 的保守校验；失效条目保留快照。
2. **范围控制**：首轮固定添加顺序；AI 生成、手动排序、导入导出和云同步留待后续讨论。
3. **扫描与播放边界**：解析使用原始成功曲库，失败不当空库；播放使用独立 snapshot，保存编辑下次 Play 生效；结束遵守既有偏好，跨进程不恢复整份活动队列。
4. **本轮本地检查**：源码 `2f660d11` Windows 构建与 29 项单元测试通过；设备执行环境 Blocked，TC/RG 操作 Pending。
5. **验收可执行性**：使用固定本地测试音频，并独立验证移除歌单成员不会影响其他歌单或原文件；设备验收与人工 Review 均待执行。

实际修改位置与实现范围见[实施记录](../development/custom-playlists-implementation.md)。本轮沿用现有技术栈，没有把数据库迁移或播放器重写作为交付前提。

## 进度与决定日志

| 日期 | 记录 | 证据 |
| --- | --- | --- |
| 2026-10-25 | 建立课程文档、Story 与 AC、验证计划 | 本地文档提交 `2372cac` |
| 2026-10-25 | 按多歌单 Story 完成本地初版数据、UI、播放与扫描集成；最终本地构建与 29 个单元测试通过 | 功能源码提交 `6ec4869` 与实施记录；设备验收待完成 |
| 2026-10-25，原交接 | 集成写入返回 403；当时 Story、功能分支与 PR 均待发布 | 原交接记录 |
| 2026-10-25，本轮接手 | 核实团队已创建 Story #2；修复服务重绑定与媒体 Pause/Stop；Windows 构建及 29 项测试通过 | `2f660d11`、Issue #2 与本轮验证证据 |
| 待填写 | 设备验收、真实审阅及后续变更 | 添加实际证据或成功建立的链接 |

## Sprint Review / Retrospective

实际完成后再填写：

- 已演示的用户行为及对应提交：Pending。
- AC 验证状态与未完成范围：Pending。
- PR、代码审阅和改进记录：Pending。
- 团队实际回顾结论与下轮行动：Pending。

Release 1 汇总入口：[Release 1](../releases/release-1.md)。

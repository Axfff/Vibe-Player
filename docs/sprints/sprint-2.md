# Sprint 2 — 自定义多歌单

更新日期：2026-10-27（Asia/Shanghai）。状态：**PR #12 的 RG01–RG07 与 #13–#15 修复已进入功能分支；PR #16 的设备反馈修复已完成最终集成自动验证与 UI 冒烟检查。集成构建成功，29 项 Android / 30 项 JVM 全部通过。用户授权可执行验证通过后合并，真人队友复审与正式签收移交 #17。**

## Sprint Goal

让用户按学习、通勤、跑步等用途保存多个命名的本地歌单，并能重新打开和播放这些集合。

## 需求入口

- 正式 Story Issue：[Issue #2](https://github.com/Axfff/Vibe-Player/issues/2)，2026-10-25 22:28 由 CroissantXinny 建立。
- 本地[需求入口](../thinking/02-custom-playlists-user-story.md)指向 Issue #2；Story 与 AC 统一在 Issue 维护。
- 选择依据：[基础功能调研](../thinking/01-basic-feature-research.md)。当前 APP 已有收藏和播放队列，新增价值是多个独立命名、长期保存的歌单。
- 手工验收：[多歌单测试计划](../testing/custom-playlists.md)、[REDMI 真机记录](../testing/custom-playlists-redmi-results.md)、[协作者模拟器记录](https://github.com/Axfff/Vibe-Player/blob/6e92066dbadbd1d6abf5db2fc6b632b975aafa26/docs/testing/custom-playlists-manual-results.md)。两次执行均基于 `785e0863`。
- 实现与自动验证：[实施记录](../development/custom-playlists-implementation.md)；接手方法见 [Agent 指南](../development/custom-playlists-agent-guide.md)。
- 最新集成状态：[最终集成验证](../testing/playlist-final-integration.md)；各被测版本的结果分别保留，不以历史通过代替当前候选结果。

首轮已按需求正文中的 v0.1 范围和产品规则完成本地初版。不在此复制完整 AC；如调整范围，先更新需求记录，再同步相关测试。代码实现状态与验收签收分开记录，当前不勾选 AC。

## 工作拆分与当前进度

| 工作项 | 当前执行 / 后续责任 | 状态 | 证据 / 后续动作 |
| --- | --- | --- | --- |
| User Story 与 AC | CroissantXinny 发布；团队验收人待定 | Issue #2 open | 正式需求已核实；AC 均待签收 |
| 歌单数据与本地保存 | CroissantXinny 集成；Codex 辅助实现 | PR #3 | UUID、独立保存与保守曲目解析；创建/改名/移除/删除的真机持久化检查通过 |
| 歌单列表、编辑与歌曲管理 UI | CroissantXinny 集成 | TC01–TC05 历史 Pass；PR #16 实现 #5/#6 修复 | 入口与添加反馈的历史设备结果见修复记录，最新集成结果见最终报告 |
| 播放集成与失效文件处理 | CroissantXinny 集成 | TC06 真机通过；TC12–TC13 模拟器通过；RG01–RG07 已通过 | PR #12 已合入；完全未索引文件的发现限制仍由 #4 跟踪 |
| 自动检查 | Codex 辅助执行 | 历史 `7b0da65c`：29 Android / 29 JVM Pass；当前集成构建成功 | `8f2e4616`：29 项 Android / 30 项 JVM 全部通过；最新结果以最终集成报告为准 |
| 真机手工验收 | CroissantXinny 操作；Codex 记录 | TC01–TC10 Pass | REDMI K90 Pro Max / Android 16 API 36；本机其余用例保持 Pending |
| 模拟器手工验收 | 曾沛涵 | TC01–TC13 Pass | Android 17 API 37；报告提交 `6e92066` |
| 集成回归 / 人工 Review | Codex 执行自动化；队友后续复审 | RG 与 #13–#15 回归已有通过记录；当前集成 29 Android / 30 JVM 全部通过 | 真实队友复审与正式 AC1–AC9 签收由 #17 跟踪，不宣称 AI 审查等于真人批准 |
| 远端发布与合并 | 用户授权；完成可执行检查后执行 | PR #16 已合并为 `a489a243`；PR #3 的实际交付状态见最终报告及 GitHub | 2026-10-27 用户允许先合并，再由 #17/#18/#19 跟进外部验证与课程准备 |

上述数据、UI、播放是同一用户故事内的技术任务。若团队评估超出 Sprint 容量，再拆成可独立验收的故事；每个故事都要有自己的 AC。

## 分支与 PR

- 文档基础提交：`b1605279`（原 `2372cac`，署名调整后内容相同）。
- 本地功能分支：`feat/custom-playlists`，已有初版实现提交；接手时检查状态并保留后续改动。
- 已发布 [PR #3](https://github.com/Axfff/Vibe-Player/pull/3)，关联 Refs #2。队友的测试报告保留在 `test/playlist-acceptance` 分支，当前 PR 链接两份验收记录。
- 功能初版 `fd00c099`，播放修复 `03e85dd7`，两份历史 TC 设备测试基于 `785e0863`；历史提交对应关系见[实施记录](../development/custom-playlists-implementation.md)。
- PR #12 已将 RG 测试及两项修复合入功能分支；#13–#15 的后续修复与 `7b0da65c` 的完整通过证据随 `f659ff7e` 保留。PR #16 实现 #4/#5/#6 的设备反馈修复；本轮以 `8f2e4616` 作集成候选，最新结果见最终集成报告。
- 团队接手分工、Sprint 起止日期：待按课程安排补充。

文档搭建不代表多歌单功能已交付，也不会自动关闭 Story Issue。正式实现 PR 要链接需求并提供 AC 对应的验证结果。

## 实施约定与待验证事项

1. **曲目关联与保存**：已采用 UUID 歌单、Moshi + SharedPreferences 的 `custom_playlists_v1` key，以及 MediaStore ID 加 displayName/duration 的保守校验；失效条目保留快照。
2. **范围控制**：首轮固定添加顺序；AI 生成、手动排序、导入导出和云同步留待后续讨论。
3. **扫描与播放边界**：解析使用原始成功曲库，失败不当空库；播放使用独立 snapshot，保存编辑下次 Play 生效；结束遵守既有偏好，跨进程不恢复整份活动队列。
4. **自动检查**：历史 Windows 构建、两台设备 TC、PR #12 的 RG01–RG07，以及 `7b0da65c` 的完整 29 项 Android / 29 项 JVM 结果分别留档。当前集成构建成功，29 项 Android 回归和 30 项 JVM 测试全部通过；真人签收仍由 #17 跟踪。
5. **验收可执行性**：真机使用三份独立提示音素材，记录用户操作、截图、原文件哈希及强停进程证据；模拟器报告保留测试者、环境和结果。

实际修改位置与实现范围见[实施记录](../development/custom-playlists-implementation.md)。本轮沿用现有技术栈，没有把数据库迁移或播放器重写作为交付前提。

## 进度与决定日志

| 日期 | 记录 | 证据 |
| --- | --- | --- |
| 2026-10-25 | 建立课程文档、Story 与 AC、验证计划 | 本地文档提交 `2372cac` |
| 2026-10-25 | 按多歌单 Story 完成本地初版数据、UI、播放与扫描集成；最终本地构建与 29 个单元测试通过 | 功能源码提交 `6ec4869` 与实施记录；设备验收待完成 |
| 2026-10-25，原交接 | 集成写入返回 403；当时 Story、功能分支与 PR 均待发布 | 原交接记录 |
| 2026-10-25，本轮接手 | 核实团队已创建 Story #2；修复服务重绑定与媒体 Pause/Stop；Windows 构建及 29 项测试通过 | `2f660d11`、Issue #2 与本轮验证证据 |
| 2026-10-26 | 以 CroissantXinny 账号发布功能分支和 Draft PR，按用户要求调整五次提交署名；保留文件树和时间 | PR #3、被测提交 `785e0863` |
| 2026-10-26 | CroissantXinny 完成真机 TC01–TC10；曾沛涵提交模拟器 TC01–TC13 报告 | 两份验收记录；协作者提交 `6e92066` |
| 2026-10-26 | 将媒体扫描、歌单入口和空白提示条纳入缺陷跟踪；复用同一代码版本的 TC 结果，转向 RG 回归 | Issues #4/#5/#6 |
| 2026-10-26 | PR #12 合入 RG01–RG07 测试及两项缺陷修复；随后修复审阅发现的 #13–#15 | RG 结果、PR 审阅修复验证；`7b0da65c` 完整 29 Android / 29 JVM Pass |
| 2026-10-27 | 集成 PR #16 的设备反馈修复，保留 `f659ff7e` 的审阅修复与证据；当前集成构建成功，29 Android / 30 JVM 全部通过 | 候选 `8f2e4616`；最终集成验证报告 |
| 2026-10-27 | 用户授权在可执行验证通过后先合并；将真人队友复审/正式签收、外部设备验证和回顾/Release 准备作为后续事项 | Issues #17/#18/#19；#4 未索引文件限制、#8 可选样式仍保留 |

## Sprint Review / Retrospective

实际完成后再填写：

- 已演示的用户行为及对应提交：Pending。
- AC 验证状态与未完成范围：Pending。
- PR、代码审阅和改进记录：已有 PR #3 / #12 / #16 与缺陷修复记录；本轮真实队友复审与正式签收仍由 [#17](https://github.com/Axfff/Vibe-Player/issues/17) 跟踪。
- 团队实际回顾结论与下轮行动：Pending，由 [#19](https://github.com/Axfff/Vibe-Player/issues/19) 跟踪；真机、可访问性与扫描边界验证由 [#18](https://github.com/Axfff/Vibe-Player/issues/18) 跟踪。

本轮合并顺序例外（2026-10-27）来自用户明确授权，区别于团队建议先取得真人同伴审阅再合并的 Definition of Done。这里不补造真人批准、不勾选正式 AC 签收，也不将合并视为课程评审或 Release 已完成。

Release 1 汇总入口：[Release 1](../releases/release-1.md)。

2026-10-27 集成记录：[PR #16](https://github.com/Axfff/Vibe-Player/pull/16) 已合并为 `a489a243`；29 Android / 30 JVM 及本轮 UI 冒烟检查通过，应用源码与被测 `8f2e4616` 相同。PR #3 的最终 CI 和主分支合并结果见[最终集成报告](../testing/playlist-final-integration.md)及 PR 的 GitHub 状态。真人复审和正式签收仍由 #17 跟进。

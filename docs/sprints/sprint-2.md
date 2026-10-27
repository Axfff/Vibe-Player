# Sprint 2 — 自定义多歌单

更新日期：2026-10-27（Asia/Shanghai）。**PR #3 已合入 main，Release `release1` 已发布。最终集成记录为 29 Android / 30 JVM Pass；真人复审与正式 AC 签收 Pending，由 #17 跟踪。**

## Sprint Goal 与需求入口

让用户按学习、通勤、跑步等用途保存多个命名的本地歌单，并能重新打开和播放这些集合。

- 正式需求：[Story #2](https://github.com/Axfff/Vibe-Player/issues/2)，2026-10-25 22:28 由 CroissantXinny 创建；AC1–AC9 的完整标准与正式签收均在该 Issue 维护。
- 选择依据：[基础功能调研](../thinking/01-basic-feature-research.md)；[需求入口](../thinking/02-custom-playlists-user-story.md)。
- 当前证据：[Release 1 evidence matrix](../releases/release-1-evidence-matrix.md)，按历史 TC、最终集成测试和真人正式签收分别列示。
- 历史实施：[实现记录](../development/custom-playlists-implementation.md)；测试定义：[多歌单测试计划](../testing/custom-playlists.md)；接手约定：[Agent 指南](../development/custom-playlists-agent-guide.md)。
- 正式 Sprint 开始 / 结束日期：**TODO**；已有事件日期见下方日志，由团队在 [Retro 模板](../releases/release-1-retrospective.md)确认计划周期。

## 工作拆分与实际进度

| 工作项 | 实际贡献 / 后续责任 | 当前状态与证据 |
| --- | --- | --- |
| Story、数据保存、UI 与播放集成 | CroissantXinny 发布与集成，Codex 辅助实现 | 已经 [PR #3](https://github.com/Axfff/Vibe-Player/pull/3) 合入 main；正式 AC 签收人 TODO |
| REDMI 手工验收 | CroissantXinny 操作并确认声音，Codex 记录 | `785e0863`，Android 16 / API 36，TC01–TC10 Pass；[真机报告](../testing/custom-playlists-redmi-results.md) |
| 模拟器手工验收 | 曾沛涵 / peihanZENG | 同一 `785e0863`，Android 17 / API 37 x86_64，TC01–TC13 Pass；[报告 6e92066](https://github.com/Axfff/Vibe-Player/blob/6e92066dbadbd1d6abf5db2fc6b632b975aafa26/docs/testing/custom-playlists-manual-results.md) |
| RG 回归与 #10/#11 修复 | Axfff 账号提交，Codex 辅助执行；peihanZENG 真人审阅并合并 PR #12 | `24beffad` 的 24 Android / 29 JVM Pass；[回归报告](../testing/custom-playlists-regression-results.md)；PR #12 APPROVED |
| #13–#15 修复与验证 | Axfff 账号提交，Codex 辅助 | 已合入 main；`7b0da65c` 的 29 Android / 29 JVM Pass；[修复报告](../testing/playlist-review-fixes.md)；后续真人复审 Pending |
| #4/#5/#6 反馈处理 | CroissantXinny 的 PR #16，Codex 辅助；Axfff 集成合并 | 可见入口、内联反馈、重新索引和指导已合入；完全未索引文件发现归 #4 |
| 最终集成与发布 | Axfff 账号记录整合验证、执行合并及发布，验证注明 Codex assistance | `8f2e4616` 的 29 Android / 30 JVM 及指定 UI 冒烟 Pass；发布 CI 在 `81b22ee5` 完成 30 JVM 与 APK 构建 |
| 真人最终复审 / AC 签收 | 负责人 TODO；各改动由作者之外的队友复审 | Pending，使用 [#17 review packet](../releases/release-1-human-review.md) |
| 扩展设备验证 / 简短 Retro | 三人实际后续分工 TODO；执行与结论由本人确认 | #18 / #19 Pending，见 [Retro 模板](../releases/release-1-retrospective.md) |

## 合并与发布

| 交付 | 实际提交 / 时间（Asia/Shanghai） |
| --- | --- |
| [PR #12](https://github.com/Axfff/Vibe-Player/pull/12) → 功能分支 | `dd5878537731aacad76e64b0a57abc10f4b1a025`；2026-10-26 22:56:25；peihanZENG 已批准 |
| [PR #16](https://github.com/Axfff/Vibe-Player/pull/16) → 功能分支 | `a489a243895d6e39aea56198eacb89f448b7f17b`；2026-10-27 00:41:50 |
| [PR #3](https://github.com/Axfff/Vibe-Player/pull/3) → main | `81b22ee5868e0cd5ac6a75472296caab3753a7b5`；2026-10-27 00:51:01；最终功能分支 head `2501c95f` |
| [Release `release1`](https://github.com/Axfff/Vibe-Player/releases/tag/release1) | 指向上述 main 合并提交；2026-10-27 01:00:15 发布；Debug 评估 APK 与校验文件已上传 |

最终集成源码 `8f2e4616` 与发布版本的 `project/`、`scripts/`、`.github/workflows/` 相同。后续 `dc17523b` 更新发布文档。[最终集成报告](../testing/playlist-final-integration.md)保留源码、APK、执行环境、原始测试输出和 UI 观察。

## 范围与实现约定

- UUID 歌单，Moshi + SharedPreferences 的 `custom_playlists_v1` 存储；使用 MediaStore ID 与 displayName/duration 保守解析，保留失效成员快照。
- 添加顺序即保存顺序；Play 使用独立 snapshot，已保存编辑在下次 Play 生效，既有结束偏好继续生效。
- 失败扫描与成功空库分别处理；权限拒绝时保留成员，使用 Unknown 状态。
- AI、云同步、手动排序和歌单导入导出为本 Story 范围外事项。

| 后续 Issue | 保留范围 / 当前状态 |
| --- | --- |
| [#17](https://github.com/Axfff/Vibe-Player/issues/17) | #13–#15、PR #16 的真人复审及 AC1–AC9 正式签收；Pending，Story #2 保持 Open |
| [#18](https://github.com/Axfff/Vibe-Player/issues/18) | 最新版本真机 TC03/TC07、真实听音/EQ、TalkBack/大字体、保存失败、扫描边界与跨版本存储验证；Pending |
| [#19](https://github.com/Axfff/Vibe-Player/issues/19) | 真实 Sprint 周期、三人贡献确认、简短 Retro 与行动项；Pending。课堂展示 N/A、内部 Demo 可选；Release 发布项已完成 |
| [#4](https://github.com/Axfff/Vibe-Player/issues/4) | 已有可访问候选的重新索引和指导已实现；完全未索引文件发现 Deferred |
| [#8](https://github.com/Axfff/Vibe-Player/issues/8) | 已有成员 Add 控件的灰显/禁用，Deferred；去重与保序已有 AC3 测试 |

## 进度与决定日志

| 日期 | 已发生的工作 | 证据 |
| --- | --- | --- |
| 2026-10-25 | 建立课程文档、Story 与测试计划，完成多歌单初版与 Windows 构建 | 文档 `b1605279`（原 `2372cac`）；源码 `fd00c099`（原 `6ec4869`）；[实施记录](../development/custom-playlists-implementation.md) |
| 2026-10-25 | 修复重绑定与显式媒体命令；构建与 29 JVM 通过 | `2f660d11`，署名调整后的对应修复为 `03e85dd7`；[Windows 证据](../testing/evidence/2026-10-25-windows/summary.json) |
| 2026-10-26 | 经用户授权使用已有 Git / gh 登录完成推送与 Draft PR；按要求调整提交署名 | PR #3；原集成 403 及后续授权见实施记录 |
| 2026-10-26 | CroissantXinny 完成 REDMI TC01–TC10；曾沛涵提交模拟器 TC01–TC13 | 两份被测 `785e0863` 的手工报告 |
| 2026-10-26 | 跟踪扫描、入口和添加反馈；完成 RG01–RG07；PR #12 获真人批准并合并；发现并修复 #13–#15 | #4/#5/#6，PR #12，[审阅修复报告](../testing/playlist-review-fixes.md) |
| 2026-10-27 | 完成整合版本 29 Android / 30 JVM 和指定 UI 冒烟；所有者授权先合并并将外部复审等移交后续 Issue | [最终集成报告](../testing/playlist-final-integration.md)、#17/#18/#19 |
| 2026-10-27 | 合并 PR #16、PR #3；发布 release1；Sprint 1 #1 结项 | 上方合并表、[Release 索引](../releases/release-1.md) |
| 2026-10-27 | 文档收尾：同步已发生的交付，整理 AC 矩阵、复审材料与 Retro/Demo 待填项 | [证据矩阵](../releases/release-1-evidence-matrix.md)、[#17 packet](../releases/release-1-human-review.md)、[#19 模板](../releases/release-1-retrospective.md) |
| 2026-10-27 | CroissantXinny 确认 Release 1、2 无课堂展示要求，采用贡献确认、简短回顾和行动项的精简流程；真人 Review / AC 与扩展验证继续跟踪 | [Retro 模板](../releases/release-1-retrospective.md)、#17/#18/#19 |

## Sprint Review / Retrospective

- [x] 实现、合并提交、发布及版本对应测试证据可追踪。
- [x] PR #12 的既有真人批准、后续待复审范围与已知限制分别记录。
- [ ] 真人队友完成合并后复审，正式签收 AC1–AC9（#17）。
- 课堂展示：N/A；内部 Demo：可选，执行后记录实际版本、设备和结果（#19）。
- [ ] 三位组员确认实际分工、Sprint 起止并完成简短 Retro，留下 1–2 个改进行动及后续安排（#19）。

2026-10-27 的合并顺序来自仓库所有者授权；真人复审及正式签收继续按 #17 完成。课程材料总入口：[Release 1](../releases/release-1.md)。

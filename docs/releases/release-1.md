# Release 1 — Review index

更新日期：2026-10-27（Asia/Shanghai）。**Draft / Not released；最终集成自动验证通过。**

这是课程评审的证据索引，不表示已经发布 APK、创建 GitHub Release 或完成验收。老师说明 Release 1 在 Sprint 2 后评审，并覆盖 Sprint 1 与 Sprint 2。

## 版本信息

| 字段 | 当前值 |
| --- | --- |
| 评审日期 | 待课程安排 |
| 被测提交 SHA | 历史 TC：`785e0863`；完整审阅修复回归：`7b0da65c`；当前集成候选：`8f2e4616`，保留 `f659ff7e` 的修复。最新验证与最终合并 SHA 统一见[最终集成报告](../testing/playlist-final-integration.md) |
| Tag / GitHub Release | 未创建；是否采用由团队决定 |
| APK / 构建产物 | Debug APK 已安装并进行真机/模拟器验收；APK 哈希见真机报告，GitHub Release 待团队决定 |
| 构建环境与设备 | JDK17 / SDK33；REDMI Android 16 API 36、模拟器 Android 17 API 37；两份报告分别记录覆盖范围 |
| 远端功能分支 / Story / PR | `feat/custom-playlists`、[Story #2](https://github.com/Axfff/Vibe-Player/issues/2)、[Draft PR #3](https://github.com/Axfff/Vibe-Player/pull/3) |

## 两个 Sprint 的证据

| 评审内容 | 入口 | 当前状态 |
| --- | --- | --- |
| Sprint 1 基础项目与环境 | [Sprint 1](../sprints/sprint-1.md)、[Issue #1](https://github.com/Axfff/Vibe-Player/issues/1) | 已有协作者环境与构建记录；提交关联和 APP 运行证据待补 |
| Sprint 2 用户价值与范围 | [Sprint 2](../sprints/sprint-2.md)、[最终集成报告](../testing/playlist-final-integration.md) | 功能位于 PR #3；PR #12 的 RG 与 #13–#15 修复已保留，PR #16 的设备反馈修复的集成自动验证通过 |
| Story 与 Acceptance Criteria | [正式 Issue #2](https://github.com/Axfff/Vibe-Player/issues/2) | 已发布；AC 待签收 |
| Agile 过程与范围调整 | Sprint 文档和实际 Issue 讨论 | 后续持续记录 |
| 代码提交与审阅 | [PR #3](https://github.com/Axfff/Vibe-Player/pull/3)、[PR #12](https://github.com/Axfff/Vibe-Player/pull/12)、[PR #16](https://github.com/Axfff/Vibe-Player/pull/16) | 审阅缺陷与修复可追踪；本轮真人队友复审和正式签收由 #17 跟踪 |
| 自动验证 | [审阅修复验证](../testing/playlist-review-fixes.md)、[最终集成报告](../testing/playlist-final-integration.md) | `7b0da65c` 的 29 Android / 29 JVM Pass；当前候选构建成功，完整 29 项 Android / 30 项 JVM 全部通过；候选包含第 30 项 JVM 检查 |
| 设备功能验收 | [真机 TC01–TC10](../testing/custom-playlists-redmi-results.md)、[模拟器 TC01–TC13](https://github.com/Axfff/Vibe-Player/blob/6e92066dbadbd1d6abf5db2fc6b632b975aafa26/docs/testing/custom-playlists-manual-results.md) | 各自列明历史用例 Pass；RG01–RG07 已有通过证据；正式 AC 签收 Pending，外部验证由 #18 跟进 |
| 代码质量与回归 | [RG 结果](../testing/custom-playlists-regression-results.md)、[审阅修复验证](../testing/playlist-review-fixes.md)、最终集成报告 | 原 24 项 Android RG 与后续完整 29 项检查已通过；本轮新集成结果独立记录，真人复审 Pending |

## Release 验证记录

在此链接最终结果，不再抄写或修改 Story 的 AC。

| 检查 | 结果 | 提交 / 环境 / 证据 |
| --- | --- | --- |
| 候选源码完成构建 | 当前集成构建成功；历史版本分别留档 | `8f2e4616` 最新结果见最终集成报告；原 `2f660d11`，JDK17 / SDK33 的[构建证据](../testing/evidence/2026-10-25-windows/summary.json)保留 |
| 安装并运行候选 APK | Pass | `785e0863`；两份设备报告分别记录环境和操作 |
| 多歌单 AC1–AC9 验证 | TC 结果已记录；正式签收 Pending | 真机 TC01–TC10、模拟器 TC01–TC13 Pass；详见逐项报告 |
| 现有播放、收藏、队列的相关回归 | RG01–RG07 与 #13–#15 已有通过证据；当前集成 29 Android / 30 JVM 全部通过 | `7b0da65c`：29 Android / 29 JVM Pass；本轮最终数量和结果见最终集成报告 |
| 同伴审阅与反馈处理 | 本轮真人队友复审 / 正式签收 Pending | 历史 Review 与修正保留；后续由 [#17](https://github.com/Axfff/Vibe-Player/issues/17) 跟踪 |

构建通过、运行成功、功能验收通过分别记录。尚未执行的检查保持 Pending；记录实际失败和限制，不以模板勾选代替证据。

## 已知限制与未完成工作

- 历史 TC、RG01–RG07 与 #13–#15 修复验证均按版本归档；当前集成候选构建成功，完整 29 项 Android / 30 项 JVM 全部通过，本轮结果已记录。
- Story #2、Draft PR #3 与功能分支已发布；当前 PR 同时链接真机记录和队友测试分支报告。
- AI、手动歌单排序、导入导出和云同步不在当前首轮需求范围内。
- [PR #16](https://github.com/Axfff/Vibe-Player/pull/16) 实现 #4/#5/#6 的扫描、入口与添加反馈修复；完全未进入系统索引的文件发现限制继续由 [#4](https://github.com/Axfff/Vibe-Player/issues/4) 跟踪，可选 Add 样式由 [#8](https://github.com/Axfff/Vibe-Player/issues/8) 跟踪。
- [#17](https://github.com/Axfff/Vibe-Player/issues/17)：合并后的真人队友复审与正式 AC1–AC9 签收；[#18](https://github.com/Axfff/Vibe-Player/issues/18)：真机、可访问性和扫描边界验证；[#19](https://github.com/Axfff/Vibe-Player/issues/19)：团队回顾与 Release 准备。这些 Issue 已建立，尚未宣称完成。

合并顺序例外（2026-10-27）：用户明确授权在本轮可执行验证通过后完成合并，将依赖外部参与的审阅、签收与验证作为上述后续事项。这是对团队建议 Definition of Done 顺序的记录例外；真人批准、正式 AC 签收、课程评审和 Release 发布均不能由合并状态推定。实际合并结果见最终集成报告。

## 评审准备

- [ ] 明确可供评审的提交 SHA 和可复现构建方式。
- [ ] Story、实现 PR、提交、Review 与测试结果可以相互追踪。
- [ ] 两个 Sprint 的真实进展、阻碍及回顾已整理。
- [ ] 演示与验证基于同一个明确版本。
- [ ] 已知限制和未完成范围清楚列出。

这些检查是团队组织评审材料的方式，不代表老师额外规定了某个 tag 命名、覆盖率或 PR 数量。

# Release 1 — Review index

更新日期：2026-10-26（Asia/Shanghai）。**Draft / Not released。**

这是课程评审的证据索引，不表示已经发布 APK、创建 GitHub Release 或完成验收。老师说明 Release 1 在 Sprint 2 后评审，并覆盖 Sprint 1 与 Sprint 2。

## 版本信息

| 字段 | 当前值 |
| --- | --- |
| 评审日期 | 待课程安排 |
| 被测提交 SHA | `785e0863`；功能 `fd00c099`，播放修复 `03e85dd7`；最终交付提交待验收和 Review 后确定 |
| Tag / GitHub Release | 未创建；是否采用由团队决定 |
| APK / 构建产物 | Debug APK 已安装并进行真机/模拟器验收；APK 哈希见真机报告，GitHub Release 待团队决定 |
| 构建环境与设备 | JDK17 / SDK33；REDMI Android 16 API 36、模拟器 Android 17 API 37；两份报告分别记录覆盖范围 |
| 远端功能分支 / Story / PR | `feat/custom-playlists`、[Story #2](https://github.com/Axfff/Vibe-Player/issues/2)、[Draft PR #3](https://github.com/Axfff/Vibe-Player/pull/3) |

## 两个 Sprint 的证据

| 评审内容 | 入口 | 当前状态 |
| --- | --- | --- |
| Sprint 1 基础项目与环境 | [Sprint 1](../sprints/sprint-1.md)、[Issue #1](https://github.com/Axfff/Vibe-Player/issues/1) | 已有协作者环境与构建记录；提交关联和 APP 运行证据待补 |
| Sprint 2 用户价值与范围 | [Sprint 2](../sprints/sprint-2.md)、[实施记录](../development/custom-playlists-implementation.md) | 功能位于 Draft PR #3；TC 验收结果已记录，RG 回归和反馈修复继续进行 |
| Story 与 Acceptance Criteria | [正式 Issue #2](https://github.com/Axfff/Vibe-Player/issues/2) | 已发布；AC 待签收 |
| Agile 过程与范围调整 | Sprint 文档和实际 Issue 讨论 | 后续持续记录 |
| 代码提交与审阅 | [PR #3](https://github.com/Axfff/Vibe-Player/pull/3) 与实施记录 | PR 已发布；人工 Review Pending |
| 自动验证 | [实施记录](../development/custom-playlists-implementation.md) | Windows 构建与 29 项单元测试通过；原记录 `2f660d11` 对应署名调整后的 `03e85dd7` |
| 设备功能验收 | [真机 TC01–TC10](../testing/custom-playlists-redmi-results.md)、[模拟器 TC01–TC13](https://github.com/Axfff/Vibe-Player/blob/6e92066dbadbd1d6abf5db2fc6b632b975aafa26/docs/testing/custom-playlists-manual-results.md) | 各自列明用例 Pass；RG01–RG07 与正式 AC 签收 Pending |
| 代码质量与回归 | 实施记录及 [PR #3](https://github.com/Axfff/Vibe-Player/pull/3) | 源码检查和本地自动验证已记录；设备回归、人工审阅 Pending |

## Release 验证记录

在此链接最终结果，不再抄写或修改 Story 的 AC。

| 检查 | 结果 | 提交 / 环境 / 证据 |
| --- | --- | --- |
| 候选源码完成构建 | Pass | `2f660d11`，JDK17 / SDK33；[本轮证据](../testing/evidence/2026-10-25-windows/summary.json) |
| 安装并运行候选 APK | Pass | `785e0863`；两份设备报告分别记录环境和操作 |
| 多歌单 AC1–AC9 验证 | TC 结果已记录；正式签收 Pending | 真机 TC01–TC10、模拟器 TC01–TC13 Pass；详见逐项报告 |
| 现有播放、收藏、队列的相关回归 | Pending | 按实际修改影响确定检查范围 |
| 同伴审阅与反馈处理 | Pending | 待提供 PR Review 与修正记录 |

构建通过、运行成功、功能验收通过分别记录。尚未执行的检查保持 Pending；记录实际失败和限制，不以模板勾选代替证据。

## 已知限制与未完成工作

- 当前候选源码完成构建、单元测试和两份 TC 验收；RG01–RG07、人工 Review 与正式签收继续完成。
- Story #2、Draft PR #3 与功能分支已发布；当前 PR 同时链接真机记录和队友测试分支报告。
- AI、手动歌单排序、导入导出和云同步不在当前首轮需求范围内。
- 已知反馈：[媒体扫描 #4](https://github.com/Axfff/Vibe-Player/issues/4)、[歌单入口 #5](https://github.com/Axfff/Vibe-Player/issues/5)、[添加白条 #6](https://github.com/Axfff/Vibe-Player/issues/6)；修复后复测受影响用例。

## 评审准备

- [ ] 明确可供评审的提交 SHA 和可复现构建方式。
- [ ] Story、实现 PR、提交、Review 与测试结果可以相互追踪。
- [ ] 两个 Sprint 的真实进展、阻碍及回顾已整理。
- [ ] 演示与验证基于同一个明确版本。
- [ ] 已知限制和未完成范围清楚列出。

这些检查是团队组织评审材料的方式，不代表老师额外规定了某个 tag 命名、覆盖率或 PR 数量。

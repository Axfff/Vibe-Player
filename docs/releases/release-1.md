# Release 1 — Review index

更新日期：2026-10-25（Asia/Shanghai）。**Draft / Not released。**

这是课程评审的证据索引，不表示已经发布 APK、创建 GitHub Release 或完成验收。老师说明 Release 1 在 Sprint 2 后评审，并覆盖 Sprint 1 与 Sprint 2。

## 版本信息

| 字段 | 当前值 |
| --- | --- |
| 评审日期 | 待课程安排 |
| 交付提交 SHA | 当前候选源码 `2f660d11`；原功能 `6ec4869`；交接 HEAD `8a5d892` |
| Tag / GitHub Release | 未创建；是否采用由团队决定 |
| APK / 构建产物 | 本地 Debug 候选 APK 已生成，尚未设备验收或发布 Release；勿把上游下载链接当作本组交付 |
| 构建环境与支持设备说明 | JDK17 / SDK33，本地构建通过；设备验收 Pending |
| 远端功能分支 / Story / PR | Story #2 已建立；功能分支与 PR 待发布，交接记录有集成写入 403 |

## 两个 Sprint 的证据

| 评审内容 | 入口 | 当前状态 |
| --- | --- | --- |
| Sprint 1 基础项目与环境 | [Sprint 1](../sprints/sprint-1.md)、[Issue #1](https://github.com/Axfff/Vibe-Player/issues/1) | 已有协作者环境与构建记录；提交关联和 APP 运行证据待补 |
| Sprint 2 用户价值与范围 | [Sprint 2](../sprints/sprint-2.md)、[实施记录](../development/custom-playlists-implementation.md) | 自定义多歌单初版已在本地 feat/custom-playlists 实现，尚未验收或发布 |
| Story 与 Acceptance Criteria | [正式 Issue #2](https://github.com/Axfff/Vibe-Player/issues/2) | 已发布；AC 待签收 |
| Agile 过程与范围调整 | Sprint 文档和实际 Issue 讨论 | 后续持续记录 |
| 代码提交与审阅 | 本地实现与实施记录；远端链接待授权后补充 | 文档基础为 2372cac；正式 PR 与人工 Review 均 Pending |
| 自动验证 | [实施记录](../development/custom-playlists-implementation.md) | 本轮 Windows 构建与 29 项单元测试通过；源码 `2f660d11` |
| 设备功能验收 | [多歌单测试计划](../testing/custom-playlists.md) | TC01–TC13 和 RG01–RG07 与 AC 签收均 Pending |
| 代码质量与回归 | 实施记录，后续关联真实 PR 与 Review | 源码检查和本地自动验证已记录；设备回归、人工审阅 Pending |

## Release 验证记录

在此链接最终结果，不再抄写或修改 Story 的 AC。

| 检查 | 结果 | 提交 / 环境 / 证据 |
| --- | --- | --- |
| 候选源码完成构建 | Pass | `2f660d11`，JDK17 / SDK33；[本轮证据](../testing/evidence/2026-10-25-windows/summary.json) |
| 安装并运行交付 APK | Pending | 待填写 |
| 多歌单 AC1–AC9 验证 | Pending | 见对应测试记录，待执行 |
| 现有播放、收藏、队列的相关回归 | Pending | 按实际修改影响确定检查范围 |
| 同伴审阅与反馈处理 | Pending | 待提供 PR Review 与修正记录 |

构建通过、运行成功、功能验收通过分别记录。尚未执行的检查保持 Pending；记录实际失败和限制，不以模板勾选代替证据。

## 已知限制与未完成工作

- 当前候选源码完成本地构建与单元测试；设备验收和人工 Review Pending。
- 正式 Story #2 已建立；交接记录的集成写入 403 待团队处理，远端功能分支与 PR 待发布。
- AI、手动歌单排序、导入导出和云同步不在当前首轮需求范围内。
- 交付时补充真实缺陷、规避方式及其 Issue 链接。

## 评审准备

- [ ] 明确可供评审的提交 SHA 和可复现构建方式。
- [ ] Story、实现 PR、提交、Review 与测试结果可以相互追踪。
- [ ] 两个 Sprint 的真实进展、阻碍及回顾已整理。
- [ ] 演示与验证基于同一个明确版本。
- [ ] 已知限制和未完成范围清楚列出。

这些检查是团队组织评审材料的方式，不代表老师额外规定了某个 tag 命名、覆盖率或 PR 数量。

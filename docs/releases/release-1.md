# DSAA2044 Release 1 — Review index

更新日期：2026-10-27（Asia/Shanghai）。**Release `release1` 已发布，PR #3 已合入 main；真人复审、正式 AC 签收和简短团队回顾 Pending。课堂展示 N/A，内部 Demo 可选。** 本索引组织 Sprint 1 与 Sprint 2 的课程评审材料。

快速入口：[AC1–AC9 evidence matrix](release-1-evidence-matrix.md) · [#17 human-review packet](release-1-human-review.md) · [#19 简短 Retro 模板](release-1-retrospective.md)。

## 发布版本与产物

| 字段 | 已核验记录 |
| --- | --- |
| main 文档整理起点 | `dc17523be591a72f9fefa98536c61689e4d99ad1` |
| PR #3 合并 / tag 目标 | `81b22ee5868e0cd5ac6a75472296caab3753a7b5`；2026-10-27 00:51:01 合并，包含 PR #12 / #16 |
| Tag / GitHub Release | [`release1`](https://github.com/Axfff/Vibe-Player/releases/tag/release1)，annotated tag；2026-10-27 01:00:15 发布 |
| 应用版本 / 包名 | 4.4.24 / versionCode 187；`com.iven.musicplayergo.debug` |
| 发布 APK | [Vibe-Player-release1-debug.apk](https://github.com/Axfff/Vibe-Player/releases/download/release1/Vibe-Player-release1-debug.apk)，CI 构建、Android Debug 签名，用于课程评估 |
| APK SHA256 | `81c6fde3da1909ff3fb86efe099f265a967af5f8284b40f15f33251b1d3cfd92`；[SHA256SUMS.txt](https://github.com/Axfff/Vibe-Player/releases/download/release1/SHA256SUMS.txt)与 GitHub asset digest 一致 |
| 发布 CI | [run 40363054583](https://github.com/Axfff/Vibe-Player/actions/runs/40363054583)，源码 `81b22ee5`，30 JVM Pass、Debug APK 构建成功 |
| 后续文档 CI | [run 40364674298](https://github.com/Axfff/Vibe-Player/actions/runs/40364674298)，源码 `dc17523b`，Success |
| 课堂展示 | N/A；CroissantXinny 于 2026-10-27 确认 Release 1、Release 2 均无课堂展示要求 |
| 内部 Demo | 可选；如执行，在 #19 记录实际版本与观察；已有测试按原报告引用 |

2026-10-27 的发布记录已提交于 `dc17523b`。本轮 closeout 整理文档；发布 tag、APK 和应用版本沿用上表已发布值。

## 两个 Sprint 的交付与证据

| 事项 | 当前状态 | 证据 / 下一步 |
| --- | --- | --- |
| Sprint 1 仓库、协作、环境、构建、运行与文档 | 已完成，#1 于 2026-10-27 00:41:16 结项 | [Sprint 1](../sprints/sprint-1.md)、[#1 结项记录](https://github.com/Axfff/Vibe-Player/issues/1#issuecomment-6421630719) |
| Sprint 2 用户价值与实现 | 自定义多歌单通过 PR #3 进入 main；PR #12、PR #16 均已合并 | [Sprint 2](../sprints/sprint-2.md)、[PR #3](https://github.com/Axfff/Vibe-Player/pull/3) |
| 历史手工验收 | `785e0863`：REDMI TC01–TC10 Pass；队友模拟器 TC01–TC13 Pass | [真机报告](../testing/custom-playlists-redmi-results.md)、[模拟器报告](https://github.com/Axfff/Vibe-Player/blob/6e92066dbadbd1d6abf5db2fc6b632b975aafa26/docs/testing/custom-playlists-manual-results.md) |
| 最终集成验证 | `8f2e4616`：29 Android / 30 JVM Pass；指定 UI 冒烟通过 | [最终集成报告](../testing/playlist-final-integration.md)、[原始汇总](../testing/evidence/2026-10-27-final-integration/summary.json) |
| REDMI #13/#15 真人复测 | 2026-10-27：Shuffle 恢复、Title/Filename 双向更新、连续 C→B→A 均 Pass；`f7ba39bb` 本地 APK 与发布源码对应 | [复测报告与证据](../testing/playlist-redmi-review-13-15.md) |
| 既有真人审阅 | peihanZENG 对 PR #12 的 `d2f54ed5` 提交 APPROVED，意见“RG验收无误” | [PR #12](https://github.com/Axfff/Vibe-Player/pull/12)；2026-10-26 22:47:57 |
| 后续修复的真人复审 / 正式 AC 签收 | Pending；Story #2 与 #17 Open | [Human-review packet](release-1-human-review.md)、[逐项 AC 矩阵](release-1-evidence-matrix.md) |
| 贡献确认 / 简短 Retro | Pending；实际 Sprint 起止、三人确认及 1–2 个行动项 TODO；可用群聊或 Issue 回复完成 | [#19 模板](release-1-retrospective.md) |

## 验证版本的对应关系

- 历史手工报告：两位测试者均使用 `785e0863e1efa8f44574cec1444b649a4016c911`，环境分别为 REDMI Android 16 / API 36 与 Android 17 / API 37 x86_64 模拟器。
- 最终集成：`8f2e46160cf4fa2c2942d38421855ec05fa53784` 在 Android 17 / API 37 arm64 上运行 29 项 Android 测试；JDK17 / SDK33 / Gradle Wrapper 下通过 30 JVM；源码 tree、日志、截图与本地 APK 哈希见原始报告。
- 发布源码：`git diff 8f2e4616 release1 -- project scripts .github/workflows` 为空。发布 CI 对 `81b22ee5` 执行 30 JVM 并生成评估 APK；CI 附件的 3 份 JUnit XML 为 24+5+1 项，失败和错误均为 0。
- 本地 Android 被测 APK 的 SHA256 为 `0311528a9af4a87f02735f15a775520c096718001fbd7387919c8cb23184f21e`；发布 APK 为上表中的 CI 独立构建。#13/#15 REDMI 本地 APK 的独立哈希见[复测报告](../testing/playlist-redmi-review-13-15.md)；可选内部演示或补测按实际安装包记录。
- 本轮核验读取了既有 CI/JUnit、设备记录和源码差异；本轮执行项为文档检查。AC 签收列维持 Pending，等待 #17 的真人结论。

复现命令及构建环境见[最终集成报告](../testing/playlist-final-integration.md#build-and-automated-execution)；逐项对应见 [evidence matrix](release-1-evidence-matrix.md)。

## 已披露限制与后续范围

| Issue | 范围与实际安排 | 状态 |
| --- | --- | --- |
| [#17](https://github.com/Axfff/Vibe-Player/issues/17) | 作者之外的队友复审 #13–#15、PR #16，核对合并版本与证据，正式签收 AC1–AC9；由团队决定 Story #2 的结项 | Pending |
| [#18](https://github.com/Axfff/Vibe-Player/issues/18) | 最新合并版本 REDMI TC03/TC07、听音和真实外部 EQ、TalkBack/200% 字体、保存失败、扫描超时/部分失败/1,000 行上限/扫描中旋转，以及跨版本 pending/trashed/scoped-storage 场景 | Pending |
| [#19](https://github.com/Axfff/Vibe-Player/issues/19) | 确认真实 Sprint 日期和三人贡献，完成简短 Retro、1–2 个行动项并关联验收及后续安排；课堂展示 N/A，内部 Demo 可选；发布项已完成 | Pending |
| [#4](https://github.com/Axfff/Vibe-Player/issues/4) | 已实现可访问 MediaStore 候选重新索引和导入指导；完全未索引或不可访问文件的发现保留后续范围 | Deferred / Open |
| [#8](https://github.com/Axfff/Vibe-Player/issues/8) | 已添加成员的 Add 控件置灰/禁用；当前重复项反馈和去重逻辑已有测试 | Deferred / Open，可选 UI 改进 |

API 37 自动化运行关闭主机音频；新增 REDMI #13/#15 已确认相关播放路径的真实发声，厂商 EQ 和其他音频边界由 #18 继续验证。自动套件后出现的 System UI ANR 截图及处理过程保留在最终报告。两份旧版手工 TC、当前集成套件、新增真机复测和剩余待测范围分别列在矩阵中。

AI、云同步、手动排序和歌单导入导出均在本次 Story 范围外。

## 评审准备清单

- [x] 记录发布 SHA、tag、现有 APK、校验文件和可复现构建入口。
- [x] 关联 Story、PR #3/#12/#16、合并提交、Review 与版本对应测试证据。
- [x] 同步 Sprint 1 结项、Sprint 2 合并和 Release 发布事实。
- [x] 整理 AC1–AC9 evidence matrix、#17 review packet、#19 简短 Retro 模板。
- [x] 明确 #18、#4、#8 及其他未完成事项。
- [ ] 完成合并后真人复审与正式 AC1–AC9 签收（#17）。
- 课堂展示：N/A；内部 Demo：可选，执行后记录实际版本和结果（#19）。
- [ ] 三位组员确认实际 Sprint 日期/分工，完成简短 Retro、决定与 1–2 个行动项（#19）。
- [ ] 汇总真人审阅、AC 结论和后续安排，由团队更新 Story #2 和 #17/#19 的结项状态。

合并顺序例外记录：仓库所有者于 2026-10-27 授权完成已验证代码的集成合并，并把真人复审、扩展验证与团队课程活动分配到 #17/#18/#19。后续活动完成时，由实际执行者更新相应记录。

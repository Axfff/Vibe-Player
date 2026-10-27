# Sprint 1 — 基础项目与环境

更新日期：2026-10-27（Asia/Shanghai）。[Issue #1](https://github.com/Axfff/Vibe-Player/issues/1) 的 7 项基础任务已于 2026-10-27 00:41:16 结项。下表按实际执行日期汇总证据；正式 Sprint 起止日期由团队在 [Retro 模板](../releases/release-1-retrospective.md)中填写。

## 目标与完成情况

建立团队仓库与协作入口，配置开发环境，构建并运行 APP，记录可复现步骤。课程 Release 1 评审覆盖 Sprint 1 与 Sprint 2；Sprint 1 记录随仓库一并保留。

| 原任务 | 实际结果与日期 | 证据 |
| --- | --- | --- |
| 创建仓库 | 已完成，基于 Music Player GO 建立团队仓库 | [仓库](https://github.com/Axfff/Vibe-Player) |
| 创建 Sprint 1 Issue | 已完成，沿用 #1 | [Issue #1](https://github.com/Axfff/Vibe-Player/issues/1) |
| 团队访问 | 已完成；Axfff 在原始正文记录 collaborators 已邀请并加入 | [#1 原任务及结项记录](https://github.com/Axfff/Vibe-Player/issues/1#issuecomment-6421630719) |
| 配置环境 | 2026-10-17，Axfff 记录 Android Studio、JVM 17、虚拟机；版本输出为 Gradle 8.0 / JVM 17.0.14 | [环境](https://github.com/Axfff/Vibe-Player/issues/1#issuecomment-6231644839)、[版本](https://github.com/Axfff/Vibe-Player/issues/1#issuecomment-6231655307) |
| 构建 APP | 2026-10-17，`bash ./gradlew :app:assembleDebug` 成功、APK 生成；10-25 补有源码 SHA 的 Windows 构建 | [初次构建](https://github.com/Axfff/Vibe-Player/issues/1#issuecomment-6231666401)、[APK](https://github.com/Axfff/Vibe-Player/issues/1#issuecomment-6231695522)、[Windows 摘要](../testing/evidence/2026-10-25-windows/summary.json) |
| 安装运行 APP | 2026-10-26，CroissantXinny 在 REDMI 安装、授权并播放三首音频，确认对应提示音 | [真机报告](../testing/custom-playlists-redmi-results.md) |
| 建立文档 | 工作流、Sprint/Release 索引、Story/PR 模板与测试材料已提交；10-27 汇总到 #1 | [文档入口](../README.md)、[#1 结项评论](https://github.com/Axfff/Vibe-Player/issues/1#issuecomment-6421630719) |

## 可复现的构建与运行记录

| 字段 | 实际记录 |
| --- | --- |
| Windows 构建日期 / 源码 | 2026-10-25；`2f660d11647ac5cc3e77a3c39c05c02224584d41` |
| 环境 | Temurin JDK 17.0.20.1+1 / SDK 33 / Gradle Wrapper 8.0 |
| 命令与结果 | 从 `project/` 执行 `gradlew.bat :app:testDebugUnitTest :app:assembleDebug`；完整参数、日志见摘要；BUILD SUCCESSFUL，29 JVM Pass |
| 运行日期 / 操作者 | 2026-10-26；CroissantXinny 操作并确认声音，Codex 辅助记录 |
| 设备 / 被测提交 | REDMI K90 Pro Max / Android 16 / API 36；`785e0863e1efa8f44574cec1444b649a4016c911` |
| 源码与安装包关联 | 上述两个提交的 `project/` 内容一致；APK SHA256 均为 `e491b14079004a86b062a3a8a01326878d41b2dae99b15ac9935a3d2514309c6` |
| 实际准备与运行 | 安装 debug APK、允许音频读取、准备三份本地 WAV；Test-A/B/C 均可播放，分别听到 1/2/3 声提示音 |
| 原始证据 | [构建摘要](../testing/evidence/2026-10-25-windows/summary.json)、[成功日志](../testing/evidence/2026-10-25-windows/windows-build-retry.log)、[设备摘要](../testing/evidence/2026-10-26-redmi/summary.json)、[逐项结果](../testing/evidence/2026-10-26-redmi/results.json) |

2026-10-17 初次构建的历史 SHA 仍留待 Axfff 补充；10-25/10-26 的上述记录提供了已关联源码、APK 和设备的后续构建与运行证据。初次 Windows 解析依赖遇到 DNS 失败，随后重试构建成功，两份日志均留在证据目录。

## Review 与 Retrospective

- 基础任务结项：已完成，见 #1。
- 正式 Sprint 起止日期、课程 Review / Demo、团队实际回顾：**TODO / Pending**，由 [#19](https://github.com/Axfff/Vibe-Player/issues/19) 跟踪。
- 团队填写入口：[Retro / Demo 模板](../releases/release-1-retrospective.md)，预填已有贡献，会议与结论待实际参与者记录。
- Sprint 2 的功能验收由 [Story #2](https://github.com/Axfff/Vibe-Player/issues/2) 与 [#17](https://github.com/Axfff/Vibe-Player/issues/17) 跟踪。

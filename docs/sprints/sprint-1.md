# Sprint 1 — 基础项目与环境

整理日期：2026-10-25（Asia/Shanghai）。这是对已有证据的整理，不是声称本文档在 Sprint 1 开始时已经存在。

## 目标与来源

建立团队仓库与协作入口，配置开发环境，构建并运行基础 APP，记录可复现步骤。原始任务入口为 [Sprint 1 Issue #1](https://github.com/Axfff/Vibe-Player/issues/1)。本文件引用该 Issue，避免重新建立重复的 Sprint 1 任务。

课程说明要求将 Sprint 1 与 Sprint 2 的进展一并留在仓库，于 Release 1 评审；Sprint 1 没有单独提交要求。

## 已有证据

| 项目 | 可确认的记录 | 来源 |
| --- | --- | --- |
| 仓库与任务入口 | 团队仓库和 Sprint 1 Issue 已存在 | [仓库](https://github.com/Axfff/Vibe-Player)、[Issue #1](https://github.com/Axfff/Vibe-Player/issues/1) |
| 团队访问 | Issue 作者记录 collaborators 已邀请并加入；本次未逐人复核 | [Issue #1 正文](https://github.com/Axfff/Vibe-Player/issues/1) |
| 环境配置 | Axfff 于 2026-10-17 记录 Android Studio、JVM 17 与虚拟机已配置 | [环境评论](https://github.com/Axfff/Vibe-Player/issues/1#issuecomment-6231644839) |
| 工具版本 | 协作者贴出 Gradle 8.0、JVM 17.0.14 的版本输出 | [版本输出](https://github.com/Axfff/Vibe-Player/issues/1#issuecomment-6231655307) |
| Debug 构建 | 协作者记录 `bash ./gradlew :app:assembleDebug` 为 `BUILD SUCCESSFUL` | [构建日志](https://github.com/Axfff/Vibe-Player/issues/1#issuecomment-6231666401) |
| APK 位置 | 协作者记录 `project/app/build/outputs/apk/debug/app-debug.apk` 的输出 | [APK 记录](https://github.com/Axfff/Vibe-Player/issues/1#issuecomment-6231695522) |
| 当时构建的提交 SHA | 现有评论未记录，待补 | Pending |
| 安装与运行 APP | 已读取记录中未见可核实的安装、播放演示或运行结果 | Pending |

以上构建证据来自协作者的历史记录。本轮没有重新构建、运行 APK 或验证其当时对应的提交，也不能据此宣称当前 `main` 的功能测试全部通过。

## 待补充的可复现记录

- [ ] 由执行者补充历史构建的提交 SHA；若无法追溯，明确说明并另做一次有 SHA 的复现。
- [ ] 记录 Android SDK / 模拟器或设备版本及必要准备步骤。
- [ ] 记录 APP 安装、曲库授权、扫描本地歌曲和基本播放的实际结果。
- [ ] 保存复现日志或演示链接，并注明执行日期与人员。
- [ ] 汇总本阶段实际发现的问题及处理方式。

### 下一次运行记录模板

| 字段 | 内容 |
| --- | --- |
| 执行日期 / 执行者 | 待执行者填写 |
| 提交 SHA | Pending |
| 环境 / 设备 / API | Pending |
| 执行命令与操作 | Pending |
| 构建结果 | Pending |
| 安装与运行结果 | Pending |
| 日志 / 录屏链接 | Pending |

## Review 与 Retrospective

由团队据实际讨论补充，不追记虚构会议。

- 实际完成内容及证据：见上表，后续补充。
- 仍存在的问题：运行证据、构建提交关联与完整复现步骤待补。
- 团队实际回顾结论：Pending。
- 对 Sprint 2 的改进：每次验证记录提交 SHA，并把 AC 与测试结果对应起来。

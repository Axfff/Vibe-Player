# Release 1 三人收尾操作指南（精简版）

更新日期：2026-10-27（Asia/Shanghai）。可以把本文链接直接发给三位组员。目标是完成 [#19](https://github.com/Axfff/Vibe-Player/issues/19) 的贡献确认和简短 Retro，并推进 [#17](https://github.com/Axfff/Vibe-Player/issues/17) 的真人 Review 与 AC 签收。

CroissantXinny 已确认 Release 1、Release 2 无课堂展示要求。课堂展示为 **N/A**，内部 Demo 可选；本轮使用现有发布、测试和代码材料。以下为计划分工，个人确认、实际操作和审阅结论由本人填写。

## 先知道已经完成什么

- 自定义多歌单已经合入 main；[Release `release1`](https://github.com/Axfff/Vibe-Player/releases/tag/release1) 已发布。
- 最终集成记录包含 30 JVM、29 Android 及指定 UI 冒烟；[证据矩阵](release-1-evidence-matrix.md)列出各自版本与环境。
- CroissantXinny 已完成 [#13/#15 REDMI 真机复测](../testing/playlist-redmi-review-13-15.md)，结果 Pass，证据已上传。
- 文档整理在 [PR #20](https://github.com/Axfff/Vibe-Player/pull/20)，本轮推送继续更新该 PR；起草时为 Draft，实际状态以 PR 页面为准。
- **待办：#17 真人代码复审与 AC1–AC9 签收；#19 三人实际回顾和确认。** #18 扩展验证以及 #4/#8 后续范围另行排期。

## 三个人各做什么

| 人员 | 现在执行 | 交付到哪里 | 完成标志 |
| --- | --- | --- | --- |
| **CroissantXinny** | 确认自己的贡献与 Sprint 日期，写简短回顾；解释已完成的手机测试；参加 AC 结论确认；将材料完整的 PR #20 标记 Ready for review | #19 的本人回复、#17 的本人意见、PR #20 | 本人记录真实且有证据链接，队友审阅意见得到回应 |
| **Axfff** | 核对 Release/CI/源码链接；复审 CroissantXinny 的 PR #16；审阅 PR #20 与后续回顾文档；写本人回顾 | #17 代码复审结论、文档 PR Review、#19 本人回复 | 记录实际审阅范围、SHA、结论及后续动作；满足合并条件后执行合并 |
| **曾沛涵 / peihanZENG** | 复审 Axfff 的 #13–#15 修复；整理 AC1–AC9 证据对应；写本人回顾；汇总三人记录并提交文档 PR | #17 代码复审与 AC 草表、#19 本人回复、回顾文档 | 三人确认记录，AC 每行有结论或明确补测项，文档有审阅记录 |

计划用时：每人先花约 5 分钟写回顾，交流约 10 分钟，汇总/核对约 5–10 分钟。CroissantXinny 在 #19 的主动投入约 15–25 分钟，加上本人参与的 #17 验收确认可先预留 30–60 分钟。代码复审者按实际差异另排约 30–60 分钟；#18 的设备测试单独安排。

## 第一步：每人发一条回顾回复

打开 [Issue #19](https://github.com/Axfff/Vibe-Player/issues/19)，滚动到评论框，用自己的账号填写下面内容。也可以先在群聊交流，由记录人汇总后请本人确认。

```text
姓名 / GitHub 账号：
实际填写日期：
我确认的 Sprint 起止日期（尚待核对的写 TODO）：
我实际完成的工作及链接：
做得好的地方（举一个真实例子）：
遇到的问题：
下一轮具体改进建议：
```

贡献入口：[已有三人贡献记录](release-1-retrospective.md)、[Sprint 1](../sprints/sprint-1.md)、[Sprint 2](../sprints/sprint-2.md)。按本人经历确认或更正，保留 AI 辅助实现、记录与实际人工操作的分工。

三人交流后选出 **1–2 个改进行动**，每项写清：行动、负责人、截止日期、完成标准。正式会议、PPT 和录屏均不列为此步骤的要求。

## 第二步：两位队友完成代码复审，三人确认 AC

使用 [human-review packet](release-1-human-review.md)，正式复审目标为 `release1` / `81b22ee5868e0cd5ac6a75472296caab3753a7b5`。

1. **Axfff** 打开 [PR #16 / Files changed](https://github.com/Axfff/Vibe-Player/pull/16/files)，重点看 #6 加歌反馈、#5 歌单入口、#4 重新索引及失败处理；再对照发布版本最终代码与 packet 中证据。
2. **peihanZENG** 打开 packet 中 #13/#15 的 [8ff9e2f2 差异](https://github.com/Axfff/Vibe-Player/commit/8ff9e2f268f48caff0eca1ba1a93ad4731bd6c9f)和 #14 的 [2ffeff62 差异](https://github.com/Axfff/Vibe-Player/commit/2ffeff622e9233fc179d6ed919932402594529b1)，核对发布版本中的最终实现。关注 Shuffle、外部音效会话、标题切换及歌单播放顺序。
3. 两人分别把 packet 中的“真人审阅结论模板”复制到 [#17](https://github.com/Axfff/Vibe-Player/issues/17)，填写实际姓名、日期、完整 SHA、所读差异/证据、发现和结论。#13–#15 与 PR #16 已合并，结论记录在 #17 的本人评论；PR #20 的文档审阅使用该 PR 的 Review 功能。
4. peihanZENG 对照 [AC1–AC9 矩阵](release-1-evidence-matrix.md)整理九行签收草表；三人核对后，由实际验收人填写接受结论、姓名和日期。结论可为“接受”“要求修改”或“需要补充验证”，并写明理由和证据版本。
5. 对需要补测的具体场景安排操作者、设备和时间，记录在 #17/#18；新发现的缺陷另开 Issue 并关联。#14 的真实外部 EQ 听音等验证继续归 #18。

CroissantXinny 提供已完成手机测试的观察及必要说明，队友直接引用报告。新增操作按实际执行另记；每份测试保留原源码、APK 和环境。

## 第三步：汇总一份简短记录

peihanZENG 把三人的确认与讨论填入 [Retro 记录](release-1-retrospective.md)：

- 真实 Sprint 起止、三人实际贡献及本人确认。
- 三人的回顾观点、实际交流日期/方式、1–2 个行动项。
- #17 的真人 Review / AC 结论链接与当前状态。
- #18、#4、#8 各自的后续范围、负责人和排期；实际决定尚待确定时保留 TODO。

CroissantXinny 检查自己的贡献和手机观察；Axfff 检查技术、版本和发布信息。课堂展示写 **N/A：Release 1、2 无课堂展示要求**；内部 Demo 标记 **可选**。如选择演示某个场景，补充实际版本、设备及结果即可。

## 第四步：提交文档，由队友检查后合并

1. CroissantXinny 在 PR #20 确认材料准备好后点 **Ready for review**；Axfff 查看 **Files changed**、检查链接和待办状态，提交自己的 Review，处理意见并在检查通过后合并。
2. #20 合并后，peihanZENG 从最新 `main` 建 `docs/release1-retro-results`，填入第三步的实际记录，同步 Sprint 2 / Release 1 索引并提交 PR。可在 GitHub 文件页点铅笔编辑，提交时选择新建分支，再将 PR 指向 `main`。
3. PR 正文使用 `Refs #17, Refs #19`，说明真实补充的记录和核验方法。Axfff 审阅，CroissantXinny 核对涉及自己的部分，由有权限的队友完成合并。
4. #17 的真人 Review 与正式 AC 签收完成后，按实际结论更新 Story #2；#19 的贡献、回顾、证据链接和后续安排齐备后，由团队确认结项。#18、#4、#8 按各自完成条件处理。

文档起草和实际确认分开推进：准备好的模板可以先合并；真人结论在完成后由本人补充。当前 Review、AC 和 Retro 结果均为 **Pending**。

## Agile 过程在这个项目中的体现

Agile（敏捷）强调以较短周期交付可工作的成果，持续交流、获取反馈，再调整产品和协作方式。[敏捷宣言](https://agilemanifesto.org/iso/zhchs/manifesto.html)和[十二条原则](https://agilemanifesto.org/iso/zhchs/principles.html)提供了这些价值与原则。

本项目已有过程：Story #2 明确用户目标与 AC → 分支/PR 实现多歌单 → 手机和模拟器测试产生 #4–#6、#13–#15 等反馈 → 修复并验证 → 合入 main、发布 Release 1。当前继续完成真人复审、AC 确认和简短 Retro，再把具体改进带到下一轮。

代码 Review 关注修改质量，AC 签收关注需求结果，Retro 关注团队下一轮怎样协作。Issue、提交、测试报告和本人结论把这些步骤连接起来。

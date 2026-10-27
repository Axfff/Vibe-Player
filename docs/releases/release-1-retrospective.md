# Release 1 Retro / Demo record — Issue #19

准备日期：2026-10-27（Asia/Shanghai）。任务入口：[Issue #19](https://github.com/Axfff/Vibe-Player/issues/19)。本文件预填可追溯事实，团队讨论、演示及回顾结论保留 **TODO / Pending**，由实际参与者补充。

## Sprint 与会议日期

| 项目 | 已核实的事件日期 | 团队确认字段 |
| --- | --- | --- |
| Sprint 1 | 2026-10-17 Axfff 留下环境/构建记录；2026-10-27 00:41:16，#1 按 completed 关闭 | 正式开始日期：TODO；结束日期：TODO |
| Sprint 2 | 2026-10-25 22:28 创建 Story #2；10-26 手工验收；10-27 00:51:01 合并 PR #3 | 正式开始日期：TODO；结束日期：TODO |
| Release 1 发布 | 2026-10-27 01:00:15；tag `release1` → `81b22ee5` | Demo / 课程评审日期：TODO |
| Retro | Pending | 实际日期、时段、地点/方式、主持人、记录人、实际参与者：TODO |

以上事件日期使用 Asia/Shanghai。正式 Sprint 起止按团队实际安排填写。来源：[Sprint 1 #1](https://github.com/Axfff/Vibe-Player/issues/1)、[Story #2](https://github.com/Axfff/Vibe-Player/issues/2)、[PR #3](https://github.com/Axfff/Vibe-Player/pull/3)、[Release](https://github.com/Axfff/Vibe-Player/releases/tag/release1)。

## 三位组员的实际分工

| 组员 | 已有记录中的实际工作 | 证据 | 待本人补充 |
| --- | --- | --- | --- |
| CroissantXinny（姓名：TODO） | 发布 Story/功能 PR；集成自定义歌单及 PR #16 反馈修复，使用 Codex 辅助；操作 REDMI、确认提示音并完成 TC01–TC10；整理证据与结项文档 | [PR #3](https://github.com/Axfff/Vibe-Player/pull/3)、[PR #16](https://github.com/Axfff/Vibe-Player/pull/16)、[真机报告](../testing/custom-playlists-redmi-results.md) | 实际投入、本人关键决策、Demo/Retro 角色：TODO |
| Axfff（姓名：TODO） | 记录开发环境和初始构建；账号提交 RG 与 #13–#15 修复/验证，记录 Codex assistance；执行集成合并与 Release 发布 | [#1](https://github.com/Axfff/Vibe-Player/issues/1)、[PR #12](https://github.com/Axfff/Vibe-Player/pull/12)、[修复报告](../testing/playlist-review-fixes.md)、[发布记录](release-1.md) | 实际投入、本人执行与 AI 辅助的细分、Demo/Retro 角色：TODO |
| 曾沛涵 / peihanZENG | 在 API 37 x86_64 模拟器执行 TC01–TC13 并提交报告；对 PR #12 留下 APPROVED“RG验收无误”并合并 | [报告提交 6e92066](https://github.com/Axfff/Vibe-Player/commit/6e92066dbadbd1d6abf5db2fc6b632b975aafa26)、[PR #12](https://github.com/Axfff/Vibe-Player/pull/12) | 实际投入、后续复审范围、Demo/Retro 角色：TODO |

这张表记录已有贡献。后续 #17 的真人复审人、#18 的验证人及 #19 的演示分工由团队填写，AI 辅助执行在相应证据中注明。

## 遇到的问题与已采取的行动

| 问题 | 已采取的行动 / 当前结果 | 团队回顾问题 |
| --- | --- | --- |
| GitHub 集成写入 403 | 用户授权使用已有 Git / gh 登录；完成分支、PR 和证据发布，记录在 [Sprint 2](../sprints/sprint-2.md) | 如何更早验证协作权限？TODO |
| 导入音频尚未被系统正确索引（#4） | PR #16 加入可访问候选重新索引及导入指导；完全未索引文件发现继续 Deferred | 原始导入场景的后续验证如何分配？TODO |
| 歌单入口不明显、添加白条遮挡（#5/#6） | PR #16 增加可见入口和内联反馈；最终集成 UI 冒烟已有记录 | 如何提前检查可发现性、反馈和可访问性？TODO |
| 暂停、权限、Shuffle、音效会话与标题回归（#10/#11/#13–#15） | 建立复现与回归测试、修复并合入 main；正式复审归 #17 | 哪些风险应更早加入验收？TODO |
| 测试夹具与环境干扰 | 保留 ID3 夹具修正、System UI ANR 及失败记录；RG07 点击间隔修正后完成通过运行，见[修复报告](../testing/playlist-review-fixes.md) | 如何改善测试素材和环境稳定性？TODO |
| 集成先于最终真人签收 | 所有者授权该次合并顺序；#17/#18/#19 分别跟踪人工复审、扩展验证及课程活动 | 下轮怎样安排审阅时间和准入条件？TODO |

## 实际 Demo 记录

状态：**Pending**。建议候选为已发布的 `release1`，实际使用版本由演示者确认。

| 字段 | 实际填写 |
| --- | --- |
| 时间 / 参与者 / 演示者 | TODO |
| 课程或评审场景 / 观众 | TODO |
| 完整源码 SHA / tag | TODO；候选 `81b22ee5868e0cd5ac6a75472296caab3753a7b5` / `release1` |
| APK 来源、文件名及实际 SHA256 | TODO；候选发布附件见 [Release 1](release-1.md) |
| 设备、Android / API、必要设置 | TODO |
| 演示素材及初始状态 | TODO |
| 实际演示内容 | TODO：创建/名称校验、跨歌单添加与去重、改名/移除/删除、保存顺序播放、重启恢复、空歌单/失效文件等实际选择的场景 |
| 每个场景的预期 / 实际结果 / 证据 | TODO |
| 演示中遇到的问题 / 关联 Issue | TODO |
| 实际反馈与接受结论 | TODO |

演示所用 APK 与被测版本的对应关系填写在上表；新执行结果注明日期、操作者和附件。正式 AC 签收使用 [#17 review packet](release-1-human-review.md)。

## What went well / What to improve

| 讨论项 | 团队实际观点与事实依据 | 发言人 / 讨论日期 |
| --- | --- | --- |
| What went well | TODO | TODO |
| What to improve | TODO | TODO |
| 协作与分工 | TODO | TODO |
| 需求和范围控制 | TODO | TODO |
| 测试、证据与审阅安排 | TODO | TODO |

## 团队决定与行动项

| 实际决定 / 行动 | 负责人 | 截止日期 | 完成条件 / 关联 Issue | 状态 |
| --- | --- | --- | --- | --- |
| TODO | TODO | TODO | TODO | Pending |

- [ ] 三位组员确认各自真实分工与 Sprint 起止。
- [ ] 完成实际 Demo，填写版本、APK、设备及结果。
- [ ] 完成实际 Retro，记录参与者、观点、决定和行动项。
- [ ] 在 #17 填写真人复审及 AC 签收结论，并同步 Story #2。
- [ ] 将 #18、#4、#8 的后续范围、负责人和排期纳入团队决定。
- [ ] 更新 [Release 1 索引](release-1.md)及 [Sprint 2](../sprints/sprint-2.md)，由团队决定 #19 的结项。

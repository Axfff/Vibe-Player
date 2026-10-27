# Release 1：REDMI #13 / #15 真人复测

执行日期：2026-10-27（Asia/Shanghai）。**#13、#15 的本次设备复测均为 Pass。** 手机操作与听音确认：CroissantXinny；ADB 状态读取、截图及材料整理：Codex。

本报告关联 [#13](https://github.com/Axfff/Vibe-Player/issues/13)、[#15](https://github.com/Axfff/Vibe-Player/issues/15) 与 [#17](https://github.com/Axfff/Vibe-Player/issues/17)。#13/#15 已随 PR #3 合并而关闭；本次补充真实设备证据。真人代码审阅结论和 AC1–AC9 正式签收由 #17 继续跟踪。

## 被测版本与环境

| 项目 | 实际记录 |
| --- | --- |
| 手机 | REDMI K90 Pro Max / 25102RKBEC，Android 16 / API 36 |
| 应用 | `com.iven.musicplayergo.debug`；沿用与原安装同签名的本地 debug 构建 |
| 构建源码 | `f7ba39bb2a4e98b5ab61c9bfa43a9512c168055f` |
| 安装包 SHA-256 | `0f1d8fc0d70eac972a6f17a3898806ccf0e205e7b69893bde04e075419d0c825`；已读取手机安装包再次核对 |
| 发布源码对应 | `release1` / `81b22ee5868e0cd5ac6a75472296caab3753a7b5`；`git diff f7ba39bb release1 -- project scripts .github/workflows` 为空 |
| 被测修复 | [8ff9e2f2](https://github.com/Axfff/Vibe-Player/commit/8ff9e2f268f48caff0eca1ba1a93ad4731bd6c9f)，涵盖 #13 与 #15 |
| 验证方式 | 真人逐步操作、观察和听音；ADB 读取 UI、媒体会话与保存数据 |

[完整环境记录](evidence/2026-10-27-redmi-review/environment.json)包含两项的执行起止时间。发布附件为 CI 独立构建，其 SHA-256 为 `81c6fde3da1909ff3fb86efe099f265a967af5f8284b40f15f33251b1d3cfd92`，见 [Release 索引](../releases/release-1.md)。本报告的手机使用上表所列本地 APK。

## #13：搜索、刷新、清空后的 Shuffle

| 步骤 | 实际结果 | 证据 |
| --- | --- | --- |
| 打开 Songs | Test-A/B/C 共三首；Shuffle 显示 3、已启用 | [结构化基线](evidence/2026-10-27-redmi-review/issue13/baseline-observation.json) |
| 搜索 `zz_no_song_13` | 列表为空；Shuffle 为 0、已禁用 | [截图](evidence/2026-10-27-redmi-review/issue13/filtered-ui.png)、[状态](evidence/2026-10-27-redmi-review/issue13/filtered-observation.json) |
| 保留搜索词，歌单 → 刷新 → 完成 | 搜索词保留、列表仍为空、Shuffle 仍为 0 且禁用 | [截图](evidence/2026-10-27-redmi-review/issue13/refreshed-ui.png)、[状态](evidence/2026-10-27-redmi-review/issue13/refreshed-observation.json) |
| 清空搜索 | 三首恢复；Shuffle 为 3、已启用且可点击 | [截图](evidence/2026-10-27-redmi-review/issue13/restored-ui.png)、[状态](evidence/2026-10-27-redmi-review/issue13/restored-observation.json) |
| 点击 Shuffle | 操作者确认“是，点击 Shuffle 后可以听到测试音” | [播放确认](evidence/2026-10-27-redmi-review/issue13/playback-observation.json)、[播放后截图](evidence/2026-10-27-redmi-review/issue13/playback-ui.png)、[稍后的媒体会话](evidence/2026-10-27-redmi-review/issue13/playback-media-session.json) |

结果：**Pass**。清空搜索后 UI XML 中的“搜索…”为输入框提示文字。基线阶段保留结构化观察，后续阶段附应用截图与 UI XML。

## #15：Title/Filename 与歌单顺序

三首测试音文件名为 `File15-A/B/C.mp3`，嵌入标题为 `Title15-A/B/C`，每首约 180.4 秒。素材由仓库测试音的完整 MP3 音频帧重复并写入独立 ID3 标签制成；源文件、标签及哈希见 [fixtures.json](evidence/2026-10-27-redmi-review/issue15/fixtures.json)，系统索引见 [MediaStore 记录](evidence/2026-10-27-redmi-review/issue15/mediastore-fixtures.txt)。

| 步骤 | 实际结果 | 证据 |
| --- | --- | --- |
| 新建 Review15，依次添加 C、B、A | 保存顺序 C → B → A，媒体 ID 为 14636 → 14635 → 14634 | [详情截图](evidence/2026-10-27-redmi-review/issue15/playlist-ready-ui.png)、[保存数据](evidence/2026-10-27-redmi-review/issue15/playlist-ready-playlists.json) |
| 播放 C，当前按文件名显示 | Songs 显示 File15-A/B/C，底部显示 File15-C；操作者确认有声音 | [截图](evidence/2026-10-27-redmi-review/issue15/filename-playing-ui.png)、[状态](evidence/2026-10-27-redmi-review/issue15/filename-playing-observation.json) |
| 播放中切换为标题 | 列表和底部立即显示 Title15-A/B/C、Title15-C；声音连续、进度正常 | [截图](evidence/2026-10-27-redmi-review/issue15/title-after-ui.png)、[状态](evidence/2026-10-27-redmi-review/issue15/title-after-observation.json) |
| 播放中切回文件名 | 列表和底部立即恢复 File15-A/B/C、File15-C；声音连续、进度正常，随后暂停 C | [截图](evidence/2026-10-27-redmi-review/issue15/filename-restored-ui.png)、[状态](evidence/2026-10-27-redmi-review/issue15/filename-restored-observation.json) |
| 重新进入歌单后的切歌检查 | 操作者确认 B → A，均有声音；A 暂停 | [该次重试截图](evidence/2026-10-27-redmi-review/issue15/order-confirmed-ui.png)、[状态](evidence/2026-10-27-redmi-review/issue15/order-confirmed-observation.json) |
| 最后一轮连续操作 | 同一次 Review15 播放：C → 标题 → 文件名 → 下一首 B → 下一首 A → 暂停；名称立即更新、声音连续、顺序正确 | 操作者确认“全部符合，连续操作完成，已暂停在 A”；[最终记录](evidence/2026-10-27-redmi-review/issue15/continuous-run-complete-observation.json) |
| 保存状态核对 | 名称、成员与 C → B → A 顺序保持；显示偏好恢复为文件名 | [核对结果](evidence/2026-10-27-redmi-review/issue15/verification-checks.json)、[最终保存数据](evidence/2026-10-27-redmi-review/issue15/continuous-run-complete-playlists.json) |

结果：**Pass**。最后一轮的即时名称更新、声音连续及中间 B 曲目由操作者确认；末次 ADB 状态为 **File15-A / PAUSED**。该连续步骤采用操作者确认、媒体会话和保存数据留证；上表的重试截图对应其自身阶段。

过程中曾报告“下一首到了 Test-A”。操作者随后确认“回到 Songs 后，又点了列表中的歌曲”，播放来源切到了普通曲库。重新从 Review15 播放后 B → A 正常；最后又完成了保持歌单播放的完整连续步骤。原始现场和澄清分别见[现场记录](evidence/2026-10-27-redmi-review/issue15/unexpected-next-test-a-observation.json)、[操作澄清](evidence/2026-10-27-redmi-review/issue15/operator-clarification.json)。

## 留证与后续

- [证据目录](evidence/2026-10-27-redmi-review/README.md)收录 10 张截图、UI XML、操作确认与状态数据；[来源校验](evidence/2026-10-27-redmi-review/provenance.json)记录公开文件和本地原始文件的 SHA-256。
- 本次执行两项手工真机复测及证据一致性检查。既有 29 Android / 30 JVM 的执行版本和原始结果继续见[最终集成报告](playlist-final-integration.md)。
- 测试结束时，显示方式为文件名，A 已暂停；Review15 和三首测试音保留在手机。
- #17：真人代码审阅结论、Story #2 的正式 AC1–AC9 签收 **Pending**。
- #18：#14 的外部 EQ/音频会话听音、最新 REDMI TC03/TC07、保存失败 UI、可访问性和扫描边界等剩余范围继续跟踪。
- #19：实际 Demo 与 Retro 由团队填写；#4/#8 的既定后续范围继续保留。

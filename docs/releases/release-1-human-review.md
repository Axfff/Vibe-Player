# Release 1 human-review packet — Issue #17

准备日期：2026-10-27（Asia/Shanghai）。状态：**材料已整理，真人复审与正式 AC 签收 Pending**。任务入口：[Issue #17](https://github.com/Axfff/Vibe-Player/issues/17)；正式需求：[Story #2](https://github.com/Axfff/Vibe-Player/issues/2)。由各修改作者之外的队友阅读对应差异、核对证据并填写结论。

2026-10-27 补充：CroissantXinny 已在 REDMI Android 16 / API 36 上完成 **#13、#15 的真人设备复测，均 Pass**，见[报告与证据](../testing/playlist-redmi-review-13-15.md)。被测本地 APK 为 `0f1d8fc0…`，构建源码 `f7ba39bb` 与发布版本的工程内容一致。真人代码审阅结论与正式 AC 签收继续按下方模板填写。

## 复审版本与合并链

| 对象 | 实际状态 / 精确提交 |
| --- | --- |
| PR #12 | 2026-10-26 22:56:25 合入功能分支，`dd5878537731aacad76e64b0a57abc10f4b1a025`；peihanZENG 已批准其审阅版本 `d2f54ed59808531154da4ea1c9334f3b15b0d285` |
| PR #16 | 2026-10-27 00:41:50 合入功能分支，`a489a243895d6e39aea56198eacb89f448b7f17b`；该变更的真人复审 Pending |
| PR #3 | 2026-10-27 00:51:01 合入 main，`81b22ee5868e0cd5ac6a75472296caab3753a7b5`；包含前两项与 #13–#15 修复 |
| release1 | 指向 PR #3 的上述合并提交；正式复审目标为该完整 SHA |
| 文档起点 | `dc17523be591a72f9fefa98536c61689e4d99ad1`，相对 tag 增补发布记录 |
| 集成测试源码 | `8f2e46160cf4fa2c2942d38421855ec05fa53784`；与发布版本的 `project/`、`scripts/`、`.github/workflows/` 内容一致 |

PR #3 / #16 中由 Axfff 账号发布的最终技术核对是注明 AI assistance 的 COMMENTED 记录。PR #12 的既有真人批准保留其原审阅范围；本次待填范围为后续修复与整体验收。合并先于本次复审的安排由仓库所有者于 2026-10-27 授权，跟踪记录见 #17。

## 重点复审范围

| 范围 / 记录作者 | 代码入口与差异 | 队友需核对的行为 / 现有证据 |
| --- | --- | --- |
| #13 Shuffle；Axfff，Codex 辅助 | [修复 8ff9e2f2](https://github.com/Axfff/Vibe-Player/commit/8ff9e2f268f48caff0eca1ba1a93ad4731bd6c9f)：`AllMusicFragment.kt` 的列表更新路径 | 搜索无结果→刷新→清空搜索后，Shuffle 数量、enabled 状态和播放一致；集成测试 `searchClearedAfterPlaylistRefreshRestoresShuffleCountAndPlayback` |
| #14 外部音效会话；Axfff，Codex 辅助 | [修复 2ffeff62](https://github.com/Axfff/Vibe-Player/commit/2ffeff622e9233fc179d6ed919932402594529b1)：`MediaPlayerHolder.kt` | 手动下一首、自然切歌、文件失败恢复、服务结束的 OPEN/CLOSE 与实际 session ID 配对；三项 Android 测试，真实设备听音/EQ 归 #18 |
| #15 Title/Filename；Axfff，Codex 辅助 | [修复 8ff9e2f2](https://github.com/Axfff/Vibe-Player/commit/8ff9e2f268f48caff0eca1ba1a93ad4731bd6c9f)：`MainActivity.kt`、`AllMusicFragment.kt` | 实际偏好双向切换后标题立即更新，当前播放器、位置、曲目及 C→B→A 快照保留；集成测试 `titleFilenamePreferenceUpdatesRetainedSongsAndMiniPlayerWithoutReorderingPlaylist` |
| PR #16 / #6 添加反馈；CroissantXinny，Codex 辅助 | [PR #16 差异](https://github.com/Axfff/Vibe-Player/pull/16/files)：`PlaylistEditDialogs.kt` | 成功后行状态更新；重复与写入失败有清楚反馈，Refresh/Done 可操作；重复反馈与旋转已有最新冒烟记录，写入失败 UI 的最新复测归 #18 |
| PR #16 / #5 入口；CroissantXinny，Codex 辅助 | 同上：`LibraryActionsFragment.kt`、`library_actions.xml`、`main_activity.xml` | 停止播放、空库、权限拒绝时仍能管理歌单；API 37 冒烟通过；TalkBack/200% 字体与横竖屏完整覆盖归 #18 |
| PR #16 / #4 重新索引；CroissantXinny，Codex 辅助 | 同上：`LibraryScanViewModel.kt`、`LocalMediaIndexer.kt`、`MediaScanPolicy.kt`、导入帮助文案 | 候选来自可访问的 MediaStore.Files；最多 1,000 行；回调完成后刷新；检查生命周期、取消/超时与错误反馈。已测无候选、两文件、权限恢复；完全未索引文件发现归 #4 |

完整功能差异：[PR #3 files](https://github.com/Axfff/Vibe-Player/pull/3/files)。修复前后证据：[审阅修复报告](../testing/playlist-review-fixes.md)（`7b0da65c`，29 Android + 29 JVM）；后续整合：[最终集成报告](../testing/playlist-final-integration.md)（`8f2e4616`，29 Android + 30 JVM）。各历史执行有自己的源码 SHA。

## 建议复审步骤

1. 记录自己的姓名/账号、日期和复审 SHA；按上表选择由自己以外的作者提交的修改。
2. 阅读上述修复差异，再看发布版本中的最终文件，重点检查播放状态、保存失败、扫描生命周期和 UI 恢复。
3. 对照 [AC1–AC9 证据矩阵](release-1-evidence-matrix.md)，逐项记录证据是否足够；需要补测的项目填写设备、提交、APK 哈希、操作和实际结果。
4. 将具体发现留在 #17，新增缺陷另建 Issue 并关联；明确其对接受结论的影响。#18 的验证和 #4/#8 的扩展范围可分别跟进。
5. 真人填写下面的结论并以自己的账号发布；团队完成正式接受后，再更新 Story #2 的 AC 和状态。

## 已知限制与验证安排

- 两份完整手工 TC 报告被测源码为 `785e0863`；最新 API 37 记录覆盖自动套件和明确列出的 UI 冒烟场景。最新合并版本的 REDMI TC03/TC07 留在 #18。
- 29 项 Android 检查记录实际播放器、进度和音效 session 事件。该自动套件运行时主机音频关闭；新增 REDMI #13/#15 已确认真实发声与上述播放路径，第三方/厂商 EQ 与 OS 音频焦点仲裁仍待执行。
- 自动套件结束后出现 System UI ANR，截图保存在 [01-empty-library.png](../testing/evidence/2026-10-27-final-integration/smoke/01-empty-library.png)；关闭该系统弹窗后执行了应用 UI 冒烟。
- 最新版本的保存失败 UI、扫描超时/部分失败/行数上限/执行中旋转、跨 Android 版本存储、完整可访问性验证 Pending，归 #18。
- #4 的完全未索引文件发现和 #8 的 Add 灰显为 Deferred；现有重新索引边界和去重反馈见矩阵。
- 发布附件是 CI 构建的 debug/evaluation APK；本地设备测试 APK 与发布 APK 各自的哈希见矩阵。Demo 安装的实际附件与设备结果由 #19 填写。

## 真人审阅结论模板

复制到 #17 后，由实际审阅者填写。当前值均为 **TODO / Pending**。

| 字段 | 真人填写 |
| --- | --- |
| 审阅人姓名 / GitHub 账号 | TODO |
| 实际审阅日期、时区 | TODO |
| 完整 reviewed SHA / tag | TODO；计划目标为 `release1` / `81b22ee5868e0cd5ac6a75472296caab3753a7b5` |
| 本人审阅范围 / 修改作者 | TODO（#13、#14、#15、PR #16 分别填写） |
| 阅读的差异及证据 | TODO：链接、被测版本、结论依据 |
| 本人实际补测 | TODO：设备、API、APK 哈希、步骤、预期、实际结果、附件；若仅查阅已有证据，填写“证据审阅” |
| 发现及严重程度 | TODO：Issue / 代码位置 / 复现条件 / 对接受的影响 |
| 审阅结论 | Pending；真人选择“接受 / 要求修改 / 需要补充验证”，并说明理由 |
| 后续动作 / 负责人 / 截止时间 | TODO |

| 正式 AC 签收 | 结果 | 证据 / 补测 / 未决项 |
| --- | --- | --- |
| AC1 | Pending | TODO |
| AC2 | Pending | TODO |
| AC3 | Pending | TODO |
| AC4 | Pending | TODO |
| AC5 | Pending | TODO |
| AC6 | Pending | TODO |
| AC7 | Pending | TODO |
| AC8 | Pending | TODO |
| AC9 | Pending | TODO |

团队最终接受人、日期及 Story #2 处理结论：**TODO / Pending**。

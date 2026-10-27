# Release 1 closeout evidence matrix

核验日期：2026-10-27（Asia/Shanghai）。正式需求为 [Story #2](https://github.com/Axfff/Vibe-Player/issues/2)。本表索引版本对应的证据；Android/JVM 自动套件引用原始执行记录，新增 H3 为当日实际执行的 REDMI #13/#15 真人复测。

## 发布基线

| 项目 | 核验结果 |
| --- | --- |
| 文档整理起点 | `main` = `dc17523be591a72f9fefa98536c61689e4d99ad1` |
| 发布版本 | [`release1`](https://github.com/Axfff/Vibe-Player/releases/tag/release1) → `81b22ee5868e0cd5ac6a75472296caab3753a7b5`；2026-10-27 01:00:15 发布 |
| 最终集成被测源码 | `8f2e46160cf4fa2c2942d38421855ec05fa53784`；`project/` tree = `4428850dffd9c784929c1522d88b20ed15064e3d` |
| 源码对应关系 | `git diff 8f2e4616 release1 -- project scripts .github/workflows` 为空；`release1` 到文档起点 `dc17523b` 只修改 Release 文档 |
| 本地 Android 被测 APK | SHA256 `0311528a9af4a87f02735f15a775520c096718001fbd7387919c8cb23184f21e`，见[原始哈希](../testing/evidence/2026-10-27-final-integration/apk-sha256.txt) |
| 发布 APK（CI 独立构建） | `Vibe-Player-release1-debug.apk`；SHA256 `81c6fde3da1909ff3fb86efe099f265a967af5f8284b40f15f33251b1d3cfd92`；[发布校验文件](https://github.com/Axfff/Vibe-Player/releases/download/release1/SHA256SUMS.txt)与 GitHub asset digest 一致 |

## 证据集与实际执行环境

| 编号 | 执行日期 / 人员或方式 | 被测提交 / 环境 | 已记录结果 |
| --- | --- | --- | --- |
| H1 | 2026-10-26；CroissantXinny 手动操作、确认声音，Codex 记录 | `785e0863e1efa8f44574cec1444b649a4016c911`；REDMI K90 Pro Max / Android 16 / API 36 | [真机报告][H1]：TC01–TC10 Pass；TC11–TC13 在该真机记录中 Pending |
| H2 | 2026-10-26；曾沛涵手动验收 | 同一 `785e0863`；Medium Phone / Android 17 / API 37 Google Play **x86_64**，1080×2400、420 dpi | [模拟器报告][H2]：TC01–TC13 Pass；报告提交 `6e92066` |
| H3 | 2026-10-27；CroissantXinny 手机操作和听音，Codex 留证 | `f7ba39bb2a4e98b5ab61c9bfa43a9512c168055f`，与 release1 的工程内容一致；REDMI Android 16 / API 36；本地 APK `0f1d8fc0…` | [复测报告][H3]：#13 搜索/刷新/清空后的 Shuffle、#15 Title/Filename 双向更新及连续 C→B→A 均 Pass |
| J | 2026-10-27；自动运行，Codex 辅助留证 | `8f2e4616`；JDK 17 / Gradle 8.0 / SDK 33 构建环境 | [汇总][Summary]：30 JVM Pass（Repository 24、FailurePlan 5、MediaScanPolicy 1）；逐项见 [J][J]、[F][F]、[M][M] |
| I | 2026-10-27；AndroidJUnitRunner，Codex 辅助留证 | `8f2e4616`；一次性 Medium Phone / Android 17 / API 37 **arm64**；主机音频关闭 | [原始输出][I]：29 Android Pass，215.954 秒；[环境][Env]；包含 RG01–RG07 与 #13–#15 的五项检查 |
| S | 2026-10-27；ADB 操作与 UIAutomator 观察 | 同一 `8f2e4616` 安装、同一 API 37 arm64 设备 | [冒烟记录][S]：入口、无候选、两文件重新索引、添加/重复反馈、旋转、权限拒绝/恢复、单成员强停后冷启动 |
| C | 2026-10-27；GitHub Actions | 发布源码 `81b22ee5`；Ubuntu runner / Temurin JDK 17，工程 SDK 33、Gradle Wrapper | [发布 CI][CI]：Debug APK 构建成功；原始 `unit-test-results` 附件为 24+5+1 = 30 JVM，0 failure / error |

H1/H2 为旧版完整手工用例；J/I/S 为最终集成版本的自动测试与指定冒烟场景；H3 补充发布源码对应版本的两项真机复测。正式团队签收单列在下一表末列，由 [#17](https://github.com/Axfff/Vibe-Player/issues/17) 的真人审阅者填写。

## AC1–AC9 对应证据

以下是正式 AC 的检索摘要；需求全文继续以 Story #2 为准。J/F/I 中的名称均可在链接的原始输出中查找。

| AC / 行为摘要 | 历史手工结果（H1/H2，785e0863） | 最终集成支持（J/I/S，8f2e4616） | 真人正式签收 |
| --- | --- | --- | --- |
| AC1 创建独立歌单与名称校验 | [H1][H1]、[H2][H2]：TC01、TC07 Pass | [J][J]：`createsIndependentPlaylistsWithTrimmedNamesAndStableUuids`、`blankAndCaseInsensitiveDuplicateNamesDoNotWriteAnything`；[S][S] 创建与冷启动 | Pending — #17 |
| AC2 改名保留成员和顺序、自身名称例外 | [H1][H1]、[H2][H2]：TC02、TC08 Pass | [J][J]：`renameExcludesItselfAndKeepsIdentityMembershipAndOrder`、`invalidRenameKeepsTheSavedNameAndTracks` | Pending — #17 |
| AC3 跨歌单独立成员、追加保序和去重 | [H1][H1]、[H2][H2]：TC03、TC07 Pass | [J][J]：`additionOrderSurvivesRestartAndDuplicatesKeepTheirPosition`；[S][S] 实际添加 A、重复 A 后仍一项、按钮无遮挡 | Pending — #17 |
| AC4 移除只影响当前关系 | [H1][H1]、[H2][H2]：TC04、TC09 Pass；H1 留有音频哈希 | [J][J]：`removalAffectsOnlyOneMembershipAndPersistsAfterRestart`；[I][I]：`rg02_editingSavedMembershipPreservesRunningSnapshot_andNextPlayUsesTheEdit` | Pending — #17 |
| AC5 删除保留其他歌单和音频 | [H1][H1]、[H2][H2]：TC05、TC10 Pass；H1 留有音频哈希 | [J][J]：`deletionPreservesOtherPlaylistsAndDoesNotReappearAfterRestart`；音频文件实际保留见历史手工记录 | Pending — #17 |
| AC6 从首个可用成员按保存顺序播放 | [H1][H1]、[H2][H2]：TC06 Pass；H2：TC12 Pass | [I][I]：`rg01_playlistClearsLiveAndPersistedQueue_andNaturallyStopsAfterItsLastSong`、`titleFilenamePreferenceUpdatesRetainedSongsAndMiniPlayerWithoutReorderingPlaylist`（C→B→A）、缺失中间文件的手动/自然跳过检查 | Pending — #17 |
| AC7 同一安装进程重启后持久化 | [H1][H1]、[H2][H2]：TC07–TC10 Pass | [J][J] 的追加、移除、删除后 reload 检查；[S][S] 接受单成员添加后 `am force-stop`、冷启动仍保留成员；改名等全部 UI 重启路径见历史 TC | Pending — #17 |
| AC8 空歌单提示且禁止播放 | [H2][H2]：TC11 Pass；H1 的 TC11 Pending | [J][J]：`emptyPlaylistHasNoPlayableTracks`；完整空歌单 UI 操作记录见 H2 | Pending — #17 |
| AC9 失效成员保留、跳过与全失效反馈 | [H2][H2]：TC12、TC13 Pass；H1 的 TC12–TC13 Pending | [J][J]：`missingTrackRetainsItsSnapshotAndAvailableTracksKeepSavedOrder`、`allUnavailableEntriesStayVisibleAndBecomeAvailableAgainWhenRestored`；[I][I]：手动/自然跳过、`allMissingFilesStopAfterFiniteAttemptsEvenWhenListsRepeat`、RG06 拒绝权限/失败扫描；[F][F] 有限恢复计划 | Pending — #17 |

H3 对 AC6 提供新增真机支持：同一次 Review15 播放内切换标题/文件名后，下一首仍依次为 B、A，且声音正常；保存成员顺序前后一致。#13 的新增记录覆盖普通曲库 Shuffle 在搜索与刷新后的恢复。其具体范围、操作者确认和截图阶段见 [H3][H3]。

## 审阅与后续事项

- [PR #12](https://github.com/Axfff/Vibe-Player/pull/12)：peihanZENG 在 `d2f54ed5` 上于 2026-10-26 22:47:57 提交 APPROVED；22:56:25 合并为 `dd587853`。审阅意见为“RG验收无误”。
- #13–#15 与 PR #16 的合并后真人复审、Story #2 的 AC 签收：**Pending**；使用 [human-review packet](release-1-human-review.md)。
- [#18](https://github.com/Axfff/Vibe-Player/issues/18)：新版本 REDMI TC03/TC07、真实听音/外部 EQ、TalkBack/200% 字体、保存失败、扫描超时/部分失败/1,000 行边界/扫描中旋转与跨版本存储验证：**Pending**。
- [#4](https://github.com/Axfff/Vibe-Player/issues/4)：已提供对现有可访问 MediaStore 候选的重新索引与导入指导；完全未索引或不可访问文件的发现为 **Deferred**。
- [#8](https://github.com/Axfff/Vibe-Player/issues/8)：已有成员的 Add 置灰/禁用为 **Deferred（可选 UI 改进）**；去重行为有上述 AC3 证据。
- [#19](https://github.com/Axfff/Vibe-Player/issues/19)：真实 Sprint 起止、Demo、团队回顾和课程评审记录 **Pending**；使用 [Retro / Demo 模板](release-1-retrospective.md)。

## 初版 closeout 文档核验（406fa072）

2026-10-27 初版 closeout 的检查结果如下；后续 H3 真机补充的文件范围和发布检查记录在 PR #20 中。

| 检查 | 实际结果 |
| --- | --- |
| 修改范围 | 10 份 Markdown 文档；范围为 README 和 docs |
| 相对链接及本地资源 | 153 个相对目标、其中 9 个 Markdown 标题锚点，全部可解析 |
| 矩阵一致性 | AC1–AC9 共 9 行，正式签收均 Pending；12 个明确列出的测试方法名与原始输出一致 |
| README 上游信息 | 分隔线后的 Music Player GO 正文、来源、许可证入口及其他信息保持原文 |
| 空白检查 | `git diff --check` 通过 |
| 发布与被测源码对应 | `git diff 8f2e4616 release1 -- project scripts .github/workflows` 为空 |
| 本轮受限目录检查 | `git diff dc17523b -- project scripts .github/workflows` 为空 |
| 执行类型 | 本轮执行文档检查；30 JVM / 29 Android 结果引用上方 J/I/C 既有记录 |

[H1]: ../testing/custom-playlists-redmi-results.md
[H2]: https://github.com/Axfff/Vibe-Player/blob/6e92066dbadbd1d6abf5db2fc6b632b975aafa26/docs/testing/custom-playlists-manual-results.md
[H3]: ../testing/playlist-redmi-review-13-15.md
[J]: ../testing/evidence/2026-10-27-final-integration/TEST-com.iven.musicplayergo.playlists.PlaylistRepositoryTest.xml
[F]: ../testing/evidence/2026-10-27-final-integration/TEST-com.iven.musicplayergo.player.PlaylistFailurePlanTest.xml
[M]: ../testing/evidence/2026-10-27-final-integration/TEST-com.iven.musicplayergo.library.MediaScanPolicyTest.xml
[I]: ../testing/evidence/2026-10-27-final-integration/instrumentation.txt
[S]: ../testing/playlist-final-integration.md#actual-ui-smoke-checks
[Env]: ../testing/evidence/2026-10-27-final-integration/environment.txt
[Summary]: ../testing/evidence/2026-10-27-final-integration/summary.json
[CI]: https://github.com/Axfff/Vibe-Player/actions/runs/40363054583

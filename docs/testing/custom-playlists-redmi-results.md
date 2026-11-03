# 自定义多歌单：REDMI 真机验收记录

测试日期：2026-10-26，Asia/Shanghai。操作与结果确认：**CroissantXinny**；记录与辅助检查：Codex。

## 当前结果

| 执行环境 | 已完成结果 | 下一步 |
| --- | --- | --- |
| REDMI K90 Pro Max / Android 16 / API 36 | **TC01–TC10 Pass** | 本机 TC11–TC13 保留 Pending；当前版本的对应功能结果引用下方模拟器报告 |
| 曾沛涵的 Android 17 / API 37 模拟器 | **TC01–TC13 Pass** | [协作者报告，提交 6e92066](https://github.com/Axfff/Vibe-Player/blob/6e92066dbadbd1d6abf5db2fc6b632b975aafa26/docs/testing/custom-playlists-manual-results.md) |
| 集成回归 | **RG01–RG07 Pending** | 优先 RG03、RG05、RG07，随后补齐其余回归 |
| 人工代码 Review / 正式 AC 签收 | Pending | 在 [PR #3](https://github.com/Axfff/Vibe-Player/pull/3) 和 [Story #2](https://github.com/Axfff/Vibe-Player/issues/2) 分别完成 |

两份报告的被测提交均为 `785e0863e1efa8f44574cec1444b649a4016c911`。协作者的 `6e92066` 提交只增加文档和图片，Android 工程与该被测提交一致。当前[测试计划](custom-playlists.md)编号为 TC01–TC13、RG01–RG07。

## 环境与测试素材

| 项目 | 实际值 |
| --- | --- |
| 手机 | REDMI K90 Pro Max，系统型号 25102RKBEC |
| 系统 | Android 16，API 36 |
| 应用 | `com.iven.musicplayergo.debug`，4.4.24 / versionCode 187 |
| 被测分支 / 提交 | `feat/custom-playlists` / `785e0863e1efa8f44574cec1444b649a4016c911` |
| APK SHA256 | `e491b14079004a86b062a3a8a01326878d41b2dae99b15ac9935a3d2514309c6` |
| 安装与音频权限 | 安装成功；音频读取权限已授予 |
| 操作方式 | 测试者在手机上点击并确认声音；Codex 用 ADB 读取界面、截屏、检查文件，以及执行并记录持久化用例的进程重启 |

素材为本轮生成的独立 PCM WAV 测试副本，每首 24 秒、44.1 kHz、单声道。测试者在开始前逐首播放，确认三种提示音均正确。测试目录为 `/storage/emulated/0/Music/VibePlayerSprint2-20261026/`。

| 别名 | 文件 | 每轮声音 |
| --- | --- | --- |
| A | `Folder-One/Test-A.wav` | 1 声 |
| B | `Folder-One/Test-B.wav` | 2 声 |
| C | `Folder-Two/Test-C.wav` | 3 声 |

各素材的 SHA256、APK 信息与设备结果汇总见 [summary.json](evidence/2026-10-26-redmi/summary.json)。逐项报告、观察内容和采集时间见 [results.json](evidence/2026-10-26-redmi/results.json)。完整证据文件校验清单见 [manifest.json](evidence/2026-10-26-redmi/manifest.json)。

## 执行方法

每个用例按计划重新建立指定输入状态。测试者确认名称校验、跨歌单状态、点击反馈和听到的播放顺序；界面 XML 与截图记录对应可见状态，文件哈希检查音频副本的完整性。

TC03 首次实际按 B、A 添加，截图也为 B、A；测试者确认输入顺序后，重新按 A、B 添加并复核去重，最终为 A、B。原观察、澄清和复核结果一起保留。

TC06 使用保存顺序 C、A、B；测试者确认关闭单曲循环、歌曲完成后继续播放、列表结束选择 Stop。手动切歌与自然切歌的结果由测试者听音并检查曲名确认。

TC07–TC10 在修改完成后使用 `am force-stop`，通过 `pidof` 确认进程已结束，再用 `am start -W` 重开同一安装。四次启动均返回 `Status: ok`、`LaunchState: COLD`；保留应用数据和测试素材，然后由测试者检查恢复内容。

| 用例 | 旧 PID → 新 PID | 进程重启证据 |
| --- | --- | --- |
| TC07 创建与添加保存 | 30192 → 793 | [TC07-restart.json](evidence/2026-10-26-redmi/TC07-restart.json) |
| TC08 改名保存 | 793 → 11702 | [TC08-restart.json](evidence/2026-10-26-redmi/TC08-restart.json) |
| TC09 移除保存 | 11702 → 21492 | [TC09-restart.json](evidence/2026-10-26-redmi/TC09-restart.json) |
| TC10 删除保存 | 21492 → 22355 | [TC10-restart.json](evidence/2026-10-26-redmi/TC10-restart.json) |

## TC01–TC10 实际结果

| 用例 | 结果 | 实际行为与证据来源 | 代表性证据 |
| --- | --- | --- | --- |
| TC01 创建与名称校验 | Pass | 测试者确认 trim、空名/纯空格/大小写重名校验均符合；截图显示 Study、Running 均为 0 首 | [歌单列表](evidence/2026-10-26-redmi/TC01-list.png) |
| TC02 改名与自身名称例外 | Pass | 测试者确认非法输入被拒绝、自身同名与大小写变体可保存、Running 不变；截图显示最终 fOcUs=[A,B] | [fOcUs 详情](evidence/2026-10-26-redmi/TC02-focus.png) |
| TC03 添加、共享成员与去重 | Pass | 测试者确认 Running=[B,C]、共享 B、重复添加反馈；复核截图显示 Study=[A,B]，各一项 | [首次 B,A](evidence/2026-10-26-redmi/TC03-study.png)、[A,B 复核](evidence/2026-10-26-redmi/TC03-study-recheck.png) |
| TC04 移除关系独立性 | Pass | 截图显示 Study=[A]；测试者确认 Running=[B,C]，B 仍可播放；B 文件哈希与原始素材一致 | [Study](evidence/2026-10-26-redmi/TC04-study.png)、[B 校验](evidence/2026-10-26-redmi/TC04-test-b-sha256.txt) |
| TC05 删除歌单 | Pass | 截图显示仅剩 Running（2 首）；测试者确认其顺序 B,C、A 可播放；A/B/C 文件哈希均保持一致 | [删除后列表](evidence/2026-10-26-redmi/TC05-list.png)、[文件校验](evidence/2026-10-26-redmi/TC05-fixture-sha256.txt) |
| TC06 按保存顺序播放 | Pass | 测试者确认手动 C→A→B、自然 C→A；截图显示保存顺序仍为 C,A,B | [Order 详情](evidence/2026-10-26-redmi/TC06-order.png) |
| TC07 创建与添加持久化 | Pass | 重启前后列表均显示 Study 2 首、Running 2 首、Order 3 首；测试者逐个确认恢复为 [A,B]、[B,C]、[C,A,B] | [重启前](evidence/2026-10-26-redmi/TC07-before-list.png)、[重启后](evidence/2026-10-26-redmi/TC07-after-list.png) |
| TC08 改名持久化 | Pass | 重启前列表为 Focus、Running；重启后截图为 Focus=[A,B]；测试者确认 Running=[B,C] 且旧名 Study 未恢复 | [重启前](evidence/2026-10-26-redmi/TC08-before-list.png)、[重启后](evidence/2026-10-26-redmi/TC08-after-focus.png) |
| TC09 移除持久化 | Pass | 重启前后截图均为 Study=[A]；测试者确认 Running=[B,C]、B 可播放；B 文件前后哈希一致 | [重启前](evidence/2026-10-26-redmi/TC09-before-study.png)、[重启后](evidence/2026-10-26-redmi/TC09-after-study.png)、[B 校验](evidence/2026-10-26-redmi/TC09-after-test-b-sha256.txt) |
| TC10 删除持久化 | Pass | 测试者确认重启后 Study 保持删除、Running=[B,C]；重启前 Running 截图和重启后三首 Songs 截图已留存；三份文件前后哈希一致 | [重启前 Running](evidence/2026-10-26-redmi/TC10-before-running.png)、[重启后 Songs](evidence/2026-10-26-redmi/TC10-after-songs.png)、[文件校验](evidence/2026-10-26-redmi/TC10-after-fixture-sha256.txt) |

## 待测项目与反馈

| 项目 | 本机状态 | 当前安排 |
| --- | --- | --- |
| TC11 空歌单 | Pending | 当前代码的功能结果引用协作者模拟器报告 |
| TC12 部分文件失效 | Pending | 当前代码的功能结果引用协作者模拟器报告 |
| TC13 全部成员不可播放 | Pending | 当前代码的功能结果引用协作者模拟器报告 |
| RG01–RG07 | Pending | 优先 RG03 暂停/播放旋转、RG05 文件失败期间 Pause/Stop、RG07 普通播放/Favorites/Queue；其余回归继续分工执行 |
| 缺陷修复后复测 | Pending | 基于修复提交重新记录受影响用例 |

| 反馈 | 来源与现象 | 跟踪 |
| --- | --- | --- |
| 新复制歌曲等待媒体索引 | 协作者模拟器：手动触发系统扫描后识别 25 首；补充扫描/空页面体验 | [Issue #4](https://github.com/Axfff/Vibe-Player/issues/4) |
| 歌单入口不明显 | 本机测试者希望主页提供更明显的歌单入口 | [Issue #5](https://github.com/Axfff/Vibe-Player/issues/5) |
| 添加歌曲时出现空白白条 | 本机测试者报告每次添加后白条覆盖“刷新/完成”，无文字，数秒后消失 | [Issue #6](https://github.com/Axfff/Vibe-Player/issues/6) |

这次证据提交更新文档和验收附件。Android 源码、Gradle 与 CI 配置沿用被测版本；构建与 29 项单元测试引用[已有 Windows 结果](evidence/2026-10-25-windows/summary.json)，推送后的 CI 状态在 PR 中按实际运行更新。

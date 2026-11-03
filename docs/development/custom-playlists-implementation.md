# 自定义多歌单：实现与验证记录

更新日期：2026-10-26。需求来源：[User Story 与 AC](../thinking/02-custom-playlists-user-story.md)。操作指南：[Coding Agent 实施指南](custom-playlists-agent-guide.md)。

## 当前结论

被测提交为 `785e0863e1efa8f44574cec1444b649a4016c911`，已发布至 `feat/custom-playlists` 和 [Draft PR #3](https://github.com/Axfff/Vibe-Player/pull/3)，关联 [Story #2](https://github.com/Axfff/Vibe-Player/issues/2)。Windows 构建、29 项单元测试及该提交的 push/PR CI 均通过。

| 2026-10-26 验收 | 结果与证据 |
| --- | --- |
| CroissantXinny / REDMI K90 Pro Max / Android 16 API 36 | [TC01–TC10 Pass](../testing/custom-playlists-redmi-results.md)；截图、原音频哈希与 TC07–TC10 强停冷启动记录已保存 |
| 曾沛涵 / Android 17 API 37 模拟器 | [TC01–TC13 Pass](https://github.com/Axfff/Vibe-Player/blob/6e92066dbadbd1d6abf5db2fc6b632b975aafa26/docs/testing/custom-playlists-manual-results.md)；报告提交为 `6e92066`，Android 源码与被测提交相同 |
| 集成回归、人工 Review、正式 AC 签收 | RG01–RG07 Pending，优先 RG03/RG05/RG07；PR 保持 Draft，签收由团队完成 |
| 待修复反馈 | [#4 曲库扫描](https://github.com/Axfff/Vibe-Player/issues/4)、[#5 歌单入口](https://github.com/Axfff/Vibe-Player/issues/5)、[#6 添加歌曲白条](https://github.com/Axfff/Vibe-Player/issues/6) |

当前源码提交为 `03e85dd7`，与下文原修复提交 `2f660d11` 的文件树相同。2026-10-26 按用户要求调整了五次提交的署名，保留每次文件树、提交内容和时间；文档/功能/交接/修复/证据提交依次为 `b1605279`、`fd00c099`、`96bc3af6`、`03e85dd7`、`785e0863`。

以下章节保留 **2026-10-25 接手时** 的状态、原提交标识和验证日志。最新设备与远端状态以上表及实际运行链接为准。

## 2026-10-25 接手结论（历史）

自定义多歌单初版已在本地 `feat/custom-playlists` 实现，包含管理界面、持久化、独立播放上下文、失效文件处理和单元测试。**当前源码 `2f660d11` 已通过本轮 Windows 构建和 29 项单元测试，AC1–AC9 待设备验收与签收。**

| 项目 | 当前证据与状态 |
| --- | --- |
| 代码基线 | `Axfff/Vibe-Player` 的 `947709e4c798a103b9fcc5962172f2e85d349196` |
| 课程结构提交 | 本地 `2372cac75dcea550041cee80d78c447ee0691885`，`docs: establish course workflow and custom playlist story` |
| 功能分支 | 本地 `feat/custom-playlists`；初版 `6ec4869`，交接 HEAD `8a5d892`，本轮修复 `2f660d11647ac5cc3e77a3c39c05c02224584d41` |
| 原始基线构建 | 独立、未修改的基线 checkout 执行 `:app:assembleDebug` 成功 |
| 功能自动检查 | 本轮 Windows 构建成功：43 个任务，17 executed / 26 up-to-date；29 tests / 0 failures / 0 errors / 0 skipped；Debug APK 已生成 |
| 设备测试 | TC01–TC13、RG01–RG07 均 Pending；环境 Blocked：adb 返回空设备列表，本机无已配置 AVD |
| 审查 | 交接版本记录有 agent 源码检查；本轮由一个 Codex agent 审查并修改公共播放文件。队友 Review Pending |
| 远端状态 | 读取确认正式 [Issue #2](https://github.com/Axfff/Vibe-Player/issues/2) 已存在；远端只有 main，开放 PR 列表为空。本轮执行本地提交与 PR 准备；交接记录的 403 写入阻塞保留待团队处理 |
| GitHub CI | 功能分支 CI Pending；分支发布后记录真实运行链接 |

## 本轮接手、修复与验证（2026-10-25）

### 实际状态与交接差异

- 提供的目录包含增量 bundle、补丁和历史日志；当前聊天目录原先为空。本轮从公开仓库取得完整 Git 历史，在 `C:\Users\ASUS\Documents\ChatGPT\dsa project\Vibe-Player` 导入 bundle，恢复 `feat/custom-playlists`。bundle SHA256 与 manifest 一致；导入后 HEAD `8a5d892`、工作区干净，原提交全部保留。
- GitHub 只读核查的 main 为 `947709e`，与交接基线一致。团队已于 22:28 创建 Issue #2；需求入口、测试和 PR 材料已改为引用正式 Issue。
- 本机原有 Android Studio JBR 25，SDK 尚未配置。本轮在仓库外准备 Temurin 17.0.20.1+1、SDK Platform 33 revision 3、Build Tools 30.0.3。Gradle 8.0、AGP 8.0.2、Kotlin 1.8.21 和工程依赖保持原版本。

### 必要修复：`2f660d11`

1. **暂停/播放后旋转的服务重绑定**：`PlayerService.onBind()` 会再次调用 `setMusicService()`，原实现每次都新建 MediaPlayer 和媒体会话。`MediaPlayerHolder.setMusicService()` 现在识别同一服务的已初始化实例并复用，保留曲目、位置和活动歌单。
2. **明确的媒体 Play/Pause 指令**：`PlayerService` 的两个回调原先都调用 toggle；暂停中的失败恢复遇到 Pause 会走恢复路径。现在分别调用 resume 和 pause，重复指令保持对应状态。
3. **文件失效期间的硬件 Pause/Stop**：失败恢复会短暂处于 PAUSED，原按键分支只在 isPlaying 时响应。现在 Pause 清除恢复播放意图，Stop 调用停止路径取消排队恢复，并阻止迟到错误重新安排播放。

### AC 与审查证据

| 范围 | 已核查实现 / 自动验证 | 设备结果 |
| --- | --- | --- |
| AC1–AC2 | Repository 的 trim、非空、不分大小写重名校验、自身改名例外；编辑弹窗仅在保存成功后回报；对应 Repository tests Pass | TC01、TC02、TC07、TC08 Pending |
| AC3–AC5 | 追加保序、mediaId 去重、跨歌单独立、移除关系与删除歌单；对应 Repository tests Pass | TC03–TC05、TC09–TC10 Pending |
| AC7 | 独立 key、Moshi JSON、同步 commit 结果及失败回退；Repository 重建/JSON 测试 Pass | 同一安装强停重启 TC07–TC10 Pending |
| AC6 | Play 清除 queueSongs/isQueue/restoreQueueSong 等旧状态；快照从 startFrom=0 开始，详情排序不改快照；失效决策 5 tests Pass | TC06、TC12、RG01–RG03 Pending |
| AC8–AC9 | UI 区分空歌单、不可用条目、扫描中/失败/未知及成功空库；完整扫描用于 resolve，可见库用于添加 | TC11–TC13、RG04–RG06 Pending |
| 既有播放、Favorites、Queue | 普通路径和收藏/队列入口已审查；保存操作只更新歌单 key；本轮明确媒体命令语义适用于普通播放和歌单 | RG07 Pending |

### 本轮自动检查

在 `project/` 执行：

```powershell
./gradlew.bat --no-daemon --console=plain -Dfile.encoding=UTF-8 -Dorg.gradle.internal.http.connectionTimeout=30000 -Dorg.gradle.internal.http.socketTimeout=30000 :app:testDebugUnitTest :app:assembleDebug
```

- 被测源码：`2f660d11647ac5cc3e77a3c39c05c02224584d41`；测试时间 23:24（Asia/Shanghai）。
- 结果：BUILD SUCCESSFUL，退出码 0；43 个任务（17 executed / 26 up-to-date）；24 项 repository + 5 项失败恢复决策测试通过，0 failures / errors / skipped。
- 首次运行在解析 aapt2 依赖时发生 DNS 错误；网络恢复后重试成功。首次与重试日志均保留。日志另记录既有翻译格式、Manifest package、Moshi kapt 和 SOFT_INPUT_ADJUST_RESIZE 警告。
- [证据清单与 APK SHA256](../testing/evidence/2026-10-25-windows/summary.json)、[成功日志](../testing/evidence/2026-10-25-windows/windows-build-retry.log)、[首次失败日志](../testing/evidence/2026-10-25-windows/windows-build-initial.log)。同目录保存两份 JUnit XML；本地 HTML 报告位于 `project/app/build/reports/tests/testDebugUnitTest/index.html`。
- APK：`project/app/build/outputs/apk/debug/app-debug.apk`，8,071,533 bytes；SHA256 `e491b14079004a86b062a3a8a01326878d41b2dae99b15ac9935a3d2514309c6`。
- 设备环境：`adb devices -l` 空列表，未配置 AVD；TC01–TC13 / RG01–RG07 Pending，环境 Blocked。优先设备验证本轮修复涉及的 RG03、RG05、RG07。

## 用户现在可以操作什么

播放器控制区增加歌单入口；曲库为空或处于权限错误页面时，仍提供歌单管理入口。管理界面支持查看多个歌单、创建、改名、删除、打开详情，以及搜索本地曲库并逐首添加歌曲。

名称先去除首尾空白，拒绝空名和不区分大小写的重复名；改名排除当前歌单本身。同一首歌曲可以属于多个歌单，在同一歌单中重复添加会提示且不改变顺序。移除仅删除成员关系，删除歌单仅删除其记录；都不删除设备音频。

Play 使用保存的添加顺序，从第一首可用歌曲的开头播放。空歌单、全部失效、尚未成功扫描或扫描失败时，界面说明原因并禁用相应操作。成功扫描后找不到的成员保留在歌单内并显示不可用；播放跳过这些条目。

## 工程选择及边界

| 选择 | 原因与实际行为 |
| --- | --- |
| UUID 标识歌单 | 改名不改变歌单身份；列表和恢复状态通过 ID 指向同一对象。 |
| Moshi + 独立 SharedPreferences key `custom_playlists_v1` | 使用现有依赖和存储风格；不迁移 Favorites、Queue 或其他偏好。保存结果经过检查，失败不显示成功。JSON 带 schemaVersion；无法识别或损坏的数据不会被静默清空覆盖。 |
| MediaStore ID + displayName/duration 校验 | 复用设备曲库身份，并保守防止 ID 被重用后指向明显不同的文件。它不是内容哈希，也不承诺重建媒体库后自动恢复；无法匹配时保留元数据快照并标为不可用。 |
| 完整扫描用于解析，可见曲库用于添加 | 用户隐藏曲目不等于音频已被删除。扫描失败或进行中也不等于一次成功的空结果。 |
| 每次扫描重新建立列表和分组 | 避免旧实现重复扫描时累加结果，确保删除/空曲库可被识别。成功扫描的 UI 发布与查询异常处理分开。 |
| 独立播放快照 | 点击 Play 时清除旧临时 Queue，创建有序播放列表。运行中编辑歌单不会修改已启动的快照；下次 Play 才读取新内容。 |
| 遵循已有播放结束偏好 | 自然切下一首要求 `continueOnEnd = true`；列表结束遵循 `onListEnded`。测试需要记录这些设置，不能把主动选择“播完暂停”误报成顺序播放缺陷。 |
| 有限的运行时失败恢复 | 已扫描的文件也可能随后消失。实际打开失败时，从当前播放快照移除该失败项并有序尝试后续项；快照缩小以防无限循环，重复或过时回调被忽略。持久化歌单条目不被删除。 |
| 保持既有技术栈 | Kotlin、XML、ViewBinding、现有 MediaPlayer；没有引入 Compose、Room、账户或云服务。新增测试依赖为 JUnit 4.13.2。 |

AC7 覆盖保存后的名称、成员和顺序。**本轮未实现跨进程恢复整个活动歌单快照**；重开后最多沿用现有最后一首与位置的恢复方式，用户可重新打开保存的歌单点击 Play。卸载或清除应用数据不在 AC7 范围内。

手工拖动排序、批量添加、导入导出、AI 功能和跨设备同步均未包含在本次实现中。

## 代码入口

下表中的 Kotlin 路径相对于 `project/app/src/main/java/com/iven/musicplayergo/`。

| 部分 | 关键文件 | 责任 |
| --- | --- | --- |
| 数据与存储 | `playlists/PlaylistModels.kt`、`PlaylistStorage.kt`、`PlaylistRepository.kt`、`PlaylistRepositories.kt` | 数据模型、序列化、校验、成员操作、文件可用性解析 |
| 歌单界面 | `playlists/PlaylistsDialogFragment.kt`、`PlaylistAdapters.kt`、`PlaylistEditDialogs.kt` | 列表/详情、创建/改名/删除、选曲、错误反馈、恢复界面状态 |
| 曲库与入口 | `MusicViewModel.kt`、`ui/MainActivity.kt`、`ui/MediaControlInterface.kt`、`fragments/ErrorFragment.kt` | 扫描状态、成功空结果、全局入口、启动独立播放 |
| 播放与恢复 | `player/MediaPlayerHolder.kt`、`player/PlaylistFailurePlan.kt` | 有序播放、快照隔离、实际文件失败时有限跳过与停止 |
| 原界面回归 | `fragments/AllMusicFragment.kt`、`MusicContainersFragment.kt`、`DetailsFragment.kt` | 刷新后更新原有界面，处理来源消失及失效索引 |
| 资源 | `res/layout/playlist_*.xml`、三种 `player_controls_panel.xml`、`res/values/playlists.xml`、中文资源及图标 | 延用现有 Material 布局和样式 |
| 自动测试 | `src/test/java/.../playlists/PlaylistRepositoryTest.kt`、`src/test/java/.../player/PlaylistFailurePlanTest.kt` | 纯 JVM 行为测试 |
| CI | `.github/workflows/android.yml` | 功能分支/PR 构建、单元测试、测试报告和 Debug APK 附件 |

## 交接包历史验证：源码 `6ec4869`

本节保留原交接环境的记录。本轮 `2f660d11` 的命令、结果和证据见上节。

### 环境

实际环境使用完整 Temurin JDK 17.0.21+9、Gradle Wrapper 8.0、AGP 8.0.2、Kotlin 1.8.21、Android SDK 33。原应用版本维持 `4.4.24` / versionCode `187`；Debug applicationId 为 `com.iven.musicplayergo.debug`。

本环境最初只有不含 `jlink` 的 Java 安装，导致未修改的原始项目也无法构建。改用完整 JDK 17 后，原始基线构建通过。下载的 JDK 在此环境使用系统 CA trust store 下载测试依赖，未关闭 TLS 校验。这些是构建环境处理，没有把机器路径或证书配置提交到工程。

最后一轮增量构建的单元测试通过，但 D8 报告生成的 `androidx.coordinatorlayout.R$attr` 重复；执行 `:app:clean` 后仍观察到旧 dex 文件。随后仅删除本次 checkout 的 `project/app/build/` 和 `project/build/` 生成目录，再执行相同测试/打包任务，**43 个任务全部重新执行并成功，29 个测试全部通过**。该重建未改动源码或依赖版本；两次失败日志与最终成功日志均保留在交接包中，便于核查。

### 执行命令

在仓库根目录先查看分支和改动；再进入 **project/** 执行：

```bash
java -version
bash ./gradlew --version
bash ./gradlew --no-daemon --console=plain :app:testDebugUnitTest :app:assembleDebug
```

Windows PowerShell 使用 `./gradlew.bat` 替换 `bash ./gradlew`。使用自己的 Android SDK 路径和完整 JDK 17；不必照搬本次环境的下载目录或 JVM 参数。

| 自动检查 | 内容 | 结果 |
| --- | --- | --- |
| 原始基线 assembleDebug | 独立干净 checkout，原始源代码能否编译 | Pass |
| PlaylistRepositoryTest | 名称、自身改名、Locale、添加顺序、重复与共享成员、删除隔离、重新创建 repository 后读取 JSON、失效成员、ID 重用校验、损坏数据、存储失败、并发写入 | 24 tests，0 failures，0 errors，0 skipped |
| PlaylistFailurePlanTest | 中间/末尾失败、列表结束偏好、全部失败时有限终止、重复/过时失败、播放字段不改变曲目身份 | 5 tests，0 failures，0 errors，0 skipped |
| 最终集成构建 | 包括详情刷新、暂停旋转和失败恢复的最后修复 | Pass；完整移除生成产物后执行，43 个任务全部重新执行，BUILD SUCCESSFUL |

历史单元测试使用可控存储与真实 JSON 编解码，覆盖 repository 重建后的数据恢复；失败恢复测试覆盖纯决策函数。真实 SharedPreferences 强停恢复、MediaPlayer 回调、音频焦点和服务生命周期列入设备验收。

生成位置：

- APK：`project/app/build/outputs/apk/debug/app-debug.apk`。
- HTML 测试报告：`project/app/build/reports/tests/testDebugUnitTest/index.html`。
- XML 结果：`project/app/build/test-results/testDebugUnitTest/`。
- 交接包中的 verification/ 保存本次构建日志和测试 XML；日志出现的既有资源格式、Moshi kapt、SDK XML 等警告不等于新增检查全部无警告。

最终成功日志名为 `feature-fresh-verified.log`；`feature-final-incremental-failed.log` 与 `feature-clean-task-failed.log` 是之前的失败尝试，不能用它们作为最终成功证据。该成功构建的源代码随后原样提交为 `6ec4869`；之后的提交仅整理课程文档和交接说明。

### 审查中修复的具体问题

1. 暂停时旋转屏幕会触发旧 Activity 销毁逻辑停止播放服务，丢失正在使用的歌单上下文。改为配置变化期间保留服务，销毁 Activity 时始终释放对应绑定。
2. 删掉当前专辑或文件夹后刷新，原 DetailsFragment 可能访问失效索引或空来源。增加空详情、索引校验、当前 holder 位置检查，并避免重复初始化菜单/滚动控件。
3. 刷新通知观察者时发生的 UI 异常不应被误标成 MediaStore 查询失败。成功结果发布移出查询与索引构建的异常捕获区域。
4. MediaPlayer 打开文件失败可能同时抛异常并发出 onError；增加同一次尝试去重、过时回调检查和有限恢复。
5. 停止操作不能只等待绑定中的 Service 最终销毁；立即取消已排队恢复并阻止迟到错误重新安排播放。恢复期间的音频焦点丢失也会清除播放意图，防止自动出声。

## 仍需完成的验收与协作

1. 在真实 Android 设备或模拟器运行[手工测试计划](../testing/custom-playlists.md)，记录 TC01–TC13 和 RG01–RG07 的真实结果、设备/API、被测提交、截图/录屏或 Logcat。
2. 失败项先修复并重跑受影响用例；不要因为自动测试全部通过就勾选全部 AC。
3. 在正式 [Issue #2](https://github.com/Axfff/Vibe-Player/issues/2) 中维护 Story + AC，并补充实际设备验收证据。
4. 在 GitHub 写入权限可用后推送本地提交、建立 PR，使用[PR 正文草稿](custom-playlists-pr-draft.md)填入真实 Issue 和证据链接；请协作者实际 review。
5. 验收和 review 完成后再合并 main，并更新 Sprint 2 / Release 1 的完成证据。

交接记录包含 GitHub 集成写入 403。本轮通过原集成完成只读核查，远端写入待团队处理授权；本轮成果为本地提交、构建产物和 PR 材料。

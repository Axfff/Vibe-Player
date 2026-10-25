# 自定义多歌单：实现与验证记录

更新日期：2026-10-25。需求来源：[User Story 与 AC](../thinking/02-custom-playlists-user-story.md)。操作指南：[Coding Agent 实施指南](custom-playlists-agent-guide.md)。

## 当前结论

自定义多歌单初版已在本地 `feat/custom-playlists` 实现，包含管理界面、持久化、独立播放上下文、失效文件处理和单元测试。**这是待设备验收的实现候选，不能据此将全部 AC 标记完成。**

| 项目 | 当前证据与状态 |
| --- | --- |
| 代码基线 | `Axfff/Vibe-Player` 的 `947709e4c798a103b9fcc5962172f2e85d349196` |
| 课程结构提交 | 本地 `2372cac75dcea550041cee80d78c447ee0691885`，`docs: establish course workflow and custom playlist story` |
| 功能分支 | 本地 `feat/custom-playlists`；功能源码提交 `6ec486928175d448140fbdf05fa63ed64515b787`，随后仅整理文档；最终分支 HEAD 和产物校验值见交接包 manifest.json |
| 原始基线构建 | 独立、未修改的基线 checkout 执行 `:app:assembleDebug` 成功 |
| 功能自动检查 | 最终完整重建成功：43 个任务全部执行，29 tests / 0 failures / 0 errors / 0 skipped；Debug APK 已生成 |
| 设备测试 | 尚未运行；TC01–TC13、RG01–RG07 均为 Pending |
| 审查 | 已进行独立 agent 源码检查并修复具体问题；协作者的正式 GitHub Review 尚未进行 |
| 远端状态 | 本轮未向 GitHub 推送；未创建远端功能分支、正式 Story Issue 或 PR；此前写入返回 `403 Resource not accessible by integration` |
| GitHub CI | 工作流已修改，但尚未在 GitHub 执行；本地成功不等于远端 CI 已通过 |

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

## 自动检查与可复现步骤

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

单元测试中的持久化使用可控存储实现与真实 JSON 编解码，证明 repository 在重建后恢复数据；它**不替代 Android 进程强停后的真实 SharedPreferences、界面和音频验收**。失败恢复测试验证纯决策函数，MediaPlayer 回调、音频焦点和服务生命周期仍需设备测试。

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
3. 在正常获得授权的团队环境发布包含 Story + AC 的正式 Issue，并把本地需求稿改为指向该 Issue 的入口。
4. 在 GitHub 写入权限可用后推送本地提交、建立 PR，使用[PR 正文草稿](custom-playlists-pr-draft.md)填入真实 Issue 和证据链接；请协作者实际 review。
5. 验收和 review 完成后再合并 main，并更新 Sprint 2 / Release 1 的完成证据。

当前集成返回的 403 说明这条写入通道不可用；它不能单独证明拥有者忘记安装某个应用。用户账号的仓库权限与 GitHub App 对该仓库的安装/授权范围要分别检查。本轮未用其他登录方式绕过该限制，也没有把本地提交称为已推送。

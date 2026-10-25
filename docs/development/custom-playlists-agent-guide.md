# 自定义多歌单：Coding Agent 实施指南

更新日期：2026-10-25（Asia/Shanghai）。适用仓库：[Axfff/Vibe-Player](https://github.com/Axfff/Vibe-Player)。

这份指南用于接手 Sprint 2 的多歌单实现，并指导学生检查交付。**本地 `feat/custom-playlists` 已有实现候选，文档基础提交为 `2372cac`；提交与验证状态以[实现与验证记录](custom-playlists-implementation.md)为准。接手者先检查现有代码和 diff，再完成审查、回归和设备验收。**

本次最终本地重建与 29 个单元测试均通过，源码对应功能提交 `6ec4869`；设备验收、人工 Review 和 AC 签收仍为 Pending。GitHub 集成仍返回 403，正式 Story Issue、远端功能分支和 PR 尚未建立。动态结果以[实现与验证记录](custom-playlists-implementation.md)为准，本指南中的提示词不代表这些工作已经全部完成。

## 1. 先理解工作位置和需求来源

### 1.1 两个目录，各有用途

| 操作 | 所在目录 |
| --- | --- |
| 给 agent 打开工作区、执行 Git 命令、阅读 docs | 外层仓库根目录 Vibe-Player |
| 用 Android Studio 打开 Android 工程 | 仓库内的 project/ |
| 执行 Gradle 命令 | project/ |
| 本指南 | docs/development/custom-playlists-agent-guide.md |

本次完整开发 checkout 位于 `/workspace/scratch/9f9888900409/Vibe-Player-development`，本地开发分支为 `feat/custom-playlists`。在自己的电脑上，使用自己实际克隆的仓库目录；不要照搬这个环境的绝对路径。

### 1.2 开工前必须读的三份文件

1. [多歌单 User Story 与 AC](../thinking/02-custom-playlists-user-story.md)：当前正式 Issue 发布前的需求来源。
2. [多歌单测试计划](../testing/custom-playlists.md)：TC01–TC13 和 RG01–RG07、测试数据、重启定义、结果记录方式。
3. [项目工作流](../README.md)：Story、PR、审阅和证据维护方式。

**User Story 和 Acceptance Criteria 放在同一个正式 Issue 中。** 当前尚无已确认的正式 Story Issue，不能猜测编号，也不能拿 Sprint 1 的 Issue #1 充当多歌单需求。用户已要求按此 Story 开始实施，可以先基于当前草稿推进本地开发。建立正式 Issue 后，将草稿转换为链接；Sprint、PR 和测试记录引用该 Issue 及 AC 编号。

需求正文已记录本轮采用的工程决定，下面第 2 节用于帮助接手，不形成另一套 AC。初版实现已有相应代码；接手 agent 应依据实施记录检查缺口并推进常规修复。若发现具体行为与用户最新要求或 AC 真正冲突，列出冲突及影响后再询问，不因正式 Issue 暂未发布而反复暂停本地工作。

### 1.3 检查现状，再决定是否切分支

先在**仓库根目录**执行只读检查：

```bash
git status --short --branch
git remote -v
git branch --show-current
git log -5 --oneline
git diff --stat
git diff --name-status
```

逐个阅读与本轮工作有关的 diff；`git diff` 不显示未跟踪文件内容，因此还要按 `git status` 中的路径读取新文件。尤其检查已经出现的 playlists/ 源码、测试、资源、Gradle 配置以及文档。

本工作区若已在 `feat/custom-playlists` 且包含已有实现，**直接接手该分支**。记录已有改动的范围，保留用户和其他 agent 的工作。不要用重新生成目录、强制 reset、clean 或覆盖文件的方式“从零开始”。

只在自己正常可访问远端的环境中更新远端引用：

```bash
git fetch origin
```

若 fetch 失败，记录原因和当前本地基线，继续能完成的本地工作；不要把本地远端引用说成已经更新。

对于一个**干净的、新接手的 checkout**，先检查分支是否存在：

```bash
git branch --list feat/custom-playlists
```

- 本地已存在该分支时，切到它：

```bash
git switch feat/custom-playlists
```

- 本地不存在、已确认远端 main 是合适基线且没有其他待保留改动时，才创建：

```bash
git switch -c feat/custom-playlists origin/main
```

这两条 switch 命令是不同情形的选择，不是连续执行的步骤。工作区有修改而分支归属不清时，先厘清修改的来源和归属，避免带着别人的改动切换基线。

## 2. 本轮采用的实现约定

本节记录已经选定的工程行为，不另写第二份 AC。验收条件仍以当前需求来源为准。

| 方面 | 本轮约定 |
| --- | --- |
| 歌单身份 | 每个歌单使用 UUID；改名不改变身份。名称校验、去重和添加顺序遵循需求稿。 |
| 保存方式 | 使用独立 SharedPreferences key `custom_playlists_v1` 与 Moshi 保存歌单数据。保留旧 Favorites、Queue 和设置的数据，不迁移整个存储层。 |
| 曲目引用 | 以 MediaStore ID 为主要引用，并用 displayName、duration 做保守校验；不将相同歌名视为相同音频。无法可信匹配时保留原条目快照并标为不可用，不冒险绑定别的文件。 |
| 完整库与可见库 | 成功扫描的原始完整结果（隐藏、去重和分组前）用于 resolve 已保存条目；用户当前可见的曲库用于“添加歌曲”。被用户隐藏的曲目不能仅因不可见就被认定为已删除。 |
| 扫描失败与空库 | 权限不足、扫描中或扫描失败，不应当作一次成功的空扫描。成功扫描得到 0 首时，歌单仍可管理，已有条目按实际可用性显示。 |
| 刷新 | 修复与本次刷新直接有关的旧列表累加问题，避免重复扫描越积越多；保持其他分组、隐藏、收藏和队列行为。 |
| 开始播放 | 用户点击歌单 Play 时清除旧的临时队列，建立独立、有序的播放上下文，从首个可用条目的开头播放。 |
| 运行中的歌单 | 播放使用启动时的 snapshot。编辑已保存歌单不会悄悄改动正在播放的 snapshot；再次点击 Play 才采用新的保存内容。 |
| 播放结束 | 遵守既有 continueOnEnd / onListEnded 偏好；退出歌单播放时不能重新跳回已清除的旧临时队列。具体行为需检查旧实现后接入。 |
| 重启后的范围 | AC7 要求保存歌单名称、成员和顺序。本轮不承诺恢复完整的活动歌单播放上下文；最多沿用现有“最后一首与位置”的恢复能力。 |
| UI 可达性 | 全局曲库为空时，也能进入歌单管理、创建/改名/删除歌单以及查看不可用条目。空曲库不等于用户没有已保存歌单。 |

除非为满足既定 AC 必须处理，不增加手工排序、导入导出、AI、云同步、账户系统或跨设备恢复。使用现有 Kotlin/XML/ViewBinding 结构，不进行 Compose、Room 或播放器架构迁移。

技术基线见 [顶层 Gradle 配置](../../project/build.gradle)、[Android 模块配置](../../project/app/build.gradle) 和 [Gradle Wrapper 配置](../../project/gradle/wrapper/gradle-wrapper.properties)：JDK 17、compileSdk 33、Gradle 8.0、Kotlin 1.8.21、AGP 8.0.2。环境问题先按这个基线定位，不用全项目升级来代替排错。

## 3. 实施分工与阶段产物

一个 agent 可以按顺序完成所有阶段；多个 agent 可以在共同确认接口后分工。下面的目录是接手线索，**实际文件内容和本轮主控分工优先**。

| 阶段 | 主要输入 | 主要产出 | 验证重点 |
| --- | --- | --- | --- |
| 基线审计 | 需求、测试计划、当前 diff、旧播放与扫描逻辑 | 当前实现/缺口说明，文件责任和接口约定 | 已有收藏/队列不被重复重写；基线与新增问题分清 |
| 数据层 | AC1–AC5、AC7、AC9，保存和曲目引用约定 | models、repository、storage、解析/保存与 resolve，相关单元测试 | 名称、去重、顺序、成员独立性、重启数据、失效条目 |
| UI | repository 接口、现有 XML 风格、AC1–AC5、AC8、AC9 | 管理入口、列表/详情、编辑和选曲交互、资源文案 | 所有操作可达、错误反馈、空状态、不可用条目 |
| 播放集成 | 可用条目有序 snapshot、旧播放器状态机 | Play 接口、独立播放上下文、刷新与状态协调 | 从头播、顺序不乱、旧队列退出、snapshot 稳定 |
| 独立审查与修复 | 完整 diff、已有测试证据、验收计划 | 按严重程度排列的问题与具体修复 | 跨层接口、持久化、后台播放、扫描失败等实际风险 |
| 提交与 PR | 已审查的变更、真实验证结果 | 本地提交、可审阅 PR 正文、实际可建立的远端资源 | Story/AC 可追踪，结果准确，未验证项目明确 |

当前已出现的代码入口包括：

- 数据：`playlists/PlaylistModels.kt`、`PlaylistRepository.kt`、`PlaylistStorage.kt`、`PlaylistRepositories.kt`。
- UI：`playlists/PlaylistsDialogFragment.kt`、`PlaylistAdapters.kt`、相关布局和字符串资源。
- 集成：`MusicViewModel.kt`、`ui/MainActivity.kt`、`ui/MediaControlInterface.kt`、`player/MediaPlayerHolder.kt`、`fragments/ErrorFragment.kt`。
- 自动检查：`project/app/src/test/java/com/iven/musicplayergo/playlists/`。

Kotlin 入口均位于 `project/app/src/main/java/com/iven/musicplayergo/` 下。文件存在只代表有人已经开始编写，不能据此宣称功能完整。

多 agent 协作时，给每个可写文件指定一个当前写入者。`MainActivity.kt`、`MusicViewModel.kt`、Gradle、CI 等共享文件由主控或指定集成者统一修改；其他 agent 提交接口需求和具体补丁建议。主控统一调度 Gradle 检查，避免多个 agent 在同一 checkout 同时构建或修改公共配置。

## 4. 构建、测试和手工验收怎么执行

### 4.1 环境与自动检查

用 Android Studio 打开 `project/`，将 Gradle JDK 配置为 JDK 17，并安装工程要求的 Android SDK 33。SDK 路径使用自己的本地配置；不要提交机器相关的 `local.properties`。

以下命令在 **project/** 执行。示例使用 Git Bash、macOS 或 Linux shell；Windows PowerShell 可使用对应的 `./gradlew.bat`。

```bash
java -version
bash ./gradlew --version
bash ./gradlew :app:testDebugUnitTest :app:assembleDebug
```

记录：使用的提交或工作区状态、命令、退出结果，以及失败日志中的首个有效原因。若验证的是尚未提交的工作区，写明“基于某 SHA 加当前 diff”，不要把结果归属到没有这些改动的旧提交。

相关输出一般位于：

- 单元测试报告：`project/app/build/reports/tests/testDebugUnitTest/`。
- 单元测试 XML：`project/app/build/test-results/testDebugUnitTest/`。
- Debug APK：`project/app/build/outputs/apk/debug/app-debug.apk`。

应以本次命令产生的实际文件为准。编译产物不放进源码目录，也不把旧报告当作本次证据。

本次曾遇到增量构建的 D8 重复生成资源类问题，显式移除 `project/app/build/` 和 `project/build/` 后完整重建成功，详见实施记录。若接手时重现同一错误，先留存日志并核对这些生成产物；清理构建目录不等于回滚源码，不能用 `git clean` 或 `git reset` 代替。

如本轮修改需要或既有检查要求 lint，运行：

```bash
bash ./gradlew :app:lintDebug
```

若 lint 报告旧代码问题，先结合 diff 或未改动基线区分来源：修复本次引入、直接影响既定功能的问题；将不相关基线问题说明在报告里。不要在当前工作区回滚所有改动以“验证基线”，也不要顺手重构整个项目。

只有**连接了可用设备/模拟器，而且仓库存在本轮可执行的仪器测试**时，才运行：

```bash
bash ./gradlew :app:connectedDebugAndroidTest
```

没有设备或没有仪器测试时，准确记录限制，继续单元测试、构建、代码审查和可执行的工作。SDK、依赖下载或网络失败属于需要定位的环境阻塞；不能算作通过，也不应直接推断业务代码错误。

### 4.2 UI 与播放验收

单元测试通过，只能支持它实际覆盖的数据和逻辑行为；APK 能构建，也不能证明按钮、生命周期和真实播放已满足 AC。

按照 [手工验收计划](../testing/custom-playlists.md) 执行 TC01–TC13 和 RG01–RG07，并记录设备、Android 版本、音频 A/B/C、被测版本、步骤、预期/实际结果和证据。特别注意：

- 各用例重新准备其指定 fixture，避免继承上一例删除或改名后的状态。
- 用 `[C, A, B]` 验证添加顺序，防止实现意外按名称排序仍然看似正确。
- 持久化检查使用计划中的 R：系统设置中强行停止，再打开同一安装；不清数据、不重装、不在中间重置 fixture。
- 删除音频用例只操作可恢复的测试副本，并等待媒体库和 APP 刷新实际识别变化。
- `Pending`、`Blocked`、`Fail`、`Pass` 根据实际情况填写。单元测试结果不能直接替手工用例改成 Pass。

此外，结合第 2 节已选的集成行为检查四个场景：

1. 已有临时队列时启动保存歌单，后续不混入旧队列，结束行为遵循既有偏好。
2. 播放期间编辑保存的歌单，当前 snapshot 不突变；重新点击 Play 使用修改后的内容。
3. 成功扫描为空时仍能管理保存歌单；扫描失败或权限不足时不会错误清空或批量判失效。
4. 隐藏过滤与文件缺失分开处理；添加列表使用可见库，已保存曲目的解析使用成功扫描的完整库。

这些是本轮实施约定的集成核对，不代表扩大成新的产品功能。无设备时，将它们列为待执行，由学生在自己的 Android Studio 模拟器或测试设备上完成。

## 5. 怎样使用下面的七份提示词

最省事的方式：打开仓库根目录，直接给 coding agent **提示词 1**。它应接手当前改动，按阶段连续推进，不需要你逐步回复“继续”。

如果需要并行分工，或一次对话过长，则把相应阶段提示词交给负责 agent。每份提示词都要求先读当前文件；已经完成且有证据的内容直接复用，不机械重做。独立审查可交给未负责主要实现的 agent，但 AI 审查不能冒充协作者在 GitHub 上的真实 Review。

### 提示词 1 — 主控：接手并完成多歌单

```text
你现在接手 Axfff/Vibe-Player 的 Sprint 2 多歌单实现。本地初版已有代码，先读 docs/development/custom-playlists-implementation.md 与当前 diff，继续完成实际缺口、审查和验证，不从零重做。

工作位置：agent 和 Git 使用外层仓库根目录；Android 工程与 Gradle 在 project/。本轮已有 feat/custom-playlists 分支和其他 agent/用户的改动。

先执行 git status --short --branch，检查当前分支、git diff 和未跟踪文件。保留已有实现，查清各部分完成程度和接口后继续；不要从零覆盖，也不要 reset/clean 掉现有工作。

必须先读：
- docs/thinking/02-custom-playlists-user-story.md
- docs/testing/custom-playlists.md
- docs/development/custom-playlists-agent-guide.md
- docs/README.md
正式 Story Issue 尚未建立，不能猜编号。当前按草稿 AC1–AC9 实施；一旦有真实 Issue，就以它作为 Story 和 AC 的共同来源。

遵循指南第 2 节已选实施约定：UUID 歌单；SharedPreferences+Moshi 的 custom_playlists_v1 key；曲目以 MediaStore ID 加 displayName/duration 保守校验；不可用条目保留快照；完整成功扫描用于 resolve，可见库用于添加；空曲库仍可管理歌单。Play 清除旧临时队列，建立独立有序 snapshot，从头播放；编辑保存数据不改变正在播放的 snapshot。结束遵循既有 continueOnEnd/onListEnded 偏好，不跳回旧队列。不承诺活动歌单跨重启恢复，AC7 验证的是保存数据。

依次完成：基线审计与接口约定 → 数据层 → UI → 播放/扫描集成 → 独立审查和必要修复 → 提交及 PR 准备。可并行的模块明确文件归属；公共文件和 Gradle/CI 由你或指定集成者统一改，避免并发覆盖。

保持 JDK17、compileSdk33、Gradle8.0、Kotlin1.8.21 和现有 XML/ViewBinding。不要进行全项目升级、Compose/Room 迁移，也不要增加 AI、云同步、导入导出或手工排序。

常规实现选择直接决定并推进；发现 AC 与最新要求的实质冲突时，说明具体分歧再问。遇到某个环境或权限阻塞，完成其他可执行工作，并明确留下的限制，不要每阶段强制等确认。

由主控串行执行 project/ 中的 bash ./gradlew :app:testDebugUnitTest :app:assembleDebug；按具体风险或现有门槛运行 lint。connectedDebugAndroidTest 只在存在仪器测试且有设备时运行。复用同一版本已完成的有效验证，修改后重跑受影响检查。

依据 TC01–TC13 和 RG01–RG07 区分自动验证和设备验收；没有实际执行的 UI/播放检查保持 Pending/Blocked，不据单元测试通过直接勾选全部 AC。

远端 GitHub 集成曾返回 403。可以完成本地实现、审查、提交与 PR 正文；不得切换凭据、账号或其他连接方式绕过拒绝。正式 Issue、远端分支、PR 是否存在，以真实成功返回为准。用户可在其自己的正常授权 Git 环境推送。

最终交付：改动范围和行为说明、实际文件、按 AC 编号整理的验证证据、剩余设备验收/限制、实际 Git 状态、真实提交或 PR 链接（若建立成功）。不要虚构 tests passed、同伴 Review 或课程完成情况。
```

### 提示词 2 — 基线审计与接手计划

```text
请执行多歌单的基线审计与接手，不重写代码、不回滚已有变更。

输入：当前仓库及 git diff/未跟踪文件；docs/thinking/02-custom-playlists-user-story.md；docs/testing/custom-playlists.md；docs/development/custom-playlists-agent-guide.md。

先确认当前分支和已有工作。阅读现有 playlists/ 实现，以及 GoPreferences、MusicViewModel、MainActivity、MediaControlInterface、MediaPlayerHolder 和相关资源。核对旧 Favorites、临时队列、播放恢复、隐藏过滤、曲库加载/刷新、无歌曲页面的行为。

输出一份简洁接手结论：
1. 哪些已有代码可继续使用，哪些 AC 尚有缺口；结论对应文件/函数，不根据文件名判定完成。
2. 数据、UI、播放和扫描之间需要的接口，以及公共文件由谁修改。
3. 与新歌单直接有关的已确认风险，尤其是刷新累加、未成功扫描被误当空库、可见库与完整库混用、旧队列恢复干扰。
4. 本地基线 SHA、已有改动范围、接下来的最小实施顺序。

按指南已有决定推进，不重新启动技术栈选型。若是主控角色，给出简短结论后直接进入实现；若只承担审计，将可操作结论交给主控，不自行改写其他 agent 的文件。

验证：检查结论是否能在当前源码中找到依据；明确未运行构建/设备测试的部分。不要把上游历史构建记录当作当前 diff 的验证结果。
```

### 提示词 3 — 数据层与单元测试

```text
请接手并完成多歌单数据层。先阅读现有 git diff、新文件、Story/AC、测试计划和 agent guide；不要覆盖其他 agent 已完成的代码。

范围：playlists/ 中的 PlaylistModels、PlaylistRepository、PlaylistStorage、PlaylistRepositories 及相关单元测试。公共 Gradle 改动先交由主控统一处理；不修改 UI 或播放核心。

按现有接口继续：歌单 UUID；独立 SharedPreferences key 与 Moshi；保存名称、成员、添加顺序和曲目快照。每项 accepted 修改应真正写入；写入失败返回明确失败，避免界面报告成功但重启丢数据。解析/存储错误不得通过静默写入空集合破坏旧数据。

实施 AC1–AC5、AC7 相关规则：trim、非空、其他歌单名称不分大小写唯一、改名排除自己、同曲跨歌单独立、同歌单去重且不移动原位置、追加顺序、移除与删除不触碰音频文件。

实现 AC9 所需 resolve：只使用主控提供的成功完整扫描结果；按 MediaStore ID 加 displayName/duration 做保守校验。不能可信匹配就返回保留快照的不可用条目，不删除成员，不按相同标题猜测文件。扫描失败由上层区分，不能当成功空库写回。

用可控的内存/可注入 storage 测试真实规则：名称边界、改名自身例外、去重保序、跨歌单移除独立性、序列化后用新 repository 读取、各类修改保存、失效引用、写入/解析失败不伪报成功。不要仅测试某个函数被调用。

产出：完成的数据实现、数据测试、稳定接口说明、已运行的具体测试命令与结果。自动测试由主控串行调度；在有条件时执行相关 :app:testDebugUnitTest。不能将这些结果标成 UI、SharedPreferences 真机重启或完整 AC 均已验收。
```

### 提示词 4 — 界面与交互

```text
请接手并完成多歌单 UI。先读 Story、TC01–TC13 和 RG01–RG07、agent guide 和已有 UI/data diff；使用真实 repository 接口，不另建第二套存储，也不从零覆盖已存在的界面。

负责 playlists/ 的界面/adapter、相关 XML 和字符串，以及主控明确交给你的 UI 入口文件。MainActivity、MediaControlInterface 等共享文件由指定集成者修改；需要公共接口时先给出最小调用约定。

完成：歌单列表与详情；创建/改名/删除；从当前可见本地曲库逐首添加；移除条目；名称冲突和重复添加反馈；空歌单提示与禁用 Play；Unavailable 条目仍可见；无可播放成员时有明确说明。

沿用 Kotlin、XML、ViewBinding 和现有视觉结构。保存失败时显示失败并保持一致状态，避免 UI 先显示成功。Activity/Fragment 重建后应重新读取实际保存数据，并妥善释放 view binding。

添加歌曲使用可见库；展示已保存成员的可用性使用成功完整扫描的 resolve。曲库为 0 首、无可添加歌曲时，用户仍能进入歌单管理并创建、改名、删除、查看旧条目；不要把整个歌单功能藏在“当前正在播放歌曲”的入口后。

Play 只交出选定歌单的有序、可用曲目 snapshot，由播放集成层处理旧队列和播放状态；不要在 UI 里重写播放器。

产出：界面代码和资源、入口/交互说明、逐项对应 AC 的实现位置。由主控安排编译检查；有设备时执行相关手工用例并留证据，无设备时说明哪些交互尚未实际验证。编译成功不等同于 UI 验收完成。
```

### 提示词 5 — 播放与曲库刷新集成

```text
请接手多歌单的播放与曲库集成。先读 Story/AC、测试计划、agent guide，并阅读已有 MainActivity、MediaControlInterface、MediaPlayerHolder、MusicViewModel 改动。不要覆盖数据/UI agent 的工作。

与主控确认公共接口后，复用现有 MediaPlayer 与服务，实现以下已选行为：
1. 点击保存歌单的 Play，清除旧临时队列及会令其自动恢复的相关状态。
2. 建立独立有序播放 snapshot，从首个可用曲目的开头播放；按保存的添加顺序切歌，跳过失效项。
3. 编辑保存的歌单不修改正在播放的 snapshot；重新点击 Play 才读取新的保存版本。
4. 尊重既有 continueOnEnd/onListEnded 结束偏好，不在结束后跳回旧临时队列；处理与已有随机/重复控制的关系，确保顺序模式满足 AC6。
5. 沿用现有最后一首/位置恢复能力即可，不新增完整活动歌单跨重启恢复承诺，不把运行时 snapshot 写成永久活动上下文。

曲库：只在完整扫描成功后 resolve 歌单；成功空结果与权限拒绝/扫描失败分开。用于添加的是可见库，已保存条目可用性基于完整库。修复与刷新直接相关的旧列表累加，避免重复扫描产生重复条目；不要扩大成整套 MediaStore 重构。

验证重点：有旧队列时启动歌单；[C,A,B] 手动/自然切歌；从头播放而非旧收藏进度；失效首曲/中间曲/全部失效；编辑时 snapshot 不变；空库可管理；刷新失败不破坏数据；歌单结束不回旧队列。保持已有普通播放、Favorites 和队列路径可用。

产出：集成代码、状态转换说明、相关回归风险与验证记录。主控统一执行构建与必要自动测试；设备上未执行的播放场景明确 Pending，不能凭静态审查宣称通过。
```

### 提示词 6 — 独立审查、修复与最终验证

```text
请对本分支的多歌单实现做独立审查，并推进已确认问题的必要修复。先读当前需求、测试计划、agent guide、完整 diff 和已有验证记录；优先复用同一版本有效的检查，不重新从零实现。

审查按影响排序，给出文件/函数、触发条件、用户后果和最小修复：
- AC1–AC9 的实现是否闭合，UI 是否真的可达；
- 保存成功与实际落盘是否一致，各类修改能否恢复；
- 同曲跨歌单与重复添加是否保持独立和顺序；
- ID 复用/元数据变化是否误绑定，Unavailable 快照是否保留；
- 完整扫描、可见库、空库和扫描失败是否混淆；
- Play 是否清除旧队列恢复状态，手动/自然切歌是否按 snapshot；
- 保存歌单编辑是否意外改变正在播放的列表，结束是否误回旧歌曲；
- 新增入口在无歌曲/无当前播放/横屏等现有布局中是否可用；
- 现有播放、收藏、队列是否出现与本次改动相关的回归。

先和主控确认其他写入者已完成或指定文件归属，再修复真实问题。局部实现选择直接推进；需求冲突才提出具体问题。不要加入新功能或无关全局重构。

由主控在 project/ 串行执行 bash ./gradlew :app:testDebugUnitTest :app:assembleDebug。需要 lint 时区分新问题和基线问题；只有有设备且存在仪器测试时才运行 connectedDebugAndroidTest。修复后重跑受影响检查，不用重复测试凑过程。

输出一张事实表：检查项/对应 AC、被测版本、验证方法、实际结果、证据、未验证限制。手工 UI/播放测试没执行就保持 Pending/Blocked，不用单元通过代替。AI 审查与真正的队友 GitHub Review 分开记录。
```

### 提示词 7 — 整理提交与 PR，处理远端权限

```text
请将当前多歌单工作整理为可审阅的本地提交与 PR 材料，不扩大功能范围。

先检查 git status、diff、未跟踪文件和已有验证结果。按明确的本轮文件路径暂存，保留其他来源的无关改动；检查 git diff --cached 后再创建有意义的提交。不要为制造过程而拆空提交、倒填日期或伪造审阅。

需求尚无正式 GitHub Issue：不能猜编号或写 Closes #1。先检查当前实际状态；若已获得授权并成功创建正式 Story Issue，把 Story 与 AC 放在同一个 Issue，更新草稿/Sprint/测试来源为真实链接。若未成功，PR 正文明确引用当前需求草稿并标注“正式 Issue 待建立”。

PR 正文使用仓库模板，说明用户问题、实现行为、关联需求、主要设计决定和限制；按 AC 编号列验证方法、实际结果与证据，不复制第二套可独立修改的验收标准。部分完成或尚有设备验收时标为 Draft，使用真实关联链接；只有完成且意图在合并时关闭需求才用 Closes。

项目当前 GitHub 集成曾返回 403 Resource not accessible by integration。若仍被拒绝，完成本地提交和 PR 正文，准确报告阻塞；不要切换 token、账号、浏览器或其他接入方式绕过同一拒绝。用户可以在自己的正常授权 Git 环境推送，或让仓库拥有者补齐集成授权。没有真实成功结果就不宣称远端分支、Issue 或 PR 已创建。

最终输出：实际本地 commit、剩余工作区状态、变更清单、自动验证结果、尚待设备验收的 AC/TC/RG、PR 正文与后续动作；成功的远端资源才给真实链接。不要自动合并，不代替同伴提交 Approve，也不把基础构建成功写成 Release 1 已完成。
```

## 6. 学生在自己电脑上怎样接手验收和推送

### 6.1 先看 agent 的交付是否可核实

检查四件事即可：

1. 有实际代码和明确改动范围，能够对应 Story 的 AC。
2. 有真实构建/测试命令和结果，不只写一句“全部通过”。
3. 所有未执行的设备验收和环境限制都列明。
4. Git 状态、提交、远端分支和 PR 的描述与实际页面一致。

把仓库在 Android Studio 打开，运行生成的 Debug APP。按照测试计划准备音频和用例，把实际结果补到手工验收记录。发现错误时，直接把“被测版本、TC/RG 编号、操作、预期、实际、日志/录屏”交回 agent，它应定位并修复具体问题，而不是重新生成整个功能。

### 6.2 提交与推送有不同的权限边界

本地 commit 保存版本；push 才把提交送到远端；PR 用于展示变更与审阅。这三步分别记录是否成功。

在自己的电脑、自己的正常授权 Git 环境中，确认已拿到正确分支及其提交后，可在仓库根目录执行：

```bash
git status --short --branch
git remote -v
git log -5 --oneline
git push -u origin feat/custom-playlists
```

确认 origin 指向团队仓库，且当前分支确实是准备推送的分支。若尚有应提交的本轮文件，让 agent 先按明确路径整理并检查暂存 diff；不要把整个工作区未经检查地一并提交。

上述用户本地操作不代表当前受限集成获得了写入权限。agent 遇到集成 403 时，应保留本地成果并说明阻塞；不能用替代凭据替用户绕过拒绝。授权补齐或用户正常推送成功后，再通过 GitHub 的实际结果确认分支与 PR。

### 6.3 PR 中应看到的最少信息

| 信息 | 应如何填写 |
| --- | --- |
| 用户问题与新增行为 | 多个独立命名歌单，以及实际完成的操作 |
| 需求来源 | 正式 Story Issue 的真实链接；未建立时明确说明并引用当前草稿 |
| 实现范围 | 数据、UI、播放与扫描相关修改，重要状态约定 |
| 验证 | AC/TC/RG 编号、测试或检查、实际结果、证据、被测版本 |
| 剩余事项 | 未执行设备用例、已知缺陷、环境阻塞 |
| 审阅 | 队友真实 review 和修改回应；AI 检查单独注明 |

提交 PR 后由队友审阅。未验证的内容保持明确状态；达到本轮既定范围和团队完成检查后，再由团队决定合并。Release 1 文档负责链接这些实际材料，而不是替代代码、测试与审阅。

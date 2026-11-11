# Album and Playlist Cover Foundation Handoff

更新日期：2026-11-04（Asia/Shanghai）  
负责人：zph  
目标分支建议：`feat/custom-cover-foundation`

## 1. 文档用途

本文件用于启动下一次开发对话，并作为 zph 在 Sprint 3 中负责部分的需求、架构和验收依据。

zph 的交付目标是建立一套完整、独立、可测试的封面基础能力：定义专辑和歌单封面的数据模型与内部 API，支持用户从本地选择、预览、保存、更换和删除封面，并让相关 UI 统一显示正确封面。后续成员应只需要产生照片候选并调用这里定义的 API，不需要了解图片文件如何存储，也不需要分别修改各个播放器页面。

本文件不授权直接实现。开始编码前仍应核对最新 `main`、正式 GitHub Story、团队确认后的 Acceptance Criteria，以及是否有其他成员正在修改相同文件。

## 2. 背景与需求澄清

团队当前讨论的 Sprint 3 总体方向是：

> 根据音乐、歌词、听感或用户选择的氛围，在用户授权的本地照片中寻找适合作为音乐封面的候选图片，由用户确认后设置为封面。

建议统一后的 Overall Story：

> 作为本地音乐用户，我希望为一个音乐专辑或自定义歌单设置封面，并根据音乐信息从我授权的本地照片中获得合适的封面候选，以便让音乐库更具个性化。

需要区分两个概念：

- 音乐专辑：music album。
- 手机照片集合：photo gallery。

团队应避免同时用“相册”指代两者。

### 2.1 与现有 Vibe Discovery 的区别

仓库现有 `feat/ai-photo-discovery` 及 Discovery 后端主要处理：

```text
照片或用户意图 -> 场景和音乐标签 -> 网络曲库 -> 推荐新音乐
```

本次新需求处理：

```text
音乐专辑或歌单 -> 音乐关键词 -> 本地照片候选 -> 用户确认 -> 自定义封面
```

两者方向相反。现有 Discovery 中的照片选择、授权、图片安全处理、测试和覆盖率经验可以复用，但不能将其视为自定义封面功能已经完成。

## 3. 团队建议分工

| 成员 | 推荐职责 | 独立交付 |
| --- | --- | --- |
| zph | 专辑和歌单封面数据模型、存储、统一显示、选择、更换、删除与 fallback | 完整的手动自定义封面流程 |
| lsy | 本地照片授权、测试照片库、照片标签索引、关键词到照片候选检索 | `PhotoCandidate` 候选列表 |
| zjy | 音乐关键词生成、模型或 API 可行性、确定性规则和 fallback | 音乐到关键词 |
| 全体 | 接口合同、端到端 UI、正式 AC、共同 Review 和设备验收 | 完整封面推荐流程 |

建议依赖顺序：

```text
共同确定数据合同
-> zph 完成手动封面垂直流程
-> lsy 接入照片候选
-> zjy 接入音乐关键词
-> 联合端到端验收
```

## 4. zph 的范围

zph 负责：

1. 定义专辑和歌单的稳定封面目标身份。
2. 定义自定义封面记录及来源。
3. 提供查询、设置、更换、删除封面的内部 Kotlin API。
4. 从系统图片选择器接收图片。
5. 显示预览，并且仅在用户确认后保存。
6. 校正方向、限制尺寸、重新编码并复制到应用私有存储。
7. 统一解析自定义封面、MediaStore 封面和默认图标。
8. 在专辑及歌单相关 UI 中显示正确封面。
9. 处理文件损坏、读取失败、目标消失和存储失败。
10. 提供单元测试、Android 集成测试和覆盖率证据。

zph 不负责：

- 音乐识别模型训练、量化或部署。
- 歌词来源和歌词关键词提取。
- 照片标签生成和照片候选排序。
- 网络音乐推荐。
- 未经用户确认自动替换封面。
- 将照片上传到云端。
- 修改音乐文件或系统 MediaStore 原始封面。

## 5. 当前代码基础

当前播放器已经按音乐专辑显示封面：

- `Music` 包含 `albumId`、`artist` 和 `album`。
- `MusicViewModel` 根据 MediaStore 数据建立歌曲和专辑结构。
- `MusicContainersFragment` 显示专辑列表封面。
- `DetailsFragment` 显示专辑详情封面。
- `NowPlaying` 根据当前歌曲的 `albumId` 加载封面。
- `MediaPlayerHolder` 将专辑封面写入播放媒体元数据。
- `MusicExts.waitForCover()` 使用 MediaStore album-art URI 和 Coil 加载图片。
- 自定义歌单已有 UUID `playlistId` 和独立持久化仓库。

因此应在现有封面读取路径前增加“自定义封面优先”层，而不是在每个页面建立独立实现。

## 6. 产品规则

### 6.1 封面粒度

- 一个音乐专辑最多有一张自定义封面。
- 一个自定义歌单最多有一张自定义封面。
- 不在本阶段支持“一首歌一张自定义封面”。
- 不使用专辑名或歌单名作为唯一身份。

### 6.2 Now Playing 行为

建议保持当前播放器语义：

- 播放普通歌曲或歌单歌曲时，Now Playing 显示歌曲所属音乐专辑的封面。
- 自定义歌单封面只显示在歌单列表和歌单详情页。
- 如果团队希望播放歌单时显示歌单封面，应另写 AC 并评估通知、媒体元数据和离开歌单后的恢复逻辑，不应隐式加入本任务。

### 6.3 用户控制

- 推荐结果只是候选，不自动成为封面。
- 用户必须能够预览、确认、取消和恢复默认。
- 取消图片选择或取消确认不得产生任何写入。
- 推荐模块不可绕过 repository 直接修改 UI 或图片文件。

### 6.4 隐私与权限

- 优先使用 Android 系统图片选择器，不申请无必要的全相册读取权限。
- 手动封面功能默认完全本地运行。
- “上传封面”在产品文案和代码中应称为“选择”或“导入本地图片”，除非确实存在服务器上传。
- 图片不得因设置封面而发送给网络服务。

## 7. 建议数据模型

### 7.1 CoverTarget

专辑和歌单使用统一的封面目标抽象，但保持不同身份类型：

```kotlin
sealed interface CoverTarget {
    data class Album(
        val albumId: Long,
        val artistSnapshot: String?,
        val albumTitleSnapshot: String?
    ) : CoverTarget

    data class Playlist(
        val playlistId: String
    ) : CoverTarget
}
```

要求：

- `Album.albumId` 必须为有效的正数。
- `Playlist.playlistId` 必须符合现有歌单 UUID 规则。
- 专辑和歌单的存储 key 必须包含 target type，避免 ID 冲突。
- artist 和 album title 是校验与展示快照，不是唯一主键。
- 如果后续发现 MediaStore `albumId` 在重新索引后变化，应通过快照识别失效记录并明确提示，不得静默错误关联。

### 7.2 CustomCover

```kotlin
data class CustomCover(
    val schemaVersion: Int = 1,
    val targetType: CoverTargetType,
    val targetId: String,
    val localFileName: String,
    val source: CoverSource,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

enum class CoverTargetType {
    ALBUM,
    PLAYLIST
}

enum class CoverSource {
    USER_SELECTED,
    RECOMMENDED
}
```

不得在记录中保存：

- 图片 Base64。
- 原始相册绝对路径。
- 模型或 API 密钥。
- 云端临时下载地址。
- 不受控制的任意文件路径。

## 8. 建议内部 API

本任务需要的是应用内部 Kotlin API，不需要先建立 HTTP API。

### 8.1 Repository

```kotlin
interface CustomCoverRepository {
    fun getCover(target: CoverTarget): CoverResult<CustomCover?>

    fun setCover(
        target: CoverTarget,
        input: CoverImageInput,
        source: CoverSource
    ): CoverResult<CustomCover>

    fun removeCover(target: CoverTarget): CoverResult<Unit>

    fun hasCover(target: CoverTarget): Boolean
}
```

### 8.2 图片输入

最小版本可以只支持系统 URI：

```kotlin
sealed interface CoverImageInput {
    data class ContentUri(val uri: Uri) : CoverImageInput
}
```

若下一位成员可能返回内部文件或处理后的字节，可扩展为：

```kotlin
sealed interface CoverImageInput {
    data class ContentUri(val uri: Uri) : CoverImageInput
    data class LocalFile(val file: File) : CoverImageInput
    data class Bytes(
        val data: ByteArray,
        val mimeType: String
    ) : CoverImageInput
}
```

扩展类型必须执行相同的格式、尺寸、解码和私有存储检查，不能让调用者绕过验证。

### 8.3 结果和错误

```kotlin
sealed interface CoverResult<out T> {
    data class Success<T>(val value: T) : CoverResult<T>
    data class Failure(val error: CoverError) : CoverResult<Nothing>
}

enum class CoverError {
    INVALID_TARGET,
    UNSUPPORTED_IMAGE_TYPE,
    IMAGE_TOO_LARGE,
    IMAGE_DECODE_FAILED,
    SOURCE_NOT_READABLE,
    STORAGE_READ_FAILED,
    STORAGE_WRITE_FAILED,
    COVER_NOT_FOUND
}
```

UI 不应根据异常文本判断错误类型。

### 8.4 CoverResolver

```kotlin
interface CoverResolver {
    fun resolve(target: CoverTarget): CoverDisplaySource
}

sealed interface CoverDisplaySource {
    data class CustomFile(val file: File) : CoverDisplaySource
    data class MediaStoreAlbum(val albumId: Long) : CoverDisplaySource
    data object DefaultAlbum : CoverDisplaySource
    data object DefaultPlaylist : CoverDisplaySource
}
```

解析顺序：

```text
存在且可读取的自定义封面
-> 显示自定义封面

否则是音乐专辑
-> 尝试 MediaStore 原始封面

否则是歌单
-> 显示默认歌单封面

所有加载失败
-> 显示对应默认图标
```

## 9. 图片导入和存储规则

建议处理流程：

```text
系统图片选择器返回 URI
-> 验证 scheme 和 MIME
-> 有界读取输入
-> 验证字节数和像素数
-> 解码并处理 EXIF 方向
-> 居中裁剪或缩放为方形
-> 限制最长边，例如 1024 px
-> 重新编码为 JPEG 或 WebP
-> 写入同目录临时文件
-> 原子替换正式文件
-> 更新记录
-> 成功后清理旧文件
```

建议将处理后的图片放入应用私有目录，例如：

```text
files/custom-covers/album/<safe-id>.webp
files/custom-covers/playlist/<safe-id>.webp
```

文件名必须由应用根据目标身份生成，不能直接使用用户提供的文件名。

写入失败时：

- 旧记录和旧图片保持可用。
- 不得留下指向半写入文件的新记录。
- 临时文件应清理。
- UI 显示明确失败状态。

更换成功后才删除旧文件。删除封面只删除本应用私有副本，不删除用户相册原图。

## 10. UI 交付范围

### 10.1 专辑

必须支持：

- 专辑详情页的“设置自定义封面”。
- 系统图片选择。
- 方形预览。
- 确认与取消。
- 更换封面。
- 恢复默认封面。
- 专辑列表显示自定义封面。
- 专辑详情显示自定义封面。
- Now Playing 显示同一专辑自定义封面。

建议支持：

- 系统通知和媒体会话显示相同封面。

### 10.2 歌单

必须支持：

- 歌单列表显示封面。
- 歌单详情或编辑入口设置、更换和恢复封面。
- 未设置时显示明确的默认歌单封面。

### 10.3 统一加载

各 UI 不得自行读取 repository JSON 或拼接私有文件路径。建议提供统一扩展或 binder：

```kotlin
fun ImageView.loadCover(
    target: CoverTarget,
    resolver: CoverResolver
)
```

必须考虑 RecyclerView 复用和异步加载的旧请求覆盖问题。

## 11. 与照片推荐成员的接口

建议照片检索成员输出：

```kotlin
data class PhotoCandidate(
    val uri: Uri,
    val matchedTags: List<String>,
    val score: Double,
    val explanation: String
)
```

推荐模块负责：

- 返回候选照片。
- 计算标签和分数。
- 显示横向滚动候选。
- 展示可解释的匹配原因。
- 允许用户手动选择或取消。

zph 模块负责：

- 接收用户确认的候选 URI。
- 预览和最终确认。
- 图片验证与规范化。
- 私有存储和记录持久化。
- 统一显示、更换、删除和 fallback。

推荐成员最终只应调用：

```kotlin
repository.setCover(
    target = selectedTarget,
    input = CoverImageInput.ContentUri(candidate.uri),
    source = CoverSource.RECOMMENDED
)
```

推荐模块不应直接复制文件、写封面记录或修改各页面 ImageView。

## 12. Acceptance Criteria

### ZPH-AC1 选择与预览

Given 用户打开一个专辑或自定义歌单，  
When 用户选择设置封面并从系统选择一张有效图片，  
Then 应用显示方形封面预览，并且在用户确认前不修改当前封面。

### ZPH-AC2 保存与显示

Given 用户确认所选图片，  
When 返回列表或重新打开详情，  
Then 对应目标显示新的自定义封面，其他目标保持不变。

### ZPH-AC3 专辑播放显示一致

Given 当前播放歌曲属于已设置自定义封面的音乐专辑，  
When 用户打开 Now Playing，  
Then 显示与专辑列表和专辑详情一致的自定义封面。

### ZPH-AC4 持久化

Given 用户已经成功设置封面，  
When 强制停止并重新打开应用，  
Then 自定义封面仍然存在并能正确显示。

### ZPH-AC5 更换封面

Given 目标已经有自定义封面，  
When 用户选择并确认另一张有效图片，  
Then 新封面替换旧封面，并且旧私有副本在安全时机被清理。

### ZPH-AC6 恢复默认

Given 目标已有自定义封面，  
When 用户选择恢复默认，  
Then 自定义记录和应用私有副本被删除，专辑恢复 MediaStore 封面，歌单恢复默认歌单封面。

### ZPH-AC7 失败回退

Given 自定义封面文件损坏、丢失或不可解码，  
When UI 加载封面，  
Then 应用不崩溃，专辑回退到 MediaStore 封面或默认图标，歌单回退到默认歌单封面，并保留可诊断的失败状态。

### ZPH-AC8 取消无副作用

Given 用户取消系统选择器或预览确认，  
Then 不创建记录、不复制图片、不替换旧封面。

### ZPH-AC9 身份隔离

Given 用户只为专辑 A 或歌单 A 设置封面，  
Then 专辑 B、歌单 B 以及不同类型但文本 ID 相同的目标均不受影响。

### ZPH-AC10 推荐接入

Given 推荐模块提供一个用户确认的 `PhotoCandidate`，  
When 它通过公开 repository API 设置封面，  
Then 结果与手动选择封面的存储、显示、更换和恢复行为一致。

### ZPH-AC11 权限和本地处理

Given 用户使用手动封面功能，  
Then 应用不申请无必要的全相册读取权限，不上传图片，并且只在用户确认后读取和保存所选图片。

## 13. 测试计划

### 13.1 JVM 单元测试

至少覆盖：

- Album target key 的生成和验证。
- Playlist target key 的生成和验证。
- 专辑和歌单 ID 类型隔离。
- 新建封面记录。
- 替换已有记录。
- 删除记录。
- 重启后的序列化和反序列化。
- schema version 不支持。
- JSON 或索引损坏。
- 图片不存在。
- 图片无法读取。
- 无效 MIME。
- 超过尺寸或像素限制。
- 解码失败。
- 写入失败时保留旧封面。
- 自定义封面优先级。
- 自定义封面失败后的 MediaStore/default fallback。

文件系统测试应使用临时目录和可注入的存储边界，不能依赖开发者真实相册。

### 13.2 Android 集成测试

至少覆盖：

- 设置入口可达。
- 系统选择取消无副作用。
- 预览取消无副作用。
- 确认后列表和详情显示更新。
- Now Playing 使用同一专辑封面。
- 更换封面。
- 恢复默认封面。
- 进程重启后恢复。
- RecyclerView 复用时不会显示其他专辑或歌单封面。

测试图片应作为明确的 fixture，不使用成员的个人照片。

### 13.3 覆盖率和 CI

课程要求对团队新增或修改的可执行代码达到至少 80% line coverage。当前仓库已有 Android/Python/Web 覆盖率收集与 PR 报告机制。实现时需要：

- 将所有新增生产文件加入覆盖率 inventory。
- 运行 JVM 与必要的 Android instrumentation 测试。
- 保留现有 APK 构建。
- 确认 PR coverage summary 和完整报告包含本功能。
- 不通过排除 UI、图片处理或存储代码人为提高覆盖率。
- 无法合理自动测试的代码在 PR 中解释，并提供设备验证证据。

## 14. 推荐实施顺序

### Phase 0 范围确认

1. 与团队确认专辑和歌单均在本 Sprint 范围。
2. 确认歌单播放时 Now Playing 仍使用歌曲专辑封面。
3. 在 GitHub Story 中登记本文件 AC，并分解任务。
4. 检查 `feat/ai-photo-discovery` 和其他开放 PR，避免编辑冲突。

### Phase 1 纯 Kotlin 领域和存储

1. `CoverTarget`、`CustomCover`、错误和结果类型。
2. Repository 接口和文件存储边界。
3. 记录序列化、原子写入和恢复。
4. Repository 单元测试。

### Phase 2 图片处理

1. 有界读取和 MIME 验证。
2. EXIF 方向处理。
3. 方形裁剪/缩放和重新编码。
4. 临时文件与原子替换。
5. 图片处理单元/集成测试。

### Phase 3 CoverResolver

1. 自定义封面优先。
2. MediaStore/default fallback。
3. 统一 Coil 加载入口。
4. Resolver 和错误 fallback 测试。

### Phase 4 UI

1. 专辑详情选择、预览、更换、恢复。
2. 专辑列表和 Now Playing 接入 resolver。
3. 歌单列表和详情接入。
4. RecyclerView 和生命周期测试。

### Phase 5 推荐接入合同

1. 提供 `PhotoCandidate -> setCover()` 示例。
2. 用 fixture candidate 验证 `RECOMMENDED` 来源。
3. 编写下一位成员的接口说明。

### Phase 6 验证与 PR

1. 运行相关 JVM 测试。
2. 运行相关 Android instrumentation 测试。
3. 构建 APK。
4. 生成并检查覆盖率报告。
5. 在设备或模拟器演示完整手动流程。
6. PR 映射 ZPH-AC1 至 ZPH-AC11。

## 15. 非目标和风险控制

### 非目标

- 不实现整套 AI 封面推荐。
- 不要求音乐识别模型成功才能完成手动封面功能。
- 不修改原始照片。
- 不修改音频文件内嵌 artwork。
- 不直接写系统 MediaStore album-art 数据。
- 不做云同步。
- 不允许推荐结果自动覆盖用户选择。

### 主要风险

| 风险 | 缓解方式 |
| --- | --- |
| MediaStore albumId 重新索引后变化 | 保存 artist/album 快照，检测失效，不做模糊静默匹配 |
| 临时 URI 权限过期 | 将规范化图片复制到应用私有目录 |
| 大图导致内存问题 | 先读取 bounds，限制字节/像素并采样解码 |
| UI 各处封面不一致 | 所有页面使用同一个 CoverResolver |
| 更换失败丢失旧封面 | 临时文件写入成功后再原子替换和更新记录 |
| RecyclerView 显示错图 | 使用稳定 target key，取消/覆盖旧 Coil 请求并做集成测试 |
| 与 Discovery 分支范围混淆 | 在 Story 和 PR 明确本功能是 music -> local photo -> cover |
| 两位成员同时修改相同 UI | 先确认文件 ownership，推荐模块只通过 API 接入 |

## 16. Definition of Done

- [ ] GitHub 中有明确 User Story、supporting requirements、AC 和 task breakdown。
- [ ] 专辑和歌单使用稳定且隔离的 CoverTarget。
- [ ] 手动选择、预览、确认、更换和恢复默认全部可运行。
- [ ] 图片保存在应用私有目录，不依赖临时 URI。
- [ ] 专辑列表、详情和 Now Playing 显示一致。
- [ ] 歌单列表和详情显示一致。
- [ ] 损坏和缺失图片有明确 fallback，应用不崩溃。
- [ ] 推荐成员可通过公开 API 设置 `RECOMMENDED` 封面。
- [ ] 合理可测试的逻辑均有自动测试。
- [ ] 新增或修改可执行代码 line coverage 至少 80%。
- [ ] CI 构建 APK、运行测试、发布摘要及完整覆盖率报告。
- [ ] PR 记录未测试项、限制和设备验证结果。
- [ ] 真人队友完成代码 Review 和 AC 验收后再合并。

## 17. 下一次 Codex 对话建议开场

可将以下内容与本文件一起交给 Codex：

> 请先完整阅读 `docs/development/album-playlist-cover-foundation-handoff.md`，并检查最新 `main`、现有专辑封面加载路径、自定义歌单模型、覆盖率配置和所有相关测试。先报告你对现状、文件 ownership、潜在冲突和实施计划的理解，不要立即修改代码。确认计划后，从最新 `main` 创建独立 feature branch，实现 zph 负责的专辑/歌单自定义封面基础能力。严格遵守文档中的范围、内部 API、Acceptance Criteria、测试和至少 80% 新增/修改代码行覆盖率要求。不要实现音乐模型或照片推荐算法，不要修改无关文件。完成后运行相关 JVM/Android 测试、APK build 和覆盖率检查，并提供逐项 AC 证据、风险与未覆盖项。

## 18. 开始实施前必须确认的问题

以下问题会实质影响实现，不能由开发者静默假设：

1. Sprint 3 是否同时要求专辑封面和歌单封面，还是先交付专辑封面？
2. 播放歌单时 Now Playing 是否继续显示歌曲所属专辑封面？本文件建议是。
3. 自定义封面是否需要进入系统通知和锁屏媒体封面？
4. 图片裁剪采用固定居中裁剪，还是允许用户拖动裁剪框？
5. 图片最终格式和尺寸是否统一为 WebP 1024 x 1024？
6. 推荐模块返回本地 `Uri`、应用内部文件，还是其他形式？
7. 团队成员分别拥有和修改哪些文件，以避免并行冲突？

未确认时，建议最小交付采用：专辑和歌单均支持、Now Playing 继续使用歌曲专辑封面、通知封面跟随专辑、固定居中方形裁剪、应用私有 WebP 文件、推荐模块传入本地 `Uri`。

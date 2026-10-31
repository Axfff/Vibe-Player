# Vibe Discovery API v0.1

默认地址：`http://127.0.0.1:8765`。静态 UI：`/demo/`。机器可读 schema：`/openapi.json`。本批是单用户回环开发服务。

## 本地调用边界

先 GET `/v1/providers/status` 读取 `demoToken`，后续 `/v1/` 请求传 `X-Discovery-Token`。此随机进程令牌用于阻止其他网站发起本地操作；默认不开放 CORS。生产身份认证在后续部署设计中处理。

`GET /health` 返回服务版本与 `status=ok`，表示本地进程就绪。

`GET /v1/providers/status` 返回 `vision` 与 `catalogs[]`，逐来源字段包括：

| 字段 | 语义 |
| --- | --- |
| `mode` | 实际有效的 `unconfigured / mock / live` |
| `configured` | 本模式所需配置是否齐全 |
| `configurationIssues` | 缺失配置的稳定错误码，不含凭据 |
| `state` | `not_run / not_configured / mock_empty / mock_example / ok / error` |
| `liveTested` | 当前进程是否已获得一次符合顶层 schema 的 live 响应；后续错误仍看 state |
| `requestsMade` | 当前进程出站请求数，包括重试与链接检查 |
| `policyBasis` | 来源政策引用或视觉同意规则 |
| `previewPolicyReady` | 项目级试听开关和审核配置齐全；逐曲以 capability 为准 |
| `errorCode` | 最近一次目录请求错误，成功后清空 |

相册扩展增加 `limits.albumPhotos=500`、`limits.visionCallsRemaining` 与 `networkRoute=direct|loopback_proxy`。

同时返回 `cloudRecipient`、`visionModel`、`policyReviewedAt`、上传限制和默认市场。配置完成后 state 仍为 `not_run`，实际请求产生结果后更新。

## POST /v1/photo-intent

原始 JPEG/PNG/WebP 二进制请求体；`Content-Type` 精确匹配格式；附 `X-Cloud-Consent: true` 与本地令牌。采用原始体而非 multipart，避免上传文件落盘。浏览器先修正方向、缩放、去除元数据，后端再检查 5 MiB/2000 万像素/单帧、方向、最长边 1280 并重编码为无 EXIF JPEG。

返回：`intent`、`vision`、`catalogs`、`processedSize`。`intent` **仅**有：

- `sceneTags`：最多 8 项；nature/water/city/indoors/night/daylight/warm_colors/cool_colors/open_space/abstract/other。
- `visualMoodSuggestions`：1–3 项；calm/bright/energetic/melancholic/dreamy/focused。
- `musicDescriptors`：最多 8 项；ambient/acoustic/piano/electronic/jazz/classical/rock/pop/chillout/instrumental/folk/dance。
- `uncertainty`：low/medium/high。

mock 固定返回示例标签，`vision.mode=mock`；它与用户图片内容无关。live 请求使用用户配置的 OpenAI 模型；图中文字和元数据始终作为不可信数据。拒绝额外字段、非法枚举和长度。

## POST /v1/discover

`inputMode` 支持 manual/photo/album；相册汇总后仍使用用户确认的 intent。

```json
{
  "intent": {
    "targetMood": "focused",
    "musicTags": ["piano"],
    "text": "",
    "energy": "any",
    "instrumental": true
  },
  "confirmed": true,
  "sceneTags": [],
  "visualSuggestions": null,
  "inputMode": "manual",
  "market": "US",
  "limit": 8,
  "exclude": []
}
```

`confirmed` 必须为 true；意图由 UI 重新确认后提交。`visualSuggestions` 只是可选上下文，查询完全使用 `intent`，不合并视觉建议覆盖用户选择。`inputMode` 是调用方标注的输入方式；实际模型执行情况看 vision 状态。场景标签用于可编辑上下文。

查询优先级：非空 `text` → 去重音乐标签 → 目标氛围映射。映射为 calm→ambient、bright→pop、energetic→dance、melancholic→acoustic、dreamy→ambient、focused→instrumental。Jamendo 支持附加 speed 和 vocalinstrumental；iTunes 只有文本检索与 market。

`limit` 为 1–8，默认 8；`market` 支持 US/GB/CN/HK/JP/DE/FR/CA/AU，默认 US（测试配置）；`exclude` 最多 500 个 `TrackRef`。

返回 `sessionId/inputMode/effectiveIntent/market/tracks/requested/returned/shortageReason/vision/catalogs`。两个来源并行，交替选取并按实际艺人元数据限制每来源同艺人最多两首。官方页通过 HEAD 重定向与身份检查才进入结果。查询后最多检查 12 个候选页，经过 35 秒预算检查后停止增加下一次页检查；正在进行的单次网络请求另有 8 秒上限。结果不足保留真实数量和说明。

响应另有 `selectionIssues: [{provider, reasonCode, count}]`。`official_track_mismatch` 表示官方重定向后的 URL 丢失曲目 ID；`call_budget_exhausted` 为请求预算耗尽。来源 state=ok 表示目录请求成功，链接筛选结果单独记录。

`TrackRef = {provider: jamendo|itunes, providerTrackId: 十进制字符串}`。该 ID 只属于来源命名空间。

`CatalogTrack`：`ref/title/artist/officialUrl/artwork/tags/recommendationReason/capability`。本版 artwork 为 null；tags 来自目录元数据，推荐理由说明检索词和目录标签。iTunes 不读取 previewUrl。

## POST /v1/discover/replace

继承 discover 请求，并增加 `sessionId` 与 `replacing: TrackRef`。服务端保存本轮所有已返回 ID 和显式排除项；即使调用方漏传历史 exclude，也会过滤已返回项。每次最多返回 1 首，返回 0 时说明不足。页面将替换位置移除，用户可继续调整条件。

会话 20 分钟过期，最多 100 个，会话排除集合最多 500 项。过期返回 HTTP 410，重新发现创建新会话。新会话可以再次出现上一轮曲目；跨平台同曲按各自 provider ID 独立处理。

## GET /v1/providers/{provider}/tracks/{id}/capabilities

必须附 `?sessionId=...` 与本地令牌，只接受当前会话已见过的 TrackRef；服务端固定来源 lookup 端点，拒绝任意 URL 参数。

重新查询曲目、许可、官方页。返回 `track`、`vision` 和当前 `catalog` 状态；曲目不可用时返回 `track=null` 与 `capability=UNAVAILABLE`。

| mode | 客户端动作 |
| --- | --- |
| EXTERNAL_ONLY | 仅官方页跳转 |
| IN_APP_PREVIEW | 点击后再次刷新 capability；检查 expiresAt 和 previewUrl；显示署名/许可；直连播放 |
| UNKNOWN | 不显示试听；有效 officialUrl 允许外跳 |
| UNAVAILABLE | 停止并释放当前试听，提示用户重新发现 |

capability 包含 `reasonCode/policyEvidence/checkedAt/expiresAt/previewUrl/maxPreviewSeconds/licenseUrl/attribution`。发现响应不提供音频 URL。只有新查资格、许可及音频 HTTPS/重定向/MIME 都通过时，返回 previewUrl 和 60 秒刷新窗口。客户端最长播放 30 秒；切换、退出、后台或播放失败立即释放，不下载缓存。第三方流地址寿命由供应商控制。

## 错误与取消

HTTP 403：未同意图片处理或缺少本地令牌；413：请求/图片过大；415：图片类型、内容或动画不支持；422：严格请求 schema 错误；410：会话过期；429：本地预算；503：视觉配置未齐。上游 403/404/429/5xx、超时与 JSON 错误以受控 errorCode 返回，目录失败可与另一来源成功并存。

目录最多重试一次；Retry-After ≤2 秒才按其值等待重试，较长值直接返回显式错误。视觉不重试。关闭/取消前端请求后后端取消进行中的异步工作；已经发送的上游请求计入调用预算。

响应和日志采用受控错误码；校验失败只列字段位置和错误类型，过滤输入值和上游原始正文。


## 相册接口（2026-10-28 新增）

所有接口需要本地令牌；创建与上传还须 `X-Cloud-Consent: true`。客户端展示本批选择数量与接收者，得到同意后再按索引串行调用。图片请求沿用 photo-intent 的二进制与 MIME 规格。

| 方法与路径 | 请求 | 行为 |
| --- | --- | --- |
| POST `/v1/albums` | `{"photoCount":3}`，整数 1–500 | 检查配置与整批剩余预算，创建会话，201 |
| POST `/v1/albums/{albumId}/photos/{index}` | 原始图片，索引 0..photoCount-1 | 分析单张，返回汇总；重复索引幂等 |
| POST `/v1/albums/{albumId}/failures/{index}` | `{"errorCode":"client_decode_error"}` | 记录本地失败，不传原图 |
| GET `/v1/albums/{albumId}` | 无 | 获取计数、逐项结果及汇总 |
| DELETE `/v1/albums/{albumId}` | 无 | 取消正在进行与后续分析，返回已完成覆盖情况 |

本地失败码仅接受 client_decode_error、unsupported_image、image_byte_limit、image_pixel_limit。文件名、路径和错误原文不在请求 schema 中。

响应字段为 `albumId/total/processed/succeeded/failed/pending/state/pauseReason/intent/frequencies/items/vision/catalogs`。state 为 ready、processing、completed、completed_with_errors、paused 或 cancelled。items 只包含 index、state 与成功的 intent 或失败的 errorCode。

frequencies 的 sceneTags、visualMoodSuggestions、musicDescriptors 各为 `[{tag, photos}]`，每张成功照片对每个标签最多计一票，按数量降序与字母顺序排序。汇总上限为 8 场景、3 氛围、8 音乐标签。无成功项时 intent=null；部分覆盖时 uncertainty=high。界面展示 succeeded/total。

最多 20 个内存标签会话，闲置 20 分钟过期。创建时整批最大调用数超过剩余预算返回 429 album_budget_insufficient。执行中的授权/限流/预算错误使 state=paused，其余图片保留 pending；取消后再次提交返回 409 album_cancelled。自动重试和恢复上传均未启用，新一批重新确认。

客户端取消当前请求并调用 DELETE；上传断开后、模型调用前再次检查连接。已经发出的上游请求仍计入预算。

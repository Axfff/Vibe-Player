# 来源与试听政策核查

核查日期：2026-10-26（Asia/Shanghai）。以下是官方文档核查及代码政策，真实 API 请求状态另见测试证据。

## Jamendo

官方依据：[API Terms of Use](https://devportal.jamendo.com/api_terms_of_use)、[tracks API](https://developer.jamendo.com/v3.0/tracks)、[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)。

API 要求项目注册的 client ID。条款 3.3 将免费使用限定为非商业用途，并限制离线内容服务；4.1 要求创作者署名、Jamendo 来源署名及逐曲回链。曲目许可由平台给出的 Creative Commons 许可决定。

读取 `id/name/artist_name/shareurl/license_ccurl/musicinfo.tags/audio`。使用 `include=musicinfo+licenses` 与 `audioformat=mp31`；不请求 lyrics 或下载字段。元数据只在内存会话短期使用，音频浏览器直连。

本版试听须同时满足：live 来源、本人项目 client ID、非商业用途确认、已接受 API 条款、项目审核记录 ID、显式启用试听，以及该曲目的官方 API 许可为 CC BY 4.0。UI 展示标题、创作者、Jamendo、官方页、许可链接。曲目与音频 track ID 必须一致。

部署审核人应在启用前记录用途、账号权限、服务市场、来源条款日期和署名检查，填写 `DISCOVERY_JAMENDO_REVIEW_ID`。当前默认审核开关均关闭。每次试听重新查询曲目，检查官方页与音频 HTTPS/重定向/MIME；未满足条件则外跳或 UNKNOWN。

流域名采用官方示例中的 `prod-1.storage.jamendo.com`，不通配存储域名。新增 CDN 域名需补充官方依据并同时修改后端白名单与前端 CSP。当前策略只开放 CC BY 4.0；其他许可按逐项审查结果扩展。

## iTunes Search

官方依据：[Apple Search API](https://performance-partners.apple.com/search-api)。原 Apple 开发者文档地址在核查时跳转到此页。

使用固定 `https://itunes.apple.com/search` 与 `/lookup`，显式 `media=music`、`entity=song`、`country`。公开搜索无需 API key。官方说明有约每分钟 20 次请求限制；本地限制为每来源每分钟 18 次 HTTP 请求。

读取 `trackId/trackName/artistName/trackViewUrl/primaryGenreName`。本版固定 EXTERNAL_ONLY，不读取 `previewUrl`，封面使用本地占位图形。iTunes 文本检索的依据是搜索词和返回目录流派，代码不作音频特征判断。市场默认 US 是测试参数，用户可改。

官方推广素材有独立使用条件。本批通过曲目标题、艺人和官方页支持发现，后续若使用试听、封面或增加持久化元数据，应核对相应用途条件。

## 视觉实现

可替换 `VisionProvider`；可选 OpenAI Responses 适配器参考官方 [Images and vision](https://developers.openai.com/api/docs/guides/images-vision) 与 [Structured outputs](https://developers.openai.com/api/docs/guides/structured-outputs)。发送清理后的 JPEG、固定指令和严格 schema；`store=false`，无工具，无歌曲信息，无自动重试。模型、项目密钥、授权和调用预算均由用户配置。

`store=false` 是请求参数；接收者的数据处理规则以其官方政策和用户账户约定为准。页面逐张或按相册批次获得明确同意，默认云调用关闭。

## 当前核验状态

| 项目 | 状态 |
| --- | --- |
| 官方 API 文档与条款 | 已于上述日期查阅 |
| Jamendo 项目 client ID / 用途审核 | 待配置 |
| Jamendo 真实检索、许可字段、媒体重定向 | Pending live smoke |
| iTunes 真实目录与 lookup | 2026-10-28 已核对真实 ID/标题/艺人，见[本轮记录](album-and-live-2026-10-28.md) |
| iTunes 官方页与真实换歌端到端 | 本机官方链接转至 /cn/new，身份筛选 0 首；Pending 官方曲目页可用 |
| 视觉供应商模型 / 项目密钥 / 调用预算 | 待配置；真实调用 0 |
| 浏览器真实试听 | Pending 合格曲目与试听审核 |

30 秒是本产品上限；试听资格来自供应商条件与逐曲许可。UNKNOWN 永不开放音频；有效官方页可继续外跳。

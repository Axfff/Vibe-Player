# 第一批实施记录

日期：2026-10-26（Asia/Shanghai）。工作分支：`feat/ai-photo-discovery`。

## 基线与隔离

- 执行 `git status --short --branch`、`git worktree list --porcelain`、分支与最近提交检查后，原工作区位于 `feat/custom-playlists`，HEAD 为 `a8e1473cfc8e4d37441546384b52ad2911d365b8`，工作树干净。
- `git fetch origin main` 后核实 main 基线：`947709e4c798a103b9fcc5962172f2e85d349196`。
- 从该提交建立相邻 worktree：`C:\Users\ASUS\Documents\ChatGPT\dsa project\Vibe-Player-discovery`。
- 所有新增文件属于 `services/discovery/` 或 `docs/development/discovery/`。Sprint 2 AC1–AC9、Android 源码和公共 CI 保持原样。

## 输入核对

已读取用户会话中的 AGENTS 指令；仓库和上层目录搜索未发现额外 AGENTS.md。

已完整读取用户附件：

- `C:\Users\ASUS\AppData\Local\Temp\01-backend-and-demo-prompt.md`：本批执行依据。
- `C:\Users\ASUS\AppData\Local\Temp\02-android-integration-next.md`：用于整理下一批接口和验收边界。

已检查原仓库、main 提交树、用户指定的 `C:\Users\ASUS\Documents\Codex\Vibe-Player-custom-playlists-handoff`。该包包含自定义多歌单文档和 Git bundle。

待补充的研究输入：`06-hybrid-photo-discovery-mvp.md`、`07-provider-api-evaluation.md`、`08-hybrid-spikes-and-tests.md`、`09-adr-hybrid-playback.md`、`10-hybrid-agent-prompts.md`。这些文件到位后，应核对 D-AC 定义并补上证据映射；当前实现以用户本轮需求和第一批提示词为依据。

## 实现结构

| 模块 | 职责 |
| --- | --- |
| `discovery/models.py` | Pydantic 严格模型；身份、意图、能力及独立来源状态 |
| `vision.py` | VisionProvider 协议、明确的 mock、可选 OpenAI HTTP 实现、图片清理 |
| `catalog.py` | CatalogProvider 协议、Jamendo/iTunes 归一化与固定查询映射 |
| `policy.py` | 许可政策；iTunes 外跳、Jamendo 多条件试听、UNKNOWN 阻止播放 |
| `network.py` | 固定域名、公共 DNS、HTTPS、逐跳检查、超时/预算/限次重试 |
| `service.py` | 两来源并行查询、交替选取、同艺人限制、会话排除集合、能力刷新 |
| `app.py` | FastAPI、同源令牌、上传入口、静态页面、取消上游请求 |
| `static/` | 无框架调试页及单独的试听控制器 |
| `tests/` | 合成 schema fixture、HTTP MockTransport、Node 控制器测试 |

## 关键决定

1. 三个来源默认未配置；用户通过项目局部 `.env` 启用。无自动 live→mock 回退。
2. 视觉结果采用封闭枚举；额外曲目、URL、许可或指令字段触发 schema 错误。目录信息与音频不进入视觉调用。
3. 用户 `intent.text` 优先，其次用户音乐标签，最后按目标氛围作有限词汇展开。场景标签保留为 UI 上下文；查询以确认后的音乐意图为准。
4. 官方链接在返回结果前进行有界 HEAD 校验；重定向后的域名与曲目身份再次核对。来源不支持该检查时本次候选会被筛掉，并返回实际数量。
5. 本批 Jamendo 仅审核 CC BY 4.0 试听路径，其余许可保留外跳；缺许可是 UNKNOWN。项目用途、账号/地区适用性审核由配置与文档承载。
6. capability 刷新通过后给出 60 秒内应使用的流地址。此期限是客户端资格刷新窗口；来源 URL 的服务器有效期由 Jamendo 控制。
7. 直接浏览器音频流，服务只做 HEAD 检查。CSP 将媒体请求限定在已核查的确切流域名；无任意 URL 代理、转存或下载接口。
8. Python 默认单 worker、只监听回环地址。每进程的会话、来源状态、调用预算相互一致；部署到其他网络环境属于后续单独工作。

## 开发中修复

浏览器操作发现 `clearTimeout` 作为实例方法调用造成 `Illegal invocation`。改为包装全局定时器，新增默认接收者测试，并重新执行浏览器发现流程。还修复了选择图片后文件名被提前清空、分析结果更新后确认状态未重置，以及跨供应商 API 重定向和许可/音频 track ID 不一致的校验。

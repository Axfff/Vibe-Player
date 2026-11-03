# Vibe Discovery — local backend

Python 3.11+ / FastAPI，同源静态调试页。Windows 实测 Python 3.14.5，依赖完整锁定于 `requirements.txt`。本目录独立于 Android 与自定义多歌单。

## 启动

在仓库根目录的 PowerShell 中执行：

```powershell
Set-Location services/discovery
py -3 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn discovery.app:app --host 127.0.0.1 --port 8765 --no-access-log
```

已安装依赖时也可以执行 `./start.ps1`。打开 <http://127.0.0.1:8765/demo/>。健康检查 <http://127.0.0.1:8765/health>。关闭前台进程用 Ctrl+C。

Linux/macOS：`python3 -m venv .venv`，`.venv/bin/python -m pip install -r requirements.txt`，`.venv/bin/python -m uvicorn discovery.app:app --host 127.0.0.1 --port 8765 --no-access-log`。

## 配置

首次启动三个来源均为 `unconfigured`。复制 `.env.example` 到本目录的 `.env`，按自己的授权填写，重启服务。环境变量优先于此文件；不自动搜索其他目录的密钥。

| 目标 | 配置 |
| --- | --- |
| 无模型密钥的真实手动发现 | `DISCOVERY_ITUNES_MODE=live`；公开搜索无需 API 密钥，关键词和所选市场会发给 Apple |
| 本地照片 UI 演示 | `DISCOVERY_VISION_MODE=mock`；仅返回固定、显式标记的示例标签，云端请求为 0 |
| Jamendo 真实检索 | `DISCOVERY_JAMENDO_MODE=live` + 项目本人注册的 `DISCOVERY_JAMENDO_CLIENT_ID` |
| Jamendo 试听 | 在上一项基础上完成[用途审核](../../docs/development/discovery/provider-verification.md)，开启四个审核配置；逐曲仍须通过 CC BY 4.0 和音频校验 |
| 可选 OpenAI 照片分析 | `DISCOVERY_VISION_MODE=live`、`DISCOVERY_VISION_MODEL`、项目 `DISCOVERY_VISION_KEY`、`DISCOVERY_CLOUD_AUTHORIZED=true`、正数 `DISCOVERY_VISION_CALL_BUDGET`；页面逐图同意 |

目录 mock 模式返回明确的空列表；合成曲目 fixture 仅供测试使用。缺配置时页面展示真实状态，选择“文字 / 标签”可继续编辑目标。来源 live 出错时保持 live/error。

预算为**每个服务进程**上限：目录默认 80 个 HTTP 请求（包含重试、链接 HEAD 与重定向）；视觉默认 0 次。每来源每分钟最多 18 个出站请求。重启会重置计数。视觉模型由用户指定，单次输出最多 600 tokens，不自动重试。

### 本地代理网络

默认直连并校验公共 DNS。使用既有本地 HTTP CONNECT 代理的电脑，可以显式设置 `DISCOVERY_LOCAL_PROXY_URL=http://127.0.0.1:7897`（填写自己的本地端口）。只接受 127.0.0.1 / ::1 和端口；不接受远程代理、凭据或路径，也不自动读取系统设置。此模式由本地代理解析目标 DNS，客户端仍验证目标 HTTPS 证书，逐跳执行官方域名白名单。状态接口返回 `networkRoute=direct|loopback_proxy`。

2026-10-28 本机 iTunes 真实搜索与 ID lookup 成功；官方曲目页转到 `/cn/new`，因此页面筛选为 0 首，并显示 `official_track_mismatch`。完整证据见[本轮联调记录](../../docs/development/discovery/album-and-live-2026-10-28.md)。

## 整个相册

“整个相册”支持多选图片或选择含子目录的文件夹。页面列出本批所有文件，逐张处理，最多 500 个文件/批；超过上限要求分批选择。原图每张仍限 5 MiB、2000 万像素，支持 JPEG/PNG/WebP。目录里的其他文件和损坏图片列为失败；文件名及路径只在浏览器展示，不提交服务端。

每批单独同意，点击“分析整个相册”后才创建分析会话并上传处理后的图片。实时显示成功/失败/待处理；可停止本批并保留已成功的标签，或取消/清除。分析标签按每张照片一票统计，显示覆盖张数和场景频次，再由用户确认目标氛围。结果不足或取消时保留实际覆盖率。重复提交同一个相册索引不重复调用模型。

真实视觉模式在开始前核对整批最大调用量与剩余预算；不足返回 `album_budget_insufficient`。授权/限流错误会暂停后续图片，其他单张错误记录后继续。内存中仅保留标签与计数，闲置 20 分钟过期、最多 20 个相册会话。汇总在本地计算，不另发模型请求。

本地相册交互演示（明确 MOCK、云调用 0）：

```powershell
$env:DISCOVERY_VISION_MODE='mock'
$env:DISCOVERY_ITUNES_MODE='unconfigured'
$env:DISCOVERY_JAMENDO_MODE='unconfigured'
.\.venv\Scripts\python.exe -m uvicorn discovery.app:app --host 127.0.0.1 --port 8766 --no-access-log
```

打开 <http://127.0.0.1:8766/demo/>。MOCK 固定示例标签用于验证交互；真实照片内容分析需要授权的模型、项目密钥和预算。

## 测试

```powershell
.\.venv\Scripts\python.exe -m pytest -q
.\.venv\Scripts\python.exe -m ruff check .
.\.venv\Scripts\python.exe -m pip check
node --test tests/preview.test.mjs tests/album.test.mjs
```

Python 测试使用 `httpx.MockTransport`，无外网请求。Node 18+ 用内置测试运行器验证试听控制器。Node 不是服务启动依赖。

以下命令供完成配置并授权后的操作人手动运行。脚本只请求所选目录及官方页，最多 3 个 HTTP 请求，不发图片或请求音频：

```powershell
.\.venv\Scripts\python.exe smoke.py --provider itunes --authorized --max-http-calls 3 --market US
```

完整受限联调：默认目标 8 首、连续换歌两次（客户端 exclude 故意留空）、最终 iTunes ID lookup 与外跳资格复查；预算包括所有重试、HEAD 和重定向，失败保持 live 并以非零退出码结束。

```powershell
.\.venv\Scripts\python.exe smoke.py --provider itunes --authorized --workflow --limit 8 --max-http-calls 15 --market US
```

## 使用与存储

照片入口先本地校正方向、缩小和重新编码，再由用户点击分析。后端以原始图片请求体接收，流式限制为 5 MiB，解码上限 2000 万像素；不创建上传临时文件。图片、密钥、上游错误正文不写入日志。

用户确认的 `intent` 独立于视觉建议。目录查询使用有限映射，实际检索词及目录标签显示在推荐理由中。每来源同艺人最多两首；按 `provider + providerTrackId` 去重。结果不足直接返回实际数量。

元数据、排除集合在进程内保留最多 20 分钟、100 个会话；重启清空。照片不进入会话。外部封面使用占位图形。音频不经过服务端；浏览器通过新查 capability 直连获准的 HTTPS 流，最长 30 秒，切换/退出释放。iTunes 始终外跳。

完整资料：[接口合同](../../docs/development/discovery/api-contract.md)、[供应商核查](../../docs/development/discovery/provider-verification.md)、[交接](../../docs/development/discovery/handoff.md)。

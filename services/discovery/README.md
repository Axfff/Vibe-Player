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

## 测试

```powershell
.\.venv\Scripts\python.exe -m pytest -q
.\.venv\Scripts\python.exe -m ruff check .
.\.venv\Scripts\python.exe -m pip check
node --test tests/preview.test.mjs
```

Python 测试使用 `httpx.MockTransport`，无外网请求。Node 18+ 用内置测试运行器验证试听控制器。Node 不是服务启动依赖。

以下命令供完成配置并授权后的操作人手动运行。脚本只请求所选目录及官方页，最多 3 个 HTTP 请求，不发图片或请求音频：

```powershell
.\.venv\Scripts\python.exe smoke.py --provider itunes --authorized --max-http-calls 3 --market US
```

## 使用与存储

照片入口先本地校正方向、缩小和重新编码，再由用户点击分析。后端以原始图片请求体接收，流式限制为 5 MiB，解码上限 2000 万像素；不创建上传临时文件。图片、密钥、上游错误正文不写入日志。

用户确认的 `intent` 独立于视觉建议。目录查询使用有限映射，实际检索词及目录标签显示在推荐理由中。每来源同艺人最多两首；按 `provider + providerTrackId` 去重。结果不足直接返回实际数量。

元数据、排除集合在进程内保留最多 20 分钟、100 个会话；重启清空。照片不进入会话。外部封面使用占位图形。音频不经过服务端；浏览器通过新查 capability 直连获准的 HTTPS 流，最长 30 秒，切换/退出释放。iTunes 始终外跳。

完整资料：[接口合同](../../docs/development/discovery/api-contract.md)、[供应商核查](../../docs/development/discovery/provider-verification.md)、[交接](../../docs/development/discovery/handoff.md)。

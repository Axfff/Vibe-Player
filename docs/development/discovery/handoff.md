# Vibe Discovery 第一批交接

后端与同源调试页已实现，代码已提交，局部环境可启动。调试地址：<http://127.0.0.1:8765/demo/>。

| 项目 | 实际值 |
| --- | --- |
| Worktree | `C:\Users\ASUS\Documents\ChatGPT\dsa project\Vibe-Player-discovery` |
| 分支 | `feat/ai-photo-discovery` |
| 核实的 main 基线 | `947709e4c798a103b9fcc5962172f2e85d349196` |
| 功能提交 | `a4458fe06ba12479c946286062660e37f7706688` |
| 运行模式 | vision / Jamendo / iTunes 均为 unconfigured；默认云调用关闭 |
| 自动测试 | Python 112 passed；Node 11 passed；Ruff、pip check、JS syntax 通过 |
| 实际浏览器 | 手动、同意/取消、照片 mock、可编辑标签、目标覆盖、空结果已操作 |

## 启动与测试

本工作区已建立 `services/discovery/.venv`。新的 PowerShell 窗口执行：

```powershell
Set-Location 'C:\Users\ASUS\Documents\ChatGPT\dsa project\Vibe-Player-discovery\services\discovery'
.\.venv\Scripts\python.exe -m uvicorn discovery.app:app --host 127.0.0.1 --port 8765 --no-access-log
```

交接时 8765 已有本批实例，可直接打开页面。重新启动前结束该实例，或使用 `--port 8767`；项目 `runtime/server.pid` 记录此次实际监听进程。

```powershell
.\.venv\Scripts\python.exe -m pytest -q
node --test tests/preview.test.mjs
.\.venv\Scripts\python.exe -m ruff check discovery tests smoke.py
```

新电脑环境与配置步骤见[服务 README](../../../services/discovery/README.md)。

## 配置待办

- **iTunes**：公开 API 无需 key；在本地 `.env` 设置 `DISCOVERY_ITUNES_MODE=live` 后，可通过手动文字/标签开始检索。授权 smoke 可使用 `smoke.py`，默认最多 3 个 HTTP 请求。
- **Jamendo**：提供项目本人注册的 client ID，启用 live。试听还需非商业用途、账号/地区适用性、API 条款与署名审核记录；仅 CC BY 4.0 逐曲通过时开放。
- **视觉**：明确授权 OpenAI 模型、项目 API key、调用预算及云处理配置；界面仍逐图获得同意。没有密钥时可使用显式 mock 或手动入口。
- **研究资料**：06–10 五份文件仍待提供；随后核对 D-AC01–D-AC12 定义并映射本批证据。

## 后续验证

真实模型、Jamendo/iTunes API、浏览器真实外跳与试听：Pending。当前真实 API/模型请求清单为空（均 0 次）。Android 集成、同进程播放器互斥、音频焦点、设备回归：Pending 下一批。

Android 对接读取[API 合同](api-contract.md)和[下一批说明](android-next.md)：使用独立 TrackRef，保留手动入口和来源状态，试听前刷新 capability，以明确协调接口实现与本地播放双向互斥。

## 文档入口

[实施记录](implementation.md) · [供应商核查](provider-verification.md) · [测试证据](testing.md) · [本地 PR 草稿](pr-draft.md)。

本批仅包含两个约定目录。原 worktree 的多歌单 HEAD 在交接核对时仍为 `a8e1473cfc8e4d37441546384b52ad2911d365b8`，工作树干净；其上游跟踪状态在另一项工作中已更新。远端发布、Issue、合并与 Release 由团队后续处理。

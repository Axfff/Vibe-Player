# 验证记录

最新验证（2026-10-28）：Python **148 passed**、Node **17 passed**；相册文件夹、多选、取消、用户目标覆盖已实际操作。iTunes 真实搜索和 ID lookup 成功，官方曲目页出现地区重定向。完整计数、命令、状态见[本轮记录](album-and-live-2026-10-28.md)。

## 2026-10-26 历史验证记录


日期：2026-10-26（Asia/Shanghai）。代码提交：`a4458fe06ba12479c946286062660e37f7706688`。代码树：`8aef0944028c7bf031b8117e1ca13dd9c17c7662`。最后一轮测试后服务代码无额外修改。

环境：Windows、Python **3.14.5**、Node **v24.19.0**，服务目录局部 `.venv`。确切依赖见 `services/discovery/requirements.txt`。

## 已执行命令

在 `services/discovery/` 执行：

| 命令 | 结果 |
| --- | --- |
| `.\.venv\Scripts\python.exe -m pytest -q` | **112 passed**，6.40 秒，1 条 Starlette TestClient 的 httpx 兼容弃用提示 |
| `node --test tests/preview.test.mjs` | **11 passed**，0 failed |
| `.\.venv\Scripts\python.exe -m ruff check discovery tests smoke.py` | All checks passed |
| `.\.venv\Scripts\python.exe -m pip check` | No broken requirements found |
| `node --check static/app.js` | exit 0 |
| `git diff --cached --check` | 代码提交前通过 |

Python 的外部 HTTP 使用 MockTransport。fixture 明确使用合成标题、艺人和测试 ID，只存于测试代码；运行时 catalog mock 返回空列表。Node 测试模拟 Audio 生命周期。

## 后端/演示检查点

| 编号 | 覆盖内容 | 实际证据 |
| --- | --- | --- |
| B01 | 正常归一化、缺字段、错误类型、重复 ID | test_catalog.py |
| B02 | 空结果、真实数量、每来源艺人限制 | test_api.py |
| B03 | 403/404/429/5xx、超时、错误 JSON、Retry-After | test_network.py |
| B04 | 部分来源失败与状态隔离 | test_api.py |
| B05 | HTTPS、域名仿冒、公共 DNS、重定向及曲目身份 | test_network.py / test_boundaries.py |
| B06 | iTunes 外跳、Jamendo 多项审核与许可未知 | test_catalog.py / test_api.py |
| B07 | 用户意图覆盖、确认 gate、换歌服务端去重 | test_api.py |
| B08 | 未同意、图片格式/字节/像素、方向/EXIF、chunked 上传 | test_vision.py / test_boundaries.py |
| B09 | 模型 schema、枚举、拒绝 URL/歌曲/指令字段 | test_vision.py |
| B10 | mock/live/未配置、无回退、密钥与照片不回显 | test_vision.py / test_api.py |
| B11 | 取消上游、限次/预算、无音频代理 | test_boundaries.py / test_network.py |
| B12 | 单首、释放、30 秒、暂停预算、过期/迟到回调 | preview.test.mjs |

**D-AC01–D-AC12**：正式编号定义来自待提供的 06–10 研究文件，当前统一标记为 **Pending 定义映射**。上表提供本批可引用证据；Android 相关条目在下一批单独验收。Sprint 2 AC1–AC9 保留原状态。

## 实际浏览器操作

通过 Codex in-app browser 实际执行，使用同源 `127.0.0.1:8765` 和临时 mock 实例 `127.0.0.1:8766`：

1. 打开页面，核对 vision/Jamendo/iTunes 独立 unconfigured 状态、默认 US、0 次来源请求。
2. 未确认目标时点击发现，页面要求先选择并确认。
3. 手动输入 piano、选择平静/钢琴并确认，返回明确 `0 / 8` 和来源未配置说明。
4. 照片入口未配置时禁用分析；同意后取消，checkbox 清空。
5. 临时 mock 实例选择生成的纯色 JPEG：原始 1800×900、EXIF Orientation=6；浏览器处理后显示 **640×1280**。这是合成测试图。
6. 勾选同意并点击分析，显示常驻 MOCK 标识、固定示例标签、视觉建议平静与 high uncertainty；云端请求为 0。
7. 用户选择活力与舞曲；再次分析后保留用户已编辑选择，确认 checkbox 重置。重新确认后发现流程正常返回明确空结果。
8. 实测发现定时器 receiver 错误并修复，重新执行以上发现流程；新增 Node 回归测试。正式实例重启后页面控制台 error 列表为空。

截图：[默认配置](evidence/browser-unconfigured.png)、[mock 照片](evidence/browser-photo-mock.png)、[可编辑标签](evidence/browser-photo-tags.png)、[交接时页面](evidence/demo-ready.png)。

## Live 与设备状态

| 验证对象 | 请求数/状态 |
| --- | --- |
| OpenAI 模型 API | **0**，Pending 授权模型/项目凭据/预算 |
| Jamendo API | **0**，Pending 项目 client ID 与用途审核 |
| iTunes Search/lookup | **0**，Pending 启用 live 配置后的授权 smoke |
| 真实官方曲目页/媒体流探测 | **0**；官方开发文档浏览另行完成 |
| 浏览器真实目录卡片、外跳、真实试听 | **Pending** |
| Android 集成、音频互斥、设备测试 | **Pending 下一批** |

授权后的目录 smoke 命令见服务 README，明确限制为最多 3 个 HTTP 请求。付费模型测试需另行配置供应商、模型、项目密钥与调用预算。

## 交接时进程

正式实例已从上述代码提交重新启动，健康返回 `status=ok`。监听实测为 **127.0.0.1:8765**；临时 8766 mock 实例已结束。正式实例有效模式均为 unconfigured，出站请求计数均为 0。运行 PID 写入被忽略的 `services/discovery/runtime/server.pid`，启动日志也只保留在该局部 runtime 目录。

# Draft PR: Add isolated Vibe Discovery backend and local demo

照片发现需要把视觉意图、真实目录身份和试听资格分开处理。本改动新增可独立启动的 FastAPI 服务与 `/demo/`：用户主动选图并同意处理，编辑标签和确认目标，再从 Jamendo/iTunes 获取候选；同时支持直接使用文字和标签。

结果包含 provider + providerTrackId、标题、艺人、经过校验的官方链接和目录检索依据。默认目标 8 首，来源失败或候选不足返回实际数量。换歌保留服务端排除记录。iTunes 固定外跳；Jamendo 仅在项目审核、CC BY 4.0 逐曲依据、最新能力和流地址检查全部满足时开放最多 30 秒浏览器直连试听。

## Scope

- `services/discovery/`：局部 Python 环境配置、服务、静态 demo、接口与控制器测试。
- `docs/development/discovery/`：接口合同、政策来源、证据、Android 下一批交接。

基线 `947709e4c798a103b9fcc5962172f2e85d349196`；独立分支 `feat/ai-photo-discovery`。本地多歌单 Story/AC1–AC9、Android 与公共 CI 保持原样。

## Validation

- Python 单元/接口测试：112 passed（合成 schema fixture 与 MockTransport）。
- Node 试听控制器：11 passed。
- Ruff、pip check、JavaScript 语法检查通过。
- 实际浏览器：手动/照片入口、目标确认、mock 图片方向/尺寸处理、可编辑标签与用户覆盖、明确空结果和取消流程已操作。
- 真实模型、Jamendo/iTunes live smoke、真实音频试听：Pending 用户授权配置。
- Android 集成、音频焦点与设备回归：Pending 下一批。

研究文件 06–10 待补齐后核对 D-AC01–D-AC12 的正式定义并映射本批证据。

这是本地 PR 草稿，供团队自行审阅和发布。队友 Review、远端 CI、Issue 关联与发布状态由真实远端操作补充。

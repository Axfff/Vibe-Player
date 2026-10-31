# Draft PR: Add Vibe Discovery backend with album analysis and local demo

新增独立 FastAPI 与同源调试页，将图片产生的音乐意图、真实目录身份和试听许可分别处理。用户可以选择单图、多张图片、相册文件夹或直接输入文字；单次/本批同意后分析，编辑标签并确认目标，再查询 Jamendo/iTunes。

相册按索引逐张处理，显示成功/失败/待处理与场景频次，支持预算预检、重复索引幂等和取消。原图只在内存处理，文件名与目录路径留在客户端。目录目标 8 首，实际不足如实返回；换歌保留服务端已返回 ID。iTunes 固定外跳，Jamendo 逐曲许可与项目条件核实后才允许最多 30 秒试听。

## Scope

仅 `services/discovery/` 与 `docs/development/discovery/`。基线 `947709e4c798a103b9fcc5962172f2e85d349196`，分支 `feat/ai-photo-discovery`。Android、原播放器、多歌单 Sprint 2 AC1–AC9 与公共 CI 保持原状。

本地网络支持显式回环 HTTP CONNECT 代理，默认直连公共 DNS；目标 HTTPS、官方域名和重定向校验保留。selectionIssues 展示官方页身份与网络预算筛选原因。

## Validation

- Python 单元/接口测试 148 passed；Node 相册与试听控制器 17 passed。
- Ruff、pip check、JS syntax 和 diff whitespace 检查通过。
- 实际浏览器验证文件夹、多选、2 成功/1 失败、24 张中第 15 张后取消、单图回归和用户目标优先；视觉为显式 MOCK，云调用 0。
- iTunes 实际搜索与 ID lookup 成功，本轮服务端共 33 次受限 HTTP 请求。官方曲目页在当前网络跳到 /cn/new，严格筛选为 0 首；真实换歌端到端 Pending 官方曲目页可用。
- 真实视觉模型、Jamendo 与许可试听、Android 设备/音频互斥待对应配置与下一批验收。

详见[本轮记录](album-and-live-2026-10-28.md)。06–10 研究文件仍待补齐，以映射 D-AC01–12 正式定义。

本文件是本地 PR 草稿；团队 Review、远端 CI 与发布状态在实际发生后另行记录。

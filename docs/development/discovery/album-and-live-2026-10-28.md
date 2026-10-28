# 相册扩展与 iTunes 真实联调 · 2026-10-28

本轮用户授权小规模调用 iTunes 公开 API，并追加整个相册分析。代码提交：`4d8ce5be6d094d59739d93ac1f60e3e5d7c472ca`；代码树：`d46dd992e09b529a34f92cdb73200c790feef673`。

Worktree：`C:\Users\ASUS\Documents\ChatGPT\dsa project\Vibe-Player-discovery`。分支：`feat/ai-photo-discovery`。原始已提交 main 基线：`947709e4c798a103b9fcc5962172f2e85d349196`。沿用该独立分支，保留此前两个提交。变更只位于 `services/discovery/` 与 `docs/development/discovery/`。

## 整个功能的使用方式

选择单图、多张照片或相册文件夹；也可直接输入文字。图片先本机处理，在用户同意后逐张分析，形成场景、视觉氛围与音乐标签。相册汇总展示成功覆盖率及各标签涉及的照片数。用户可以根据自己的目标修改标签，例如把雨夜相册的视觉建议“平静”改成“活力”。

确认目标后，查询真实音乐目录，目标 8 首，按实际可用结果展示。卡片包含来源、来源 ID、标题、艺人、官方链接和检索依据；换歌排除当前会话已返回的同来源 ID。iTunes 固定外跳，Jamendo 在来源条件、项目用途审核、曲目许可与流校验都通过后提供最多 30 秒试听。

Android 下一批接入同一接口，并验证与原播放器的音频协调。DiscoveryCollection 保存来源身份及必要元数据，和本地多歌单分别维护。照片与目录音频不进入收藏数据。

## 相册实现

- 多选文件与文件夹选择支持子目录；每批最多 500 个文件，超过时要求分批选择。选中的文件逐一处理；非支持格式、损坏和超限文件在清单中标记失败。
- 每批同意后创建会话，随后按索引逐张发送；服务端不接收文件名、目录路径或 ZIP。浏览器只保留一个正在预处理的像素缓冲区。
- 每张原图限制 5 MiB / 2000 万像素，JPEG/PNG/WebP 单帧；方向校正、最长边 1280、重编码去 EXIF。后端仍执行同一图片校验，不落盘。
- 同一会话和索引重复请求复用已有结果，不重复调用模型。服务端串行处理同一相册。
- 每张照片对每个标签最多计一票。汇总使用成功图片的标签频次，无二次模型调用。部分覆盖的汇总标记 high uncertainty，展示成功/失败/待处理数。
- 开始前检查整批最大模型请求数是否在剩余预算内。授权/限流/预算错误暂停其余图片；普通单张错误记录后继续。
- 停止本批取消当前异步工作与后续上传，保留已完成标签；清除会释放浏览器选择与预览。内存标签会话闲置 20 分钟过期，上限 20 个；原图不进入会话。
- 单图入口、手动入口继续可用，用户已编辑的标签和目标优先。

## iTunes 真实验证

| 步骤 | 实际结果 | 后端出站 HTTP 数 |
| --- | --- | ---: |
| 初次直连 | 本机 DNS 返回代理虚拟地址，`private_address` 在发送前拦截 | 0 |
| 使用已存在的本地代理，US / piano，目标 8 首 | 搜索 HTTP 200；官方页重定向到 `/cn` → `/cn/new`，曲目身份丢失，筛选 0 首，smoke 非零退出 | 15 |
| CN / piano | 搜索 HTTP 200，目录空结果，smoke 非零退出 | 1 |
| 单次 US 目录元数据核对 | 40 条可归一化歌曲；保存三个样本 | 1 |
| 从实际调试页发起 US / piano | live/ok，官方页身份筛选 0/8；显示 4 次身份不匹配与预算耗尽 | 15 |
| ID lookup `1440861221` | 返回 Ariana Grande / Piano，同一 ID 与官方 URL | 1 |
| **合计** | 单次过程均有限额；未请求音频 | **33** |

浏览器另实际导航一次 Apple 官方曲目链接，落点为 `https://music.apple.com/cn/new`。这次导航及页面资源不计入服务端 HTTP 计数。

真实目录样本：

| 来源 ID | 标题 | 艺人 |
| --- | --- | --- |
| 1440861221 | Piano | Ariana Grande |
| 844514243 | Piano (Emotional Music) | Solenne Whisper |
| 1065946792 | Calm Piano Music for Sleeping Baby | Baby Lullaby Academy |

第一个样本的 [API 返回官方链接](https://music.apple.com/us/album/piano/1440860819?i=1440861221&uo=4) 已由 lookup 复核。本机实际访问的地区重定向记录如上。当前官方页可用性阻止曲目进入结果；真实发现→两次换歌→capability 的完整 smoke 状态为 **Pending 官方曲目页可用**。合成 fixture 的整个流程及连续换歌去重通过。

本轮改进包括：显式本地代理配置、链接筛选原因 `selectionIssues`、包含连续两次换歌及 lookup 的受限 smoke。失败保留 live 状态与实际错误，保持非零退出码。

网络配置说明：本机已有 `127.0.0.1:7897` 代理，在局部 `.env` 中显式选择。默认仍为直连公共 DNS；代理模式由本地代理解析目标 DNS，客户端保留 HTTPS 证书、用途域名白名单、每跳验证及预算。代理设置只接受回环 IP 与端口，不支持账号密码、远程代理或任意 URL 转发端点。系统网络设置保持原样。

技术依据：[Apple Search API](https://performance-partners.apple.com/search-api)（本轮重新查阅）；[HTTPX 代理与 CONNECT](https://www.python-httpx.org/advanced/proxies/)（本轮查阅）。iTunes 未读取或播放 previewUrl。

## 已执行验证

环境：Windows、Python 3.14.5、Node v24.19.0，沿用服务局部 `.venv`。

| 命令（在 services/discovery） | 结果 |
| --- | --- |
| `.\.venv\Scripts\python.exe -m pytest -q` | **148 passed**，9.68 秒；1 条 Starlette/httpx 兼容弃用提示 |
| `node --test tests/preview.test.mjs tests/album.test.mjs` | **17 passed** |
| `.\.venv\Scripts\python.exe -m ruff check discovery tests smoke.py` | 通过 |
| `.\.venv\Scripts\python.exe -m pip check` | 通过 |
| `node --check static/app.js` | 通过 |
| `git diff --cached --check` | 代码提交前通过 |

新增自动测试涵盖：整批同意、预算预检、字节/像素上限、上传断开、逐张失败、重复索引幂等、模型错误暂停、处理中取消、会话过期、按照片频次汇总、用户目标覆盖、代理输入约束、重定向限制，以及 live smoke 的身份与预算失败判定。自动测试的外网全部由 MockTransport 替代，实际请求仅来自上表显式 live 操作。

实际浏览器操作：

1. 文件夹选择三份合成文件：两张有效图片、一份损坏 JPEG。未同意时按钮禁用；分析后显示成功 2、失败 1、待处理 0，MOCK 云请求 0。
2. 预先选择“活力 / 舞曲”，分析建议为“平静”后，用户选择保留；确认目标后进入发现接口。
3. 24 张合成图批次：停止时成功 15、待处理 9，剩余图片未继续上传。
4. 多选两张有效图：成功 2/2；单图方向与本地预览、分析路径回归通过。
5. 真实 iTunes 发现按钮：显示 live、15 次请求、0/8 与明确官方页面筛选诊断。

截图：[最终相册汇总](evidence/album-final-2026-10-28.png)、[相册部分成功](evidence/album-mock-2026-10-28.png)、[中途停止](evidence/album-cancel-2026-10-28.png)、[iTunes 真实链路诊断](evidence/itunes-live-filtered-2026-10-28.png)。结构化证据见 [JSON](evidence/album-live-results-2026-10-28.json)。

## 运行与后续配置

```powershell
Set-Location 'C:\Users\ASUS\Documents\ChatGPT\dsa project\Vibe-Player-discovery\services\discovery'
.\.venv\Scripts\python.exe -m uvicorn discovery.app:app --host 127.0.0.1 --port 8765 --no-access-log
```

主调试页：<http://127.0.0.1:8765/demo/>。局部 `.env` 已开启 iTunes live、15 次进程预算及本地代理；vision/Jamendo 为 unconfigured。独立相册 MOCK 演示：<http://127.0.0.1:8766/demo/>；启动方式见 README。两实例仅回环监听，进程 ID 写入忽略的 runtime 文件。服务重启重置该进程计数，历史验证数量保留在本报告。

真实视觉待授权模型、项目 key、预算和云端处理设置；Jamendo 待 client ID 与试听用途审核；两者本轮实际 API 请求均为 0。真实相册内容分析、合格歌曲试听、Android 对接与音频互斥仍待各自实际验证。

06–10 研究文件仍待提供；D-AC01–12 定义映射保持 Pending。原 01/02 临时附件已在上一批读取，本轮复查时原 Temp 路径已不存在，实施记录沿用上一批摘录。原 Sprint 2 AC1–AC9 保持原状态。

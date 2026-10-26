# Android 下一批接口交接

本批交付 FastAPI 与桌面浏览器 demo。Android 集成、构建、音频互斥与设备验收状态：Pending。

## 对接顺序

1. 在独立工作区读取本批实际提交、API 合同和供应商状态，确认下一批集成基线。保留 Sprint 2 AC1–AC9。
2. 建立独立 discovery 包与三阶段状态：选图、编辑/确认目标、结果。继续现有 Kotlin/XML/ViewBinding 结构。
3. 使用单图 Photo Picker 与兼容回退。选择后仅本机预处理；逐图获得云处理同意后调用 photo-intent，取消时结束请求。
4. 查询 providers/status 获取实际来源与本地开发令牌；所有 discover 请求提交用户确认的 intent。Mock、live、unconfigured 分别展示。
5. TrackRef 始终为 provider + providerTrackId；单独定义 DTO/Collection，存必要元数据及官方链接。
6. 点击试听前重新查 capabilities。对 UNKNOWN、UNAVAILABLE、过期或无 URL 状态执行相应 UI；合格时直接使用许可来源流。
7. 实现单独 PreviewController 与明确的播放协调接口：开启试听前暂停本地播放；本地播放开始时停止试听；停止、切换、退出和音频焦点变化使旧回调失效；试听结束保持停止状态。
8. 由集成负责人协调最小入口、Manifest/网络依赖改动。开发连接方案单独核查 Android 官方网络文档，限制 debug 的本机明文策略；服务继续默认回环监听。

## 合同细节

- `/v1/photo-intent` 为原始图片二进制 POST，不是 multipart；精确 Content-Type、X-Cloud-Consent 和 X-Discovery-Token。
- 用户可以直接走 `/v1/discover` 手动入口；模型不可用时本地播放器继续工作。
- 替换带 sessionId/replacing/exclude，服务维护已返回 ID；HTTP 410 要重新开始发现。
- capability 的 expiresAt 是客户端 60 秒资格刷新窗口。不得永久保存 previewUrl；本版没有音频代理。
- 使用响应的 `recommendationReason` 和元数据来源；不要自行推断已听音频、真实情绪或歌曲授权。
- 页面市场设置是用户选择，默认 US 为测试配置；不读取 IP/GPS 推断位置。
- 本地令牌是电脑调试机制。远程服务身份、TLS、限额和多用户部署应在对应任务中明确后实现。

## Android 必验项目

选图/取消、旋转、重复点击与过期响应、拒绝云分析、无网与部分来源失败、官方外跳、能力变化、30 秒上限、试听与原播放器双向互斥、迟到准备回调、收藏重启恢复、原本地多歌单回归。设备证据单独记录。

本批浏览器和控制器测试是下一批的参考输入。Android 验收记录由下一批的实际设备操作产生。

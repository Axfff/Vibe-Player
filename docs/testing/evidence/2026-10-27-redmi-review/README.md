# REDMI #13 / #15 设备复测证据

执行日期：2026-10-27（Asia/Shanghai）。[汇总报告](../../playlist-redmi-review-13-15.md) · [设备与版本](environment.json) · [来源校验](provenance.json)。

- `issue13/`：搜索、歌单刷新、清空搜索与 Shuffle 实际播放。基线使用结构化观察；后续阶段包含截图和 UI XML。
- `issue15/`：Title/Filename 双向切换、保存顺序、操作澄清及最后的连续 C → B → A 复测。
- 原始阶段记录中的 Pending 保留当时进度。最终结果分别见 Issue 13 的 `playback-observation.json` 和 Issue 15 的 `continuous-run-complete-observation.json`，两项均 Pass。
- 公开文本附件统一为 LF 换行；`provenance.json` 分别记录本地源文件与公开附件的 SHA-256，截图保持原始字节。
- Issue 15 最后一轮的即时名称更新、声音连续与中间 B 曲目由操作者确认；最终 A/PAUSED 与保存顺序由 ADB 留证。`order-confirmed-ui.png` 为此前重试阶段的截图。
- 真机测试由 CroissantXinny 实际操作，Codex 整理记录；真人代码审阅结论和正式 AC 签收继续由 #17 跟踪。

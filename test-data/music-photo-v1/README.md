# 音乐 → 多张照片排序 → 用户选封面：试验数据 v1

本批实际包含 **20 首完整 MP3、33 张唯一 JPEG、80 个候选引用**。四个歌单各五首不同歌曲；四个候选库各 20 张、不在组内重复，按固定种子混排。媒体共 **118,158,915 bytes（112.685 MiB）**；另外包含四张派生 contact sheet 和说明/证据。图库不是 80 张全球唯一照片。

这是 #40 / #41 的试验输入，不代表推荐功能完成。**APP_PLAYLISTS_PENDING、PHOTO_IMPORT_PENDING；推荐实跑 Blocked。** 本任务没有运行中的授权设备，没有在 APP 中创建歌单、导入照片或进行播放/Next/重启验收。全部标签是 agent 初选的 **provisional**，未冒充真人试听或 ground truth。

## 直接浏览混合照片库

HTML 可本地直接打开；GitHub 页面可直接浏览 JPEG contact sheet。盲版和答案版分目录，顺序完全一致。

| 歌单 / 中性案例 | 混排盲版 | Contact sheet | 独立评价答案页 |
|---|---|---|---|
| 明亮轻快 / case_01 | [bright gallery](galleries/bright.html) | [20 图预览](galleries/bright.jpg) | [provisional 标签](evaluation/bright.html) |
| 安静舒缓 / case_02 | [calm gallery](galleries/calm.html) | [20 图预览](galleries/calm.jpg) | [provisional 标签](evaluation/calm.html) |
| 忧伤沉静 / case_03 | [melancholic gallery](galleries/melancholic.html) | [20 图预览](galleries/melancholic.jpg) | [provisional 标签](evaluation/melancholic.html) |
| 激昂有力 / case_04 | [energetic gallery](galleries/energetic.html) | [20 图预览](galleries/energetic.jpg) | [provisional 标签](evaluation/energetic.html) |

每组比例为 **8 明显匹配 / 4 弱但可接受 / 4 困难干扰 / 4 不匹配**。困难干扰保留相近水体、天空、山景、植物等表面特征，同时在动作强度、场景意义或构图上与音乐不同。不能只看颜色。33 个独立文件被引用 80 次，额外复用引用 47 次；逐图使用次数在 [验证摘要](evidence/validation-summary.json)。四张 contact sheet 是这些照片的拼图预览，不额外计入唯一照片。

## 固定音乐顺序与代表曲

所有歌曲作者均为 **Kevin MacLeod**，来自 Incompetech 官方 CC 曲目与官方下载，许可 **CC BY 4.0**。完整 MP3 字节保留，未剪辑、转码或清除原 ID3/专辑/内嵌封面。`source_album` 是官方目录分类，`embedded_album` 是实际 TALB，二者不混同。下列时长来自完整 PCM 解码，显示到秒；精确值见 manifest。

分组理由依据作者描述、公开标签和乐器信息；未声称真人听过。大部分为纯音乐，At Rest / Hitman 有声乐或合唱织体。音乐全部来自同一作者，属于受控首版选材，有作者/风格偏差。

| 分组 | 顺序 | 实际曲名 | 作者 | 时长 | 初选理由 / 单曲差异 |
|---|---:|---|---|---|---|
| 明亮轻快 | 1 | [Carefree](https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1400037) ★ 代表曲 | Kevin MacLeod | 3:25 | 尤克里里与轻打击，作者标签 Bouncy/Bright/Uplifting；代表曲，偏轻松而非强烈运动。 |
| 明亮轻快 | 2 | [Monkeys Spinning Monkeys](https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1400011) | Kevin MacLeod | 2:05 | 木管与拨弦的幽默、跳跃感；比代表曲更俏皮，不能等同于所有欢快场景。 |
| 明亮轻快 | 3 | [Fluffing a Duck](https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100768) | Kevin MacLeod | 1:07 | 短小滑稽的管弦与打击；活泼、卡通感较强。 |
| 明亮轻快 | 4 | [Happy Alley](https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100482) | Kevin MacLeod | 1:20 | 作者标签 Bouncy/Bright/Grooving/Uplifting，节奏轻快。 |
| 明亮轻快 | 5 | [Life of Riley](https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1400054) | Kevin MacLeod | 3:55 | 明亮、轻松、上扬的吉他风格；比本组最快曲目更舒展。 |
| 安静舒缓 | 1 | [Thinking of You](https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100637) ★ 代表曲 | Kevin MacLeod | 1:50 | 柔和电钢琴，作者标签 Calming/Relaxed；代表曲。 |
| 安静舒缓 | 2 | [Clear Waters](https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100290) | Kevin MacLeod | 2:08 | 轻盈简洁的钢琴独奏，作者标签 Calming/Relaxed。 |
| 安静舒缓 | 3 | [Winter Reflections](https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100580) | Kevin MacLeod | 2:22 | 平静合成器织体；音色与本组钢琴不同，不能假定逐图适配完全相同。 |
| 安静舒缓 | 4 | [Reminiscing](https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100097) | Kevin MacLeod | 2:32 | 简洁、以五声音阶为主的慢钢琴；怀旧但作者未以悲伤为主标签。 |
| 安静舒缓 | 5 | [Midsummer Sky](https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100158) | Kevin MacLeod | 1:54 | New-age 钢琴，Calming/Relaxed/Uplifting；比代表曲略带希望感。 |
| 忧伤沉静 | 1 | [Heartbreaking](https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100208) ★ 代表曲 | Kevin MacLeod | 1:36 | 作者以失望与失落描述钢琴曲，Calming/Somber；代表曲。 |
| 忧伤沉静 | 2 | [At Rest](https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100748) | Kevin MacLeod | 3:27 | 钢琴、弦乐与无歌词声乐织体；作者描述悲伤中带安宁与释然，不是纯悲痛。 |
| 忧伤沉静 | 3 | [Blue Paint](https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100566) | Kevin MacLeod | 2:11 | 围绕反复动机的安静钢琴，Calming/Relaxed/Somber；变化在结尾，不应由开头推断整曲。 |
| 忧伤沉静 | 4 | [Memory Lane](https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100485) | Kevin MacLeod | 1:51 | 作者说明适合内省片刻的安静钢琴，3/4 拍；忧郁而非只因慢速归组。 |
| 忧伤沉静 | 5 | [Feather Waltz](https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100658) | Kevin MacLeod | 1:30 | 钢琴华尔兹，Calming/Relaxed/Somber；有摇摆感，与代表曲不同。 |
| 激昂有力 | 1 | [Volatile Reaction](https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1400039) ★ 代表曲 | Kevin MacLeod | 2:45 | 7/4 拍、强铜管和重打击，Action/Aggressive/Intense；代表曲，偏紧张而非欢乐。 |
| 激昂有力 | 2 | [Five Armies](https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100875) | Kevin MacLeod | 2:36 | 大编制逐渐推进，Action/Driving/Epic/Intense；开头只代表构建阶段，不代表结尾高潮。 |
| 激昂有力 | 3 | [Hitman](https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1300013) | Kevin MacLeod | 3:21 | 低音、弦乐、合唱与打击，Dark/Driving/Epic/Intense；76 BPM 也可有高能量。 |
| 激昂有力 | 4 | [Chase Pulse](https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100383) | Kevin MacLeod | 1:57 | 低音区持续脉冲，Driving/Mysterious；紧迫感来自持续节奏，不单看速度。 |
| 激昂有力 | 5 | [Crusade](https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1100884) | Kevin MacLeod | 3:19 | 铜管与打击的行进感，作者说明对旅行背景而言偏活跃；后半铜管段不在 30 秒窗口内。 |

有序清单：[bright](playlists/bright.json)、[calm](playlists/calm.json)、[melancholic](playlists/melancholic.json)、[energetic](playlists/energetic.json)。同目录提供辅助 M3U8；清单不等于 APP 已建歌单。

## 真实代码边界与输入隔离

核查 main `844caf7cd1cfb3348cdd130136763528c3274573`、Issue #40/#41、PR #46 `4776e4e1c5ab0ee56aa7931f53d1177690a2d2a2` 和封面分支 `25849a42feac0b631911909830162b3d807a5b94`。见 [仓库审计](evidence/repository-audit.json)。

PR #46 的 MoodAnalyzer 默认只解码单曲**开头最多 30 秒**，跳过接近静音的 patch，并丢弃不完整 patch。全曲解码这里只检查文件完整性，不表示模型分析整首。五首聚合成歌单情绪的功能尚不存在；先使用每组第一首代表曲。开头实际 PCM 非静音，是否已足够代表风格仍需人工试听。Five Armies 的渐进构建、Crusade 后段铜管、Blue Paint 末段变化不能由 30 秒窗口代表。

推荐器只可读取 `candidates/case_01.json`–`case_04.json`（中性 case ID、audio ID/path、photo ID/path）和对应媒体字节，或导出目录中各 case 的 `input.json`。**不可给推荐器 evaluation、manifest、来源描述、预览页、歌单情绪名称或预期标签。** 使用封闭的单 case 目录可以避免误读操作员材料；原 MP3 自带的真实曲名/专辑信息按要求保留，不额外写入答案。

[evaluation/freeze.json](evaluation/freeze.json) 在任何排序前锁定候选、初始标签、歌单、协议与 manifest；之后改标签须另立版本并记录理由/审核者/时间，不能看结果后修改。其余 16 首记录 [单曲差异](evaluation/per-track-overrides.csv)，尚未逐图独立标注，不假定整个歌单每首都同样适配。

当前 main 没有音乐到照片内容排序实现；PR #46 的 caption probe 使用人指定 vibes，不能拿来充当照片推荐实跑。没有执行模型排名、没有 Top-5、没有实测准确率。四组随机抽取五张的 Precision@5 数学期望均为 **12/20 = 0.60**，困难干扰数量期望 **1.0**，这不是模型成绩。真实实现可用后，将带实际源码 SHA 的结果交给 `evaluate_rankings.py`，按相关度 >= 2 计算 P@5。

## 安装与离线验证

从仓库根运行。建议 Python 3.11+，使用独立 venv；依赖只属于素材工具，不改变 Android/后端依赖或公共 CI。下面命令在 PowerShell 中运行；其他系统改用 venv/bin/python。

```powershell
python -m venv ../vibe-fixture-venv
& ../vibe-fixture-venv/Scripts/python.exe -m pip install -r scripts/fixtures/requirements.txt
& ../vibe-fixture-venv/Scripts/python.exe -m unittest discover -s scripts/fixtures/tests -v
& ../vibe-fixture-venv/Scripts/python.exe scripts/fixtures/validate_music_photo_fixtures.py --output ../fixture-validation.json
& ../vibe-fixture-venv/Scripts/python.exe scripts/fixtures/probe_audio_fixtures.py --ffprobe ffprobe --output ../fixture-ffprobe.json
```

`ffprobe` 使用你已安装的 FFmpeg 工具；脚本不会下载或安装可执行文件。本次实际使用 ffprobe 6.1.1 和 PyAV 完整解码，版本、二进制来源/哈希见 [验证记录](evidence/validation.md)。验证无须网络；解码坏图/坏音频、hash/大小/引用/比例/许可/冻结/图库链接/包校验和不一致均非零退出，不吞失败。输出报告放包外，避免改变已锁定校验和。

每项原始和测试副本 SHA-256 位于 manifest；整个包的逐文件校验和为 [checksums.sha256](checksums.sha256)。包内 .gitattributes 禁止换行自动转换，确保 Windows/Linux checkout 字节一致。不要在本版本重写 freeze 或校验和掩盖变动。

## 恢复下载与导出

正常 clone 已有实际媒体。缺文件时，可用锁定下载器重建；不得用其跳过许可复核。只接受 manifest 原始哈希，来源变化会失败。保留包外 `.part` 以便续传；HTTP 200 忽略 Range 时从头写缓存；403 停止，不绕过。完整性检查失败绝不安装为正式素材。

```powershell
& ../vibe-fixture-venv/Scripts/python.exe scripts/fixtures/download_music_photo_fixtures.py --cache ../fixture-download-cache
& ../vibe-fixture-venv/Scripts/python.exe scripts/fixtures/materialize_music_photo_fixtures.py --output ../music-photo-export
```

下载可加 `--asset img_0039` 选择锁定 ID。导出在仓库外按 case_01…case_04 创建四个 audio/ 和四个混排 images/ 文件夹，每组五首音频、20 张照片，另有中性 input.json 与有序 M3U8。额外副本只在外部导出，不重复提交。再次导出相同内容不改文件；已有不同内容则报错保留用户文件。署名、manifest、许可证据在导出顶层独立保存，禁止传给推理器。

目前 APP 无 JSON/M3U 歌单导入；在设备上需正常索引后通过现有 UI 依次添加五首。完整步骤及实际 Pending 状态见 [设备导入记录](evidence/device-import.md)。不伪造 SharedPreferences/MediaStore ID，不清理真实手机，不卸载用户应用。

## 来源、验证与待完成

[ATTRIBUTION.md](ATTRIBUTION.md) 给出全部 53 项标题、作者、作品页、下载 URL、许可版本/链接、必要署名、改动和两套 SHA-256；[逐项许可证据](evidence/licenses/) 保留核验元数据，日期 2026-11-07。音乐 20 首 CC BY 4.0；照片 31 CC0 1.0、2 CC BY 4.0。第三方素材没有改成仓库的代码许可，参见 [LICENSES.md](LICENSES.md)。

图片统一 RGB/JPEG，最长边不超过 1280，不放大；去除 EXIF/IPTC/XMP/GPS，以随附记录保留署名。img_0029/0030/0039 有明确裁剪坐标，去除可见面部/前景人物。历史白水运动照片保留原照片颗粒，不靠损坏或低分辨率设置干扰项。四类照片均使用相同处理规则。

[实际验证记录](evidence/validation.md)、[全曲解码](evidence/audio-decode.json)、[ffprobe 结果](evidence/audio-ffprobe.json)、[最终结构/解码摘要](evidence/validation-summary.json)、[标签复核状态](evaluation/review-status.md)。

未完成：真人试听和 80 条 provisional 标注复核；设备导入、UI 歌单数量/顺序、播放/Next/同安装重启保存、系统选图器访问；真实照片推荐 Top-5 实跑。以上分别为 Pending / Blocked，PR 保持 Draft，不关闭正式 Story。

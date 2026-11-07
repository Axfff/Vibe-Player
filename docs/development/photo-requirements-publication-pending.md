# Requirements publication pending

GitHub issue #41 remains the formal entry. Updating it through the connected GitHub integration returned HTTP 403 `Resource not accessible by integration` on 2026-11-07. No alternate credential was used to bypass that denial. The following is a pending publication payload, not a second accepted specification. A repository-authorized maintainer must apply it to #41; human acceptance is not claimed.

## User story

As a listener, I want the app to recommend photos that match my music, so that I can quickly find a suitable album cover.

## Scope

Use the music as the query and recommend photos from the user's local collection first. Use lyrics or the sound/mood of the music as the matching signal. The user makes the final choice; a recommendation does not change the cover automatically.

## Acceptance criteria

- [ ] From an album's cover-selection flow, the user can request recommendations for the relevant music and see matching local photos in a scrollable list.
- [ ] Recommendations use a documented signal from the music (lyrics or sound/mood) and photo content; their ordering is based on that match rather than a random list. The screen indicates the signal used without claiming information that was not analyzed.
- [ ] The user can preview and apply a recommended photo through the cover flow in #40, or cancel without changing the current cover.
- [ ] If there are no accessible photos, no usable music signal, no suitable results, or matching fails, the app explains the situation and still lets the user choose a local photo manually when available.
- [ ] Photos stay on the device unless the user is clearly told what will be sent and explicitly agrees. Refusal or cancellation sends nothing.
- [ ] The team records a small, repeatable evaluation with example music and photos, expected matches, actual recommendations, and limitations. Results and the chosen matching approach are available for review; no unagreed accuracy target is assumed.

## Implementation tasks

- [ ] Decide and document whether to use lyrics or sound/mood, how photos are matched, and how missing inputs are handled.
- [ ] Implement the matching and ranking of accessible local photos.
- [ ] Connect recommendations to the scrollable selection and cover flow in #40.
- [ ] Add focused tests for ranking and failure states; run the agreed examples and an Android device or emulator flow.
- [ ] Link the implementation pull request and evaluation results to this issue.

## Verification

Check the criteria with matching and non-matching examples, missing inputs, denied access, cancellation, and failed matching. Record the tested build, environment, results, and any limits. Leave the criteria unchecked until verified.

## Priority, dependencies, and open decisions

Release 2 feature; depends on the basic cover flow in #40. The matching method and evaluation examples need team agreement. The shared note mentions “before Sunday?” as tentative; confirm the date before treating it as a deadline. Sprint assignment is for the team to decide. Related: #36 uses a photo to find music, while this story uses music to find a cover photo.

---

## 用户故事

作为听歌的人，我希望应用推荐与音乐相符的照片，以便快速找到合适的专辑封面。

## 范围

以音乐作为查询，优先从用户的本地相册中推荐照片。使用歌词或音乐的声音／氛围作为匹配依据。由用户最终选择；推荐不会自动更改封面。

## 验收标准

- [ ] 用户可以在专辑封面选择流程中，针对相应音乐请求推荐，并在可滚动的列表中查看匹配的本地照片。
- [ ] 推荐使用音乐的已记录信号（歌词或声音／氛围）以及照片内容；排序基于匹配程度，而非随机列表。界面说明使用了什么信号，不声称分析过实际未分析的信息。
- [ ] 用户可以通过 #40 的封面流程预览并应用推荐照片，也可以取消而不更改当前封面。
- [ ] 如果没有可访问的照片、没有可用的音乐信号、没有合适结果或匹配失败，应用会说明情况；有可用照片时仍允许用户手动选择。
- [ ] 照片保留在设备上，除非用户被清楚告知将发送什么并明确同意。拒绝或取消时不发送照片。
- [ ] 团队记录一组可重复的评估，包括音乐和照片示例、预期匹配、实际推荐及局限，并提供匹配方法和结果供审阅；不预设未经商定的准确率目标。

## 实现任务

- [ ] 决定并记录使用歌词还是声音／氛围、照片匹配方式及输入缺失时的处理方式。
- [ ] 实现对可访问本地照片的匹配和排序。
- [ ] 将推荐连接到 #40 的可滚动照片选择和封面流程。
- [ ] 添加匹配排序和失败状态的针对性测试；运行约定示例并在 Android 真机或模拟器上验证流程。
- [ ] 将实现该功能的 Pull Request 和评估结果关联到本 Issue。

## 验证

使用匹配和不匹配的示例检查各项标准，并测试输入缺失、拒绝访问、取消和匹配失败。记录测试构建、环境、结果和限制；验证前不要勾选验收项。

## 优先级、依赖与待定事项

Release 2 功能，依赖 #40 的基本封面流程。匹配方法和评估示例需要团队商定。共享笔记中“周日前？”是暂定说法，需要确认具体日期后才能作为截止时间。具体 Sprint 由团队决定。相关 Issue：#36 是通过照片查找音乐；本故事是通过音乐查找封面照片。


---
## Scoped photos extension / 目标照片范围扩展（2026-11-07）

作为本地音乐用户，我希望为不同歌单或音乐专辑分别授权照片来源，并依据代表曲与照片内容获得候选，最终自行预览、裁剪和确认封面。本 Issue 是此增量 Story/AC 的唯一正式维护入口，关联 #40；方向与 #36 相反。原中英需求和历史保留。

三个阶段全部保留：① 来源与范围，② 真实推理/匹配/封面，③ 系统搜索增强/回退。规划对应建议：阶段一、二 Sprint 3；阶段三 Sprint 4，**待团队确认**；当前正式 Sprint、人选和日期不编造。沿用 handoff 的 zph 封面、lsy 照片、zjy 音乐职责，本任务负责集成，不重署他人贡献。

- [ ] **P-AC01**：A/B 分别添加多选照片与目录，查看来源与有效候选。
- [ ] **P-AC02**：稳定的目标类型/ID/专辑快照隔离同名目标，推荐无跨目标泄露。
- [ ] **P-AC03**：改名、Activity/进程/设备重启保持合法绑定，重验持久授权。
- [ ] **P-AC04**：取消选择、确认、重新授权均不清空原绑定。
- [ ] **P-AC05**：共享来源移除只影响当前目标；删除不删除原图、不误释放他人 grant。
- [ ] **P-AC06**：失效、离线、删除、拒绝、坏图明确显示，不扫描未经授权图库。
- [ ] **R-AC01**：真实 MoodAnalyzer 或明确标识的用户氛围，展示代表曲与实际片段。
- [ ] **R-AC02**：真实图像内容特征、可解释确定性规则；不读文件名/答案/预期分组。
- [ ] **R-AC03**：先限定当前可读范围，再提取/缓存/排序；确定性去重/并列/Top-5/无匹配。
- [ ] **R-AC04**：现有预览/裁剪/封面仓库，确认后 RECOMMENDED；取消/失败保留封面。
- [ ] **R-AC05**：target/request/revision 防串用，保存前重新校验目标、授权与内容。
- [ ] **R-AC06**：复用四组冻结混合素材，真实音频/图像推理与 Top-5、耗时、失败及 provisional 指标留证。
- [ ] **S-AC01**：查看/修改建议词，披露交给系统及媒体提供方，主动点击后请求。
- [ ] **S-AC02**：核实公开 API/Extension；不支持、未知、忽略、无结果、启动失败可回退普通多选/目录并保留词。
- [ ] **S-AC03**：仅接受最终选中 URI，经确认加入发起目标，取消不变更。
- [ ] **S-AC04**：普通选图和真实查询高亮分别留证，无支持设备保持 Pending/Blocked。
- [ ] **Q-AC01**：端侧分析不上传音频/照片/特征；不复用 Discovery 上传同意。
- [ ] **Q-AC02**：播放、多歌单、手动封面回归；APK、完整报告、含 Release 1 新增/修改行覆盖率 ≥80%。

状态独立报告 IMPLEMENTED / AUTOMATED_TESTED / DEVICE_VERIFIED / HUMAN_REVIEW_PENDING / BLOCKED；模拟数据不能证明真实模型通过。不提高 minSdk、不新增广泛图片权限、不上传、不改 release tag。新增私有来源记录采用 noBackup；原图、特征缓存和最终封面分开。真人 Review 与验收仍待团队完成。

Student ID (CroissantXinny): 50027204


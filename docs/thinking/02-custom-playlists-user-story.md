# [Sprint 2] Create and manage multiple local playlists

## 文档状态与维护方式

- 版本：**v0.1，本地初版已实现，待验收**，2026-10-25。
- **GitHub Issue 尚未建立。** 本文件仍是正式 Issue 发布前唯一的需求正文。已按用户要求在本地 `feat/custom-playlists` 实施以下规则；GitHub 集成 403 阻断远端发布，正式 Story Issue、远端功能分支与 PR 尚未建立。
- 文档基础提交为 `2372cac`，功能源码提交为 `6ec4869`；最终本地构建与 29 个单元测试已通过。设备验收与人工 Review 尚待完成，AC1–AC9 均保留未签收状态；实际结果见[实施记录](../development/custom-playlists-implementation.md)。
- 建立 Issue 时，使用本文件标题，并复制下方英文正文。成功建立后，将本文件替换为正式 Issue 的链接与简短状态说明；之后只在该 Issue 维护需求和 AC，避免两处需求不一致。
- 背景依据：[基础功能调研](01-basic-feature-research.md)。当前已有 Favorites 和播放队列；本次新增多个独立、可命名、可保存的歌单。
- 验收方法见[自定义多歌单手工验收](../testing/custom-playlists.md)。其中结果均为 Pending，不是测试完成记录。

## 本轮采用的产品规则

以下 v0.1 规则已用于本地初版实现，仍需通过实际验收与同伴审阅确认交付结果：

1. 创建与改名时先去除名称首尾空白；结果不能为空。不同歌单的名称不分大小写唯一；改名时排除当前歌单本身，因此保留自己的原名或只改变自身名称大小写是合法的。
2. 同一曲目可以属于不同歌单；同一歌单内重复添加该曲目不会产生第二个条目，并给出明确反馈。
3. 新加入的曲目排在歌单末尾，保存添加顺序；首版暂不提供手工排序。测试使用逐首添加，避免尚未约定的批量选择顺序影响验收。
4. 按顺序播放时，从首个可播放曲目开始，后续按歌单顺序播放。曲库刷新后，缺失的文件对应条目显示 `Unavailable`，保留可见并在播放时跳过。
5. 首版暂不纳入云同步、导入导出或 AI 功能。

## 可直接粘贴的英文 Issue 正文

### Status

Scope v0.1 has an initial local implementation on `feat/custom-playlists`, following documentation baseline `2372cac`. The GitHub integration still returns 403, so the remote feature branch, Story Issue, and PR have not been created. Final local unit tests (29) and the debug APK build passed for feature commit `6ec4869`; device acceptance and human review remain pending. All acceptance criteria below remain unchecked until supported by the required verification. See the implementation record for actual results and limitations.

### User Story

As a listener with a local music library, I want to create and manage multiple named playlists using songs already on my device, so that I can organize music for different activities and easily replay those collections later.

### Background

The app already has a Favorites collection and a playback queue. This feature adds multiple independently named, saved playlists that users can manage and play.

### Scope

Create, rename, and delete local playlists; add and remove local tracks; play tracks in their order of addition; and retain accepted changes after an app restart on the same device.

Names are trimmed and must be non-empty and unique across other playlists using case-insensitive comparison. Keeping a playlist's own name, or changing only its letter case, is valid. A track may belong to multiple playlists, but may appear only once within any one playlist. New tracks are appended to the saved playlist order.

Manual reordering, cloud synchronization, playlist import/export, and AI features are outside this first version.

### Acceptance Criteria

- [ ] **AC1 — Create and view multiple playlists:** Users can create at least two independent playlists and open each playlist's track list. Names are trimmed, must not be empty, and must be unique across other playlists using case-insensitive comparison. Invalid names are rejected with an explanation and do not create a playlist.
- [ ] **AC2 — Rename a playlist:** Users can rename an existing playlist using the same naming rules, excluding the current playlist from the uniqueness check. Keeping its own name or changing only its letter case is valid. A valid new name is displayed consistently, and existing tracks and their order remain unchanged. An invalid rename leaves the saved name unchanged and displays an explanation.
- [ ] **AC3 — Add tracks:** Users can add songs from the available local music library. New tracks are appended in their order of addition. A song can belong to multiple playlists. Adding it again to the same playlist leaves the existing membership and position unchanged, does not create a duplicate, and provides clear feedback.
- [ ] **AC4 — Remove tracks:** Users can remove a track from one playlist. This removes only that playlist membership; the audio file and the track's membership in other playlists remain unchanged.
- [ ] **AC5 — Delete a playlist:** Users can delete a selected playlist. Other playlists and the audio files stored on the device remain unchanged.
- [ ] **AC6 — Play in the saved order:** In ordered playback mode, selecting Play for a playlist starts its first available track. Advancing to the next track follows the playlist's saved order of addition, skipping unavailable entries.
- [ ] **AC7 — Preserve accepted changes after restart:** After closing and reopening the same installed app on the same device, playlist names, track membership, and track order are retained. Accepted additions, renames, track removals, and playlist deletions are preserved. Clearing app data or reinstalling the app is outside this criterion.
- [ ] **AC8 — Handle empty playlists:** An empty playlist displays a clear empty state, and its Play action is disabled.
- [ ] **AC9 — Handle unavailable files:** After a library refresh, playlist entries whose audio files are no longer available remain visible and are marked as Unavailable. Playlist playback skips these entries. If no playable tracks remain, the app displays an explanation and does not attempt to start playlist playback.

### Implementation Tasks and Status

- Playlist identity, membership, and persistence: implemented locally and covered by the local unit-test run; device acceptance pending.
- Playlist list/detail views and editing interactions: initial implementation available locally; device acceptance pending.
- Ordered playback, library refresh, and unavailable-file handling: integrated locally; device playback/regression checks pending.
- Verification: the final local run passed 29 unit tests and built the debug APK; the device acceptance plan remains to be completed. A real PR and human review are still pending.

These engineering status notes do not replace or sign off the acceptance criteria above.

### Verification Plan

Use three distinct local test tracks, A, B, and C. Build independent fixtures such as Study = [A, B] and Running = [B, C] to check shared membership, duplicate handling, removal isolation, and deletion without deleting audio files. Verify playback with a deliberately different insertion order, such as [C, A, B].

Check persistence of additions, renames, track removals, and playlist deletions in separate cases with clean starting fixtures. Restart the installed app without clearing its data or reinstalling it. Use disposable test audio to check unavailable-file behavior after a library refresh, including a playlist with no remaining playable tracks.

Record the tested commit, device and Android version, steps, expected and actual results, and supporting evidence in the implementation PR. All acceptance criteria remain unchecked until supported by actual verification.

### Implementation Decisions for This Iteration

- Playlists have UUID identities. Data is serialized with Moshi under the independent SharedPreferences key `custom_playlists_v1`; existing Favorites and Queue data are preserved.
- Track resolution uses MediaStore ID with conservative `displayName` and `duration` validation. Unmatched entries retain their snapshots as Unavailable instead of being rebound by title or removed.
- Resolution uses the raw, complete result of a successful library scan, before UI hiding or deduplication. Adding tracks uses the currently visible library. Permission errors and failed scans are not treated as successful empty scans; a genuinely empty library still permits playlist management.
- Starting playlist playback clears the old temporary queue and creates an independent ordered snapshot, starting from the beginning of the first available track. AC6 specifies behavior in ordered playback mode.
- Editing a saved playlist leaves the active playback snapshot unchanged. The next Play action uses the updated saved content.
- Playback completion follows the existing `continueOnEnd` / `onListEnded` preference and does not return to the cleared temporary queue.
- Across process restarts, only the existing last-track/position restoration may apply; restoring the full active playlist queue is outside this iteration. AC7 covers saved playlist data.

Further changes to these decisions should be recorded against this requirement and checked for their impact on the tests. Device behavior and acceptance sign-off remain pending.

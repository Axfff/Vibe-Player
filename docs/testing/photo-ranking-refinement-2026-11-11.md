# Ranking and cover-source UI refinement — 2026-11-11

Status: implementation, local Android regression, physical-phone recommendations
and final wording verified. See PR #50 for current-head CI. Human subjective
acceptance is open.

## Change

The prior phone review placed a window-side butterfly second for Heartbreaking and
shared three of five results between Thinking of You and Heartbreaking. Both audio
excerpts contain relaxing support. The old sum rule treated all relaxation as
peaceful even when explicit sadness coexisted.

`music-atmosphere-v4-valence` redistributes relaxation using specific valence cues,
sharpens the resulting music preference, and abstains from pictures led by a
conflicting atmosphere when the music preference is clear. It contains no image
IDs, object blacklist, filename/song-title inference or tuned per-song exception.
The exact rule is in [model/ranking documentation](../development/photo-scene-model.md).
No image-model weights/prompts, model hashes, SDKs or dependencies changed.

The cover-source screen now displays four actions plus the current sources;
adding methods and management commands are in separate menus. Manual single-cover
choice, model configuration, folder grants, sharing, exclusion recovery, search,
and default-cover restoration remain reachable. Default restoration now asks for
confirmation. Detailed analysis is available without filling the result screen.

## Completed local checks

- JVM suite, app APK and test APK build passed.
- API 33: three real-inference runs covering the original cases, the same 20-photo
  development pool, and the prior exploratory 12-photo pool. All 208 photo inputs
  completed with zero read/inference failures; reversed candidate order matched.
- API 33: nine UI/model/input/real-folder-authorization tests passed, including
  cancellation, recreation, source isolation, candidate removal/restoration,
  crop save/revocation checks and the reorganized menus.
- Original provisional P@5: `1.00 / 1.00 / .80 / 1.00` →
  `1.00 / 1.00 / 1.00 / 1.00`. Labels were not changed. These are already-seen
  development cases, not generalization accuracy or human acceptance.
- The 20-photo Android replay returns five cheerful, five calm, three reflective
  and four intense photos in the first batch. Reflective results are foggy sea,
  foggy street and rain; butterfly is excluded because its leading picture cue
  is peaceful. Calm includes forest waterfall, lake, monochrome waterfall,
  window-side butterfly and rainy trees/path. Calm/reflective overlap is zero.
- The 12-photo exploratory replay returns 5/3/1/2 photos respectively, with zero
  calm/reflective overlap. This is a recall tradeoff; it must not be described as
  perfect matching or a new unseen holdout. Some inputs are paintings, as recorded
  in the original manifest.

The first UI test attempt clicked list items before layout completed. Test steps
now wait for actual menu rows before selecting them; the rerun passed. The app's
menus and authorization paths were exercised, not replaced by mocked picker grants.

## Physical Redmi and plain-language follow-up

Actual phone counts: 7 cheerful / 6 calm / 3 reflective / 4 intense candidates.
The first batches contain at most five. The phone order differs slightly from the
emulator; only observed phone screenshots/scores are used below.

- Thinking of You: forest waterfall, window-side butterfly, bright lake,
  monochrome waterfall, rainy trees/path.
- Heartbreaking: foggy sea, foggy street, rainy night with umbrella.
- The window-side butterfly moves from reflective rank 2 to not recommended for
  this song. Calm/reflective first-batch overlap is **3 → 0**, with **5 calm versus
  only 3 reflective results**. This is not a five-versus-five accuracy claim.
- Carefree retains flowers/fireworks; Volitile Reaction returns three surfing
  photos and rushing water (four, without padding).
- All four actual audio/photo analyses report zero read/inference failures. The
  existing 20-photo scope, four playlists, stored covers and model files remain
  unchanged. No new cover was saved. Temporary display/notification settings were
  restored before handing back the phone.

After the user's additional wording request, the final APK was installed and the
calm/reflective recommendations rechecked. The ranking methods are byte-for-byte
unchanged after newline normalization; their hashes are in the phone receipt.
Visible copy now uses **推荐封面**, **选择歌曲**, **自己选氛围**, **推荐设置**, and
**设为封面**. Cards show one brief scene/atmosphere sentence; numerical scores,
analysis duration and diagnostics are in **更多 → 分析详情**. Scope authorization,
folder refresh behavior, search fallback and cancellation remain explicit in plain
language. The crop page shows actionable Chinese errors instead of enum codes.

Phone checks also cover the real single-photo picker and cancellation, folder
options/cancellation, restore-default confirmation/cancellation, model-settings and
search copy, a recommended crop preview and cancellation. The final nine-test UI
regression passed. A stale test expectation for the renamed readiness message was
corrected before that final rerun.

[Exact evidence and checksums](evidence/photo-refinement-2026-11-11/checksums.json),
[phone result receipt](evidence/photo-refinement-2026-11-11/phone-results.json),
[development comparison](evidence/photo-refinement-2026-11-11/development-evaluation.json).
Source-photo attribution and frozen manifests remain in the
[prior v4 evidence](evidence/photo-quality-v4-2026-11-11/fresh-ATTRIBUTION.txt).

## Remaining review

Human review is still needed for subjective matching, the lower candidate-count
tradeoff and the existing 159 MB model/license decision. The original and fresh
pools were used during development; the 12-image pool is an exploratory replay,
not a newly unseen evaluation. No general accuracy claim or AC sign-off is made.

The existing ONNX 16 KB compatibility issue #55 and Xiaomi automatic-keyword
handoff limitation are unchanged. No acceptance checkbox is marked by these tests.

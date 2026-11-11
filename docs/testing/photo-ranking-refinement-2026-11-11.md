# Ranking and cover-source UI refinement — 2026-11-11

Status: implementation and local Android regression verified; physical-phone
retest and current-head CI are still pending. Human subjective acceptance is open.

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

## Outstanding checks

- Physical Redmi: four-song recommendation and visual UI retest (device locked;
  user unlock requested). Do not substitute emulator order for phone results.
- Current-head GitHub CI and cumulative coverage.
- Human review of subjective matching, fewer-result behavior, and model size/license.

The existing ONNX 16 KB compatibility issue #55 and Xiaomi automatic-keyword
handoff limitation are unchanged. No acceptance checkbox is marked by these tests.

# Device import status — 2026-11-07

| Step | Actual status |
|---|---|
| Host assets and complete-file decoding | See validation-summary.json and audio-ffprobe.json |
| Running authorized ADB device | None; `adb devices -l` returned an empty device list |
| App playlists created through UI | **APP_PLAYLISTS_PENDING** — 0 created in this task |
| Photos copied/indexed on Android | **PHOTO_IMPORT_PENDING** — no device import performed |
| Playback/Next/same-install restart | **Pending** — host decoding is not Android or listening acceptance |
| System photo picker access | **Pending** |
| Actual music-to-photo Top-5 | **Blocked / RECOMMENDATION_PENDING** — integration absent |

No APK was rebuilt, installed or replaced for this data-only task. APK hash and
Android runtime version are therefore **not applicable / not tested**. No phone
data was cleared, preferences seeded, MediaStore IDs injected or app uninstalled.
Do not reuse older emulator/playback results as acceptance of this dataset.

Checked source: main `844caf7cd1cfb3348cdd130136763528c3274573`;
audio API PR #46 `4776e4e1c5ab0ee56aa7931f53d1177690a2d2a2`;
unmerged cover branch `feat/custom-cover-foundation`
`25849a42feac0b631911909830162b3d807a5b94`.
The cover branch uses `PickVisualMedia` and a preview/crop flow, but was not built
or executed in this task. Latest main does not expose this new custom-cover flow.
The existing playlist UI creates playlists and appends songs individually;
source inspection found no JSON/M3U playlist import. Exporting JSON/M3U8 does not
create any app playlist.

## Accurate follow-up on a disposable, explicitly selected test device

1. Validate first, then materialize to a directory outside the fixture package:
   `python scripts/fixtures/materialize_music_photo_fixtures.py --output ../music-photo-export`.
   The export has `case_01`–`case_04`, each with `audio/`, a mixed `images/`,
   `input.json` and auxiliary `order.m3u8`. No role folders or answer metadata.
2. Confirm the selected device is a disposable test device with `adb devices -l`
   and `adb -s SERIAL shell getprop ro.kernel.qemu`. Record the serial, Android
   version, installed package/version and the exact installed APK hash. If the
   signature or storage access differs, stop; do not clear data or uninstall.
3. Copy the four audio folders under `/sdcard/Music/VibePhotoFixtures/case_XX/`
   and the four mixed image folders under
   `/sdcard/Pictures/VibePhotoFixtures/case_XX/` with `adb -s SERIAL push`.
   Copy actual files, not host symlinks. Keep attribution with the exported set.
4. Allow the platform media scanner to index the files. If the selected Android
   image supports `MEDIA_SCANNER_SCAN_FILE` for shell-published files, request
   scanning for each actual device path and confirm MediaStore visibility.
   Otherwise use system Files/media indexing and document that route. Do not
   insert made-up MediaStore IDs. The app's refresh cannot guarantee discovery
   of wholly unindexed copies (existing issue #4).
5. Grant only normal requested audio access. In the app, open **Playlists → New
   playlist**, enter each display name, open **Add songs**, search each real title
   and append in the exact order from `playlists/*.json`. Confirm five songs and
   each displayed row's title/order. Do not assume alphabetic order is the saved
   order; do not import M3U8 through a nonexistent feature.
6. For every playlist, disable shuffle/repeat-one, press Play, observe progressing
   playback and the representative title; press Next and verify the second title.
   Record actual screenshots/player state. Audible playback needs human listening.
7. Force-stop and reopen the **same installation**, without reinstalling or
   clearing anything. Recheck all four names, five members each and exact order.
8. Open the system image picker or the implemented cover flow, browse all four
   Pictures folders and verify mixed candidates can be selected/cancelled. If the
   tested APK lacks the cover feature, record only actual picker/file visibility;
   do not claim the cover or recommendation feature passed.

Use neutral exported cases for recommendation. A future device-only MediaStore
mapping belongs in local run evidence, never in the portable dataset manifest.

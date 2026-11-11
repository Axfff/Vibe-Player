# Discovery local-photo increment — 2026-11-11

## Delivered behavior

Discovery now reuses the user's imported TinyCLIP photo model: select one photo →
explicit local-analysis action → finite, editable picture/atmosphere suggestions →
confirm the desired music → live catalog results → official platform → Collection.
The local action never falls back to a cloud upload. Missing/corrupt models,
unreadable images, uncertain signals and cancellation leave manual input available.
Applying suggestions retains the user's search text and existing style when the
photo model has no style suggestion. Picture atmosphere is an editorial association
with music intent, **not** a person's emotion or analysis of recommended song audio.

This work continues Discovery `9344f99c`, main `844caf7c` and the completed cover
PR #50 at `85e53f20`, integrated by merge `e6aea7de`. Existing authorship, cover
selection, photo scopes, local music models and saved-cover implementation remain.
Production changes are `85f734c7` and `50b6b9c1`; `ed652c15` fixes an integration
test that incorrectly prohibited the entire merged app's INTERNET permission.
Broad image/storage/location permissions remain prohibited. The original untracked
`feature-summary-zh.md` was preserved without replacement.

## Real model and license

- Actual artifact: `tinyclip-atmospheres-v4.onnx`, 158,993,255 bytes, SHA-256
  `c6811b6a8127a3091e2fc21e0a5b48f0234de40afa30472028b15ab215092dd9`.
- Reused [pinned source/export](../photo-scene-model.md) and
  [upstream MIT model](https://huggingface.co/wkcn/TinyCLIP-ViT-40M-32-Text-19M-LAION400M).
  No new model download, paid API, key, subscription or account was used.
- Scene cosines and four relative atmosphere supports are not calibrated
  probabilities. Unsupported/document/screenshot signals and weak support yield
  no suggestion. Close leading atmospheres are marked uncertain. No music genre
  is invented from the photograph.
- Four existing public development fixtures were run through real ONNX inference:
  `img_0006`, `img_0013`, `img_0026`, `img_0027`. Three produced suggestions (one
  explicitly ambiguous); one correctly took the no-suggestion path. This is a
  functional smoke test, **not** a held-out quality score or human acceptance.
  [Fixture provenance](evidence/local-vision-2026-11-11/fixture-attribution.json).

## Actual runtime evidence

Production APK SHA-256: `37cd9504875196677c5511fd3fa8b29ff60adf24f2d69b3df7f2045a004d60a5`.
Production source: `50b6b9c14b593e8670b0aba0d0182f936a4ef26e` (later test/doc
changes do not change production source). Backend production is the same integrated
source; live iTunes, unconfigured Jamendo, cloud vision unconfigured/unauthorized,
vision budget 0 and requests made 0. The native app uses the existing local service
over debug-only ADB reverse port 8765; this does not supply a hosted release service.

On **API 33 x86_64 emulator-5560**, the real opt-in test verified actual image
inference, unchanged user input until applying suggestions, retained `piano` text,
user override to `focused`, explicit confirmation and eight real catalog rows.
[Model/real catalog evidence](evidence/local-vision-2026-11-11/emulator-real-evidence.json)
and [UI test result](evidence/local-vision-2026-11-11/emulator-real-ui-evidence.json).

A separate actual picker/UI flow used `img_0013` (snowy mountain/trees):

1. System photo picker → local consent → `nature` / `calm` suggestion.
2. Apply and confirm → eight live `ambient` results.
3. First result: `itunes:1511052092`, **Ambient — Baby Beethoven**,
   official Apple album URL carrying `i=1511052092`.
4. The result's official button launched Chrome. **Actual destination was
   `https://music.apple.com/cn/new`, the regional homepage, not the target song.**
   No platform login or subscription was used. A separate live CN catalog probe
   returned zero results; no synthetic songs or guessed alternate links were added.
5. Return → Save discovery → force-stop → launch → Collection restored the same
   source identity, title, artist, official URL and confirmed intent.

See the [real results](evidence/local-vision-2026-11-11/emu18-live-results.png),
[actual official destination](evidence/local-vision-2026-11-11/emu24-official-page.png)
and [restored Collection](evidence/local-vision-2026-11-11/emu29-restored-collection.png).
The opt-in test's two external-navigation/cold-restart flags remain false because
those steps were measured separately by the UI flow above; its historical output
has not been rewritten to imply additional assertions.

## Phone boundary and data preservation

Redmi `25102RKBEC`, serial `7512baaf`, currently API **37**, is connected and ADB
authorized. Initial merged APK (`85f734c7` production, SHA-256
`4e938b6bd2c3f28b1fa6308d6d4e0c0073a63e25f3b3660785a2c196d0c9eb33`)
was installed with `install -r`, never uninstall or clear-data. Before updating,
the app's files/no_backup/shared_prefs were backed up privately. All **10 existing
files** matched their original SHA-256 afterward, including models, photo scopes,
playlists and saved-cover state. The backup is not committed or published.

The phone is locked. A later test-APK installation returned
`INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`. An unlock request is
pending; no lock bypass was attempted. **This report does not claim phone
inference, target-song navigation, or a full phone flow passed.** The final APK
update, physical-phone UI run and optional official music-client route remain
pending until the user unlocks the device/allows its installation prompt.

## Tests, CI and remaining acceptance

- Local JVM: **124 passed**, including four new finite-policy tests.
- Existing backend: **166 passed**.
- Targeted Android: **14 passed**, including six boundary tests, seven Discovery
  flow tests, and the corrected scoped-permission assertion.
- Real-model/real-catalog opt-in Android test: **1 passed**, separate from fixtures
  and standard CI. Its optional external weights/fixtures are not bundled in APKs
  or required in ordinary CI; absence must be reported as a skip, never success.
- Full coverage inventory: **77 production files** (61 Android, 12 backend, 4 web),
  including Release 1 and both branches. No production exclusion or threshold was
  relaxed. CI agent independently checked the complete scope.
- Historical first integration run at `85f734c7` failed the incompatible INTERNET
  assertion: Android 3293/3922 (83.962264%), backend 954/992 (96.169355%), web
  812/825 (98.424242%). That failed run is retained as history; percentages do not
  override a failed test. Final-head CI results belong to the PR's current checks.

S3-AC2 now has a genuine **local-model implementation and emulator evidence**;
phone validation and human acceptance remain pending. Cloud analysis stays
Blocked without separately authorized configuration/budget. S3-AC5 target-song
availability remains unpassed. S3-AC8 must retain these limits. All human AC
checkboxes remain unchecked; no Story was closed or human Review supplied.
Sprint 4 still contains the second authorized source, legal per-track previews,
audio mutual exclusion and the complete hybrid experience. No product scope,
model budget, teammate assignment or team date was decided here.

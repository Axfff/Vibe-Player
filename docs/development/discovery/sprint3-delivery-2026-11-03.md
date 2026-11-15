# Sprint 3 Discovery delivery evidence — 2026-11-03

The manual Android increment is runnable: confirmed music intent → live iTunes results → validated official link launch → independent Collection save → restore after force-stop. This is an engineering delivery record, not human sign-off. [Story #36](https://github.com/Axfff/Vibe-Player/issues/36), [formal SR/AC/tasks](../../requirements/vibe-discovery.md), [Sprint 3/4 plan](../../sprints/sprint-3.md) and [run instructions](android-sprint3.md) are linked together.

Implementation is published as [draft PR #37](https://github.com/Axfff/Vibe-Player/pull/37). Its exact-head cloud check and coverage bot comment contain the current downloadable full reports/APK; they are separate from the dated local measurements below.

Two acceptance limits remain explicit: **S3-AC2 Blocked** without real vision authorization; **S3-AC5 partial** because the actual browser redirected the official song link to an Apple regional homepage. Full hybrid playback remains the overall goal; second-source authorization, legally permitted previews and local/preview audio exclusivity remain S4-AC1–4.

## Provenance and runnable artifact

The existing local worktree and backend were reused at `fa86950d0f8955a8b0718de95f7a0509dedee832`. Main/Release 1, CI readiness and existing backend/web tests were integrated before the Android implementation. No second service or replacement backend was created.

| Item | Recorded value |
| --- | --- |
| Feature / backend source for device run | `8ef69e0bdfd113dc690ac935a40e40e298c8cbe7` |
| Coverage inventory candidate | `45e8f68299f7f202943d13f7c098b2c1cbaa8d22` (same production source; 46 files: 30 Android, 12 backend, 4 web) |
| Device | Dedicated `Coverage_API_33`, `emulator-5582`, Android 13 / API 33 |
| Installed package | `com.iven.musicplayergo.debug`, existing version 4.4.24 / 187 |
| Tested APK | `project/app/build/outputs/apk/debug/app-debug.apk` |
| Tested APK SHA-256 | `6480c0df6f007d248784f8e8f23da53e087565ec78a7d5e176200ba0273a3cce` |
| Catalog | iTunes live, country US; metadata / official external links only |
| Vision | unconfigured, cloud authorized false, budget zero, actual calls zero |
| Jamendo | unconfigured; no live or license-completion claim |
| Server | loopback `127.0.0.1:8765`; debug device uses ADB reverse; existing explicit loopback CONNECT proxy for outbound catalog requests |
| Release 1 | `release1` remains `81b22ee5868e0cd5ac6a75472296caab3753a7b5`; no released APK replaced |

See the [machine-readable summary](evidence/sprint3-device-summary.json). A rebuild may have a different APK checksum; the checksum above identifies the actual installed artifact for these screenshots. The source tree, not the historical checksum, is the reproduction input. Release uses HTTPS; the debug network policy allows only the named loopbacks. No server/model secret enters the APK or evidence.

## Actual emulator sequence

1. Fresh task-owned app data; launch main and decline local audio permission. The Discovery button is reachable: [02 screenshot](evidence/sprint3-device/02-no-audio-permission.png).
2. Check the loopback service, leave the photo branch unconfigured, select scene `city`, mood `focused`, text `piano`, country `US`, and confirm intent: [04 screenshot](evidence/sprint3-device/04-intent.png).
3. Request and display eight live catalog results with native IDs, source URLs and metadata reasons: [05 screenshot](evidence/sprint3-device/05-real-results.png). The first is `itunes:1440861221`, “Piano” by Ariana Grande. The test does not claim that all songs match an audio-derived mood; ranking uses catalog metadata and query tokens only.
4. Tap the original validated Apple song link with no Apple Music app installed. Android opens Chrome; no user account is signed in. The resulting URL is **`https://music.apple.com/cn/new`**: [09 screenshot](evidence/sprint3-device/09-official-page.png). Browser fallback works, but target-song availability does not pass. No substitute URL, fake result or preview is presented as success.
5. Return and save the first discovery: [10 screenshot](evidence/sprint3-device/10-saved.png). Force-stop the app, relaunch main, decline the existing audio-permission rationale, reopen Discovery Collection: [12 screenshot](evidence/sprint3-device/12-collection-restored.png). The same source identity restores.
6. Read the task-owned app's `no_backup/discovery-collection-v1.json` to confirm the persisted record: [storage evidence](evidence/sprint3-device/restored-collection.json). It contains source identity/link, title/artist, confirmed user intent/market and exclusions; no image/audio/key/local MediaStore ID.

The XML files beside each screenshot are the corresponding UI Automator dumps. Images 01/03 show setup; 06–08 are Chrome onboarding. **11 is the intervening existing audio-permission rationale, despite its historical filename; it is not proof of restoration. 12 is the actual restored Collection.** The device was disposable; the user's normal phone was not reset.

## Live catalog smoke versus deterministic tests

The separate bounded real-service smoke at 14:21 SGT returned eight initial results, two new replacement identities and a matching lookup, in four catalog HTTP calls. Its [original console capture](evidence/sprint3-live-catalog-2026-11-03.json) has Windows console replacement characters in some Chinese explanation fields; song IDs/titles/URLs and machine checks remain readable. No damaged text was reconstructed or claimed as exact.

A repeated four-call run at 14:34 SGT captured Python stdout bytes directly as UTF-8, with no replacement characters: [UTF-8 live report](evidence/sprint3-live-catalog-utf8-2026-11-03.json). It again passed real result/unique identity/replacement/lookup/external-only checks. Both runs used the same production source and no model calls. Catalog success is distinct from the browser availability failure above. These smoke reports are not CI fixtures.

Reproduce from `services/discovery` only in an authorized metadata-catalog environment:

```powershell
$env:PYTHONUTF8 = '1'
$env:DISCOVERY_ITUNES_MODE = 'live'
$env:DISCOVERY_VISION_MODE = 'unconfigured'
$env:DISCOVERY_CLOUD_AUTHORIZED = 'false'
$env:DISCOVERY_VISION_CALL_BUDGET = '0'
$env:DISCOVERY_JAMENDO_MODE = 'unconfigured'
.\.venv\Scripts\python.exe smoke.py --provider itunes --authorized --workflow --limit 8 --max-http-calls 4 --market US
```

The installed-app run and the standalone smoke are separate requests. No fixed fixture supplies the actual device results. Automated Android/pytest/web tests instead use synthetic files, loopback fixture servers or mocked transport; they do not call a paid model or third-party catalog.

## AC evidence map (human acceptance pending)

| AC | Evidence / current technical result |
| --- | --- |
| S3-AC1 | Existing library entry plus actual no-audio-permission run; Activity tests cover picker cancellation, consent refusal and manual operation. |
| S3-AC2 | **Blocked** for real model. Strict schema, metadata-stripped image handling, consent and user override are tested with explicit test fixtures only. Real photo recognition is not signed off. |
| S3-AC3 | Actual Android displays eight live iTunes identities; bounded smoke independently checks lookup identity. Missing/invalid/non-live rows are rejected in tests. |
| S3-AC4 | Backend ranking tests cover normalized metadata scores, stable provider ordering, exclusions, provider-scoped dedupe and cross-provider artist cap. No mood/BPM/audio confidence is fabricated. |
| S3-AC5 | Safe protocol/host/native identity tests and real Chrome fallback. **Partial:** actual Apple target redirects to `/cn/new`, so song-page availability remains unpassed. |
| S3-AC6 | Actual save/force-stop/reopen plus JSON evidence. Repository/AtomicFile tests cover failure, corrupt data, identity separation, limits and no silent overwrite. |
| S3-AC7 | Boundary/Activity tests exercise timeout/cancel/stale results, HTTP errors, empty/invalid responses, strict photo schema and local playback continuing. Real-service failure is never filled with mock songs. |
| S3-AC8 | Source/APK hash, config, screenshots and raw reports recorded above, including the incomplete external-song step. The full real-photo/hybrid journey is not accepted. |

## Automated verification and coverage handoff

At feature source `8ef69e0b`, the six new Discovery JVM behavior tests and all twelve Discovery Android tests passed. The device package command was:

```powershell
$env:ANDROID_SERIAL = 'emulator-5582'
$env:DISPOSABLE_EMULATOR = '1'
python scripts/require-disposable-emulator.py
# From project/:
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.iven.musicplayergo.discovery --offline --console=plain
```

The feature owner's targeted Python run had 166 passing tests and the web run had 22. Final coverage numbers below come from the CI owner's complete clean collection. Test execution uncovered and fixed actual AtomicFile failure reporting, default bitmap sampling and device-UI issues before the passing rerun; earlier failed logs were retained in the local verification directory.

The CI agent completed the clean same-revision collection at **`00bad97bfb9eade7ec6ef9361de50b1554f2fbab`**, using its own disposable `emulator-5584`. This includes Release 1, all eight new Android source files, the changed entry and backend ranking. No UI, transport, image, storage or unexecuted code is excluded to inflate results. The [unaltered machine summary](evidence/sprint3-coverage-00bad97b.json) records a clean tree and `measurement_only=false`.

| Module | Tests (failure/error/skipped all zero) | Covered / executable | Gate |
| --- | --- | --- | --- |
| Android JVM + device | 37 JVM + 49 instrumentation | 1727 / 1952 = 88.473361% | Pass |
| Python | 166 | 954 / 992 = 96.169355% | Pass |
| Web | 22 | 812 / 825 = 98.424242% | Pass |

The class-ID audit matched all 336 production classes. JVM instrumentation diagnostics contain zero duplicate-instrumentation errors; actual JVM execution data still contains 35 production-class probe hits. The first exploratory collection reported duplicate instrumentation and is explicitly invalid; those percentages are not used here. The public fix avoids transforming already offline-instrumented app classes twice, without excluding them from report classes or the scope. Twelve gate-contract tests include rejection of a passing JUnit report with instrumentation errors.

The exact full local report is `Vibe-Player-ci-feature/artifacts/coverage/index.html`; an immutable copy is under the workspace's `verification/sprint3-coverage/discovery-feature-20261103/final-00bad97b/`. Source fingerprint: `ed924bdc122dd1aa92c0402a963a27bc68f5a9b5daba1f06e91a597621ec83be`. The current feature branch includes this configuration and identical production source, plus delivery documents. This local result stays attributed to `00bad97b`; the draft PR's final head must pass its own cloud run and is never relabeled as this local measurement.

## Remaining work and handoff

- Account owner + Discovery implementer: explicitly authorize/configure and verify a real vision model, or have the human team formally decide a scope change. Until then S3-AC2 stays Blocked in Sprint 3.
- Discovery implementer + reviewer: verify the original official song URL on a supported actual storefront/device. Keep the current availability disclosure and browser evidence; do not infer availability from an ID match or HTTP 200.
- CI agent + Discovery implementer: local cumulative gate is complete; verify the final draft PR head's cloud gate and downloadable report/APK. Any later production change requires new scope review and fresh collection.
- Sprint 4: authorized second source, per-track legal preview/attribution, local/preview audio focus and lifecycle exclusivity, then the complete hybrid device flow (D4-01–03 / S4-AC1–4).
- Human team: review and sign acceptance, assign actual member names and dates. This work does not merge the PR, close the Story or change `release1`.

# Android Sprint 3 implementation and reproducible flow

Parent [Story #36](https://github.com/Axfff/Vibe-Player/issues/36); [requirements / staged AC](../../requirements/vibe-discovery.md). Existing backend work and its commit history are retained. This document supersedes the earlier Android-next planning note for implemented behavior.

## Run

Use JDK 17 / SDK 33 / Gradle 8.0 and the existing project wrapper. No SDK/Kotlin upgrade. `:app:assembleDebug` builds `project/app/build/outputs/apk/debug/app-debug.apk`; `:app:testDebugUnitTest` runs JVM behavior tests. CI agent owns shared build/coverage configuration.

In `services/discovery`, install the locked requirements in its own venv. Set the project-local `.env` to `DISCOVERY_ITUNES_MODE=live`; keep `DISCOVERY_VISION_MODE=unconfigured`, `DISCOVERY_CLOUD_AUTHORIZED=false`, `DISCOVERY_VISION_CALL_BUDGET=0` without explicit model authorization. No model key is needed for manual discovery. Existing optional loopback CONNECT proxy configuration is reused, never searched outside the project. Run:

```powershell
.\.venv\Scripts\python.exe -m uvicorn discovery.app:app --host 127.0.0.1 --port 8765 --no-access-log
```

Connect a disposable emulator or an explicitly authorized phone over ADB, then target its actual serial:

```powershell
adb -s emulator-5582 reverse tcp:8765 tcp:8765
adb -s emulator-5582 install -r project/app/build/outputs/apk/debug/app-debug.apk
```

1. Launch Vibe Player and tap **Vibe Discovery** in the main library actions (available without local songs/audio permission).
2. The debug default service is `http://127.0.0.1:8765`. **Check service** shows each actual provider/model mode. Alternatively the emulator can use `http://10.0.2.2:8765`. No global cleartext is enabled; release permits HTTPS only. For HTTPS deployment, terminate TLS with a correctly configured reverse proxy forwarding the service's accepted local Host. The local token is a loopback anti-cross-site boundary, not production user authentication; this increment does not deploy a public service.
3. Pick an optional scene; select a desired mood/style or type plain search words. Choose the catalog country; text overrides style/mood for catalog query. Confirm the music intent, then **Find real music**.
4. Results show source/native ID, title, artist, original official URL, metadata-based reason and availability notice. Open the official platform with an unscoped HTTPS VIEW intent: Android chooses the official app or a browser. A missing browser is reported. The app does not play or download these songs.
5. Return and **Save discovery**. Open **Discovery Collection**. Stop and reopen the app, open Discovery and the Collection again: saved items and their confirmed intent/market restore. Removal only changes this Collection. **Hide** and saved items are excluded from future searches; clearing hidden entries does not erase saved entries.

## Photo branch and live boundary

System `PickVisualMedia` selects one image (ACTION_OPEN_DOCUMENT fallback on older systems); no broad image/media permission is added. Selection/cancellation never uploads. With no authorized real model, **Review upload consent** explains the block and manual input remains usable. Mock mode is refused by the Android photo parser and cannot satisfy S3-AC2.

For authorized live configuration only, the consent dialog identifies recipient/model and explains actual retention boundaries. A changed recipient/model after consent is rejected. Only its positive action reads the selected URI, bounds original bytes to 5 MiB and decoded pixels to 20 million, handles EXIF orientation/flip, resizes to longest edge 1280, composites transparency and encodes a fresh metadata-free JPEG. The backend independently repeats validation/sanitization before model transmission. Images/URIs are not persisted; photo suggestions are finite and editable. Applying suggestions is explicit; analysis itself does not replace the user's choices. After edits, confirmation is required again.

`store=false` on the existing model adapter is not a zero-retention guarantee. Account/provider retention terms must be disclosed and reviewed before enabling a real model. This task's inspected configuration has no authorization, key/model or budget; **S3-AC2 stays Blocked**, real model calls zero. Android fixture-model tests are clearly test-only.

## Decision rules and request behavior

- Query: nonempty user text → unique chosen music tags → finite mood mapping (unchanged adapter mapping). iTunes has text/country search, no invented BPM/mood filter. Energy/instrumental fields are not represented as supported iTunes features.
- Candidate validation retains the existing strict provider parser. Dedup by provider+native ID; caller sends saved/hidden IDs (at most 500). Results are fresh from the source; no offline/fake music fill.
- `ranking.py`: NFKC/casefold/whitespace normalization; descending count of exact wanted catalog-tag matches, then title/artist query-token matches, then original interleaved provider relevance position. Mood mapping contributes a wanted tag only when text/tags are absent. Missing tags score zero. Explanations report actual match counts; at most two songs per normalized artist across sources. Provider-local ID spaces remain distinct. No confidence or audio-analysis claim.
- Apple `trackViewUrl` from Search/Lookup is validated as HTTPS, exact allowlisted host and matching native song ID (including conflicting/duplicate selector rejection). It remains the provider-supplied URL. Server HEAD may geo-redirect to a storefront homepage and is not used as proof that the user's target is unavailable. `linkVerification=catalog_identity` and `linkNotice` distinguish identity from browser/region availability. Jamendo page/stream checks and preview policy gates remain intact.
- Backend uses 8-second total outgoing request deadlines, catalog retry at most once, Retry-After at most 2 seconds (longer values return an error), per-source 18 HTTP/minute and configured process budget. Vision uses 30 seconds and no automatic retry. Discovery page checks retain at most 12 / 35-second soft traversal budget for sources that need them; one in-progress request can extend that traversal up to 8 seconds.
- Android: 8-second connect timeout, 45-second hard per-HTTP deadline, 55-second whole operation timeout, no automatic retries. One operation at a time. Cancellation disconnects and interrupts the worker; lifecycle stop cancels; generation checks suppress late responses. Loading/cancel controls are visible and duplicate submit disabled. Invalid/error requests do not retain misleading previous-result cards; Collection stays accessible.

## Persistence, tests and remaining scope

`DiscoveryFileStorage` uses an AtomicFile in `noBackupFilesDir`, JSON schema version 1; maximum 200 saved/300 hidden identities and 1 MiB. Save verifies committed bytes before reporting success; repository state changes only after a successful commit. Corrupt/unreadable storage is shown and cannot be silently overwritten. Data includes source ID/link, title/artist snapshot and user intent/market, without artwork, audio, photos or service tokens. No `Music.id`, MediaStore, local playlist or player-state mutation.

`DiscoveryPolicyTest` covers identity/URL/endpoint/input policy and repository failure/bounds. `DiscoveryBoundaryTest` exercises actual Android JSON, local HTTP, token/body, failure/deadline/cancellation, AtomicFile restoration/corruption and EXIF image preprocessing. `DiscoveryFlowTest` exercises real Activity views, explicit confirmation, result/save/remove/hide/reopen, picker cancellation and consent, user overrides, late responses/errors and ongoing local playback. Synthetic transport data lives only in `androidTest`; backend pytest uses marked fixtures and web tests keep their existing scope.

Run device tests only on a disposable emulator after `scripts/require-disposable-emulator.py`; they reset test-owned Discovery state. Final full coverage must start clean and include all new production files plus Release 1, matching a committed SHA. Local targeted test results do not substitute for CI's fresh full report. Live evidence is recorded separately with actual provider/model status, build hash and original results.

Second source authorization, legal in-app preview and audio mutual exclusion remain S4-AC1–4. Existing Jamendo/vision adapters and developer preview code are retained, with their real-service acceptance still pending. Human acceptance and review remain pending.

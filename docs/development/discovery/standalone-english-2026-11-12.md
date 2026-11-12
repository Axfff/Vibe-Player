# Phone usability and English UI — 2026-11-12

Refs: [Discovery Story #36](https://github.com/Axfff/Vibe-Player/issues/36), [English Story #65](https://github.com/Axfff/Vibe-Player/issues/65).

## Scope

The previous phone APK used a development service at `127.0.0.1:8765` through USB forwarding. Its earlier successful real-model/catalog tests remain valid development-chain evidence, but did not establish use away from the computer. This increment removes that dependency from the default path and makes English the default application language. Full Chinese is outside this increment, as confirmed by the user.

## Implemented

The default phone flow calls the public HTTPS iTunes Search API directly. A listener chooses an atmosphere, optional music style or search words, confirms the intent, and receives up to eight real catalog songs. Search words override styles; styles override the atmosphere-to-search-term association. Scene descriptions are context, not evidence of a song's audio characteristics.

Catalog rows must contain valid song identities, title/artist metadata and an official HTTPS URL identifying the same song. Results are deduplicated by provider identity, ranked by genre and title/artist word matches with stable source order, filtered against saved/hidden tracks and limited to two songs per artist. Requests have deadlines, cancellation and response-size limits; redirects are not followed. Errors and shortages remain visible. No account, API key, subscription, generated song identities, artwork or audio-preview download is introduced.

An optional photo is analyzed by the previously approved, imported TinyCLIP model on the phone. Its picture-atmosphere scores map to editable music intent. The user explicitly applies, edits and confirms suggestions before searching. Uncertain pictures and missing/invalid models fall back to manual intent. Discovery's model setup shows only the photo model; the music-to-cover feature retains its three-model setup. The photo model is not bundled or silently downloaded: manual discovery works without it, while photo suggestions require that one-time setup. Existing installed models are reused.

The custom service remains available through an explicit advanced option. A previously stored localhost address is retained but does not activate this option during upgrade. Cloud analysis still requires a configured authorized live service and per-photo consent; the default path cannot upload a photo.

English is selected independently of the device language when no app-language preference is saved. Activities, application resources, notification text and deferred domain descriptions use the shared locale policy. App-owned photo/model/cover/Discovery prose lives in default Android resources; typed messages defer resolution until presentation. Protocol keys and model vocabulary order remain unchanged. Legacy generated photo-source names are displayed in the current language without rewriting source records. Original song, artist, album and user-entered names are preserved; system pickers and other apps retain their own language settings.

## Verified and verification boundary

Local JVM/resource tests, isolated emulator UI tests and explicit real-model phone tests are separate kinds of evidence. Synthetic catalog rows exercise rejection and ranking only; they are never presented as real recommendations. The real-photo test now uses phone HTTPS catalog retrieval and still requires explicit opt-in plus separately supplied public images. Its cleanup removes only media rows created by that test. Full state-resetting regression fixtures must run only on disposable emulators.

The exact revision, full cumulative CI reports, phone APK hash, retained-data comparison, device outcomes and remaining failures belong in the linked PR's dated verification update. Earlier report versions and boundaries are not rewritten. This document does not assert a phone run or CI result before it occurs.

## Next

1. Verify the full real-phone flow with no host backend or USB reverse: editable photo suggestions, real results, official target song, Collection save and cold reopening.
2. Retain the complete cumulative coverage inventory and the existing 80% gate for every module; inspect the exact-revision full report.
3. Keep the full mixed experience, additional catalog integration, licensed previews and audio mutual exclusion in Sprint 4. External-app navigation is distinct from catalog integration, login and playback entitlement.

## Links

- [Apple Search API documentation](https://performance-partners.apple.com/search-api)
- [Android localization documentation](https://developer.android.com/guide/topics/resources/localization)
- Integration baseline: `b94736a6`; current main checked before implementation: `adacdcae`.
- Implementation assisted by Codex under CroissantXinny's instruction. Human review and AC acceptance are not supplied by these checks.

**Student ID (CroissantXinny):** 50027204

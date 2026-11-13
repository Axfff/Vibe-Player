# Discovery screen hierarchy — 2026-11-13

## Scope

[Story #68](https://github.com/Axfff/Vibe-Player/issues/68) responds to the user's observation that Discovery reads like an instruction manual. It builds on [PR #66](https://github.com/Axfff/Vibe-Player/pull/66), preserving independent phone search, English resources, existing cover integration and [Story #36](https://github.com/Axfff/Vibe-Player/issues/36)'s functional boundaries.

## Implemented

Decision: separate input, results and saved-collection presentation, and make explanations available when needed. Reason: equal-weight full-width controls and repeated paragraphs obscured the main action and the song itself. Action/owner: the focused UI branch for #68, implemented by Codex under CroissantXinny's instruction.

- Input starts with atmosphere and optional search words in one card, followed by a separate optional-photo card. Country, scene, style, model setup and custom service remain in More options. A compact summary retains selected option values.
- Explicit intent confirmation and Find music stay in the input screen's bottom action area. Selecting a photo still does not analyze or upload it; the existing local/cloud consent and editable-suggestion flow remains. High uncertainty stays visible beside the suggestion even when its explanation is collapsed.
- Search completion presents results with Change choices. Collection has its own heading; returning and recreating the activity preserve editing/options/detail state and input. The first screen no longer repeats successful readiness/provider prose.
- Song cards prioritize title, artist and visible provider/native identity, with distinct open/save actions. Official URL, matching/entitlement explanation and hide action remain under Details & options. Save/remove updates retain expanded details. About explains the metadata-based approach and listening restrictions.
- Resource-backed sentence-case controls, card spacing, headings, labels, 48dp minimum action targets and descriptive song actions distinguish controls from prose and follow the existing app theme.

No catalog/ranking policy, photo model, data format, consent authority, network configuration or playback entitlement changes. Optional model import still exists; this is not a model-onboarding or preview implementation. Additional catalogs, licensed previews and audio mutual exclusion remain Sprint 4.

## Verified

Local build/JVM checks and 18 targeted disposable-emulator tests passed, including three new presentation tests and the existing consent, error/cancellation, storage, local playback and English regressions. The initial presentation test compared a String against a SpannableStringBuilder with identical text; its assertion now compares text content and the selection passed. This was a test assertion failure, retained in the local log.

Synthetic HTTP rows in those tests verify behavior only. Screenshots, actual live-phone results, APK/source hashes, light/dark/large-text observations and full exact-revision coverage belong to the PR's dated verification update. No old CI pass, fixture output or screenshot mock is substituted. Source scope remains cumulative from Release 1 with the unchanged 80% minimum per module.

## Next

1. Verify actual Android rendering and data-preserving phone behavior; report any device or layout limits.
2. Audit exact-head full CI and publish the dated evidence in the linked PR.
3. Follow normal teammate review/integration; no human AC, Review, merge or release tag is supplied by this work.

## Links

- [User Story and pre-work estimate #68](https://github.com/Axfff/Vibe-Player/issues/68)
- [Standalone/English baseline #66](https://github.com/Axfff/Vibe-Player/pull/66)
- [Overall Discovery Story #36](https://github.com/Axfff/Vibe-Player/issues/36)

**Student ID (CroissantXinny):** 50027204

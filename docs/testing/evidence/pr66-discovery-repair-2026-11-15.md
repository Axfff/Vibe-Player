# PR #66 — search priority, per-song evidence and service status

## Scope and implementation

Refs [Story #36](https://github.com/Axfff/Vibe-Player/issues/36) and [PR #66's review](https://github.com/Axfff/Vibe-Player/pull/66#issuecomment-6874699962). Owner: Shiyuan LIN / CroissantXinny, with Codex-assisted implementation, technical review and automated execution. Pre-work estimate/tasks were recorded on Story #36 at 2026-11-15 20:21:33 SGT. This is the existing Sprint 3 follow-up; Sprint 4 scope, budgets and human acceptance remain unchanged.

- Integrated #62 `c5754bbcfe59bcc4c74ae41c0adb73ea37a282d6` via `5e86f6dd`; retained pending-only fixtures, strict coverage attribution and the latch-based double-tap regression. Target remains `test/discovery-phone-fixture-lifecycle`. Main inspected: `99bc4264f55c2de2ec4c3fdfdeba6fb974f0a4b9`. Future main/#61/#69 integration requires separate verification.
- Repair commit: `c380f1b297586614e2c72eab2d82139789e7a649`. With explicit text, both device/direct iTunes and Python/custom-service ranking compare title/artist query-word matches first, then exact selected catalog tags, then source order. Blank text preserves tag/mood fallback. Deduplication, exclusions, eight results and two-per-artist limits remain.
- Cards show actual matched words/tags, or explicit no-match/missing-metadata wording, through English resources. Evidence uses the confirmed result query rather than later input edits. It derives from each song's title/artist and catalog tags, not arbitrary service prose or inferred audio features. Both transports use the same Android formatter.
- Optional bounded `tags` are saved with new Collection entries. Version 1 remains readable, including old entries without tags; no migration rewrites/deletes existing data. Restored entries retain their original intent, identity and evidence. Older entries explicitly report unavailable tags instead of inventing matches.
- Custom-service status lists each music source separately from cloud photos: live success, untested configuration, failure, disabled, mock and unknown differ. The latest discover response updates catalog state. Reaching a status endpoint alone is not proof of live catalog availability.

## Verified locally

Production/tests correspond to repair commit `c380f1b2`; the following evidence/scope commit changes no executable behavior.

| Check | Actual result |
| --- | --- |
| Main and Android-test APK builds | Passed, pinned JDK 17 / offline Gradle dependencies |
| JVM | 136 passed, zero failures/errors |
| Backend | 168 passed; one existing FastAPI/Starlette deprecation warning |
| Disposable API 33 `emulator-5560` | 26 passed in 20.186 seconds |
| #62 coverage contract | 17 passed; no rule or threshold relaxed |
| Cumulative inventory | 94 production files including both new Android sources and Release 1 |

Android selection: `DeviceCatalogTest`, `DiscoveryPresentationTest`, `DiscoveryFlowTest`, `DiscoveryBoundaryTest`, `PendingDiscoveryPhotosTest`, `EnglishUiTest`. Synthetic catalog rows verify behavior, not live catalog/model quality. Tests ran only on the disposable emulator after verifying `ro.kernel.qemu=1`.

Regressions cover nine candidates where eight Rock rows previously displaced piano; Unicode/case normalization, artist words, repeated words and stable ties; distinct card evidence; unchanged evidence after input edits; save/reopen; legacy collections and malformed optional metadata; independent healthy/unavailable/partial/unchecked/mock catalog states with cloud disabled. Existing English-on-Chinese-system and #62 pending-media regressions pass.

Local evidence is under `verification/pr66-discovery-20261115/` outside Git:

- Main APK SHA-256: `b25f5bd76e6386698c523da7120e7f1e03620d93ba563843abb04e0c515f92b1`.
- Test APK SHA-256: `c66cd6018c828d7582a257886eac0708df27a4cd0b288fa82073abdc6428eebb`.
- Android log SHA-256: `5102bcfac0d04b6e7dffe422b4051d86c7c467833e9c15873a7b35fc20d5071c`.

## Full CI and review boundary

This record is committed before full CI dispatch. The dated final verification appendix on [PR #66](https://github.com/Axfff/Vibe-Player/pull/66) owns the exact tested head, run, independently audited report ZIP digest, per-module covered/total and JUnit counts. It is added only after current-head checks pass; this pre-CI record does not claim that result. CI retains the complete 80% per-module gate and publication conditions. Manual dispatch on this stacked target does not claim an automatic PR publisher run.

Technical self-review found no remaining in-scope blocker after local checks; full CI was pending when this record was created. Axfff's genuine review is requested only after final report audit. No teammate approval, merge, AC checkmark or Story closure is supplied by Codex.

The phone's newer #69 main app and its data were untouched: no install, uninstall, reset or fixture instrumentation in this repair. This adds no live-model, external-song-page, audio-preview or real-phone acceptance claim. Previous evidence/failures retain their original revisions and boundaries. Lawful previews, additional sources and audio mutual exclusion remain Sprint 4 work.

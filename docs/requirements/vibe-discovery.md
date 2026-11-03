# Vibe Discovery — overall Story and staged acceptance

Date: 2026-11-03 (Asia/Singapore). Technical work can be verified by automation; human review and acceptance remain pending. This Story is separate from local-playlist Story #2; its AC1–AC9 are unchanged.

Formal parent: [Story #36](https://github.com/Axfff/Vibe-Player/issues/36).

## Overall Story

As a listener seeking unfamiliar music, I want to select a photo and adjust the atmosphere I want, receive real, verifiable music suggestions, preview them in the app when legitimately permitted or open their official platform otherwise, and save discoveries I like.

The final goal is the complete hybrid experience. Sprint 3 delivers an independently usable manual-intent → real catalog → Android results → official platform → saved Collection increment. Photo recognition remains a Sprint 3 requirement with **Blocked** status until an authorized real model is configured and verified. Only an explicit team scope decision can move it to Sprint 4; a mock does not satisfy it.

## Supporting requirements

| ID | Requirement |
| --- | --- |
| SR1 | User selects one image through the system picker and explicitly consents before any upload. Cancellation/refusal uploads nothing. Manual input is always available. |
| SR2 | Scene and desired music atmosphere are separate finite, editable fields. Explicit user music choices override model suggestions. |
| SR3 | Each production result has a provider/native ID, nonblank title/artist and an official HTTPS URL whose host and song identity are validated. Models cannot supply authoritative songs or URLs. |
| SR4 | Normalize/reject invalid candidates, deduplicate by provider+ID, filter saved/excluded identities and apply deterministic metadata ranking with at most two tracks per normalized artist. State tie order and missing-feature behavior. |
| SR5 | Discovery Collection is separate from MediaStore playlists. Persist source identity/link, a small title/artist snapshot and user intent/market; no original image, model key or audio URL. Restore after process restart. Failed/corrupt storage must be visible and must not silently overwrite prior data. |
| SR6 | No image, refusal, unconfigured model, malformed output and timeout allow manual input. One catalog failure may retain another source's real results. Zero results are explained without synthetic fill. |
| SR7 | Explicit network deadlines, bounded retries/budgets, no duplicate submission, cancellation and stale-response protection. Exact values are implementation choices, not claimed course mandates. |
| SR8 | Keys stay on the server. Restrict upload bytes/pixels/type, orient/resize/re-encode and strip metadata; no image persistence. Disclose actual cloud recipient/retention policy rather than claiming zero retention. Release transport requires HTTPS; loopback HTTP is debug-only. |

## Sprint 3 acceptance criteria

All checkboxes represent **human acceptance**, not implementation progress.

- [ ] **S3-AC1** — An Android entry is reachable even without local songs. Single-photo picker cancellation, refused upload and manual scene/music inputs can be exercised.
- [ ] **S3-AC2 — Blocked (real vision authorization)** — With an authorized real model, photo analysis yields editable finite scene/music suggestions, and user overrides win. Unconfigured/failed/mock analysis never claims real photo understanding. Manual mode is independently usable; it does not pass this AC.
- [ ] **S3-AC3** — At least one production catalog returns real songs end to end. Each card shows provider/native ID and a verifiable source. Aim for eight; show the actual count when fewer qualify.
- [ ] **S3-AC4** — Fixed candidate input deterministically reproduces validation, deduplication, exclusions, ranking, tie order and artist diversity. Explanations cite actual metadata, not invented audio analysis.
- [ ] **S3-AC5** — Open validated official HTTPS song URLs; without an installed music app a browser can handle the same link. Invalid links cannot launch arbitrary targets. Storefront/account/region availability is disclosed separately from URL validity.
- [ ] **S3-AC6** — Save and restore Discovery Collection after stopping/reopening the installed app. Internet IDs never enter `Music.id` or local playlists. Persistence failures are explicit.
- [ ] **S3-AC7** — Offline/timeout/429/5xx/empty/missing fields/invalid model output have explicit states and fallbacks. Cancelled or obsolete requests cannot replace current UI state. Local playback remains functional.
- [ ] **S3-AC8** — Record the actual APK hash, source/backend revision, configuration and emulator/device flow: confirm intent → real result → official platform → save → reopen/restore. Record live vs fixture boundaries and remaining blocks.

## Sprint 4 acceptance criteria (retained hybrid goal)

- [ ] **S4-AC1** — Add a second genuinely authorized catalog, with independent source identity, normalization, partial-failure behavior and current use/territory review.
- [ ] **S4-AC2** — Permit in-app short previews only after project and per-track license/attribution checks, fresh capability and valid stream. Unknown, expired, unavailable or external-only cases offer a valid official link where possible. HTTP 200 and a 30-second duration are not evidence of permission.
- [ ] **S4-AC3** — Preview and local playback are mutually exclusive. Cover audio focus, interruption, next/stop, background, lifecycle and unavailable streams on Android. Do not mark this passed based on the existing developer webpage.
- [ ] **S4-AC4** — Validate the complete photo → editable intent → multi-source results → legal preview/external fallback → save/restore experience, plus richer feedback, on an actual device/emulator with authorized services. Photo AC remains S3-AC2 unless the team explicitly revises scope.

## Tasks and responsibility

| Task | Sprint / linked AC | Implementation owner | Acceptance owner |
| --- | --- | --- | --- |
| D3-01 Maintain this Story, SR/AC and evidence mapping | 3 / all | Discovery agent | Human team |
| D3-02 Reuse backend; strengthen normalization, deterministic rank, exclusions and failure handling | 3 / AC3,4,7 | Discovery agent | Human reviewer |
| D3-03 Android input/results, picker/consent and bounded/cancellable transport | 3 / AC1,2,3,7 | Discovery agent | Human reviewer |
| D3-04 Safe official navigation and independent durable Collection | 3 / AC5,6 | Discovery agent | Human reviewer |
| D3-05 Real vision configuration and smoke | 3 / AC2 | Authorized account owner supplies model/project/budget; Discovery agent integrates | Human team; currently Blocked |
| D3-06 Automated feature tests and device flow with real catalog | 3 / AC1–8 | Discovery agent | Human team |
| E3-01 Include every new production file and Release 1 changes in measured CI, build APK, publish reports and enforce failures | 3 / engineering | CI agent (Agent A); Discovery agent supplies behavioral tests | Human reviewer |
| D4-01 Second catalog and per-track preview authorization/attribution | 4 / AC1,2 | Discovery agent + authorized account owner | Human team |
| D4-02 Android audio mutual exclusion and lifecycle tests | 4 / AC3 | Playback/Discovery implementer, human assignee TBD | Human team |
| D4-03 Full hybrid device acceptance and feedback | 4 / AC4 | Discovery implementer; human assignee TBD | Human team |

Human member assignments and dates require the actual team's confirmation; agent roles above are technical responsibility, not invented contributions. Engineering acceptance separately requires measured coverage of newly added/modified executable code (including Release 1), at least 80%, per the course guideline. Existing CI applies a transparent per-module gate and publishes full reports. Coverage is not a user behavior and does not sign off these ACs.

## Evidence and implementation

- [Sprint 3 plan/status](../sprints/sprint-3.md)
- [API contract](../development/discovery/api-contract.md)
- [Provider review](../development/discovery/provider-verification.md)
- [Existing backend handoff](../development/discovery/handoff.md)
- Public CI coordination: [PR #31](https://github.com/Axfff/Vibe-Player/pull/31); combined validation [PR #32](https://github.com/Axfff/Vibe-Player/pull/32). Neither older report proves coverage of the new Android code.

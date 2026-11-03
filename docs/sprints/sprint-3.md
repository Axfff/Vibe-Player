# Sprint 3 — runnable Vibe Discovery increment

The [overall Story, supporting requirements, Sprint 3/4 AC and task responsibility](../requirements/vibe-discovery.md) are the requirements source. Formal acceptance remains with the human team. Legal preview and Android audio mutual exclusion remain assigned to Sprint 4; the final hybrid goal is unchanged.

Published parent: [Story #36](https://github.com/Axfff/Vibe-Player/issues/36). Both sprints' retained AC and task assignments are recorded there.

## Inherited state (2026-11-03)

Reused `feat/ai-photo-discovery` at `fa86950d0f8955a8b0718de95f7a0509dedee832`, in the existing independent `Vibe-Player-discovery` worktree. Inspected all repository-listed local worktrees. No additional AGENTS.md was found in this workspace/ancestors. Latest remote main `dc17523be591a72f9fefa98536c61689e4d99ad1` has been merged into this branch, preserving Release 1. `release1` remains `81b22ee5868e0cd5ac6a75472296caab3753a7b5`.

| Module | Inherited actual state |
| --- | --- |
| FastAPI service / model adapter / schema / retry/budgets | Implemented with automated tests; locally runnable |
| iTunes | Previously live search and lookup verified; new bounded live smoke reproduced genuine IDs and storefront HEAD redirect to `/cn/new` |
| Jamendo | Adapter exists; no authorized client/project review, no live completion claim |
| Vision | Adapter exists; mode unconfigured, cloud authorization false, budget zero; S3-AC2 **Blocked** |
| Web developer demo | Existing mock/fixture and live diagnostics, not the Android deliverable |
| Android Discovery / Collection | Absent at inherited SHA; current implementation in progress |
| Coverage | Existing backend/web coverage commit `c87d4e6d`; CI agent owns public Gradle/workflow/scope integration. Old measurements exclude new Android code. |

## Delivery sequence and assignments

1. Discovery agent: D3-01 requirements and traceability; D3-02 backend rank/link identity and error cases.
2. Discovery agent: D3-03 Android input/results/photo fallback; D3-04 official links and persistent Collection.
3. Discovery agent: D3-06 behavior tests and disposable emulator real-catalog flow; account owner + Discovery agent: D3-05 only with explicit configuration/authorization.
4. CI agent: E3-01 shared Gradle/workflow, full production inventory, fresh same-SHA Android/Python/web coverage and report publishing. Feature agent supplies tests and exact feature SHA. No concurrent edits to public build files.
5. Humans: review implementation and evidence, assign named members, decide actual AC sign-off. No automated merge or acceptance.

## Sprint 4 retained work

D4-01 second authorized source and legal previews (S4-AC1/2); D4-02 audio focus/local-preview mutual exclusion (S4-AC3); D4-03 full mixed experience and richer feedback (S4-AC4). Photo recognition remains blocked in Sprint 3 rather than being silently moved.

## Source constraints

Read the user's `02-SPRINT3-DISCOVERY-PROMPT.md`, existing backend/provider/API docs and the two-page `Guidelines for DSAA2044 Sprint 3 (2026-27 Fall).pdf`. The course requires a working, testable increment, staged Story/AC/tasks in GitHub, APK generation, automated tests and at least 80% coverage of new/modified executable code including Release 1. It does not prescribe new databases, AI, specific timeout values or classroom slides. Historical research files 06–10 were not present in the provided starter archive; no acceptance numbers are invented from them.

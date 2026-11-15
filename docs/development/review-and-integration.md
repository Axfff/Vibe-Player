# Review and integration operations

Refs [engineering Issue #71](https://github.com/Axfff/Vibe-Player/issues/71). The
[development workflow](workflow.md) remains the policy source; this page explains
the checks and where to find current evidence. It does not record approval.

## What the checks establish

| Check | Evidence | Does not establish |
| --- | --- | --- |
| `build` | APK, executed JVM/Android/backend/web tests and the complete cumulative coverage gate at the reported head | Peer approval, product acceptance or a later integration |
| `Team line coverage` | Validated provenance and full reports for that head; original 80% per-module threshold | A live model result when its optional test was skipped |
| `PR integration` | Clean exact head contains the event's exact target base; JSON receipt records both commits and the resulting head tree | Full tests, review, or freshness if the target advances later |

Android CI accepts PRs to **all target branches**, including feature/test/fix
parents. Its existing push filters remain unchanged to avoid adding duplicate
full builds for every local working branch. The separate integration workflow
also reacts to PR edits so retargeting checks the new base. It has only
`contents: read`, does not use `pull_request_target`, and never writes branches,
Reviews, issue checkboxes or rulesets. The coverage publisher's existing
validation and permissions remain unchanged.

The integration guard requires base to be an ancestor of head, even for a
conflict-free merge. Therefore the head tested by full CI already contains all
target content: a subsequent merge adds no untested source combination. Keep the
administrator's **require branches to be up to date** setting enabled so that
later target changes cannot reuse this earlier result. This is a deliberately
strict workflow; it may require another integration commit and CI run.

GitHub may not run a `pull_request` workflow when a merge conflict exists. Resolve
the integration and push normally; an absent check is not a pass. Do not switch
to a privileged event to execute unreviewed code. See GitHub's
[trigger documentation](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow)
and [strict required checks](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches).

## Refresh a stacked PR

1. Read the latest target, head, review comments and local changes. Identify all
   production changes inherited from unmerged parents. A two-file child diff
   does not approve its entire parent branch.
2. In an independent checkout, merge the agreed target into the existing branch,
   preserving history. Resolve actual source conflicts, then regenerate
   `docs/testing/coverage-scope.json` using
   `python scripts/coverage/coverage.py scope --write`. Do not select an old
   manifest wholesale or reduce scope/thresholds.
3. Commit the complete candidate. Run the integration guard with the full base
   and head SHAs, relevant tests, and full CI. Inspect the raw reports and
   integration receipt; retain failures/skips and distinguish device evidence
   belonging to a different integration SHA.
4. Address outstanding technical findings, update the dated PR verification and
   request a non-author review. If retargeting changes the diff or the head does
   not include the new target, refresh and verify again. The lightweight edited
   event is not a substitute for full CI on a new candidate.

Local preflight from a clean repository root:

```text
python scripts/test_pr_integration.py
python scripts/pr_integration.py --base <full-target-commit> --head <full-current-head>
python scripts/coverage/coverage.py scope
python scripts/test_coverage_contract.py
```

The JSON receipt is `artifacts/process/pr-integration.json`, uploaded as
`pr-integration-<run_id>`. It records an integration precondition; check full CI
separately. Existing branches must incorporate this workflow change before its
new checks are available. Do not claim that merging the process PR retroactively
reran or certified every open stacked PR.

## Administrator rollout

After the workflow is reviewed and installed, confirm the actual check names
exist, then configure the existing main ruleset:

- Enable enforcement; require PRs and at least one non-author approval.
- Dismiss approvals after reviewable changes, require approval of the latest
  push and resolution of blocking review conversations.
- Require `build`, `Team line coverage` and `PR integration`, with strict target
  freshness. Keep force pushes, deletion and routine bypasses disabled.
- Verify settings and the merge UI on an unapproved/unverified PR without
  attempting a prohibited merge. The code change cannot enable rulesets for a
  writer who lacks administrator permission.

At the November 15 audit, main was unprotected and the disabled ruleset had
zero required approvals and no required checks. That dated observation is not a
claim about settings after an administrator changes them.

## Read the current state without rewriting history

Use each PR's latest head, unresolved findings and checks as the current source.
An older “Ready for peer review” paragraph is superseded by a later actionable
review. Keep the earlier tested SHA/results; append a short dated correction and
link the finding instead of repeating logs or treating a bot comment as approval.

The November 15 inspection provides these entry points:

- Main `99bc4264` contains #43 and #49. Their review records need a dated
  non-author follow-up: #43 has only a comment Review; #49's approval predates
  its later fixture/evaluation changes. Do not invent pre-merge approval.
- [#50](https://github.com/Axfff/Vibe-Player/pull/50) records verified review fixes
  at `bbb029d1`; [#61](https://github.com/Axfff/Vibe-Player/pull/61),
  [#62](https://github.com/Axfff/Vibe-Player/pull/62),
  [#66](https://github.com/Axfff/Vibe-Player/pull/66),
  [#69](https://github.com/Axfff/Vibe-Player/pull/69) and
  [#70](https://github.com/Axfff/Vibe-Player/pull/70) own their outstanding
  technical findings. These are separate product changes, not part of #71.
- The primary later Discovery phone evidence is the November 13 appendix in
  [#69](https://github.com/Axfff/Vibe-Player/pull/69#integration-verification--2026-11-13-asiasingapore),
  for combined head `a99ad4e6`. It records local inference, phone HTTPS catalog,
  Apple target navigation and saved Collection recovery, with setup and device
  limits. It does not certify current main or a fresh installation.
- [#54](https://github.com/Axfff/Vibe-Player/pull/54) and
  [#60](https://github.com/Axfff/Vibe-Player/pull/60) virtually merged into
  `99bc4264` without changing its tree. Authors can record an incorporated
  disposition while preserving contribution and test history.
- [#17](https://github.com/Axfff/Vibe-Player/issues/17) owns human Release 1
  acceptance; [#19](https://github.com/Axfff/Vibe-Player/issues/19) owns the
  actual retrospective. Named human owners/reviewers, product decisions and
  target dates belong in the existing Stories. Release ownership is already
  recorded in the [team registry](../../README.md#team-members); an agent is
  implementation assistance, not a substitute for the accountable member.

These links identify primary records, not a second live status board. Historical
estimates remain as recorded; newly planned work gets a dated estimate before
implementation. Formal AC and Review decisions stay with the actual humans.

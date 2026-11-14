# Evaluation status

All 80 labels are **provisional** and authored by Codex during initial curation.
The agent viewed the actual downloaded images and contact sheets. Music grouping
uses the composer's descriptions and public tags; **no human listening or human
label review is claimed**. These are experimental priors, not calibrated emotion
diagnoses or ground truth.

Each group has 8 strong matches, 4 acceptable/weak matches, 4 hard distractors,
and 4 mismatches. Role describes sampling, while relevance is an independent
0–3 judgement. This initial version gives acceptable candidates 2 and hard
distractors 0 or 1. All four groups therefore have a uniform-random expected
Precision@5 of 12/20 = 0.60, and expected hard-distractor count 1.0.
These are mathematical baselines, not measured recommendation results.
Photo composition, action, setting and implications
of emptiness/decay matter alongside color. A single photo can legitimately have
different roles in different groups.

- [Frozen initial labels](expected-relevance.csv)
- [Per-track differences and provisional-prior boundaries](per-track-overrides.csv)
- [Protocol and seed](protocol.json)
- [Bright answers](bright.html), [calm answers](calm.html),
  [melancholic answers](melancholic.html), [energetic answers](energetic.html)

`freeze.json` locks manifest, candidate order, playlist order, protocol and CSV
labels **before any recommendation run**. No recommendation was run for this
delivery. Changes after freeze require a separately named version plus reviewer,
reason and time; never overwrite labels after viewing model results.

The representative track is the first ordered song in each playlist. Candidate
inputs `case_01`–`case_04` select those four tracks. The remaining 16 songs have
individual difference notes but no independently established photo relevance;
using the same library for them initially uses a **playlist-level prior only**.

The observed `MoodAnalyzer` API at PR #46 head `4776e4e1` decodes at most the
first 30 seconds of a single track. It discards incomplete mel patches and skips
near-silent patches. Full-file decoding in the fixture validator proves media
integrity; it does not mean the model analyzed the complete song. Openings are
non-silent in measured PCM; the composer-described style and later changes still
need human listening confirmation. Five Armies builds over time; Crusade's later
brass section and Blue Paint's late variation are outside the initial window.

**Recommendation evaluation: Blocked.** Latest main has no music-to-image-content
ranking flow. PR #46 exposes an audio signal, while its caption experiment uses
human-assigned vibes. Feeding these expected labels to that probe would leak the
answers. No model weights or paid/cloud inference were used here; no Top-5 or
accuracy result is fabricated.

Once a real image-content recommender is available, pass only one exported
`case_XX/input.json`, its audio bytes and its photo bytes. Do not grant it access
to this directory, the attribution/source manifest, or previews. Supply actual
Top-5 IDs to `scripts/fixtures/evaluate_rankings.py`, with the recommender's source
SHA. It reports provisional Precision@5 at relevance >= 2 and hard-distractor
count, alongside the exact uniform-random expectations. Random expectation is
not an executed model result. Four small curated groups and one music composer
cannot establish generalization.

Human tasks: listen to each opening; review all 80 ratings without consulting
rankings first; review ambiguity and per-song variation; record reviewer and time
in a new label version; then run the frozen evaluation on the real implementation.

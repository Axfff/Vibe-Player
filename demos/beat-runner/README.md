# Raptor Radio

A local web prototype for Vibe-Player's Release 3 music-driven runner.

## Run on this Mac

From this directory, use Python 3 and open [the local game](http://127.0.0.1:8765/).

```sh
python3 -m http.server 8765 --bind 127.0.0.1 --directory public
```

Choose a soundtrack, then select **Start run**. Use **Space**, **Up Arrow**, **Jump**, or a tap on the game to jump. Use the pause button or **P** to pause and resume. **Run again** starts from the beginning of the track. Your best score is stored in this browser.

**Open local audio** accepts browser-decodable files up to 35 MB and five minutes. The browser decodes audio and analyzes up to two channels in a worker. The local HTTP server only serves the game files and the trained weights. The browser keeps selected files and intermediate audio in memory.

## What the music controls

Afterglow, Signal Hop, and Meteor FM are original synthesized loops. The worker analyzes the decoded samples for both built-in and imported music. The [trained classifier](model/README.md) chooses Drift, Groove, or Surge from each eight-second window. Predictions below 45% model confidence or silent windows use the Neutral scene. The model score is an uncalibrated class probability.

The separate beat tracker estimates the dominant tempo from recurring audio onsets. The audio output clock drives pulses on the horizon and the beat indicator. If the classifier changes its prediction, the scene switches on the next detected beat. If the tracker finds no steady pulse, visual pulses stay off and the classifier can still select a scene. Changes of tempo inside a recording can reduce alignment. Reduced-motion preferences disable the beat pulse.

## Release 3 story draft

As a Vibe-Player listener, I want to play an endless runner whose world changes with the song, so changing music gives me a different play experience.

- Start, jump, pause, resume and retry with keyboard or touch. Running into an obstacle ends the run. Retrying resets the run and restarts the music.
- Offer original built-in music and accept a decodable local audio file. Handle missing rhythm without inventing beats, and preserve the current track on a failed import.
- Run an audio-style classifier on this Mac and use its predictions to change the game scene. Drive visual accents from detected beat positions on the playback clock.
- Keep music and model inputs local. Carry reproducible training, held-out evaluation, latency and verification evidence into the Android implementation.

Tasks: build the runner and controls, train and evaluate the audio classifier, connect beat detection and inference to the game, verify the Mac prototype, and hand off the portable model contract for Android. Provisional estimate for the Mac prototype: 8 engineering hours, including local checks and evidence. Team owner, reviewer, TA discussion, sprint option and issue number are to be confirmed in GitHub. Proposed placement under Option B is a Sprint 5 Android feature and Sprint 6 verified improvements in model quality or beat timing.

Base revision: `8fa17200a86396c576391d2404dc154d92baff40`.

## Verification

Run `npm test` and `npm run coverage` from this directory with Node 24 or later. The coverage command fails below 80% line coverage for the web demo. To save its LCOV report, run:

```sh
node --experimental-test-coverage --test-coverage-include='public/*.js' --test-coverage-lines=80 --test --test-reporter=lcov --test-reporter-destination=/tmp/raptor-radio.lcov
```

- **Automated on macOS.** All 36 tests passed. Node V8 reported 1,446 of 1,464 lines exercised (98.77%) across all nine production JavaScript files, including the page controller and worker. The suite checks game physics, audio-clock pause and resume, slow-start and track-selection races, classification, beat pulses, and renderer behavior.
- **Beat verification.** The tracker recovered all 144 composed beat timestamps within 8 ms. There are tests for shifted intros, syncopation, missing hits, and silence. A seeded 100-case fuzz test varies tempo, gain, sample rate, and phase. A metamorphic test varies gain, polarity, and sample rate while preserving beat alignment. Targeted tests detected three temporary injected faults involving half-tempo selection, sustained bass, and a 50 ms beat offset. Beat tracking follows the general onset and tempo approach described in [Ellis, 2007](https://www.ee.columbia.edu/~dpwe/pubs/Ellis07-beattrack.pdf).
- **Browser on this Mac.** Verified start, jump, pause/resume, mute, collision, retry, all three scene styles, and a Drift-to-Surge transition with a visible beat pulse during a run. A real browser WAV decode accepted a silent file and reported no beat. A malformed file kept the previous track and displayed a decoder error. The 390 px layout fit without horizontal scrolling.
- **Model quality.** On 144 windows from 72 artist-held-out tracks, the classifier achieved 56.94% balanced accuracy against a 33.33% constant baseline and a 45% prototype target. The fitted model is 1,876 bytes. A local Node run measured 18.56 ms p95 per window including feature extraction. These are Mac feasibility results using track-level arousal labels. [Model evidence and reproduction](model/README.md)

## Course release checkpoint

The Mac demo covers local inference, dataset preparation, training, held-out evaluation, and a testable interaction. Release 3 also requires a working new feature in the Android app. For that milestone, reuse the versioned weights and preprocessing contract, verify prediction parity and playback synchronization on Android, and retain the APK and current-revision CI and coverage evidence. Record the agreed sprint option, linked GitHub stories and PRs, TA discussion, non-author reviews, and the report and presentation requirements from Canvas. [Official Release 3 requirements](https://docs.google.com/document/d/1NF1KM8L02LOej5EYfzitkHz-59G9dBiLc6bMKApGjW0) · [Canvas kickoff](https://hkust-gz.instructure.com/courses/3865/announcements/36324)

The user supplied the feature direction and Mac-first scope. Codex assisted with design, implementation, training, original soundtrack synthesis, testing, and evidence.

## Android music analysis increment

As a listener, I want to analyze a song in my Android library so that I can see the tempo and scene the runner would use for that song.

- **AC1** A song's overflow menu opens an analysis dialog without starting or changing playback. The opening excerpt is decoded and analyzed on the device using the same fitted weights as the web demo.
- **AC2** An excerpt with a steady rhythm returns BPM and beat timestamps. The result also carries time-coded scene predictions in eight-second windows. The dialog shows these scenes and falls back to Neutral for low-confidence windows.
- **AC3** Silence gives a neutral scene and no steady beat. Unavailable audio yields an error. Dismissing analysis cancels it. Inputs longer than 30 seconds are capped at the first 30 seconds.
- **AC4** The debug build bundles the weights and installs on a disposable Android emulator. Record the tested revision and checks. Retain the APK and checksum when packaging a release.

Tasks: reuse local audio decoding, port beat and scene inference, connect the song menu, and verify against JavaScript and on Android. Provisional estimate on 2026-11-21: 8 engineering hours with the trained model and an available emulator. Sprint 5 under the proposed Option B. Owner and non-author reviewer are to be confirmed by the team.

The Android increment puts a native analysis dialog in the music library. The game remains in the web demo at this checkpoint. [Android developer guide](../../docs/development/raptor-music-analysis.md)

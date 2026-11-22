# Android Raptor Radio game

## Sprint story draft

As a Vibe-Player listener, I want to play an endless runner whose world follows my music so that changing songs changes the game experience.

- **AC1** Open the game from the library and choose any of the bundled tracks. The game can also use a selected library track or audio document.
- **AC2** Start, jump, pause, resume and retry with touch controls. An obstacle collision ends the run, and the best score persists in the app.
- **AC3** The on-device analyzer selects scenes from the track and detects beat times. The game reads the playback position for beat pulses and scene changes. Audio with no detected beat remains playable.
- **AC4** Leaving the game pauses playback. Unreadable audio is recoverable by choosing another track. The ordinary library, queue and playback controls remain usable after leaving the game.

Tasks: port the game and renderer, connect analysis and playback, add the library and soundtrack controls, and verify the feature on Android. Provisional estimate on 2026-11-22 is 12 engineering hours with working on-device analysis and a disposable emulator. Proposed Sprint 5 feature under Option B. The team can confirm the owner, reviewer and sprint assignment.

The game builds on the [Android music analysis API](raptor-music-analysis.md) in [PR #81](https://github.com/Axfff/Vibe-Player/pull/81).

## Play the demo

Open **Raptor Radio** from the library actions. Start with Afterglow, Signal Hop or Meteor FM. Tap the stage or **Jump** to clear obstacles. Use **Pause** and **Resume run** to continue the same run, or **Run again** after a collision.

To use a library song, long-press it and select **Play Raptor Radio**. The game also accepts an audio document through **Open local audio**. It analyzes and loops up to the first 30 seconds of the chosen track. The game pauses if it leaves the foreground and saves the best score locally.

## Implementation and verification

- `RaptorRun` handles physics, collisions and scoring in 480 × 222 world coordinates. `RaptorStageView` draws the scene in those coordinates at the view's scale.
- `RaptorRadioActivity` sends each chosen URI to `RaptorTrackAnalyzer`. It gives the returned duration to `RaptorPlayback` and uses the player's position in seconds with `RaptorTimeline`. Scene changes with a steady rhythm land on the next detected beat. If there is no steady beat, the model's windows still select scenes by time.
- Audio focus loss or a new library playback command during a run pauses the game. The game retains its own player and keeps song selection and scoring separate from the library queue.
- The Android soundtrack clips are generated from the original web synth. From the repository root, run `node demos/beat-runner/tools/render-android-tracks.mjs` with Node 24+ and FFmpeg to regenerate and check the assets.
- Run `:app:testDebugUnitTest` for simulation and timeline tests, and `:app:connectedDebugAndroidTest` on a disposable API 33 emulator for launcher, gameplay, artwork, decoding and playback checks. [Android verification setup](workflow.md#from-source-to-a-running-app)

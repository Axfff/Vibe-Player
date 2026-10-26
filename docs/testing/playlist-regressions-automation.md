# Run the playlist Android regressions

The instrumentation suite covers RG01–RG07 in [the existing plan](custom-playlists.md).
It uses real MediaStore WAVs, the app's repository, Activity, playback service and
Android MediaPlayer. Screenshots, decoder/player state assertions, commands and
logs provide the execution evidence. A passing build alone is not a device result.

## Disposable emulator

Use JDK 17, the existing SDK 33 build configuration and an Android API 29+ emulator.
The fixture resets the **debug app's preferences** before each test. Run it only on
a disposable emulator; a read-only AVD instance provides temporary data overlays:

```bash
"$ANDROID_HOME/emulator/emulator" -avd YOUR_AVD -read-only -no-snapshot-save \
  -no-boot-anim -no-window -no-audio -gpu swiftshader
```

Wait for boot, choose its actual serial from `adb devices -l`, then run from the
repository root:

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_SERIAL=emulator-5554
bash scripts/check-playlist-regressions.sh
```

The script builds and installs **both** current APKs before executing the suite.
This avoids testing new test code against a previously installed upstream app.
It fails on JUnit failures, crashes, initialization errors or zero executed tests,
even if Android's `am instrument` command itself exits zero.

Evidence defaults to `project/app/build/playlist-regression-evidence/` (ignored
build output). Set `REGRESSION_EVIDENCE_DIR` to keep independent runs. Set
`REGRESSION_TEST_CLASS` to an exact class or `Class#method` for a focused rerun.

## Interpretation

- RG03 rotates the actual display and requires a new Activity with the same live
  MediaPlayer. It also exercises stopped/background and resumed Activity states.
- RG05 physically removes owned audio without refreshing the cached app library.
  Failure and Pause/Stop are dispatched within one main-loop turn where needed,
  so pending-recovery cancellation is tested without unreliable human timing.
  Explicit commands are checked through the service and receiver entry points.
- RG06 injects a controlled `SecurityException` through a test Application's
  ContentResolver accessor to exercise the real scanner's error path. Its
  successful-empty checks use real MediaStore and live UI. Actual Android
  permission revocation is a separate device check because it kills the runner.
- RG07 uses a hidden-album preference as fixture preparation, then checks real
  filtering, the add-song picker, saved playback, Songs, Favorites and Queue.
  This does not assert that the Hide popup gesture was exercised.
- Host audio is disabled in the headless emulator. Assertions inspect the actual
  Android player's state and advancing position; they do not claim audible host
  verification.

Keep failed evidence that explains a fix. Record actual test/source revisions and
APK hashes in [the regression result record](custom-playlists-regression-results.md).
TC01–TC13 and human teammate review are tracked separately.

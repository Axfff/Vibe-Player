# Vibe-Player — course project

[![Android CI](https://github.com/Axfff/Vibe-Player/actions/workflows/android.yml/badge.svg)](https://github.com/Axfff/Vibe-Player/actions/workflows/android.yml)

Vibe-Player is the team's Android local music player course project, based on [Music Player GO](https://github.com/enricocid/Music-Player-GO). It adds persistent, named playlists to the upstream player. This repository contains the application, course workflow and verification evidence.

## Features

- Browse local music by song, artist, album or folder, and search your library.
- Create, rename and delete multiple playlists; add or remove songs and play them in insertion order. Playlists survive app restarts; removing entries or deleting a playlist keeps the audio files.
- Keep saved playlist entries when audio becomes unavailable, and refresh availability after restoring files or permissions.
- Use the inherited queue, favorites, equalizer, sleep timer, playback controls and light/dark themes.

Android 5.0 (API 21) or later is required. The application still uses the upstream app name and icon; choosing new branding is tracked in [#22](https://github.com/Axfff/Vibe-Player/issues/22).

## Download and try

Get the course evaluation APK and `SHA256SUMS.txt` from [Release 1 (`release1`)](https://github.com/Axfff/Vibe-Player/releases/tag/release1). The release contains the playlist feature merged in [PR #3](https://github.com/Axfff/Vibe-Player/pull/3). It is a debug build (version `4.4.24` / `187`, package `com.iven.musicplayergo.debug`). The F-Droid and Google Play listings for Music Player GO distribute the upstream application.

For builds of other revisions, open a successful [Android CI run](https://github.com/Axfff/Vibe-Player/actions/workflows/android.yml) and download its `app-debug` artifact while available. CI artifacts are retained for 14 days; the Release 1 APK is attached separately to its release.

After installation, allow music/audio access, then open **Playlists** to create a collection and add songs. If songs are missing, use **Missing songs?** and **Scan local music**. Android must index copied files before the app can discover them; see the [FAQ](FAQ.md).

## Build and test

1. Clone this repository:

   ```sh
   git clone https://github.com/Axfff/Vibe-Player.git
   cd Vibe-Player/project
   ```

2. Open the `project/` directory in Android Studio. Configure the Gradle JDK as **JDK 17**, install **Android SDK Platform 33**, and set your SDK location through Android Studio or a local `project/local.properties` file (`sdk.dir=...`). Keep machine-specific paths out of commits. Use the checked-in Gradle wrapper (Gradle 8.0).

3. From `project/`, run the same unit-test and debug-build tasks as CI:

   ```sh
   bash ./gradlew --no-daemon --console=plain :app:testDebugUnitTest :app:assembleDebug
   ```

   On Windows PowerShell, replace `bash ./gradlew` with `./gradlew.bat`. For shell builds, configure `JAVA_HOME` to your JDK 17 installation.

The APK is written to `project/app/build/outputs/apk/debug/app-debug.apk` relative to the repository root. JVM test reports are under `project/app/build/reports/tests/testDebugUnitTest/`.

Device regression instructions and evidence are in the [playlist test plan](docs/testing/custom-playlists.md) and [final integration report](docs/testing/playlist-final-integration.md). Android instrumentation is a separate check requiring a test device or emulator.

## Course documentation and status

- [Course workflow and documentation](docs/README.md)
- [Sprint 1 evidence](docs/sprints/sprint-1.md) · [Sprint 2 progress](docs/sprints/sprint-2.md)
- [Release 1 review index](docs/releases/release-1.md)
- [Playlist requirements — Story #2](https://github.com/Axfff/Vibe-Player/issues/2)
- [Feature research and decisions](docs/thinking/README.md)
- [Implementation record](docs/development/custom-playlists-implementation.md) · [Coding-agent guide](docs/development/custom-playlists-agent-guide.md)

Release 1 is published. Its CI passed 30 JVM tests and built the evaluation APK. The matching application source at `8f2e4616` passed 29 Android integration tests; environments, revisions and UI checks are recorded in the [final integration report](docs/testing/playlist-final-integration.md). These are recorded results for those revisions.

Human teammate review and formal AC sign-off remain tracked in [#17](https://github.com/Axfff/Vibe-Player/issues/17), broader device/accessibility validation in [#18](https://github.com/Axfff/Vibe-Player/issues/18), and contribution confirmation and retrospective work in [#19](https://github.com/Axfff/Vibe-Player/issues/19). Unindexed-file discovery is tracked in [#4](https://github.com/Axfff/Vibe-Player/issues/4). Manual playlist reordering, import/export and cloud sync are outside the current playlist scope.

## Contributing and reporting problems

Use [this repository's issues](https://github.com/Axfff/Vibe-Player/issues) for bugs and feature requests. For a bug, include the tested revision or APK, Android version, reproduction steps, expected/actual behavior and relevant logs or screenshots.

Create a feature branch and open a pull request against `main`, using the [PR template](.github/PULL_REQUEST_TEMPLATE.md). Link the relevant issue and record the checks actually run. See the [course workflow](docs/README.md) for Story/AC tracking, teammate review and evidence conventions.

Translation changes for this fork can be submitted in a PR under `project/app/src/main/res/values-*/`. Preserve resource names and format placeholders. The upstream Weblate project maintains upstream translations.

## Upstream credits and license

Based on **Music Player GO** by **Enrico D'Ortenzio** — upstream copyright notice: **2024 © Enrico D'Ortenzio**. Upstream contributors and third-party authors are credited in [CONTRIBUTORS.md](CONTRIBUTORS.md) and [LIBS.md](LIBS.md).

This repository retains the [GNU GPLv3 license](LICENSE.md). The inherited [supported-format reference](FORMATS.md) and [upstream privacy policy](PRIVACY_POLICY.md) are retained for reference; the latter describes Music Player GO and its original developer. Direct questions about this course fork to [Vibe-Player issues](https://github.com/Axfff/Vibe-Player/issues).

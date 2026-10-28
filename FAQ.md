# Vibe-Player — Frequently Asked Questions

Vibe-Player is the course fork of [Music Player GO](https://github.com/enricocid/Music-Player-GO). This FAQ describes this repository; upstream credits remain in the [README](README.md#upstream-credits-and-license).

## Where can I download the course project?

Download the evaluation APK and checksum from [Release 1 (`release1`)](https://github.com/Axfff/Vibe-Player/releases/tag/release1), or build from source using the [README instructions](README.md#build-and-test). The F-Droid and Google Play listings for Music Player GO distribute upstream builds.

The evaluation APK uses the debug package `com.iven.musicplayergo.debug`. The installed app still uses the upstream name and icon; new branding is tracked in [#22](https://github.com/Axfff/Vibe-Player/issues/22).

## How do I create and use a playlist?

Open **Playlists**, choose **New playlist**, enter a name and save. Open that playlist and choose **Add songs** to select tracks from your local library. Tracks play in the order you add them; duplicate tracks are rejected. Press **Play** to start the saved collection. Changes apply the next time you press **Play**.

Playlists are saved on this device and survive app restarts. Removing a track or deleting a playlist keeps the audio files and other playlists. Manual reordering, import/export and cloud sync are outside the current scope.

## Why are copied songs missing?

Allow music/audio access in Android settings, then use **Scan local music**. Also check hidden artists, albums and folders. **Missing songs?** in the app explains these steps.

Each scan checks up to 1,000 recent audio files already accessible through Android's media library. It cannot discover every file that Android has never indexed. If songs are still missing, use the system Files app to copy them into **Music**, wait for Android to index them, then scan again. This discovery limitation is tracked in [#4](https://github.com/Axfff/Vibe-Player/issues/4).

**Refresh** inside a playlist updates its library view. Saved entries are kept when files or permissions are unavailable; restore access and refresh before trying them again.

## Which audio formats are supported? Can I stream music?

Playback uses Android's native MediaPlayer support; see the inherited [supported formats reference](FORMATS.md). This player uses your local music library and does not provide online streaming.

## Can I change the theme or use an equalizer?

The inherited settings offer light, dark/pure black and automatic themes, plus accent colors. The equalizer is available from **Now Playing**. The app can use a system equalizer when available or the built-in equalizer selected in settings; device support varies.

## Does the app need internet access?

The [Android manifest](project/app/src/main/AndroidManifest.xml) does not declare the `INTERNET` permission. Music access lets the player read your local library; saved playlists are stored locally in app preferences. The retained [upstream privacy policy](PRIVACY_POLICY.md) describes the original Music Player GO project and developer.

## How do I report a bug or contribute?

Use [Vibe-Player issues](https://github.com/Axfff/Vibe-Player/issues), including your revision/APK, Android version, reproduction steps and relevant logs or screenshots. Check existing issues first.

For code, documentation or translation changes, open a PR against `main` and follow the [course workflow](docs/README.md). Translation resources for this fork live under `project/app/src/main/res/values-*/`; submit changes here rather than relying on the upstream Weblate project to update this fork.

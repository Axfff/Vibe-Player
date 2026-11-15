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

The current Android app's [manifest](project/app/src/main/AndroidManifest.xml) does not declare the `INTERNET` permission. Music access lets the player read your local library; saved playlists are stored locally in app preferences. The separately run [Discovery service and browser demo](services/discovery/README.md) use the network according to their configured providers and authorization. The retained [upstream privacy policy](PRIVACY_POLICY.md) describes the original Music Player GO project and developer.

## How do I report a bug or contribute?

Use [Vibe-Player issues](https://github.com/Axfff/Vibe-Player/issues), including your revision/APK, Android version, reproduction steps and relevant logs or screenshots. Check existing issues first.

### Does Music Player GO have or use an equalizer?

Yes!
The equalizer can be found in the "Now Playing" dialog.

Note however that by default MPGO tries to open the system equalizer (**if it exists**)!
You can force the use of the built-in equalizer from the app settings! 

Note: in case of built-in equalizer faults, MPGO tries to fall back to the the system equalizer (**if it exists**) found on the device.

PS: don't blame the devs if equalizer is not working properly, blame the device manufacturer instead!

:)


### What is audio focus?

It is an Android feature to moderate playback in media apps that prevents multiple media apps from playing at the same time.
Every media app should deal with audio focus. This option is ON by default so that MPGO can:
1. **Pause** if another app requests audio focus.
2. **Lower** its volume when a new message is received.


### Does Music Player GO play music tracks from online sources?

Playback in the current Vibe-Player increment uses the local library. Vibe Discovery retrieves online catalog metadata and opens official platform links. Legally permitted in-app previews and their audio interaction with local playback remain Sprint 4 work.

### Does Music Player GO access the Internet?

The Vibe-Player course fork requests INTERNET for its optional Discovery service. Manual searches send the confirmed query/country to the configured catalog through that service. A photo is sent only after explicit upload consent and with an authorized live model. Local playback remains available independently. See the [Discovery privacy information](PRIVACY_POLICY.md#vibe-player-course-fork-discovery-data-handling-2026-11-03).


### How does Music Player GO categorized music tracks?

By artists, folders and albums.
Artists' music are in turn categorized by albums.
Folders can be considered albums.
Each album consists of tracks of the same album.


### Can I exclude folders from my library?

Filter them once the app has loaded.


### How does MPGO handle shuffled lists?

To avoid performance issues queue built from shuffled lists is limited to 250 items.


### Sometimes MediaButton does not work

Make sure headphones are properly connected.


### Android 12 and battery optimization: media player stops working

"Apps that target Android 12 (API level 31) or higher can't start foreground services while running in the background, except for a few special cases. If an app tries to start a foreground service while the app is running in the background, and the foreground service doesn't satisfy one of the exceptional cases, the system throws a [ForegroundServiceStartNotAllowedException](https://developer.android.com/guide/components/foreground-services#background-start-restrictions)."

As mentioned in the [Android documentation](https://developer.android.com/guide/components/foreground-services#background-start-restriction-exemptions) please consider disabling battery optimization for MPGO in system settings to solve the issue:
Apps > All apps > Music Player GO > Battery > Unrestricted

### What about features requests/crash reports?

1. Unfortunately, not all requests can be taken into consideration for a variety of reasons:

   - Job/real life.
   - Complex feature clashing with the KISS philosophy behind the app: Keep it snimple snupid :)
   - The rather poor developer is rocking a ~~Xiaomi Mi A1~~ Xiaomi Mi A2 lite as the only test device. A good computer could ensure emulator usage.
   - Keep in mind that the app is built around the **developer's usage needs** and is shared for the love of libre software.
   - Sometimes time is just short…
   - Cyclic lack of motivation/interest in Android development.


2. A crash report is useful only when a logcat and/or a detailed explanation on how to reproduce the bug are attached. If you don't know how to proceed, contact the developer to receive guidance.


### Does this app keep any info or data about me or my device?

Discovery keeps its saved source identities, title/artist snapshots, user music intent and hidden identities locally. It also retains the selected service address. It does not persist the photo, model key or audio URL. Searches and consented photo analysis have the network boundaries described in the [privacy information](PRIVACY_POLICY.md); this feature adds no analytics SDK or user account.

Pose other questions by creating issues, and we will answer them.
For code, documentation or translation changes, open a PR against `main` and follow the [course workflow](docs/README.md). Translation resources for this fork live under `project/app/src/main/res/values-*/`; submit changes here rather than relying on the upstream Weblate project to update this fork.

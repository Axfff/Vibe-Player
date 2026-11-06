# Local music mood inference

This module supplies an experimental music signal for [issue #41](https://github.com/Axfff/Vibe-Player/issues/41). It does not analyze photographs, rank covers, or change the current cover. Music is analyzed locally; the module does not upload audio or fetch models from the network.

## Models and provisioning

The model files are not bundled with the app or committed to Git. Obtain the two official ONNX files and provide them as local files when creating the inference module:

| Role | Official file | SHA-256 |
| --- | --- | --- |
| Discogs-EffNet dynamic-batch backbone | [discogs-effnet-bsdynamic-1.onnx](https://essentia.upf.edu/models/feature-extractors/discogs-effnet/discogs-effnet-bsdynamic-1.onnx) | `a280825b334797cf677939db8cd5762c0392aedd0ca6415dbc1cd083f045e43c` |
| MTG-Jamendo mood/theme classifier | [mtg_jamendo_moodtheme-discogs-effnet-1.onnx](https://essentia.upf.edu/models/classification-heads/mtg_jamendo_moodtheme/mtg_jamendo_moodtheme-discogs-effnet-1.onnx) | `7d6270acaa5f4bba4b115a0d6849aca05ed6bd153dcb6d9da4f6ab9f99ef10ff` |

The [official metadata](https://essentia.upf.edu/models/classification-heads/mtg_jamendo_moodtheme/mtg_jamendo_moodtheme-discogs-effnet-1.json) lists the 56 labels in output order. Verify the hashes when provisioning. The model files must be accessible to the app process; a path on a developer's computer is not available automatically on an Android device. There is no end-user model download or file-selection flow yet.

For a developer-side download:

```sh
mkdir -p artifacts/mood-models
curl -fL -o artifacts/mood-models/discogs-effnet-bsdynamic-1.onnx https://essentia.upf.edu/models/feature-extractors/discogs-effnet/discogs-effnet-bsdynamic-1.onnx
curl -fL -o artifacts/mood-models/mtg_jamendo_moodtheme-discogs-effnet-1.onnx https://essentia.upf.edu/models/classification-heads/mtg_jamendo_moodtheme/mtg_jamendo_moodtheme-discogs-effnet-1.onnx
shasum -a 256 artifacts/mood-models/*.onnx
```

The repository's `artifacts/` directory is Git-ignored. Provision these files to an app-readable local location on the test device before calling the API; this download command alone does not install them on Android.

According to [MTG's licensing information](https://essentia.upf.edu/licensing_information.html), its models are CC BY-NC-SA 4.0 for non-commercial use, with proprietary licensing available on request. The app is GPLv3 and ONNX Runtime has its own MIT and third-party terms; review intended use and distribution separately before bundling or shipping the weights. Refer to the [runtime license](https://github.com/microsoft/onnxruntime/blob/v1.20.0/LICENSE) and [evaluation](../testing/essentia-mood-evaluation.md).

## Calling the module

`MoodAnalyzer.analyze` is a suspending function. Pass an accessible local `content:` or `file:` URI and the two app-readable model files:

```kotlin
val analyzer = MoodAnalyzer(
    context,
    MoodModelFiles(
        backbone = File(context.filesDir, "mood-models/discogs-effnet-bsdynamic-1.onnx"),
        moodTheme = File(context.filesDir, "mood-models/mtg_jamendo_moodtheme-discogs-effnet-1.onnx")
    )
)
when (val result = analyzer.analyze(audioUri)) {
    is MoodResult.Available -> {
        // result.scores is a descending list of MoodScore(label, score).
        // result.patchCount is the number scored; result.skippedSilentPatchCount is the number skipped.
        // result.analyzedSeconds is the span covered, including skipped silent patches.
        // result.decodedSeconds and result.reachedDurationLimit describe the decoded excerpt.
    }
    is MoodResult.Unavailable -> {
        // Show an appropriate explanation and allow a manual photo choice.
        val reason = result.reason
    }
}
```

Import the classes from `com.iven.musicplayergo.analysis` and `java.io.File`. Call from a coroutine and preserve cancellation: `CancellationException` is propagated rather than converted to a result. The module performs decoding and inference on an IO dispatcher. It decodes at most the first 30 seconds using Android's media codecs, averages channels, and resamples to mono 16 kHz. Codec availability and supported input formats vary by device, and the resampler has not been shown equivalent to Essentia's `resampleQuality=4`. The module computes 128-frame mel patches with a 62-frame hop and discards incomplete final patches; a complete patch requires at least 2.048 seconds. Near-silent patches (RMS below 0.0001) are skipped, and each label's score is averaged across the remaining patches. `analyzedSeconds` is the covered span, including any skipped patches; `patchCount` and `skippedSilentPatchCount` report the respective counts. `reachedDurationLimit` means that the decode limit was reached, not that additional audio was necessarily present. The decoder and current engineering silence gate do not prove perceptual or tagging quality.

`Unavailable` distinguishes `MISSING_MODEL`, `INVALID_MODEL` (including a hash mismatch), `AUDIO_UNAVAILABLE` (for example an inaccessible URI or unsupported decode), `TOO_SHORT`, `SILENT_AUDIO`, `INVALID_AUDIO`, and `INFERENCE_FAILED`. The silence gate is based on a low RMS signal threshold, not the classifier's confidence. Handle all reasons as unavailable; do not substitute a guessed mood. Model hashes are checked and inference sessions are created on each call, so caller-side caching and device-specific performance work are still needed.

## Interpretation

The head yields 56 independent scores for mood and theme labels. These are not calibrated probabilities of a listener's feelings or photo suitability. Aggregating patch scores into a track profile is an implementation choice, and no threshold or photo ranking has been validated. An unusable or unavailable signal must leave the caller able to fall back to manual selection.

## Device verification

On 2026-11-06, the production module passed all four instrumentation tests with the optional real-model fixtures on a disposable Android 17/API 37 arm64 emulator; none were skipped. The run compared all 56 averaged scores across three patches against the Essentia reference, exercised stereo 44.1 kHz decoding and resampling, and checked silence, inaccessible audio, and missing or invalid models. The complete JVM suite also passed 66 tests. This does not establish performance or compatibility on physical devices, other Android versions, or other audio codecs.

The instrumentation test exercises Android decoding, preprocessing and inference. It tests explicit missing/corrupt model failures without optional assets. Three real-model tests are **skipped** unless `moodTestAssetsDir` supplies the two official graphs and two WAVs. Do not count skipped tests as model or device validation. The WAV source below is the five-second excerpt used for the [recorded desktop experiment](../testing/essentia-mood-evaluation.md); it is downloaded locally and is not committed. The 16 kHz reference scores in `project/app/src/androidTest/assets/mood-vibe-reference.json` are the per-label mean of three patches from the official Essentia TensorFlow chain. The reference records its input hash and model hashes; FFmpeg or resampler differences may change generated audio.

From the repository root, after downloading the models as above:

```sh
mkdir -p artifacts/mood-android-test
cp artifacts/mood-models/discogs-effnet-bsdynamic-1.onnx artifacts/mood-android-test/backbone.onnx
cp artifacts/mood-models/mtg_jamendo_moodtheme-discogs-effnet-1.onnx artifacts/mood-android-test/head.onnx
curl -fL -o artifacts/mood-android-test/vibe.ogg https://librosa.org/data/audio/Kevin_MacLeod_-_Vibe_Ace.ogg
ffmpeg -y -v error -i artifacts/mood-android-test/vibe.ogg -t 5 -ar 16000 -ac 1 -c:a pcm_s16le artifacts/mood-android-test/audio16.wav
ffmpeg -y -v error -i artifacts/mood-android-test/vibe.ogg -t 5 -ar 44100 -ac 2 -c:a pcm_s16le artifacts/mood-android-test/audio44100-stereo.wav
shasum -a 256 artifacts/mood-android-test/audio16.wav
```

The expected 16 kHz WAV SHA-256 is `85131e61a67ed47cb720ab2426253d2ae19fa2adc5e9836dfaee2d4c81c557b4`. Use a disposable emulator and select its serial; the test installs the debug app and test APK on it:

```sh
export ANDROID_HOME=/path/to/Android/sdk
export ANDROID_SERIAL=emulator-5554 # replace with the disposable emulator serial
bash ./project/gradlew -p project \
  -PmoodTestAssetsDir="$PWD/artifacts/mood-android-test" \
  -Pandroid.testInstrumentationRunnerArguments.class=com.iven.musicplayergo.analysis.MoodAnalyzerInstrumentedTest \
  :app:connectedDebugAndroidTest
```

The optional assets are packaged into the test APK only and include the licensed model weights and recording. They must be treated as local test artifacts, not distributed as part of the app. Run ordinary JVM tests with `bash ./project/gradlew -p project :app:testDebugUnitTest`.

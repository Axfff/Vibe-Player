# Android ONNX audio smoke test

This standalone disposable test app checks a single 16 kHz mono PCM16 WAV patch through an experimental Java mel preprocessor and the official Discogs-EffNet and Jamendo mood/theme ONNX graphs. It checks its mel patch and scores against desktop Essentia/ONNX reference data. It is not part of Vibe Player, and does not test Android audio decoding, resampling, full-track aggregation, image ranking, a physical device, or the album-cover UI. The chosen tolerance is a numerical parity check for this fixture, not an accuracy target.

## Reproduce

Start from the repository root. First follow the model, sample and isolated Python environment setup in [the model evaluation](../../../docs/testing/essentia-mood-evaluation.md). This requires Python 3.12, the TensorFlow-enabled Essentia wheel, NumPy < 2 and FFmpeg. Then, with the Apple Silicon environment from that document:

```sh
/tmp/vibe-essentia-evaluation/venv/bin/pip install onnxruntime==1.31.1
mkdir -p artifacts/essentia-evaluation/android-demo
curl -fL -o artifacts/essentia-evaluation/models/discogs-effnet-bsdynamic-1.onnx https://essentia.upf.edu/models/feature-extractors/discogs-effnet/discogs-effnet-bsdynamic-1.onnx
curl -fL -o artifacts/essentia-evaluation/models/mtg_jamendo_moodtheme-discogs-effnet-1.onnx https://essentia.upf.edu/models/classification-heads/mtg_jamendo_moodtheme/mtg_jamendo_moodtheme-discogs-effnet-1.onnx
curl -fL -o artifacts/essentia-evaluation/android-demo/onnxruntime-android-1.20.0.aar https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/1.20.0/onnxruntime-android-1.20.0.aar
ffmpeg -y -v error -i artifacts/essentia-evaluation/audio/Kevin_MacLeod_-_Vibe_Ace.ogg -t 5 -ar 16000 -ac 1 -c:a pcm_s16le artifacts/essentia-evaluation/android-demo/audio.wav
/tmp/vibe-essentia-evaluation/venv/bin/python scripts/experiments/android-onnx/prepare_fixtures.py \
  --wav artifacts/essentia-evaluation/android-demo/audio.wav \
  --tf-backbone artifacts/essentia-evaluation/models/discogs-effnet-bs64-1.pb \
  --tf-head artifacts/essentia-evaluation/models/mtg_jamendo_moodtheme-discogs-effnet-1.pb \
  --onnx-backbone artifacts/essentia-evaluation/models/discogs-effnet-bsdynamic-1.onnx \
  --onnx-head artifacts/essentia-evaluation/models/mtg_jamendo_moodtheme-discogs-effnet-1.onnx \
  --metadata artifacts/essentia-evaluation/models/mtg_jamendo_moodtheme-discogs-effnet-1.json \
  --output-dir artifacts/essentia-evaluation/android-demo/fixtures
```

The generator checks the first ONNX patch against the corresponding official TensorFlow result and writes its inputs, expected scores, file hashes and versions locally. Model weights and audio are ignored by Git; review their [licensing](https://essentia.upf.edu/licensing_information.html) before redistribution. The APK below is test-only and bundles these locally supplied assets.

Use JDK 17, Android SDK 33, Gradle 8.0 (the repository wrapper), and a **disposable arm64 emulator**. The runner refuses a non-emulator, installs only the experiment package, waits up to 60 seconds for its atomically written result, and fails on timeout or parity failure. It does not use a real phone.

```sh
export ANDROID_HOME=/path/to/Android/sdk
export ANDROID_SERIAL=emulator-5554  # replace with the disposable emulator's actual serial
./project/gradlew -p scripts/experiments/android-onnx \
  -PfixtureDir="$PWD/artifacts/essentia-evaluation/android-demo/fixtures" \
  -PonnxAar="$PWD/artifacts/essentia-evaluation/android-demo/onnxruntime-android-1.20.0.aar" assembleDebug
bash scripts/experiments/android-onnx/run-emulator.sh artifacts/essentia-evaluation/android-demo/android-result.json
```

The app checks max absolute error < 0.0001 for generated mel values, graph scores, and WAV-to-score output. The WAV parser deliberately accepts only mono 16-bit PCM at 16 kHz; production code would need safe decoding, resampling, streaming, cancellation and additional parity tests. The prototype packages only arm64. The ONNX Runtime 1.20.0 AAR declares min SDK 21, but this test does not establish operation across the app's supported Android versions or ABIs.

## Observed run

On 2026-11-06 the isolated app passed on a read-only Android 17/API 37 arm64 emulator, using ONNX Runtime Android 1.20.0 and a five-second PCM16 *Vibe Ace* excerpt. Maximum absolute errors were 2.48e-5 for the on-device mel patch, 1.79e-7 for graph scores from the desktop patch, and 7.15e-7 for scores from the on-device WAV path. The five top first-patch labels were `space` 0.1182, `relaxing` 0.1182, `film` 0.0951, `soundscape` 0.0841 and `dream` 0.0782. These uncalibrated scores do not establish appropriate search labels or photo matches.

The debug APK was 26,805,408 bytes and SHA-256 `fd08f8630d70a6d882ff5c4a971cedf3982faed0b464acd71b69e7cf29bc2ab2` for that run; it includes models and test assets and is not a production size estimate. Reference hashes: WAV `85131e61a67ed47cb720ab2426253d2ae19fa2adc5e9836dfaee2d4c81c557b4`, patch `a366b6666c01c05940b76de4142008878b1882e4d4657dfbc375487bb2683b0a`, scores `4c8bb8277cad0d54db2fb73346aa378c9ef7c66bffe5ddd99c9e9cc7f86c536f`; ONNX backbone `a280825b334797cf677939db8cd5762c0392aedd0ca6415dbc1cd083f045e43c`, head `7d6270acaa5f4bba4b115a0d6849aca05ed6bd153dcb6d9da4f6ab9f99ef10ff`, and Android runtime AAR `07a8f71ef890afed8c6087a56220e6d558a492804276ee2dd7cb7f6262242027`.

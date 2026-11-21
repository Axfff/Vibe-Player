# Raptor Radio music analysis on Android

Use `RaptorTrackAnalyzer` from Kotlin code in the Android `:app` module. It opens an existing local audio URI, decodes up to the first 30 seconds and runs the bundled scene model and beat detector on the device. The analyzer manages I/O and CPU dispatching. It needs an Android context and permission to read the selected URI.

## Analyze a library track

The library uses `song.id?.toContentUri()` from `com.iven.musicplayergo.extensions`. A document picker can supply its granted `content://` URI directly.

In a Fragment with a view, call the suspend API from that view's lifecycle scope. The callbacks below represent the caller's result and error UI.

```kotlin
import android.net.Uri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.iven.musicplayergo.analysis.RaptorTrackAnalysis
import com.iven.musicplayergo.analysis.RaptorTrackAnalyzer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal fun Fragment.analyzeLocalTrack(
    uri: Uri,
    render: (RaptorTrackAnalysis) -> Unit,
    showError: (Exception) -> Unit
): Job = viewLifecycleOwner.lifecycleScope.launch {
    val analysis = try {
        RaptorTrackAnalyzer(requireContext()).analyze(uri)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        showError(error)
        return@launch
    }
    render(analysis)
}
```

Cancel the returned job before starting analysis for a different selection. The current song menu uses the same analyzer in [RaptorAnalysisDialogFragment](../../project/app/src/main/java/com/iven/musicplayergo/analysis/RaptorAnalysisDialogFragment.kt) and cancels on dismissal. Invalid, unreadable, or unsupported audio can fail the call. Treat successful analysis with no detected beat as a normal result.

## Read the result

- `durationSeconds` is the decoded duration. `reachedDurationLimit` indicates that decoding filled the 30-second excerpt.
- `bpm` is the estimated dominant tempo, or `null` when the detector finds no steady beat. `beats` contains beat timestamps in ascending order, in seconds from the start of the track. It is empty when `bpm` is unknown.
- `scenes` gives a theme and confidence for each analyzed window. `startSeconds` is the window start from the beginning of the track. Windows start eight seconds apart. The result omits later windows that would contain two seconds or less of audio.
- `theme` is `drift`, `groove`, `surge`, or `neutral`. Confidence is the model's top class score on a 0–1 scale. Scores below 0.45 use Neutral. Quiet audio or insufficient signal can produce Neutral with a score of zero.

For synced visuals, convert the playback position from milliseconds to seconds and read the latest beat and scene at that time. To match the web demo, apply each new scene on or after the first beat at or beyond its window start. Apply the scene by time if there is no detected beat. Use a fallback after the analyzed excerpt ends. The beat tracker estimates one dominant tempo across the excerpt.

## Build and verify

Gradle copies the [shared model JSON](../../demos/beat-runner/public/model.json) into the Android app's assets. Replace the shared artifact through the [training workflow](../../demos/beat-runner/model/README.md) when updating the model. The JavaScript and Kotlin parity tests pin the preprocessing and prediction contract.

- Use `:app:testDebugUnitTest` for classifier and beat parity.
- Use `:app:connectedDebugAndroidTest` on a disposable API 33 emulator for decoding, packaged-model and song-menu checks. Follow the [development workflow](workflow.md#from-source-to-a-running-app) for emulator setup.
- CI assembles the debug build for verification. Prepare a manual APK release package when a release is requested.

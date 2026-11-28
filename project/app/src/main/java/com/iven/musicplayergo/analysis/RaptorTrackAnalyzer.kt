package com.iven.musicplayergo.analysis

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class RaptorSceneWindow(
    val startSeconds: Double,
    val theme: String,
    val confidence: Double
)

internal data class RaptorTrackAnalysis(
    val durationSeconds: Double,
    val reachedDurationLimit: Boolean,
    val bpm: Int?,
    val beats: List<Double>,
    val scenes: List<RaptorSceneWindow>
)

/** Analyzes a bounded opening excerpt of a selected local track using the bundled model. */
internal class RaptorTrackAnalyzer(context: Context) {
    private val appContext = context.applicationContext
    private val decoder = AndroidAudioDecoder(appContext)

    suspend fun analyze(uri: Uri): RaptorTrackAnalysis = withContext(Dispatchers.Default) {
        val audio = decoder.decodeNative(uri, MAX_SECONDS)
        val model = withContext(Dispatchers.IO) {
            appContext.assets.open(MODEL_ASSET).bufferedReader().use {
                RaptorStyleModel.fromJson(it.readText())
            }
        }
        val timing = detectRaptorBeats(audio.channels, audio.sampleRate) { ensureActive() }
        val duration = audio.channels.first().size.toDouble() / audio.sampleRate
        val scenes = buildList {
            var start = 0.0
            while (start < maxOf(1.0, duration - 2.0)) {
                ensureActive()
                val prediction = model.predict(audio.channels, audio.sampleRate, start)
                val theme = if (prediction.confidence >= RaptorStyleModel.MIN_SCENE_CONFIDENCE) {
                    prediction.style
                } else "neutral"
                add(RaptorSceneWindow(start, theme, prediction.confidence))
                start += RaptorStyleModel.WINDOW_SECONDS
            }
        }
        RaptorTrackAnalysis(duration, audio.reachedDurationLimit, timing.bpm, timing.beats, scenes)
    }

    companion object {
        const val MAX_SECONDS = 30
        const val MODEL_ASSET = "raptor/model.json"
    }
}

package com.iven.musicplayergo.raptor

import com.iven.musicplayergo.analysis.RaptorStyleModel
import com.iven.musicplayergo.analysis.RaptorTrackAnalysis

internal data class RaptorSignal(val theme: String = "neutral", val pulse: Float = 0f)

/** Maps the audio playback position to visual cues without advancing the game clock. */
internal class RaptorTimeline(private val analysis: RaptorTrackAnalysis) {
    fun signalAt(seconds: Double): RaptorSignal {
        val duration = analysis.durationSeconds
        if (!seconds.isFinite() || seconds < 0.0 || !duration.isFinite() || duration <= 0.0) {
            return RaptorSignal()
        }

        val position = seconds % duration
        val beat = analysis.beats.lastAtOrBefore(position) { it }
        // A new scene takes effect on its first beat, with time-based changes for beatless audio.
        val sceneTime = if (analysis.beats.isEmpty()) position else beat ?: 0.0
        val scene = analysis.scenes.lastAtOrBefore(sceneTime) { it.startSeconds }
        val theme = if (scene != null &&
            scene.confidence >= RaptorStyleModel.MIN_SCENE_CONFIDENCE &&
            scene.theme in themes
        ) scene.theme else "neutral"
        val fade = if (beat == null) 0.0 else maxOf(0.0, 1.0 - (position - beat) / 0.23)
        return RaptorSignal(theme, (fade * fade).toFloat())
    }

    private companion object {
        val themes = setOf("drift", "groove", "surge")
    }
}

private inline fun <T> List<T>.lastAtOrBefore(position: Double, time: (T) -> Double): T? {
    var low = 0
    var high = size
    while (low < high) {
        val mid = low + (high - low) / 2
        if (time(this[mid]) <= position) low = mid + 1 else high = mid
    }
    return getOrNull(low - 1)
}

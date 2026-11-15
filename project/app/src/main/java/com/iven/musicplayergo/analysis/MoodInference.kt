package com.iven.musicplayergo.analysis

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.sqrt

/** Independent model scores; these are not calibrated probabilities or photo matches. */
data class MoodScore(val label: String, val score: Float)

enum class MoodUnavailableReason {
    MISSING_MODEL, INVALID_MODEL, AUDIO_UNAVAILABLE, TOO_SHORT, SILENT_AUDIO, INVALID_AUDIO, INFERENCE_FAILED
}

sealed class MoodResult {
    data class Available(
        val scores: List<MoodScore>,
        val analyzedSeconds: Float,
        val patchCount: Int,
        val decodedSeconds: Float,
        val reachedDurationLimit: Boolean = false,
        val skippedSilentPatchCount: Int = 0
    ) : MoodResult()

    data class Unavailable(val reason: MoodUnavailableReason) : MoodResult()
}

/** Official label order from mtg_jamendo_moodtheme-discogs-effnet-1.json. */
internal val MOOD_LABELS = listOf(
    "action", "adventure", "advertising", "background", "ballad", "calm", "children", "christmas",
    "commercial", "cool", "corporate", "dark", "deep", "documentary", "drama", "dramatic", "dream",
    "emotional", "energetic", "epic", "fast", "film", "fun", "funny", "game", "groovy", "happy", "heavy",
    "holiday", "hopeful", "inspiring", "love", "meditative", "melancholic", "melodic", "motivational",
    "movie", "nature", "party", "positive", "powerful", "relaxing", "retro", "romantic", "sad", "sexy",
    "slow", "soft", "soundscape", "space", "sport", "summer", "trailer", "travel", "upbeat", "uplifting"
)

/** Detect invalid/near-silent input before a model can invent labels for silence. */
internal fun classifyPcm(samples: FloatArray): MoodUnavailableReason? {
    if (EffnetFeatures.patchCount(samples.size) == 0) return MoodUnavailableReason.TOO_SHORT
    val count = EffnetFeatures.patchCount(samples.size)
    val usedSamples = (count - 1) * EffnetFeatures.PATCH_HOP * 256 + EffnetFeatures.FRAMES * 256
    for (i in 0 until usedSamples) {
        val sample = samples[i]
        if (!sample.isFinite() || sample < -1f || sample > 1f) return MoodUnavailableReason.INVALID_AUDIO
    }
    if ((0 until count).all { isSilentPatch(samples, it * EffnetFeatures.PATCH_HOP) }) {
        return MoodUnavailableReason.SILENT_AUDIO
    }
    return null
}

/** An engineering silence gate, not a confidence threshold or an accuracy claim. */
private fun isSilentPatch(samples: FloatArray, startFrame: Int): Boolean {
    val first = (startFrame * 256 - 256).coerceAtLeast(0)
    val last = (startFrame + EffnetFeatures.FRAMES) * 256
    var sumSquares = 0.0
    for (index in first until last) {
        val sample = samples[index].toDouble()
        sumSquares += sample * sample
    }
    // Include the first patch's padded samples in the RMS denominator.
    return sqrt(sumSquares / (EffnetFeatures.FRAMES * 256 + 256)) < 0.0001
}

/** Shared aggregation path; `scorePatch` consumes one 128 x 96 mel tensor. */
internal suspend fun analyzePcm(samples: FloatArray, scorePatch: (FloatArray) -> FloatArray): MoodResult {
    classifyPcm(samples)?.let { return MoodResult.Unavailable(it) }
    val coroutine = currentCoroutineContext()
    val count = EffnetFeatures.patchCount(samples.size)
    val totals = DoubleArray(MOOD_LABELS.size)
    var scoredCount = 0
    for (index in 0 until count) {
        coroutine.ensureActive()
        val startFrame = index * EffnetFeatures.PATCH_HOP
        if (isSilentPatch(samples, startFrame)) continue
        val patch = EffnetFeatures.patch(samples, startFrame) { coroutine.ensureActive() }
        val scores = scorePatch(patch)
        check(scores.size == MOOD_LABELS.size && scores.all { it.isFinite() && it in 0f..1f }) {
            "Unexpected model scores"
        }
        scores.forEachIndexed { i, score -> totals[i] = totals[i] + score.toDouble() }
        scoredCount++
    }
    check(scoredCount > 0) { "No usable audio patches" }
    return MoodResult.Available(
        scores = MOOD_LABELS.indices.map { MoodScore(MOOD_LABELS[it], (totals[it] / scoredCount).toFloat()) }
            .sortedByDescending { it.score },
        analyzedSeconds = ((count - 1) * EffnetFeatures.PATCH_HOP * 256 + EffnetFeatures.FRAMES * 256).toFloat() / EffnetFeatures.SAMPLE_RATE,
        patchCount = scoredCount,
        decodedSeconds = samples.size.toFloat() / EffnetFeatures.SAMPLE_RATE,
        skippedSilentPatchCount = count - scoredCount
    )
}

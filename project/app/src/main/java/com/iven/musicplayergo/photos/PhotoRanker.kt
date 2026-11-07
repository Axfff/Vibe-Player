package com.iven.musicplayergo.photos

import com.iven.musicplayergo.analysis.MoodScore
import kotlin.math.exp

data class PhotoLabel(val label: String, val confidence: Float)
data class PhotoFeatures(val photo: ScopedPhoto, val fingerprint: String, val modelVersion: String, val labels: List<PhotoLabel>)
data class RankedPhoto(val features: PhotoFeatures, val score: Float, val reasons: List<String>, val ruleVersion: String = PhotoRanker.VERSION)

/** Explicit editorial associations, not learned psychological or identity inferences. */
object PhotoRanker {
    const val VERSION = "music-visual-v1"
    val mapping = linkedMapOf(
        "happy" to setOf("daisy", "butterfly", "seashore"), "positive" to setOf("daisy", "butterfly"),
        "upbeat" to setOf("butterfly", "volleyball"), "fun" to setOf("butterfly", "volleyball"),
        "summer" to setOf("seashore", "daisy"), "calm" to setOf("lakeside", "valley"),
        "relaxing" to setOf("lakeside", "seashore", "valley"), "meditative" to setOf("lakeside", "valley"),
        "soft" to setOf("daisy", "lakeside"), "melancholic" to setOf("valley", "cliff"),
        "sad" to setOf("cliff", "valley"), "dark" to setOf("volcano", "cliff"),
        "energetic" to setOf("canoe", "volleyball", "volcano"), "powerful" to setOf("volcano", "alp"),
        "action" to setOf("canoe", "volleyball"), "adventure" to setOf("alp", "canoe", "cliff"),
        "sport" to setOf("volleyball", "canoe"), "nature" to setOf("daisy", "butterfly", "valley", "lakeside"))
    fun rank(signal: List<MoodScore>, candidates: List<PhotoFeatures>, limit: Int = 5): List<RankedPhoto> {
        require(limit > 0)
        val usable = signal.filter { it.score.isFinite() && it.score in 0.05f..1f && it.label in mapping }
            .sortedWith(compareByDescending<MoodScore> { it.score }.thenBy { it.label }).take(5)
        val total = usable.sumOf { it.score.toDouble() }.toFloat()
        if (total <= 0) return emptyList()
        return candidates.distinctBy { it.fingerprint }.mapNotNull { feature ->
            val reasons = mutableListOf<String>(); var score = 0f
            for (music in usable) {
                val best = feature.labels.filter { it.label in mapping.getValue(music.label) && it.confidence.isFinite() && it.confidence in 0.1f..1f }
                    .sortedWith(compareByDescending<PhotoLabel> { it.confidence }.thenBy { it.label }).firstOrNull() ?: continue
                score += music.score / total * best.confidence
                reasons += "${music.label} (${music.score}) → ${best.label} (${best.confidence})"
            }
            if (score < 0.05f) null else RankedPhoto(feature, score, reasons)
        }.sortedWith(compareByDescending<RankedPhoto> { it.score }.thenBy { it.features.photo.id }).take(limit)
    }
    fun suggestions(signal: List<MoodScore>): List<String> = signal.filter { it.score.isFinite() && it.score >= 0.05f }
        .sortedWith(compareByDescending<MoodScore> { it.score }.thenBy { it.label }).flatMap { mapping[it.label].orEmpty().sorted() }.distinct().take(3)
    fun softmax(logits: FloatArray): FloatArray {
        require(logits.size == 1000 && logits.all { it.isFinite() })
        val max = logits.maxOrNull()!!; val values = logits.map { exp((it - max).toDouble()) }; val sum = values.sum()
        return values.map { (it / sum).toFloat() }.toFloatArray()
    }
}

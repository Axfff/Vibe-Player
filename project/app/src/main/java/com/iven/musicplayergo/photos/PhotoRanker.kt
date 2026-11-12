package com.iven.musicplayergo.photos

import com.iven.musicplayergo.R
import com.iven.musicplayergo.preferences.UiText
import com.iven.musicplayergo.analysis.MoodScore

data class PhotoLabel(val label: String, val confidence: Float)
data class PhotoFeatures(val photo: ScopedPhoto, val fingerprint: String, val modelVersion: String, val labels: List<PhotoLabel>,
    val atmospheres: List<PhotoLabel> = emptyList())
data class RankedPhoto(val features: PhotoFeatures, val score: Float, val reasons: List<UiText>, val ruleVersion: String = PhotoRanker.VERSION)

/** Main-thread pagination reservation; repeated taps cannot own the same offset. */
internal class PhotoPageCursor(private val total: Int) {
    var shown = 0; private set
    private var reserved: IntRange? = null
    fun begin(): IntRange? {
        if (reserved != null || shown >= total) return null
        return (shown until minOf(shown + 5, total)).also { reserved = it }
    }
    fun finish(success: Boolean) {
        if (success) reserved?.let { shown = it.last + 1 }
        reserved = null
    }
}

/** Explicit editorial associations, not learned psychological or identity inferences. */
object PhotoRanker {
    const val VERSION = "music-atmosphere-v4-valence"
    private val moodNames = mapOf("happy" to UiText.of(R.string.photo_mood_happy), "positive" to UiText.of(R.string.photo_mood_positive), "upbeat" to UiText.of(R.string.photo_mood_upbeat), "fun" to UiText.of(R.string.photo_mood_fun),
        "summer" to UiText.of(R.string.photo_mood_summer), "calm" to UiText.of(R.string.photo_mood_calm), "relaxing" to UiText.of(R.string.photo_mood_relaxing), "meditative" to UiText.of(R.string.photo_mood_meditative), "soft" to UiText.of(R.string.photo_mood_soft),
        "melancholic" to UiText.of(R.string.photo_mood_melancholic), "sad" to UiText.of(R.string.photo_mood_sad), "dark" to UiText.of(R.string.photo_mood_dark), "energetic" to UiText.of(R.string.photo_mood_energetic), "powerful" to UiText.of(R.string.photo_mood_powerful),
        "action" to UiText.of(R.string.photo_mood_action), "adventure" to UiText.of(R.string.photo_mood_adventure), "sport" to UiText.of(R.string.photo_mood_sport), "nature" to UiText.of(R.string.photo_mood_nature), "epic" to UiText.of(R.string.photo_mood_epic))
    fun moodName(label: String) = moodNames[label] ?: UiText.Literal(label)
    val mapping = linkedMapOf(
        "happy" to setOf("flowers", "flower_plants", "butterfly", "seashore", "party"), "positive" to setOf("flowers", "flower_plants", "butterfly", "meadow"),
        "upbeat" to setOf("butterfly", "party", "sport", "concert"), "fun" to setOf("butterfly", "party", "sport"),
        "summer" to setOf("seashore", "flowers", "flower_plants", "meadow"), "calm" to setOf("lakeside", "waterscape", "valley", "forest"),
        "relaxing" to setOf("lakeside", "waterscape", "seashore", "valley", "forest"), "meditative" to setOf("lakeside", "waterscape", "valley", "forest", "night_sky"),
        "soft" to setOf("flowers", "flower_plants", "lakeside", "waterscape", "sunset"), "melancholic" to setOf("valley", "cliff", "rain", "rainy_weather", "sunset"),
        "sad" to setOf("rain", "rainy_weather", "cliff", "valley"), "dark" to setOf("volcano", "storm", "rainy_weather", "city_night", "cliff"),
        "energetic" to setOf("canoe", "sport", "volcano", "concert"), "powerful" to setOf("volcano", "alp", "mountain_scenery", "waterfall"),
        "action" to setOf("canoe", "sport"), "adventure" to setOf("alp", "mountain_scenery", "canoe", "cliff", "desert"),
        "sport" to setOf("sport", "canoe"), "epic" to setOf("volcano", "alp", "waterfall"), "nature" to setOf("flowers", "flower_plants", "butterfly", "insects", "forest", "valley", "mountain_scenery", "lakeside", "waterscape"))
    private val atmosphereMusic = linkedMapOf(
        "lively" to setOf("happy", "positive", "upbeat", "fun", "summer"),
        "peaceful" to setOf("calm", "relaxing", "meditative", "soft"),
        "reflective" to setOf("sad", "melancholic", "dark"),
        "intense" to setOf("epic", "action", "energetic", "powerful", "adventure", "sport"))
    /** Relaxation describes pace, not positive valence. Allocate its support using
     * explicit sadness after counterbalancing positive cues; darkness alone cannot
     * redirect relaxation. Sharpen the resulting relative preference, not model
     * probabilities. No song titles, scene labels or photo identities enter here. */
    private fun weights(usable: Map<String, Float>): Map<String, Float> {
        val weights = atmosphereMusic.mapValues { (_, moods) -> moods.sumOf { (usable[it] ?: 0f).toDouble() }.toFloat() }.toMutableMap()
        val relaxing = usable["relaxing"] ?: 0f
        val sadness = ((usable["sad"] ?: 0f) + (usable["melancholic"] ?: 0f) - weights.getValue("lively")).coerceAtLeast(0f)
        val specific = sadness + (weights.getValue("peaceful") - relaxing).coerceAtLeast(0f)
        val transfer = if (specific > 0f) relaxing * sadness / specific else 0f
        weights["peaceful"] = weights.getValue("peaceful") - transfer
        weights["reflective"] = weights.getValue("reflective") + transfer
        val squared = weights.mapValues { (_, value) -> value * value }
        val total = squared.values.sum()
        return if (total > 0f) squared.mapValues { (_, value) -> value / total } else emptyMap()
    }
    /** Report the excerpt's evidence, including coexistence, without inferring from the song title. */
    fun musicDescription(signal: List<MoodScore>): UiText {
        val usable = signal.filter { it.label in atmosphereMusic.values.flatten() && it.score.isFinite() && it.score in .05f..1f }
            .groupBy { it.label }.map { (_, values) -> values.maxBy { it.score } }
            .sortedWith(compareByDescending<MoodScore> { it.score }.thenBy { it.label })
        if (usable.isEmpty()) return UiText.of(R.string.photo_music_no_signal)
        val labels = usable.map { it.label }.toSet()
        val mixed = labels.any { it in atmosphereMusic.getValue("peaceful") } && labels.any { it in atmosphereMusic.getValue("reflective") }
        return UiText.Joined(listOfNotNull(
            UiText.of(R.string.photo_music_signals, UiText.Joined(usable.take(3).map { moodName(it.label) })),
            if (mixed) UiText.of(R.string.photo_music_mixed) else null), " ")
    }
    fun rank(signal: List<MoodScore>, candidates: List<PhotoFeatures>, limit: Int = 5): List<RankedPhoto> {
        require(limit > 0)
        val usable = signal.filter { it.score.isFinite() && it.score in 0.05f..1f }
            .groupBy { it.label }.mapValues { (_, values) -> values.maxOf { it.score } }
        val weights = weights(usable)
        if (weights.isEmpty()) return emptyList()
        val dominant = weights.maxBy { it.value }.takeIf { it.value >= .65f }?.key
        return candidates.sortedBy { it.photo.id }.distinctBy { it.fingerprint }.mapNotNull { feature ->
            val cues = feature.atmospheres
            if (cues.size != 4 || cues.map { it.label }.toSet() != atmosphereMusic.keys ||
                cues.any { !it.confidence.isFinite() || it.confidence !in 0f..1f } ||
                kotlin.math.abs(cues.sumOf { it.confidence.toDouble() } - 1.0) > .001) return@mapNotNull null
            // With a clear musical preference, an image led by another atmosphere
            // is a secondary match, even if the blended score is high. Abstain
            // instead of padding the list with conflicting atmospheres. Ambiguous
            // music keeps blended ranking; tied image cues remain eligible.
            if (dominant != null && cues.first { it.label == dominant }.confidence < cues.maxOf { it.confidence }) return@mapNotNull null
            val contributions = cues.map { it to (weights.getValue(it.label) * it.confidence) }
            val score = contributions.sumOf { it.second.toDouble() }.toFloat()
            // Uniform atmosphere support gives .25; require positive separation.
            // Never pad a weak or unsupported scope just to return five images.
            if (score < .26f) null else {
                val atmosphereReasons = contributions.filter { it.second > 0 && it.first.confidence > .25f }
                    .sortedByDescending { it.second }.take(2).map { (cue, _) ->
                        val music = atmosphereMusic.getValue(cue.label).filter { it in usable }
                            .sortedWith(compareByDescending<String> { usable.getValue(it) }.thenBy { it }).first()
                        UiText.of(R.string.photo_match_reason, moodName(music), PhotoAtmospherePolicy.names.getValue(cue.label))
                    }
                val reasons = mutableListOf<UiText>(UiText.Joined(atmosphereReasons, "; "))
                feature.labels.firstOrNull()?.let { reasons += UiText.of(R.string.photo_scene_reason, PhotoScenePolicy.title(it.label)) }
                RankedPhoto(feature, score, reasons)
            }
        }.sortedWith(compareByDescending<RankedPhoto> { it.score }.thenBy { it.features.photo.id }).take(limit)
    }
    /** One short user-facing explanation; detailed scores remain in the details dialog. */
    fun caption(candidate: RankedPhoto): UiText {
        val strongest = candidate.features.atmospheres.maxByOrNull { it.confidence }?.label
        val feeling = PhotoAtmospherePolicy.names[strongest] ?: UiText.of(R.string.photo_matched_feeling)
        val scene = candidate.features.labels.firstOrNull()?.let { UiText.of(R.string.photo_caption_scene, PhotoScenePolicy.title(it.label)) }
        return UiText.Joined(listOfNotNull(scene, UiText.of(R.string.photo_caption_feeling, feeling)), " ")
    }
    fun suggestions(signal: List<MoodScore>): List<UiText> = signal.filter { it.score.isFinite() && it.score >= 0.05f }
        .sortedWith(compareByDescending<MoodScore> { it.score }.thenBy { it.label }).flatMap { mapping[it.label].orEmpty().sorted() }
        .distinct().take(3).map { PhotoScenePolicy.title(it) }
}

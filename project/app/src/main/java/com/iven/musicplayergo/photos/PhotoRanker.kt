package com.iven.musicplayergo.photos

import com.iven.musicplayergo.analysis.MoodScore

data class PhotoLabel(val label: String, val confidence: Float)
data class PhotoFeatures(val photo: ScopedPhoto, val fingerprint: String, val modelVersion: String, val labels: List<PhotoLabel>,
    val atmospheres: List<PhotoLabel> = emptyList())
data class RankedPhoto(val features: PhotoFeatures, val score: Float, val reasons: List<String>, val ruleVersion: String = PhotoRanker.VERSION)

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
    private val moodNames = mapOf("happy" to "轻快", "positive" to "积极", "upbeat" to "活泼", "fun" to "欢快",
        "summer" to "夏日", "calm" to "舒缓", "relaxing" to "放松", "meditative" to "沉静", "soft" to "柔和",
        "melancholic" to "忧郁", "sad" to "伤感", "dark" to "暗沉", "energetic" to "充满活力", "powerful" to "有力量",
        "action" to "动感", "adventure" to "探索", "sport" to "运动", "nature" to "自然", "epic" to "恢宏")
    fun moodName(label: String) = moodNames[label] ?: label
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
    fun musicDescription(signal: List<MoodScore>): String {
        val usable = signal.filter { it.label in atmosphereMusic.values.flatten() && it.score.isFinite() && it.score in .05f..1f }
            .groupBy { it.label }.map { (_, values) -> values.maxBy { it.score } }
            .sortedWith(compareByDescending<MoodScore> { it.score }.thenBy { it.label })
        if (usable.isEmpty()) return "片段中没有明确的可用氛围信号，可使用“自己选氛围”。"
        val labels = usable.map { it.label }.toSet()
        val mixed = labels.any { it in atmosphereMusic.getValue("peaceful") } && labels.any { it in atmosphereMusic.getValue("reflective") }
        return "片段线索：${usable.take(3).joinToString("、") { moodName(it.label) }}（模型推测）。" +
            if (mixed) "舒缓与伤感线索并存，推荐可能重叠；可使用“自己选氛围”侧重其中一种。" else ""
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
                        "${moodName(music)}的音乐氛围 × 模型推测的${PhotoAtmospherePolicy.names.getValue(cue.label)}画面"
                    }
                val reasons = mutableListOf(atmosphereReasons.joinToString("；"))
                feature.labels.firstOrNull()?.let { reasons += "画面线索：${PhotoScenePolicy.title(it.label)}（模型推测），请看预览确认" }
                RankedPhoto(feature, score, reasons)
            }
        }.sortedWith(compareByDescending<RankedPhoto> { it.score }.thenBy { it.features.photo.id }).take(limit)
    }
    /** One short user-facing explanation; detailed scores remain in the details dialog. */
    fun caption(candidate: RankedPhoto): String {
        val strongest = candidate.features.atmospheres.maxByOrNull { it.confidence }?.label
        val feeling = mapOf("lively" to "明快", "peaceful" to "平和", "reflective" to "低沉", "intense" to "有动感")[strongest] ?: "与音乐相配"
        val scene = candidate.features.labels.firstOrNull()?.let { "画面可能是${PhotoScenePolicy.title(it.label)}，" }.orEmpty()
        return "${scene}感觉${feeling}。"
    }
    fun suggestions(signal: List<MoodScore>): List<String> = signal.filter { it.score.isFinite() && it.score >= 0.05f }
        .sortedWith(compareByDescending<MoodScore> { it.score }.thenBy { it.label }).flatMap { mapping[it.label].orEmpty().sorted() }
        .distinct().take(3).map { PhotoScenePolicy.title(it) }
}

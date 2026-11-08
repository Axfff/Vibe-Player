package com.iven.musicplayergo.photos

import com.iven.musicplayergo.analysis.MoodScore

data class PhotoLabel(val label: String, val confidence: Float)
data class PhotoFeatures(val photo: ScopedPhoto, val fingerprint: String, val modelVersion: String, val labels: List<PhotoLabel>)
data class RankedPhoto(val features: PhotoFeatures, val score: Float, val reasons: List<String>, val ruleVersion: String = PhotoRanker.VERSION)

/** Explicit editorial associations, not learned psychological or identity inferences. */
object PhotoRanker {
    const val VERSION = "music-visual-v2-families"
    private val moodNames = mapOf("happy" to "轻快", "positive" to "积极", "upbeat" to "活泼", "fun" to "欢快",
        "summer" to "夏日", "calm" to "舒缓", "relaxing" to "放松", "meditative" to "沉静", "soft" to "柔和",
        "melancholic" to "忧郁", "sad" to "伤感", "dark" to "暗沉", "energetic" to "充满活力", "powerful" to "有力量",
        "action" to "动感", "adventure" to "探索", "sport" to "运动", "nature" to "自然")
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
        "sport" to setOf("sport", "canoe"), "nature" to setOf("flowers", "flower_plants", "butterfly", "insects", "forest", "valley", "mountain_scenery", "lakeside", "waterscape"))
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
                reasons += "${moodName(music.label)}的音乐氛围 × ${PhotoScenePolicy.title(best.label)}画面"
            }
            if (score < 0.05f) null else RankedPhoto(feature, score, reasons)
        }.sortedWith(compareByDescending<RankedPhoto> { it.score }.thenBy { it.features.photo.id }).take(limit)
    }
    fun suggestions(signal: List<MoodScore>): List<String> = signal.filter { it.score.isFinite() && it.score >= 0.05f }
        .sortedWith(compareByDescending<MoodScore> { it.score }.thenBy { it.label }).flatMap { mapping[it.label].orEmpty().sorted() }
        .distinct().take(3).map { PhotoScenePolicy.title(it) }
}

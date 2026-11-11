package com.iven.musicplayergo.discovery

import android.content.Context
import android.net.Uri
import com.iven.musicplayergo.photos.*

/** Reuses the explicitly imported cover model. No network client or photo upload. */
class DiscoveryLocalPhoto(private val context: Context) {
    suspend fun analyze(uri: Uri): LocalPhotoSuggestion {
        val file = PhotoModelStore(context).file(PhotoModel.PHOTO)
        if (!file.isFile) throw DiscoveryFailure("local_photo_model_missing")
        return withPhotoExtractor({ OnnxPhotoFeatureExtractor(context, file) }) { extractor ->
            val features = extractor.extract(ScopedPhoto("discovery-selected", uri.toString()))
            DiscoveryPhotoPolicy.suggest(features) ?: throw DiscoveryFailure("local_photo_uncertain")
        }
    }
}

data class LocalPhotoSuggestion(val intent: PhotoIntent, val description: String)

/** Editorial picture-to-intent associations, not song analysis or a person's emotions. */
object DiscoveryPhotoPolicy {
    private val moods = mapOf("lively" to "bright", "peaceful" to "calm",
        "reflective" to "melancholic", "intense" to "energetic")
    private val scenes = mapOf(
        "flowers" to "nature", "flower_plants" to "nature", "butterfly" to "nature",
        "insects" to "nature", "forest" to "nature", "meadow" to "nature",
        "alp" to "nature", "cliff" to "nature", "valley" to "nature", "mountain_scenery" to "nature",
        "snow" to "nature", "volcano" to "nature", "desert" to "nature",
        "lakeside" to "water", "river" to "water", "waterfall" to "water", "waterscape" to "water",
        "seashore" to "water", "canoe" to "water",
        "city" to "city", "urban" to "city", "street" to "city", "building" to "city", "bridge" to "city",
        "city_night" to "night", "night_sky" to "night", "indoor" to "indoors")

    fun suggest(features: PhotoFeatures): LocalPhotoSuggestion? {
        val cues = features.atmospheres
        if (cues.size != moods.size || cues.map { it.label }.toSet() != moods.keys ||
            cues.any { !it.confidence.isFinite() || it.confidence !in 0f..1f } ||
            kotlin.math.abs(cues.sumOf { it.confidence.toDouble() } - 1.0) > .001 ||
            features.labels.any { it.label in setOf("document", "screenshot") }) return null
        val ordered = cues.sortedWith(compareByDescending<PhotoLabel> { it.confidence }.thenBy { it.label })
        val first = ordered.first()
        if (first.confidence < .35f) return null
        val uncertain = first.confidence - ordered[1].confidence < .10f
        val selected = ordered.take(if (uncertain) 2 else 1)
        val scene = features.labels.filter { it.confidence.isFinite() && it.confidence >= .20f }
            .mapNotNull { scenes[it.label] }.distinct()
        val intent = PhotoIntent(scene, selected.map { moods.getValue(it.label) }, emptyList(), if (uncertain) "high" else "medium")
        val description = "手机本地分析 · ${features.modelVersion}\n画面氛围：" +
            selected.joinToString("、") { PhotoAtmospherePolicy.names.getValue(it.label) } +
            (if (scene.isEmpty()) "\n场景未明确识别" else "\n场景建议：${scene.joinToString()}") +
            "\n这是画面线索与音乐氛围的规则关联，不是人物情绪或歌曲听感识别。请修改并确认。" +
            (if (uncertain) "\n多种氛围接近，建议不确定。" else "")
        return LocalPhotoSuggestion(intent, description)
    }
}

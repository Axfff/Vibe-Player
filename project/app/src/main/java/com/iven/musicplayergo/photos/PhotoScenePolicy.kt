package com.iven.musicplayergo.photos

import com.iven.musicplayergo.R
import com.iven.musicplayergo.preferences.UiText
/** Order is the exported scenes-v2 vocabulary; scores are cosines, not probabilities.
 * A crop cannot overrule the full frame. Weak or conflicting cues produce no label.
 */
object PhotoScenePolicy {
    val names = linkedMapOf(
        "flowers" to UiText.of(R.string.photo_label_flowers), "butterfly" to UiText.of(R.string.photo_label_butterfly), "forest" to UiText.of(R.string.photo_label_forest), "lakeside" to UiText.of(R.string.photo_label_lakeside),
        "river" to UiText.of(R.string.photo_label_river), "seashore" to UiText.of(R.string.photo_label_seashore), "alp" to UiText.of(R.string.photo_label_alp), "cliff" to UiText.of(R.string.photo_label_cliff),
        "valley" to UiText.of(R.string.photo_label_valley), "volcano" to UiText.of(R.string.photo_label_volcano), "snow" to UiText.of(R.string.photo_label_snow), "waterfall" to UiText.of(R.string.photo_label_waterfall),
        "meadow" to UiText.of(R.string.photo_label_meadow), "sunset" to UiText.of(R.string.photo_label_sunset), "night_sky" to UiText.of(R.string.photo_label_night_sky), "city" to UiText.of(R.string.photo_label_city),
        "city_night" to UiText.of(R.string.photo_label_city_night), "street" to UiText.of(R.string.photo_label_street), "building" to UiText.of(R.string.photo_label_building), "bridge" to UiText.of(R.string.photo_label_bridge),
        "concert" to UiText.of(R.string.photo_label_concert), "party" to UiText.of(R.string.photo_label_party), "sport" to UiText.of(R.string.photo_label_sport), "canoe" to UiText.of(R.string.photo_label_canoe),
        "animal" to UiText.of(R.string.photo_label_animal), "bird" to UiText.of(R.string.photo_label_bird), "insect" to UiText.of(R.string.photo_label_insect), "portrait" to UiText.of(R.string.photo_label_portrait),
        "people" to UiText.of(R.string.photo_label_people), "indoor" to UiText.of(R.string.photo_label_indoor), "food" to UiText.of(R.string.photo_label_food), "document" to UiText.of(R.string.photo_label_document),
        "screenshot" to UiText.of(R.string.photo_label_screenshot), "vehicle" to UiText.of(R.string.photo_label_vehicle), "object" to UiText.of(R.string.photo_label_object), "artwork" to UiText.of(R.string.photo_label_artwork),
        "storm" to UiText.of(R.string.photo_label_storm), "fire" to UiText.of(R.string.photo_label_fire), "desert" to UiText.of(R.string.photo_label_desert), "rain" to UiText.of(R.string.photo_label_rain))
    // Visually overlapping classes can support a broad cue without asserting a species
    // or specific landscape. Unrelated classes still compete outside each family.
    private val families = linkedMapOf(
        "flower_plants" to setOf("flowers", "meadow"),
        "waterscape" to setOf("lakeside", "river", "waterfall"),
        "mountain_scenery" to setOf("alp", "cliff", "valley", "snow"),
        "insects" to setOf("butterfly", "insect"),
        "urban" to setOf("city", "city_night", "street", "building", "bridge"),
        "rainy_weather" to setOf("rain", "storm"))
    val broadNames = mapOf("flower_plants" to UiText.of(R.string.photo_label_flower_plants), "waterscape" to UiText.of(R.string.photo_label_waterscape), "mountain_scenery" to UiText.of(R.string.photo_label_mountain_scenery),
        "insects" to UiText.of(R.string.photo_label_insects), "urban" to UiText.of(R.string.photo_label_urban), "rainy_weather" to UiText.of(R.string.photo_label_rainy_weather))
    fun title(label: String) = names[label] ?: broadNames[label] ?: UiText.Literal(label)

    fun labels(views: Array<FloatArray>): List<PhotoLabel> {
        require(views.size == 2 && views.all { row -> row.size == names.size && row.all { it.isFinite() && it in -1f..1f } })
        val ordered = views.map { row -> row.indices.sortedByDescending { row[it] } }
        val winner = ordered[0][0]
        if (ordered[1][0] == winner && views.indices.all { i -> views[i][winner] >= .20f && views[i][winner] - views[i][ordered[i][1]] >= .02f })
            return listOf(PhotoLabel(names.keys.elementAt(winner), (views[0][winner] + views[1][winner]) / 2))
        val keys = names.keys.toList()
        val family = families.entries.firstOrNull { (_,members) -> ordered.all { keys[it[0]] in members } } ?: return emptyList()
        if (views.indices.any { i ->
            val top = views[i][ordered[i][0]]
            val outside = ordered[i].first { keys[it] !in family.value }
            top < .20f || top - views[i][outside] < .02f
        }) return emptyList()
        return listOf(PhotoLabel(family.key, (views[0][ordered[0][0]] + views[1][ordered[1][0]]) / 2))
    }
}

/** Broad picture atmosphere, independent of whether a specific object can be named.
 * Relative support among four text prototypes is not a probability or person-emotion inference.
 */
object PhotoAtmospherePolicy {
    val names = linkedMapOf("lively" to UiText.of(R.string.photo_label_lively), "peaceful" to UiText.of(R.string.photo_label_peaceful),
        "reflective" to UiText.of(R.string.photo_label_reflective), "intense" to UiText.of(R.string.photo_label_intense))
    fun cues(views: Array<FloatArray>, sceneViews: Array<FloatArray>): List<PhotoLabel> {
        require(views.size == 2 && views.all { it.size == names.size && it.all { score -> score.isFinite() && score in -1f..1f } })
        require(sceneViews.size == 2 && sceneViews.all { it.size == PhotoScenePolicy.names.size && it.all { score -> score.isFinite() && score in -1f..1f } })
        val scenes = PhotoScenePolicy.names.keys.toList()
        // A document/screenshot winning either view must not gain a recommendation
        // merely because one of the four atmosphere prompts is least dissimilar.
        if (sceneViews.any { row -> scenes[row.indices.maxBy { row[it] }] in setOf("document", "screenshot") }) return emptyList()
        val conservative = FloatArray(names.size) { minOf(views[0][it], views[1][it]) }
        val maximum = conservative.maxOrNull()!!
        if (maximum < .20f) return emptyList()
        val support = conservative.map { kotlin.math.exp(((it - maximum) / .05f).toDouble()).toFloat() }
        val total = support.sum()
        if (support.maxOrNull()!! / total < .30f) return emptyList()
        return names.keys.mapIndexed { index, name -> PhotoLabel(name, support[index] / total) }
    }
}

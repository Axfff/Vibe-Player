package com.iven.musicplayergo.photos

/** Order is the exported scenes-v2 vocabulary; scores are cosines, not probabilities.
 * A crop cannot overrule the full frame. Weak or conflicting cues produce no label.
 */
object PhotoScenePolicy {
    val names = linkedMapOf(
        "flowers" to "花卉", "butterfly" to "蝴蝶", "forest" to "树林", "lakeside" to "湖面",
        "river" to "河流", "seashore" to "海滩", "alp" to "山峰", "cliff" to "峭壁",
        "valley" to "山谷", "volcano" to "火山", "snow" to "雪景", "waterfall" to "瀑布",
        "meadow" to "草地", "sunset" to "落日", "night_sky" to "星空", "city" to "城市",
        "city_night" to "城市夜景", "street" to "街道", "building" to "建筑", "bridge" to "桥梁",
        "concert" to "音乐演出", "party" to "聚会", "sport" to "运动", "canoe" to "水上船只",
        "animal" to "动物", "bird" to "鸟类", "insect" to "昆虫", "portrait" to "人物肖像",
        "people" to "人群", "indoor" to "室内", "food" to "食物", "document" to "文档",
        "screenshot" to "屏幕截图", "vehicle" to "车辆", "object" to "日常物品", "artwork" to "绘画",
        "storm" to "阴云", "fire" to "火焰", "desert" to "沙漠", "rain" to "雨景")
    // Visually overlapping classes can support a broad cue without asserting a species
    // or specific landscape. Unrelated classes still compete outside each family.
    private val families = linkedMapOf(
        "flower_plants" to setOf("flowers", "meadow"),
        "waterscape" to setOf("lakeside", "river", "waterfall"),
        "mountain_scenery" to setOf("alp", "cliff", "valley", "snow"),
        "insects" to setOf("butterfly", "insect"),
        "urban" to setOf("city", "city_night", "street", "building", "bridge"),
        "rainy_weather" to setOf("rain", "storm"))
    val broadNames = mapOf("flower_plants" to "花草", "waterscape" to "水景", "mountain_scenery" to "山地景色",
        "insects" to "昆虫", "urban" to "城市场景", "rainy_weather" to "阴雨景色")
    fun title(label: String) = names[label] ?: broadNames[label] ?: label

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

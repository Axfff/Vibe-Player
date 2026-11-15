package com.iven.musicplayergo.discovery

import com.iven.musicplayergo.R
import com.iven.musicplayergo.preferences.UiText

/** Presentation labels are independent from the stable query/schema values. */
object DiscoveryLabels {
    private val labels = mapOf(
        "calm" to R.string.discovery_choice_calm,
        "bright" to R.string.discovery_choice_bright,
        "energetic" to R.string.discovery_choice_energetic,
        "melancholic" to R.string.discovery_choice_melancholic,
        "dreamy" to R.string.discovery_choice_dreamy,
        "focused" to R.string.discovery_choice_focused,
        "ambient" to R.string.discovery_choice_ambient,
        "acoustic" to R.string.discovery_choice_acoustic,
        "piano" to R.string.discovery_choice_piano,
        "electronic" to R.string.discovery_choice_electronic,
        "jazz" to R.string.discovery_choice_jazz,
        "classical" to R.string.discovery_choice_classical,
        "rock" to R.string.discovery_choice_rock,
        "pop" to R.string.discovery_choice_pop,
        "chillout" to R.string.discovery_choice_chillout,
        "instrumental" to R.string.discovery_choice_instrumental,
        "folk" to R.string.discovery_choice_folk,
        "dance" to R.string.discovery_choice_dance,
        "nature" to R.string.discovery_choice_nature,
        "water" to R.string.discovery_choice_water,
        "city" to R.string.discovery_choice_city,
        "indoors" to R.string.discovery_choice_indoors,
        "night" to R.string.discovery_choice_night,
        "daylight" to R.string.discovery_choice_daylight,
        "warm_colors" to R.string.discovery_choice_warm_colors,
        "cool_colors" to R.string.discovery_choice_cool_colors,
        "open_space" to R.string.discovery_choice_open_space,
        "abstract" to R.string.discovery_choice_abstract,
        "other" to R.string.discovery_choice_other,
        "US" to R.string.discovery_choice_us,
        "GB" to R.string.discovery_choice_gb,
        "CN" to R.string.discovery_choice_cn,
        "HK" to R.string.discovery_choice_hk,
        "JP" to R.string.discovery_choice_jp,
        "DE" to R.string.discovery_choice_de,
        "FR" to R.string.discovery_choice_fr,
        "CA" to R.string.discovery_choice_ca,
        "AU" to R.string.discovery_choice_au)
    fun text(value: String): UiText = labels[value]?.let { UiText.of(it) } ?: UiText.Literal(value)
}

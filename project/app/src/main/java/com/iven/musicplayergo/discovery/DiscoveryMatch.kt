package com.iven.musicplayergo.discovery

import java.text.Normalizer
import java.util.Locale

/** Observable catalog metadata only; never claims to infer the sound of a recording. */
data class DiscoveryMatch(val tags: List<String>, val words: List<String>) {
    companion object {
        private val moodTerms = mapOf("calm" to "ambient", "bright" to "pop", "energetic" to "dance",
            "melancholic" to "acoustic", "dreamy" to "ambient", "focused" to "instrumental")
        fun normalized(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
        private fun words(value: String) = Regex("[\\p{L}\\p{N}_]+").findAll(normalized(value)).map { it.value }.toSet()
        fun term(intent: MusicIntent): String = intent.text.trim().ifEmpty {
            intent.tags.distinct().joinToString(" ").ifEmpty { moodTerms.getValue(intent.mood) }
        }
        fun of(track: DiscoveryTrack, intent: MusicIntent): DiscoveryMatch {
            val wanted = intent.tags.map(::normalized).toSet().ifEmpty {
                if (intent.text.isBlank()) setOf(moodTerms.getValue(intent.mood)) else emptySet()
            }
            return DiscoveryMatch(
                (track.catalogTags.orEmpty().map(::normalized).toSet() intersect wanted).sorted(),
                (words(track.title + " " + track.artist) intersect words(term(intent))).sorted())
        }
    }
}

package com.iven.musicplayergo.discovery

import com.iven.musicplayergo.R
import com.iven.musicplayergo.preferences.UiText

/** App-authored wording is localized; matched words remain the user's/catalog's content. */
object DiscoveryPresentation {
    fun reason(track: DiscoveryTrack, intent: MusicIntent): UiText {
        val match = DiscoveryMatch.of(track, intent)
        return UiText.Joined(listOf(
            if (match.words.isEmpty()) UiText.of(R.string.discovery_words_none)
            else UiText.of(R.string.discovery_words_match, match.words.joinToString(", ")),
            when {
                track.catalogTags == null -> UiText.of(R.string.discovery_tags_unknown)
                match.tags.isEmpty() -> UiText.of(R.string.discovery_tags_none)
                else -> UiText.of(R.string.discovery_tags_match, UiText.Joined(match.tags.map(DiscoveryLabels::text)))
            },
            UiText.of(R.string.discovery_metadata_boundary)), "\n")
    }

    fun service(status: DiscoveryStatus?): UiText {
        if (status == null) return UiText.of(R.string.discovery_status_unknown)
        val sources = status.catalogs.map { provider ->
            val name = UiText.of(when (provider.name) {
                "itunes" -> R.string.discovery_source_itunes
                "jamendo" -> R.string.discovery_source_jamendo
                else -> R.string.discovery_source_other
            })
            UiText.of(R.string.discovery_source_state, name, state(provider))
        }.ifEmpty { listOf(UiText.of(R.string.discovery_no_sources)) }
        return UiText.Joined(sources + UiText.of(R.string.discovery_cloud_state, state(status.vision)), "\n")
    }

    private fun state(provider: ProviderState) = UiText.of(when {
        provider.mode == "mock" -> R.string.discovery_state_mock
        provider.mode == "unconfigured" -> R.string.discovery_state_disabled
        provider.mode != "live" -> R.string.discovery_state_unknown
        provider.error != null || provider.state == "error" -> R.string.discovery_state_error
        provider.state == "ok" -> R.string.discovery_state_ok
        provider.state in listOf("not_run", "ready") -> R.string.discovery_state_unchecked
        else -> R.string.discovery_state_unknown
    })
}

package com.iven.musicplayergo.discovery

import java.net.URI
import java.net.URLDecoder

/** Internet identities are intentionally unrelated to MediaStore Music.id. */
data class DiscoveryRef(val provider: String, val id: String) {
    val key get() = "$provider:$id"
    fun valid() = provider in listOf("itunes", "jamendo") && id.matches(Regex("[1-9][0-9]{0,19}"))
}

data class DiscoveryTrack(
    val ref: DiscoveryRef,
    val title: String,
    val artist: String,
    val officialUrl: String,
    val reason: String = "",
    val linkNotice: String = "",
) {
    fun valid() = ref.valid() && title.isNotBlank() && title.length <= 300 &&
        artist.isNotBlank() && artist.length <= 300 && DiscoveryLinks.valid(ref, officialUrl)
}

object DiscoveryChoices {
    val moods = listOf("calm", "bright", "energetic", "melancholic", "dreamy", "focused")
    val tags = listOf("ambient", "acoustic", "piano", "electronic", "jazz", "classical", "rock", "pop", "chillout", "instrumental", "folk", "dance")
    val scenes = listOf("nature", "water", "city", "indoors", "night", "daylight", "warm_colors", "cool_colors", "open_space", "abstract", "other")
    val markets = listOf("US", "GB", "CN", "HK", "JP", "DE", "FR", "CA", "AU")
}

data class MusicIntent(val mood: String = "calm", val tags: List<String> = emptyList(), val text: String = "") {
    fun valid() = mood in DiscoveryChoices.moods && tags.size <= 8 && tags.all { it in DiscoveryChoices.tags } &&
        text.length <= 160 && text.none { it.code < 32 } && !text.contains("://")
}

data class PhotoIntent(val scenes: List<String>, val moods: List<String>, val tags: List<String>, val uncertainty: String) {
    fun valid() = scenes.size <= 8 && scenes.all { it in DiscoveryChoices.scenes } &&
        moods.size in 1..3 && moods.all { it in DiscoveryChoices.moods } &&
        tags.size <= 8 && tags.all { it in DiscoveryChoices.tags } && uncertainty in listOf("low", "medium", "high")
}

data class DiscoveryQuery(
    val intent: MusicIntent,
    val scene: String?,
    val market: String,
    val excluded: List<DiscoveryRef>,
    val photo: PhotoIntent? = null,
) {
    fun valid() = intent.valid() && (scene == null || scene in DiscoveryChoices.scenes) &&
        market in DiscoveryChoices.markets && excluded.size <= 500 && excluded.all { it.valid() } &&
        (photo == null || photo.valid())
}

data class ProviderState(val name: String, val mode: String, val state: String, val error: String?)
data class DiscoveryStatus(val token: String, val vision: ProviderState, val catalogs: List<ProviderState>, val recipient: String, val model: String) {
    val canAnalyze get() = vision.mode == "live"
}
data class DiscoveryResults(val tracks: List<DiscoveryTrack>, val shortage: String, val catalogs: List<ProviderState>)
data class SavedDiscovery(val track: DiscoveryTrack, val intent: MusicIntent, val market: String)
data class DiscoveryCollection(val saved: List<SavedDiscovery> = emptyList(), val excluded: List<DiscoveryRef> = emptyList())

object DiscoveryLinks {
    fun valid(ref: DiscoveryRef, url: String): Boolean = try {
        val uri = URI(url)
        val hosts = when (ref.provider) {
            "itunes" -> setOf("music.apple.com", "itunes.apple.com")
            "jamendo" -> setOf("jamendo.com", "www.jamendo.com")
            else -> emptySet()
        }
        ref.valid() && url.length <= 2048 && url.none { it.code <= 32 || it == '\\' } &&
            uri.scheme == "https" && uri.host in hosts && uri.rawUserInfo == null &&
            uri.port in listOf(-1, 443) && uri.fragment == null && when (ref.provider) {
                "jamendo" -> Regex("^/track/${ref.id}(/.*)?$").matches(uri.path)
                else -> {
                    val songIds = uri.rawQuery.orEmpty().split('&').mapNotNull {
                        val pair = it.split('=', limit = 2)
                        if (pair.size == 2 && pair[0] == "i") URLDecoder.decode(pair[1], "UTF-8") else null
                    }
                    if (songIds.isNotEmpty()) songIds == listOf(ref.id)
                    else Regex(".*/(?:id)?${ref.id}/?$").matches(uri.path)
                }
            }
    } catch (_: Exception) { false }

    /** HTTP is confined to explicitly named development loopbacks in debug builds. */
    fun endpoint(value: String, debug: Boolean): String {
        val normalized = value.trim().trimEnd('/')
        val uri = try { URI(normalized) } catch (_: Exception) { throw DiscoveryFailure("invalid_endpoint") }
        val safe = uri.host != null && uri.rawUserInfo == null && uri.query == null && uri.fragment == null &&
            uri.path.isNullOrEmpty() && normalized.none { it.code <= 32 || it == '\\' } &&
            (uri.port == -1 || uri.port in 1..65535) &&
            (uri.scheme == "https" || (debug && uri.scheme == "http" && uri.host in listOf("127.0.0.1", "10.0.2.2", "localhost")))
        if (!safe) throw DiscoveryFailure("invalid_endpoint")
        return normalized
    }
}

class DiscoveryFailure(val code: String) : Exception(code)

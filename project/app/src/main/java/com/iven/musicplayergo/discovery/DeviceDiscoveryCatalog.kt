package com.iven.musicplayergo.discovery

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Public catalog only. No account, backend, photo upload, artwork or preview request. */
class DeviceDiscoveryCatalog(
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val deadlineMillis: Long = 25_000
) : DiscoveryGateway {
    private val cancelled = AtomicBoolean(false)
    private val active = AtomicReference<HttpURLConnection?>()
    override fun status() = DiscoveryStatus("", ProviderState("vision", "unconfigured", "optional_local", null),
        listOf(ProviderState("itunes", "live", "ready", null)), "", "")
    override fun analyze(token: String, jpeg: ByteArray): PhotoIntent = throw DiscoveryFailure("vision_not_live")
    override fun cancel() { cancelled.set(true); active.getAndSet(null)?.disconnect() }

    override fun discover(token: String, query: DiscoveryQuery): DiscoveryResults {
        if (!query.valid()) throw DiscoveryFailure("invalid_input")
        if (cancelled.get()) throw DiscoveryFailure("cancelled")
        val url = URL("https://itunes.apple.com/search?term=${URLEncoder.encode(DeviceCatalogPolicy.term(query.intent), "UTF-8")}" +
            "&country=${query.market}&media=music&entity=song&limit=40&explicit=No&lang=en_us")
        val connection = open(url)
        active.set(connection)
        val timedOut = AtomicBoolean(false)
        val deadline = deadlines.schedule({ timedOut.set(true); connection.disconnect() }, deadlineMillis, TimeUnit.MILLISECONDS)
        try {
            if (cancelled.get()) throw DiscoveryFailure("cancelled")
            connection.connectTimeout = 8_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code != 200) throw DiscoveryFailure("http_$code")
            // Apple's public search API also serves text/javascript JSON.
            if (connection.contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT) !in
                setOf("application/json", "text/javascript")) throw DiscoveryFailure("invalid_response")
            val raw = connection.inputStream.use { it.readBytesBounded(1_000_000).toString(Charsets.UTF_8) }
            if (timedOut.get()) throw DiscoveryFailure("timeout")
            if (cancelled.get()) throw DiscoveryFailure("cancelled")
            val tracks = DeviceCatalogPolicy.select(DeviceCatalogPolicy.parse(raw), query)
            return DiscoveryResults(tracks, "", listOf(ProviderState("itunes", "live", "ready", null)))
        } catch (e: DiscoveryFailure) { throw e
        } catch (_: SocketTimeoutException) { throw DiscoveryFailure("timeout")
        } catch (_: Exception) {
            throw DiscoveryFailure(if (timedOut.get()) "timeout" else if (cancelled.get()) "cancelled" else "network_error")
        } finally { deadline.cancel(false); active.compareAndSet(connection, null); connection.disconnect() }
    }
    companion object {
        private val deadlines = Executors.newSingleThreadScheduledExecutor { Thread(it, "Catalog deadline").apply { isDaemon = true } }
    }
}

data class DeviceCatalogCandidate(val track: DiscoveryTrack, val genre: String)

/** Same metadata-only policy as the service: user intent wins, two songs per artist, eight results. */
object DeviceCatalogPolicy {
    private val moodTerms = mapOf("calm" to "ambient", "bright" to "pop", "energetic" to "dance",
        "melancholic" to "acoustic", "dreamy" to "ambient", "focused" to "instrumental")
    fun term(intent: MusicIntent): String = intent.text.trim().ifEmpty {
        intent.tags.distinct().joinToString(" ").ifEmpty { moodTerms.getValue(intent.mood) }
    }
    private fun normalized(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
    private fun words(value: String) = Regex("[\\p{L}\\p{N}_]+").findAll(normalized(value)).map { it.value }.toSet()
    fun parse(raw: String): List<DeviceCatalogCandidate> {
        try {
            val rows = JSONObject(raw).getJSONArray("results")
            if (rows.length() > 200) throw DiscoveryFailure("invalid_response")
            return (0 until rows.length()).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                if (row.opt("kind") != "song") return@mapNotNull null
                val id = row.opt("trackId")
                if (id !is Int && id !is Long) return@mapNotNull null
                val title = row.opt("trackName") as? String ?: return@mapNotNull null
                val artist = row.opt("artistName") as? String ?: return@mapNotNull null
                val link = row.opt("trackViewUrl") as? String ?: return@mapNotNull null
                val track = DiscoveryTrack(DiscoveryRef("itunes", id.toString()), title.trim(), artist.trim(), link)
                if (!track.valid()) return@mapNotNull null
                val genre = (row.opt("primaryGenreName") as? String).orEmpty().takeIf { it.length <= 40 }.orEmpty()
                DeviceCatalogCandidate(track, genre)
            }
        } catch (e: DiscoveryFailure) { throw e
        } catch (_: Exception) { throw DiscoveryFailure("invalid_response") }
    }
    fun select(candidates: List<DeviceCatalogCandidate>, query: DiscoveryQuery): List<DiscoveryTrack> {
        if (!query.valid()) throw DiscoveryFailure("invalid_input")
        val wanted = query.intent.tags.map(::normalized).toSet().ifEmpty {
            if (query.intent.text.isBlank()) setOf(moodTerms.getValue(query.intent.mood)) else emptySet()
        }
        val terms = words(term(query.intent))
        val excluded = query.excluded.map { it.key }.toSet()
        val artists = mutableMapOf<String, Int>()
        return candidates.filter { it.track.valid() }.distinctBy { it.track.ref.key }.withIndex()
            .sortedWith(compareByDescending<IndexedValue<DeviceCatalogCandidate>> { if (normalized(it.value.genre) in wanted) 1 else 0 }
                .thenByDescending { (words(it.value.track.title + " " + it.value.track.artist) intersect terms).size }
                .thenBy { it.index })
            .map { it.value.track }.filter { track ->
                val artist = normalized(track.artist)
                if (track.ref.key in excluded || (artists[artist] ?: 0) >= 2) false
                else { artists[artist] = (artists[artist] ?: 0) + 1; true }
            }.take(8)
    }
}

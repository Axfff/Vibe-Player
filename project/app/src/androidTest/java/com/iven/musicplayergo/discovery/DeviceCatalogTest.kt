package com.iven.musicplayergo.discovery

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Deterministic boundary tests; these synthetic rows are never real-catalog evidence. */
@RunWith(AndroidJUnit4::class)
class DeviceCatalogTest {
    private val query = DiscoveryQuery(MusicIntent(text = "piano & rain"), null, "US", emptyList())
    private fun row(id: Any = 123) = JSONObject().put("kind", "song").put("trackId", id)
        .put("trackName", " Piano ").put("artistName", " Artist ").put("primaryGenreName", "Classical")
        .put("trackViewUrl", "https://music.apple.com/us/album/piano/100?i=123")
    private fun response(vararg rows: Any) = JSONObject().put("results", JSONArray(rows.toList())).toString()
    private class Connection(val raw: String, val code: Int = 200, val type: String = "application/json",
        val failure: Exception? = null, val block: Boolean = false) : HttpURLConnection(URL("https://itunes.apple.com/search")) {
        val entered = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun disconnect() { disconnected.countDown() }
        override fun getResponseCode(): Int {
            entered.countDown()
            failure?.let { throw it }
            if (block) { assertTrue(disconnected.await(2, TimeUnit.SECONDS)); throw IOException("disconnected") }
            return code
        }
        override fun getContentType() = type
        override fun getInputStream() = ByteArrayInputStream(raw.toByteArray())
    }
    private fun fails(code: String, action: () -> Unit) {
        assertEquals(code, assertThrows(DiscoveryFailure::class.java, action).code)
    }

    @Test fun parserRejectsMismatchedIdentitiesWrongTypesAndMalformedResponses() {
        val rows = listOf(row(), row(true), row("123"), row(123.5), row(-1), row().put("kind", "movie"),
            row().put("trackName", 12), row().put("artistName", false), row().put("trackViewUrl", 123),
            row().put("trackViewUrl", "https://music.apple.com/us/new"), row().put("trackViewUrl", "https://music.apple.com/us/song/456"),
            row().put("trackName", ""), row().put("artistName", "x".repeat(301)), JSONObject(), "bad row")
        val result = DeviceCatalogPolicy.parse(response(*rows.toTypedArray()))
        assertEquals(1, result.size)
        assertEquals("Piano", result.single().track.title)
        assertEquals("Artist", result.single().track.artist)
        assertEquals("Classical", result.single().genre)
        assertEquals("", DeviceCatalogPolicy.parse(response(row().put("primaryGenreName", 4))).single().genre)
        assertEquals("", DeviceCatalogPolicy.parse(response(row().put("primaryGenreName", "a".repeat(41)))).single().genre)
        assertTrue(DeviceCatalogPolicy.parse(response()).isEmpty())
        listOf("not json", "{}", "{\"results\":{}}", response(*Array(201) { row() })).forEach { raw ->
            fails("invalid_response") { DeviceCatalogPolicy.parse(raw) }
        }
    }
    @Test fun publicRequestSendsOnlyConfirmedWordsAndUsesNoAccountOrPhoto() {
        val connection = Connection(response(row(), row()), type = "text/javascript; charset=utf-8")
        var requested: URL? = null
        val client = DeviceDiscoveryCatalog({ url -> requested = url; connection })
        val status = client.status()
        assertFalse(status.canAnalyze)
        assertTrue(status.token.isEmpty())
        assertEquals("live", status.catalogs.single().mode)
        val result = client.discover("unused secret", query.copy(photo = PhotoIntent(listOf("water"), listOf("calm"), emptyList(), "medium")))
        assertEquals(1, result.tracks.size)
        assertEquals("itunes.apple.com", requested!!.host)
        assertTrue(requested!!.query.contains("term=piano+%26+rain"))
        assertTrue(requested!!.query.contains("country=US"))
        assertFalse(requested.toString().contains("secret"))
        assertFalse(requested.toString().contains("water"))
        assertFalse(connection.instanceFollowRedirects)
        assertFalse(connection.doOutput)
        assertNull(connection.getRequestProperty("Authorization"))
        assertNull(connection.getRequestProperty("X-Discovery-Token"))
        assertEquals(0, connection.disconnected.count)
        fails("vision_not_live") { client.analyze("", byteArrayOf(1)) }
    }
    @Test fun errorsNeverBecomeSubstituteSongsAndResponsesAreBounded() {
        listOf(301, 403, 429, 500).forEach { code ->
            val connection = Connection(response(row()), code)
            fails("http_$code") { DeviceDiscoveryCatalog({ connection }).discover("", query) }
            assertEquals(0, connection.disconnected.count)
        }
        fails("invalid_response") { DeviceDiscoveryCatalog({ Connection("<html/>", type = "text/html") }).discover("", query) }
        fails("response_too_large") { DeviceDiscoveryCatalog({ Connection("a".repeat(1_000_001)) }).discover("", query) }
        fails("timeout") { DeviceDiscoveryCatalog({ Connection("", failure = SocketTimeoutException()) }).discover("", query) }
        fails("network_error") { DeviceDiscoveryCatalog({ Connection("", failure = IOException()) }).discover("", query) }
        fails("invalid_input") { DeviceDiscoveryCatalog({ error("must not connect") }).discover("", query.copy(market = "invalid")) }
        val cancelled = DeviceDiscoveryCatalog({ error("must not connect") })
        cancelled.cancel()
        fails("cancelled") { cancelled.discover("", query) }
    }
    @Test fun cancellationAndDeadlineDisconnectBlockedRequests() {
        val worker = Executors.newSingleThreadExecutor()
        try {
            val connection = Connection("", block = true)
            val client = DeviceDiscoveryCatalog({ connection })
            val future = worker.submit<String> { try { client.discover("", query); "unexpected" } catch (e: DiscoveryFailure) { e.code } }
            assertTrue(connection.entered.await(1, TimeUnit.SECONDS))
            client.cancel()
            assertEquals("cancelled", future.get(2, TimeUnit.SECONDS))
            val timeout = Connection("", block = true)
            fails("timeout") { DeviceDiscoveryCatalog({ timeout }, 30).discover("", query) }
        } finally { worker.shutdownNow() }
    }
}

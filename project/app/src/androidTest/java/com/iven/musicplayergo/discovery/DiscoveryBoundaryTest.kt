package com.iven.musicplayergo.discovery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.security.NetworkSecurityPolicy
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DiscoveryBoundaryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun strictJsonRejectsBadMetadataAndNonLiveTracksWhileRetainingGoodOnes() {
        val bad = DiscoveryFixtureServer.row("789").put("title", 123)
        val unsafe = DiscoveryFixtureServer.row("900").put("officialUrl", "https://evil.test/?i=900")
        val rows = JSONArray().put(DiscoveryFixtureServer.row()).put(DiscoveryFixtureServer.row()).put(bad).put(unsafe).put(JSONObject())
        val result = DiscoveryJson.results(DiscoveryFixtureServer.results(rows))
        assertEquals(listOf("123"), result.tracks.map { it.ref.id })
        assertTrue(result.shortage.contains("4 invalid"))
        assertTrue(DiscoveryJson.results(DiscoveryFixtureServer.results(mode = "mock")).tracks.isEmpty())
        fails { DiscoveryJson.results("not json") }
        fails { DiscoveryJson.results(DiscoveryFixtureServer.results(JSONArray().apply { repeat(9) { put(DiscoveryFixtureServer.row()) } })) }
        fails { DiscoveryJson.status(JSONObject(DiscoveryFixtureServer.status()).put("demoToken", "bad").toString()) }
    }

    @Test fun photoSchemaAndUserOverrideCannotBeReplacedWithMockOrInventedMusic() {
        val photo = DiscoveryJson.photo(DiscoveryFixtureServer.photo())
        val query = DiscoveryQuery(MusicIntent("energetic", listOf("dance"), "jazz"), "city", "JP", listOf(DiscoveryRef("itunes", "123")), photo)
        val encoded = JSONObject(DiscoveryJson.query(query))
        assertEquals("energetic", encoded.getJSONObject("intent").getString("targetMood"))
        assertEquals("jazz", encoded.getJSONObject("intent").getString("text"))
        assertEquals("dreamy", encoded.getJSONObject("visualSuggestions").getJSONArray("visualMoodSuggestions").getString(0))
        assertEquals("photo", encoded.getString("inputMode"))
        assertTrue(encoded.getBoolean("confirmed"))
        assertEquals(1, encoded.getJSONArray("exclude").length())
        fails { DiscoveryJson.photo(DiscoveryFixtureServer.photo("mock")) }
        val invalid = JSONObject(DiscoveryFixtureServer.photo())
        invalid.getJSONObject("intent").put("songs", JSONArray().put("fabricated"))
        fails { DiscoveryJson.photo(invalid.toString()) }
        invalid.getJSONObject("intent").remove("songs")
        invalid.getJSONObject("intent").put("visualMoodSuggestions", JSONArray().put("invented"))
        fails { DiscoveryJson.photo(invalid.toString()) }
        fails { DiscoveryJson.query(query.copy(market = "XX")) }
    }

    @Test fun collectionFileSurvivesNewRepositoryAndCorruptionDoesNotOverwriteIt() {
        val file = File(context.noBackupFilesDir, "discovery-test-${System.nanoTime()}.json")
        try {
            val repository = DiscoveryRepository(DiscoveryFileStorage(file))
            assertTrue(repository.load().saved.isEmpty())
            val track = DiscoveryJson.results(DiscoveryFixtureServer.results()).tracks.first()
            repository.save(track, MusicIntent("focused", listOf("piano")), "JP")
            repository.exclude(DiscoveryRef("itunes", "456"))
            val restored = DiscoveryRepository(DiscoveryFileStorage(file)).load()
            assertEquals("123", restored.saved.single().track.ref.id)
            assertEquals("JP", restored.saved.single().market)
            assertEquals("focused", restored.saved.single().intent.mood)
            assertFalse(file.readText().contains("recommendationReason"))
            assertFalse(file.readText().contains("previewUrl"))
            file.writeText("{broken")
            val corrupt = DiscoveryRepository(DiscoveryFileStorage(file))
            fails { corrupt.load() }
            fails { corrupt.save(track, MusicIntent(), "US") }
            assertEquals("{broken", file.readText())
            fails { DiscoveryJson.decode("{\"version\":2}") }
            fails { DiscoveryFileStorage(context.noBackupFilesDir).save(restored) }
            fails { ByteArrayInputStream(ByteArray(8193)).readBytesBounded(8192) }
        } finally { file.delete() }
    }

    @Test fun imageIsOrientedResizedAndMetadataRemovedBeforeUpload() {
        val file = File(context.cacheDir, "discovery-photo-${System.nanoTime()}.jpg")
        val bitmap = Bitmap.createBitmap(1600, 800, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            ExifInterface(file).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                setAttribute(ExifInterface.TAG_ARTIST, "private fixture metadata")
                setLatLong(1.0, 2.0)
                saveAttributes()
            }
            val bytes = DiscoveryPhoto.sanitize(file.readBytes())
            val result = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertEquals(640, result.width)
            assertEquals(1280, result.height)
            result.recycle()
            ExifInterface(ByteArrayInputStream(bytes)).apply {
                assertNull(getAttribute(ExifInterface.TAG_ARTIST))
                assertNull(latLong)
                assertEquals(0, rotationDegrees)
            }
            val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            assertTrue(DiscoveryPhoto.sanitize(png).isNotEmpty())
            fails { DiscoveryPhoto.sanitize(byteArrayOf(1, 2, 3)) }
            fails { DiscoveryPhoto.sanitize(byteArrayOf()) }
            fails { DiscoveryPhoto.sanitize(ByteArray(5 * 1024 * 1024 + 1)) }
        } finally { bitmap.recycle(); file.delete() }
    }

    @Test fun networkPolicyAndTransportUseRealLocalHttpWithTokenAndNoRedirectFollowing() {
        listOf("localhost", "127.0.0.1", "10.0.2.2").forEach { assertTrue(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(it)) }
        assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("example.org"))
        DiscoveryFixtureServer().use { server ->
            val client = DiscoveryClient(server.endpoint)
            val status = client.status()
            val query = DiscoveryQuery(MusicIntent(), null, "US", emptyList())
            assertEquals(2, client.discover(status.token, query).tracks.size)
            val request = server.requests.last()
            assertEquals(DiscoveryFixtureServer.TOKEN, request.headers["x-discovery-token"])
            assertEquals("manual", JSONObject(request.body).getString("inputMode"))
            listOf(302, 403, 429, 500).forEach { code ->
                server.handler = { DiscoveryFixtureServer.Response("do not expose upstream secret", code) }
                val error = fails { client.status() }
                assertEquals("http_$code", error.code)
                assertFalse(error.message.orEmpty().contains("secret"))
            }
            server.handler = { DiscoveryFixtureServer.Response("not json", type = "text/html") }
            assertEquals("invalid_response", fails { client.status() }.code)
            server.handler = { DiscoveryFixtureServer.Response("x".repeat(1_000_001)) }
            assertEquals("response_too_large", fails { client.status() }.code)
            client.cancel()
            assertEquals("cancelled", fails { client.status() }.code)
        }
    }

    @Test fun transportDeadlineAndCancellationAreBounded() {
        DiscoveryFixtureServer().use { server ->
            server.handler = { DiscoveryFixtureServer.Response(DiscoveryFixtureServer.status(), delay = 2_000) }
            val start = System.nanoTime()
            assertEquals("timeout", fails { DiscoveryClient(server.endpoint, 150).status() }.code)
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 3_000)
            val executor = Executors.newSingleThreadExecutor()
            try {
                val client = DiscoveryClient(server.endpoint)
                val result = executor.submit<String> { fails { client.status() }.code }
                Thread.sleep(100)
                client.cancel()
                assertEquals("cancelled", result.get(3, TimeUnit.SECONDS))
            } finally { executor.shutdownNow() }
        }
    }

    private fun fails(block: () -> Unit): DiscoveryFailure {
        try { block(); fail("Expected controlled failure") } catch (error: DiscoveryFailure) { return error }
        throw AssertionError()
    }
}

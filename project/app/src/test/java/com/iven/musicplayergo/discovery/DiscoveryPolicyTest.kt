package com.iven.musicplayergo.discovery

import org.junit.Assert.*
import org.junit.Test

class DiscoveryPolicyTest {
    private fun track(id: String = "123", provider: String = "itunes") = DiscoveryTrack(
        DiscoveryRef(provider, id), "Piano", "Artist", if (provider == "itunes") "https://music.apple.com/us/album/music/99?i=$id" else "https://www.jamendo.com/track/$id")

    @Test fun officialLinksRejectProtocolHostCredentialsPortFragmentsAndWrongSong() {
        val ref = track().ref
        assertTrue(track().valid())
        assertTrue(track(provider = "jamendo").valid())
        assertTrue(DiscoveryLinks.valid(ref, "https://itunes.apple.com/us/song/id123"))
        listOf("http://music.apple.com/us/song/123", "https://music.apple.com.evil.test/us/song/123",
            "https://evil.test/?i=123", "https://name@music.apple.com/us/song/123", "https://music.apple.com:444/us/song/123",
            "https://music.apple.com/us/song/123#x", "https://music.apple.com/us/song/999", "https://music.apple.com/us/song/123?i=999",
            "https://music.apple.com/us/song/123?i=123&i=999", "https://music.apple.com/us/song/123?i=", "https://music.apple.com/\n123",
            "https://music.apple.com\\evil.test/123", "https://music.apple.com/%zz", "javascript:alert(123)").forEach {
            assertFalse(it, DiscoveryLinks.valid(ref, it))
        }
        assertFalse(DiscoveryLinks.valid(DiscoveryRef("other", "123"), track().officialUrl))
        assertFalse(DiscoveryLinks.valid(DiscoveryRef("jamendo", "12"), "https://jamendo.com/track/123"))
        assertFalse(track().copy(title = " ").valid())
        assertFalse(track().copy(artist = "a".repeat(301)).valid())
    }

    @Test fun onlyKnownTrackRoutesAndUnambiguousDecodedIdentityAreAccepted() {
        val apple = DiscoveryRef("itunes","123")
        listOf("https://music.apple.com/us/new?i=123", "https://music.apple.com/us/artist/someone/123",
            "https://music.apple.com/us/album/title/123", "https://music.apple.com/us/song/title/999?i=123",
            "https://music.apple.com/us/song/123?i=123&%69=999", "https://music.apple.com/us/song/123?%69=999",
            "https://music.apple.com/us/song/123?%2569=999", "https://music.apple.com/us/song/123?i",
            "https://music.apple.com/us/song/../123", "https://music.apple.com/us/song/%2e%2e/123",
            "https://music.apple.com/us/song/a%2fb/123", "https://music.apple.com/us/song/%252e%252e/123")
            .forEach { assertFalse(it,DiscoveryLinks.valid(apple,it)) }
        listOf("https://music.apple.com/us/song/title/123", "https://itunes.apple.com/gb/album/title/id99?i=123&uo=4",
            "https://music.apple.com/us/song/123?%69=%31%32%33", "https://music.apple.com/us/album/title/99?i=123")
            .forEach { assertTrue(it,DiscoveryLinks.valid(apple,it)) }
        val jamendo = DiscoveryRef("jamendo","123")
        listOf("https://www.jamendo.com/track/123/../../discover", "https://jamendo.com/track/123/%2e%2e",
            "https://jamendo.com/track/123/a/b", "https://jamendo.com/track/123/a%2fb", "https://jamendo.com/track/123/%252e%252e")
            .forEach { assertFalse(it,DiscoveryLinks.valid(jamendo,it)) }
        assertTrue(DiscoveryLinks.valid(jamendo,"https://www.jamendo.com/track/123/some-title"))
    }
    @Test fun releaseRequiresHttpsAndDebugHttpIsLimitedToExactLoopbackHosts() {
        assertEquals("https://example.org", DiscoveryLinks.endpoint(" https://example.org/ ", false))
        listOf("127.0.0.1", "10.0.2.2", "localhost").forEach {
            assertEquals("http://$it:8765", DiscoveryLinks.endpoint("http://$it:8765", true))
            fails("invalid_endpoint") { DiscoveryLinks.endpoint("http://$it:8765", false) }
        }
        listOf("", "http://example.org", "http://localhost.evil.test", "https://u:p@example.org", "https://example.org/path", "https://example.org?q=1", "https://example.org#x", "https://example.org:99999", "file:///tmp/file", "not a url").forEach {
            fails("invalid_endpoint") { DiscoveryLinks.endpoint(it, true) }
        }
    }

    @Test fun inputUsesClosedChoicesAndBoundedPlainText() {
        val intent = MusicIntent("focused", listOf("piano"), "Evening jazz")
        assertTrue(DiscoveryQuery(intent, "city", "US", listOf(track().ref)).valid())
        assertFalse(intent.copy(mood = "invented").valid())
        assertFalse(intent.copy(tags = listOf("secret" )).valid())
        assertFalse(intent.copy(tags = List(9) { "piano" }).valid())
        listOf("x".repeat(161), "hello\nworld", "https://example.org").forEach { assertFalse(intent.copy(text = it).valid()) }
        assertFalse(DiscoveryQuery(intent, "private_location", "US", emptyList()).valid())
        assertFalse(DiscoveryQuery(intent, null, "XX", emptyList()).valid())
        assertFalse(DiscoveryQuery(intent, null, "US", List(501) { track().ref }).valid())
        assertFalse(DiscoveryRef("itunes", "01").valid())
        assertFalse(DiscoveryRef("itunes", "-1").valid())
        val photo = PhotoIntent(listOf("night"), listOf("calm"), listOf("ambient"), "high")
        assertTrue(photo.valid())
        assertFalse(photo.copy(moods = emptyList()).valid())
        assertFalse(photo.copy(scenes = listOf("health")).valid())
        assertFalse(photo.copy(uncertainty = "perfect").valid())
    }

    private class Store : DiscoveryStorage {
        var value = DiscoveryCollection()
        var failRead = false
        var failWrite = false
        override fun load(): DiscoveryCollection { if (failRead) throw DiscoveryFailure("read_failed"); return value }
        override fun save(value: DiscoveryCollection) { if (failWrite) throw DiscoveryFailure("write_failed"); this.value = value }
    }

    @Test fun collectionRestoresDeduplicatesSeparatesNamespacesAndKeepsUserIntent() {
        val storage = Store()
        val repository = DiscoveryRepository(storage)
        assertTrue(repository.load().saved.isEmpty())
        val intent = MusicIntent("energetic", listOf("dance"))
        repository.save(track().copy(reason = "temporary explanation"), intent, "JP")
        repository.save(track(), intent, "JP")
        repository.save(track(provider = "jamendo"), intent, "JP")
        repository.exclude(track("456").ref)
        repository.exclude(track("456").ref)
        val fresh = DiscoveryRepository(storage)
        assertEquals(2, fresh.load().saved.size)
        assertEquals(intent, fresh.collection.saved.first().intent)
        assertEquals("JP", fresh.collection.saved.first().market)
        assertEquals("", fresh.collection.saved.first().track.reason)
        assertEquals(3, fresh.exclusions().size)
        fresh.remove(track().ref)
        assertEquals("jamendo", fresh.collection.saved.single().track.ref.provider)
        fresh.clearExclusions()
        assertEquals(listOf(track(provider = "jamendo").ref), fresh.exclusions())
    }

    @Test fun failedWritesDoNotChangeMemoryOrLosePreviousCollection() {
        val storage = Store()
        val repository = DiscoveryRepository(storage)
        repository.load()
        repository.save(track(), MusicIntent(), "US")
        storage.failWrite = true
        fails("write_failed") { repository.remove(track().ref) }
        fails("write_failed") { repository.exclude(track("456").ref) }
        assertEquals(1, repository.collection.saved.size)
        assertEquals(1, storage.value.saved.size)
        assertTrue(repository.collection.excluded.isEmpty())
        storage.failRead = true
        fails("read_failed") { repository.load() }
        fails("collection_unreadable") { repository.save(track("456"), MusicIntent(), "US") }
        assertEquals(1, storage.value.saved.size)
    }

    @Test fun collectionRejectsCorruptIdentityAndEnforcesBoundsBeforeWrite() {
        val storage = Store()
        storage.value = DiscoveryCollection(excluded = listOf(DiscoveryRef("itunes", "0")))
        fails("collection_invalid") { DiscoveryRepository(storage).load() }
        storage.value = DiscoveryCollection(excluded = List(300) { DiscoveryRef("itunes", (it + 1).toString()) })
        val repository = DiscoveryRepository(storage)
        repository.load()
        fails("collection_limit") { repository.exclude(DiscoveryRef("itunes", "9999")) }
        assertEquals(300, storage.value.excluded.size)
        fails("invalid_track") { repository.save(track().copy(officialUrl = "https://evil.test"), MusicIntent(), "US") }
        fails("invalid_track") { repository.exclude(DiscoveryRef("bad", "123")) }
    }

    private fun fails(code: String, block: () -> Unit) {
        try { block(); fail("Expected $code") } catch (error: DiscoveryFailure) { assertEquals(code, error.code) }
    }
}

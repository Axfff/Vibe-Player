package com.iven.musicplayergo.discovery

import org.junit.Assert.*
import org.junit.Test

class DeviceCatalogPolicyTest {
    private fun item(id: Int, title: String, artist: String = "Artist $id", genre: String = "") = DeviceCatalogCandidate(
        DiscoveryTrack(DiscoveryRef("itunes", "$id"), title, artist, "https://music.apple.com/us/song/$id"), genre)
    private fun query(intent: MusicIntent = MusicIntent(), excluded: List<DiscoveryRef> = emptyList()) = DiscoveryQuery(intent, null, "US", excluded)

    @Test fun userWordsOverrideStyleAndMoodWithoutUsingUnconfirmedPhotoCues() {
        assertEquals("night piano", DeviceCatalogPolicy.term(MusicIntent("energetic", listOf("rock"), " night piano ")))
        assertEquals("piano jazz", DeviceCatalogPolicy.term(MusicIntent(tags = listOf("piano", "piano", "jazz"))))
        assertEquals(listOf("ambient", "pop", "dance", "acoustic", "ambient", "instrumental"),
            DiscoveryChoices.moods.map { DeviceCatalogPolicy.term(MusicIntent(it)) })
        val pool = listOf(item(1, "night piano"), item(2, "rock"))
        assertEquals(DeviceCatalogPolicy.select(pool, query()), DeviceCatalogPolicy.select(pool,
            query().copy(photo = PhotoIntent(listOf("city"), listOf("energetic"), listOf("rock"), "high"))))
    }
    @Test fun styleThenMetadataThenProviderOrderRankWithoutInventingAudioFeatures() {
        val pool = listOf(item(1, "Piano", genre = "Pop"), item(2, "Other", genre = "Piano"), item(3, "ＰＩＡＮＯ", genre = "piano"), item(4, "Piano", genre = "piano"))
        assertEquals(listOf("3", "4", "2", "1"), DeviceCatalogPolicy.select(pool, query(MusicIntent(tags = listOf("piano")))).map { it.ref.id })
        assertEquals(listOf("1", "3", "4", "2"), DeviceCatalogPolicy.select(pool, query(MusicIntent(text = "piano"))).map { it.ref.id })
        assertTrue(DeviceCatalogPolicy.select(pool, query()).all { it.reason.isEmpty() })
    }
    @Test fun identitiesExclusionsArtistDiversityAndRealShortagesArePreserved() {
        val pool = listOf(item(1, "One", "ＡＢＣ"), item(1, "Duplicate", "Another"), item(2, "Two", "abc"),
            item(3, "Three", "  ABC "), item(4, "Hidden"), item(5, "Unsafe").let { it.copy(track = it.track.copy(officialUrl = "https://music.apple.com/us/new")) })
        assertEquals(listOf("1", "2"), DeviceCatalogPolicy.select(pool, query(excluded = listOf(DiscoveryRef("itunes", "4")))).map { it.ref.id })
        assertEquals(8, DeviceCatalogPolicy.select((1..20).map { item(it, "Song") }, query()).size)
        assertTrue(DeviceCatalogPolicy.select(emptyList(), query()).isEmpty())
        assertThrows(DiscoveryFailure::class.java) { DeviceCatalogPolicy.select(pool, query().copy(market = "invalid")) }
    }
}

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

    @Test fun explicitPianoSurvivesEightRockMatchesAndTiesKeepProviderOrder() {
        val pool = (1..8).map { item(it, "Other $it", genre = "Rock") } + item(9, "ＰＩＡＮＯ", genre = "Classical")
        val input = query(MusicIntent(tags = listOf("rock"), text = "piano"))
        val result = DeviceCatalogPolicy.select(pool, input)
        assertEquals(listOf("9", "1", "2", "3", "4", "5", "6", "7"), result.map { it.ref.id })
        assertEquals(DiscoveryMatch(emptyList(), listOf("piano")), DiscoveryMatch.of(result.first(), input.intent))
        assertEquals(DiscoveryMatch(listOf("rock"), emptyList()), DiscoveryMatch.of(result[1], input.intent))
        assertEquals(listOf("Classical"), result.first().catalogTags)
        assertEquals((1..8).map(Int::toString), DeviceCatalogPolicy.select(pool, input.copy(intent = input.intent.copy(text = " "))).map { it.ref.id })
        assertFalse(DeviceCatalogPolicy.select(pool, input.copy(excluded = listOf(DiscoveryRef("itunes", "9")))).any { it.ref.id == "9" })
    }

    @Test fun multipleWordsArtistWordsAndSecondaryTagsBreakTiesWithoutChangingSourceRows() {
        val pool = listOf(item(1, "Piano", genre = "Classical"), item(2, "Piano", genre = "Rock"),
            item(3, "Moon", artist = "ＰＩＡＮＯ ＮＩＧＨＴ", genre = "Classical"), item(4, "Piano", genre = "Rock"))
        val intent = MusicIntent(tags = listOf("rock"), text = "Night PIANO piano")
        val result = DeviceCatalogPolicy.select(pool, query(intent))
        assertEquals(listOf("3", "2", "4", "1"), result.map { it.ref.id })
        assertEquals(listOf("night", "piano"), DiscoveryMatch.of(result.first(), intent).words)
        assertTrue(pool.all { it.track.catalogTags == null })
        assertEquals(result, DeviceCatalogPolicy.select(pool, query(intent)))
    }
}

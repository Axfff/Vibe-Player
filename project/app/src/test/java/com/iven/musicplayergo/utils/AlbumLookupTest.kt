package com.iven.musicplayergo.utils

import com.iven.musicplayergo.models.Album
import com.iven.musicplayergo.models.Music
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AlbumLookupTest {
    private fun album(title: String?, trackIds: List<Long> = listOf(3, 1, 2)) = Album(
        title, "2026", trackIds.map { id ->
            Music(
                "Artist", 2026, id.toInt(), "Track $id", "$id.mp3", 1000L,
                title, 10L, "Music", id, "ARTIST", 0, 0
            )
        }.toMutableList(), trackIds.size * 1000L
    )

    @Test
    fun exactAlbumKeepsItsPositionAndSongOrder() {
        val selected = album("Selected")
        val albums = mutableMapOf("Artist" to listOf(album("First"), selected))

        val result = MusicUtils.getAlbumFromList("Artist", "Selected", albums)

        assertSame(selected, result?.first)
        assertEquals(1, result?.second)
        assertSame(selected.music, MusicUtils.getAlbumSongs("Artist", "Selected", albums))
        assertEquals(listOf(3L, 1L, 2L), selected.music?.map { it.id })
    }

    @Test
    fun missingAlbumDoesNotSubstituteTheArtistsFirstAlbum() {
        val albums = mutableMapOf("Artist" to listOf(album("First"), album("Second")))

        assertNull(MusicUtils.getAlbumFromList("Artist", "Removed", albums))
        assertNull(MusicUtils.getAlbumSongs("Artist", "Removed", albums))
    }

    @Test
    fun missingArtistDoesNotUseAnotherArtistsAlbum() {
        val albums = mutableMapOf("Artist" to listOf(album("Selected")))

        assertNull(MusicUtils.getAlbumFromList("Other artist", "Selected", albums))
        assertNull(MusicUtils.getAlbumSongs("Other artist", "Selected", albums))
    }

    @Test
    fun absentAndEmptyLibrariesHaveNoAlbum() {
        assertNull(MusicUtils.getAlbumFromList("Artist", "Selected", null))
        assertNull(MusicUtils.getAlbumFromList("Artist", "Selected", mutableMapOf()))
        assertNull(MusicUtils.getAlbumFromList("Artist", "Selected", mutableMapOf("Artist" to emptyList())))
        assertNull(MusicUtils.getAlbumSongs("Artist", "Selected", null))
    }

    @Test
    fun nullAlbumTitleOnlyMatchesTheUnknownAlbumGroup() {
        val unknownAlbum = album(null)
        val albums = mutableMapOf("Artist" to listOf(album("First"), unknownAlbum))

        assertSame(unknownAlbum, MusicUtils.getAlbumFromList("Artist", null, albums)?.first)
        assertSame(unknownAlbum.music, MusicUtils.getAlbumSongs("Artist", null, albums))
        assertNull(MusicUtils.getAlbumFromList("Artist", null, mutableMapOf("Artist" to listOf(album("First")))))
    }
}

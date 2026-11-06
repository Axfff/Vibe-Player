package com.iven.musicplayergo.utils

import com.iven.musicplayergo.models.Music
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class MusicSearchTest {
    @Test
    fun titleSearchKeepsMatchesAroundMissingMetadataInLibraryOrder() {
        val first = song(3, title = "Night Drive")
        val last = song(1, title = "NIGHT Train")
        val library = listOf(first, song(2, title = null), song(4, title = "Morning"), last)

        assertEquals(listOf(first, last), filterMusicByQuery("nIgHt", library, false))
    }

    @Test
    fun filenameSearchSkipsMissingFilenamesAndUsesOnlyTheSelectedField() {
        val first = song(3, title = null, displayName = "Night.ogg")
        val last = song(1, displayName = "NIGHT.mp3")
        val library = listOf(first, song(2, title = "Night", displayName = null),
            song(4, title = "Night", displayName = "Morning.mp3"), last)

        assertEquals(listOf(first, last), filterMusicByQuery("nIgHt", library, true))
    }

    @Test
    fun titleSearchDoesNotMatchTheFilename() {
        val library = listOf(song(1, title = null, displayName = "Night.mp3"),
            song(2, title = "Morning", displayName = "Night.ogg"))

        assertEquals(emptyList<Music>(), filterMusicByQuery("night", library, false))
    }

    @Test
    fun clearingTheQueryRestoresEverySongIncludingMissingMetadata() {
        val library = listOf(song(3, title = null, displayName = null), song(1))

        for (showDisplayName in listOf(false, true)) {
            for (query in listOf(null, "")) {
                assertEquals(library, filterMusicByQuery(query, library, showDisplayName))
            }
        }
    }

    @Test
    fun missingOrEmptyLibraryReturnsNoMatches() {
        assertEquals(emptyList<Music>(), filterMusicByQuery("night", null, false))
        assertEquals(emptyList<Music>(), filterMusicByQuery("night", emptyList(), true))
    }

    @Test
    fun caseInsensitiveSearchDoesNotDependOnTheDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            val library = listOf(song(1, title = "NIGHT", displayName = "NIGHT.mp3"))

            assertEquals(library, filterMusicByQuery("night", library, false))
            assertEquals(library, filterMusicByQuery("night", library, true))
        } finally {
            Locale.setDefault(previous)
        }
    }

    private fun song(id: Long, title: String? = "Song", displayName: String? = "song.mp3") = Music(
        artist = "Artist", year = 2026, track = 0, title = title, displayName = displayName,
        duration = 60_000, album = "Album", albumId = 1, relativePath = "Music", id = id,
        launchedBy = "", startFrom = 0, dateAdded = 0
    )
}

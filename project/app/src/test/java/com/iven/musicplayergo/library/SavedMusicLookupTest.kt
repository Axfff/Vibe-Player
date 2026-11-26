package com.iven.musicplayergo.library

import com.iven.musicplayergo.models.Music
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SavedMusicLookupTest {
    private val saved = Music(
        "Artist B", 2026, 1, "Track", "track.mp3", 180000L,
        "Greatest Hits", 20L, "Old folder", 2L, "PLAYLIST", 42000, 100
    )

    @Test
    fun anotherArtistBeforeTheSavedRecordingDoesNotStealItsMatch() {
        val otherArtist = saved.copy(artist = "Artist A", id = 10L, albumId = 100L)
        val refreshed = saved.copy(id = 20L, albumId = 200L)

        assertSame(refreshed, findSavedMusic(saved, listOf(otherArtist, refreshed)))
    }

    @Test
    fun onlyAnotherArtistsRecordingIsNotAMatch() {
        assertNull(findSavedMusic(saved, listOf(saved.copy(artist = "Artist A"))))
    }

    @Test
    fun nullArtistMatchesOnlyNullArtist() {
        val unknownArtist = saved.copy(artist = null)

        assertNull(findSavedMusic(saved, listOf(unknownArtist)))
        assertNull(findSavedMusic(unknownArtist, listOf(saved)))
        assertSame(unknownArtist, findSavedMusic(unknownArtist, listOf(saved, unknownArtist)))
    }

    @Test
    fun changedStorageAndPlaybackMetadataStillResolveTheSameRecording() {
        val refreshed = saved.copy(
            id = 20L, albumId = 200L, relativePath = "New folder", dateAdded = 300,
            launchedBy = "ARTIST", startFrom = 0
        )

        assertSame(refreshed, findSavedMusic(saved, listOf(refreshed)))
    }

    @Test
    fun eachExistingRecordingMetadataGuardIsRetained() {
        val mismatches = listOf(
            saved.copy(title = "Other track"),
            saved.copy(displayName = "other.mp3"),
            saved.copy(track = 2),
            saved.copy(album = "Other album"),
            saved.copy(year = 2025),
            saved.copy(duration = 180001L)
        )

        mismatches.forEach { assertNull(findSavedMusic(saved, listOf(it))) }
    }

    @Test
    fun nullSongAndAbsentOrEmptyLibraryHaveNoMatch() {
        assertNull(findSavedMusic(null, listOf(saved)))
        assertNull(findSavedMusic(saved, null))
        assertNull(findSavedMusic(saved, emptyList()))
    }
}

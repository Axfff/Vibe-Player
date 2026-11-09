package com.iven.musicplayergo.utils

import androidx.recyclerview.widget.RecyclerView
import com.iven.musicplayergo.models.Album
import com.iven.musicplayergo.models.Music
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayingAlbumPositionTest {
    private fun song(artist: String? = "Artist A", album: String? = "Greatest Hits") = Music(
        artist, 2026, 1, "Track", "track.mp3", 1000L,
        album, 10L, "Music", 1L, "ARTIST", 0, 0
    )

    private fun album(song: Music) = Album(song.album, "2026", mutableListOf(song), song.duration)

    @Test
    fun anotherArtistsSharedAlbumTitleHasNoPlayingPosition() {
        val albums = mutableMapOf(
            "Artist B" to listOf(album(song("Artist B", "Debut")), album(song("Artist B")))
        )

        assertEquals(
            RecyclerView.NO_POSITION,
            MusicUtils.getPlayingAlbumPosition("Artist B", albums, song())
        )
    }

    @Test
    fun playingArtistKeepsTheExactAlbumPosition() {
        val currentSong = song()
        val albums = mutableMapOf("Artist A" to listOf(album(song(album = "Debut")), album(currentSong)))

        assertEquals(1, MusicUtils.getPlayingAlbumPosition("Artist A", albums, currentSong))
    }

    @Test
    fun noCurrentSongDoesNotSelectTheUnknownAlbumGroup() {
        val albums = mutableMapOf("Artist A" to listOf(album(song()), album(song(album = null))))

        assertEquals(
            RecyclerView.NO_POSITION,
            MusicUtils.getPlayingAlbumPosition("Artist A", albums, null)
        )
    }

    @Test
    fun missingLibraryArtistOrAlbumHasNoPlayingPosition() {
        val currentSong = song()
        val albums = mutableMapOf("Artist A" to listOf(album(song(album = "Debut"))))

        assertEquals(RecyclerView.NO_POSITION, MusicUtils.getPlayingAlbumPosition("Artist A", null, currentSong))
        assertEquals(RecyclerView.NO_POSITION, MusicUtils.getPlayingAlbumPosition("Artist A", mutableMapOf(), currentSong))
        assertEquals(RecyclerView.NO_POSITION, MusicUtils.getPlayingAlbumPosition("Artist A", mutableMapOf("Artist A" to emptyList()), currentSong))
        assertEquals(RecyclerView.NO_POSITION, MusicUtils.getPlayingAlbumPosition("Artist A", albums, currentSong))
        assertEquals(RecyclerView.NO_POSITION, MusicUtils.getPlayingAlbumPosition(null, albums, currentSong))
        assertEquals(RecyclerView.NO_POSITION, MusicUtils.getPlayingAlbumPosition("Artist A", albums, song(artist = null)))
    }

    @Test
    fun playingSongsNullAlbumTitleStillMatchesItsArtistsUnknownGroup() {
        val currentSong = song(album = null)
        val albums = mutableMapOf("Artist A" to listOf(album(song()), album(currentSong)))

        assertEquals(1, MusicUtils.getPlayingAlbumPosition("Artist A", albums, currentSong))
    }
}

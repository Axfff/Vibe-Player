package com.iven.musicplayergo.player

import com.iven.musicplayergo.models.Music
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistFailurePlanTest {
    private fun song(id: Long) = Music(
        "Artist", 2026, id.toInt(), "Track $id", "$id.mp3", 1000L,
        "Album", 10L, "Music", id, "CUSTOM_PLAYLIST", 0, 0
    )

    @Test
    fun failedMiddleTrackAdvancesWithoutChangingSavedOrder() {
        val saved = listOf(song(3), song(1), song(2))
        val result = planAfterPlaylistFailure(saved, saved[1], repeatList = false)
        assertEquals(listOf(song(3), song(2)), result.remaining)
        assertEquals(song(2), result.next)
        assertEquals(listOf(song(3), song(1), song(2)), saved)
    }

    @Test
    fun failedLastTrackRespectsListEndPreference() {
        val snapshot = listOf(song(1), song(2))
        assertNull(planAfterPlaylistFailure(snapshot, song(2), repeatList = false).next)
        assertEquals(song(1), planAfterPlaylistFailure(snapshot, song(2), repeatList = true).next)
    }

    @Test
    fun aFullyUnavailablePlaylistExhaustsAfterOneAttemptPerEntry() {
        var snapshot = listOf(song(3), song(1), song(2))
        var next: Music? = snapshot.first()
        val attempted = mutableListOf<Long?>()
        while (next != null) {
            attempted.add(next.id)
            val result = planAfterPlaylistFailure(snapshot, next, repeatList = true)
            snapshot = result.remaining
            next = result.next
        }
        assertEquals(listOf(3L, 1L, 2L), attempted)
        assertTrue(snapshot.isEmpty())
    }

    @Test
    fun aLateDuplicateFailureCannotStartAnotherTrack() {
        val first = planAfterPlaylistFailure(listOf(song(1), song(2)), song(1), repeatList = true)
        val repeated = planAfterPlaylistFailure(first.remaining, song(1), repeatList = true)
        assertEquals(first.remaining, repeated.remaining)
        assertNull(repeated.next)
    }

    @Test
    fun positionAndPlaybackSourceDoNotChangeTrackIdentity() {
        val failed = song(1).copy(startFrom = 30, launchedBy = "other")
        val result = planAfterPlaylistFailure(listOf(song(1), song(2)), failed, repeatList = false)
        assertEquals(listOf(song(2)), result.remaining)
        assertEquals(song(2), result.next)
    }
}

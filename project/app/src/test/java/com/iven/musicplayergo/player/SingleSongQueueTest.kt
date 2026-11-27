package com.iven.musicplayergo.player

import com.iven.musicplayergo.models.Music
import org.junit.Assert.assertEquals
import org.junit.Test

class SingleSongQueueTest {
    private val current = song(1L)
    private val queued = song(2L)
    private val last = song(3L)

    @Test
    fun requeuingABookmarkReplacesTheExistingTrackDespiteItsDifferentPosition() {
        val bookmark = queued.copy(startFrom = 9000)
        val queue = mutableListOf(current, last, queued)

        addSingleSongToQueue(queue, bookmark, current, isQueueActive = true)

        assertEquals(listOf(current, bookmark, last), queue)
    }

    @Test
    fun requeuingFromAnotherPlaybackSourceStillKeepsOneEntryPerTrack() {
        val favorite = queued.copy(launchedBy = "1")
        val queue = mutableListOf(current, last, queued)

        addSingleSongToQueue(queue, favorite, current, isQueueActive = true)

        assertEquals(listOf(current, favorite, last), queue)
    }

    @Test
    fun removingAnEarlierTrackRecalculatesTheCurrentPositionBeforeInsertion() {
        val bookmark = queued.copy(startFrom = 9000, launchedBy = "1")
        val queue = mutableListOf(queued, current, last)

        addSingleSongToQueue(queue, bookmark, current, isQueueActive = true)

        assertEquals(listOf(current, bookmark, last), queue)
    }

    @Test
    fun requeuingAnEqualTrackMovesItImmediatelyAfterTheCurrentTrack() {
        val queue = mutableListOf(current, last, queued)

        addSingleSongToQueue(queue, queued, current, isQueueActive = true)

        assertEquals(listOf(current, queued, last), queue)
    }

    @Test
    fun aNewTrackIsInsertedAfterTheCurrentTrackInAnActiveQueue() {
        val queue = mutableListOf(current, last)

        addSingleSongToQueue(queue, queued, current, isQueueActive = true)

        assertEquals(listOf(current, queued, last), queue)
    }

    @Test
    fun aNewTrackIsAppendedToAnInactiveQueue() {
        val queue = mutableListOf(current, last)

        addSingleSongToQueue(queue, queued, current, isQueueActive = false)

        assertEquals(listOf(current, last, queued), queue)
    }

    @Test
    fun anInactiveQueueKeepsItsExistingEntryAndOrderForTheSameTrack() {
        val queue = mutableListOf(current, last, queued)
        val bookmark = queued.copy(startFrom = 9000, launchedBy = "1")

        addSingleSongToQueue(queue, bookmark, current, isQueueActive = false)

        assertEquals(listOf(current, last, queued), queue)
    }

    @Test
    fun trackIdentityRequiresBothTheSongAndAlbumIds() {
        val sameSongId = queued.copy(albumId = 200L)
        val sameAlbumId = queued.copy(id = 20L)
        val queue = mutableListOf(current, queued, last)

        addSingleSongToQueue(queue, sameSongId, current, isQueueActive = true)
        addSingleSongToQueue(queue, sameAlbumId, current, isQueueActive = true)

        assertEquals(listOf(current, sameAlbumId, sameSongId, queued, last), queue)
    }

    private fun song(id: Long) = Music(
        "Artist", 2026, id.toInt(), "Track $id", "track-$id.mp3", 180000L,
        "Album", 10L, "Music", id, "0", 0, 100
    )
}

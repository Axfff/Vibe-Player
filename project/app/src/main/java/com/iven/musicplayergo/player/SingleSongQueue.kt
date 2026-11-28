package com.iven.musicplayergo.player

import com.iven.musicplayergo.models.Music

/** Keep one entry per track when adding or moving a song within the queue. */
internal fun addSingleSongToQueue(
    queueSongs: MutableList<Music>,
    songToQueue: Music,
    currentSong: Music?,
    isQueueActive: Boolean
) {
    fun indexOfSong(song: Music?) = queueSongs.indexOfFirst {
        it.id == song?.id && it.albumId == song?.albumId
    }

    val currentIndex = indexOfSong(songToQueue)
    if (currentIndex == -1) {
        if (isQueueActive) {
            val currentPosition = indexOfSong(currentSong)
            queueSongs.add(currentPosition + 1, songToQueue)
        } else {
            queueSongs.add(songToQueue)
        }
    } else if (isQueueActive) {
        queueSongs.removeAll {
            it.id == songToQueue.id && it.albumId == songToQueue.albumId
        }
        val currentPosition = indexOfSong(currentSong)
        queueSongs.add(currentPosition + 1, songToQueue)
    }
}

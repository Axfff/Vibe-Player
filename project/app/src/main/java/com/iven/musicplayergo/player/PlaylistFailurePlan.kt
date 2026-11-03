package com.iven.musicplayergo.player

import com.iven.musicplayergo.models.Music

/** Only the in-memory playback list is changed; saved playlist membership is untouched. */
internal data class PlaylistFailurePlan(val remaining: List<Music>, val next: Music?)

internal fun planAfterPlaylistFailure(
    snapshot: List<Music>,
    failedSong: Music?,
    repeatList: Boolean
): PlaylistFailurePlan {
    val failedIndex = snapshot.indexOfFirst {
        it.id == failedSong?.id && it.albumId == failedSong?.albumId
    }
    if (failedIndex < 0) return PlaylistFailurePlan(snapshot.toList(), null)
    val remaining = snapshot.filterIndexed { index, _ -> index != failedIndex }
    val next = remaining.getOrNull(failedIndex)
        ?: if (repeatList) remaining.firstOrNull() else null
    return PlaylistFailurePlan(remaining, next)
}

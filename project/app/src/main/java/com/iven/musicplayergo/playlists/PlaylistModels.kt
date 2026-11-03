package com.iven.musicplayergo.playlists

import com.iven.musicplayergo.models.Music
import com.squareup.moshi.JsonClass

/** A separate, versioned document; existing favorites and queue preferences are untouched. */
@JsonClass(generateAdapter = true)
data class PlaylistCollection(
    val schemaVersion: Int = 1,
    val playlists: List<Playlist> = emptyList()
)

@JsonClass(generateAdapter = true)
data class Playlist(
    val id: String,
    val name: String,
    val tracks: List<PlaylistTrack> = emptyList()
)

/**
 * [mediaId] refers to the app's external MediaStore audio collection on this device.
 * Metadata is a display snapshot, so an unavailable file remains visible in its playlist.
 * It is never used to guess a replacement track with a different ID.
 */
@JsonClass(generateAdapter = true)
data class PlaylistTrack(
    val mediaId: Long,
    val title: String?,
    val displayName: String?,
    val artist: String?,
    val album: String?,
    val duration: Long
)

data class ResolvedPlaylistTrack(
    val saved: PlaylistTrack,
    val music: Music?
) {
    val isAvailable: Boolean get() = music != null
}

sealed class PlaylistResult<out T> {
    data class Success<T>(val value: T) : PlaylistResult<T>()
    data class Failure(val error: PlaylistError) : PlaylistResult<Nothing>()
}

enum class PlaylistError {
    EMPTY_NAME,
    DUPLICATE_NAME,
    PLAYLIST_NOT_FOUND,
    DUPLICATE_TRACK,
    INVALID_TRACK,
    TRACK_NOT_FOUND,
    STORAGE_READ_FAILED,
    STORAGE_WRITE_FAILED
}

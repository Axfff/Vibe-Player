package com.iven.musicplayergo.covers

import com.squareup.moshi.JsonClass
import java.util.UUID

sealed class CoverTarget {
    abstract val storageKey: String

    data class Album(
        val albumId: Long,
        val artistSnapshot: String?,
        val albumTitleSnapshot: String?
    ) : CoverTarget() {
        override val storageKey: String get() = "album:$albumId"
    }

    data class Playlist(val playlistId: String) : CoverTarget() {
        override val storageKey: String get() = "playlist:$playlistId"
    }

    fun isValid(): Boolean = when (this) {
        is Album -> albumId > 0
        is Playlist -> try {
            UUID.fromString(playlistId).toString().equals(playlistId, ignoreCase = true)
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}

enum class CoverTargetType { ALBUM, PLAYLIST }
enum class CoverSource { USER_SELECTED, RECOMMENDED }

@JsonClass(generateAdapter = true)
data class CustomCover(
    val schemaVersion: Int = 1,
    val targetType: CoverTargetType,
    val targetId: String,
    val localFileName: String,
    val source: CoverSource,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val artistSnapshot: String? = null,
    val albumTitleSnapshot: String? = null
)

@JsonClass(generateAdapter = true)
data class CustomCoverCollection(
    val schemaVersion: Int = 1,
    val covers: List<CustomCover> = emptyList()
)

sealed class CoverResult<out T> {
    data class Success<T>(val value: T) : CoverResult<T>()
    data class Failure(val error: CoverError) : CoverResult<Nothing>()
}

enum class CoverError {
    INVALID_TARGET,
    UNSUPPORTED_IMAGE_TYPE,
    IMAGE_TOO_LARGE,
    IMAGE_DECODE_FAILED,
    SOURCE_NOT_READABLE,
    STORAGE_READ_FAILED,
    STORAGE_WRITE_FAILED,
    COVER_NOT_FOUND
}


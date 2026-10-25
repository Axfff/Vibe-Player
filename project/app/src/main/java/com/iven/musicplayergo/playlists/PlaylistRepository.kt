package com.iven.musicplayergo.playlists

import com.iven.musicplayergo.models.Music
import com.squareup.moshi.Moshi
import java.util.UUID

/**
 * Serializes each read/validate/write operation and persists every accepted mutation.
 * Call storage-facing methods on an IO dispatcher; [resolve] performs no storage or Android IO.
 * There is no mutable cache that can diverge from the saved JSON after a failed write.
 */
class PlaylistRepository(
    private val storage: PlaylistStorage,
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) {
    private val adapter = Moshi.Builder().build().adapter(PlaylistCollection::class.java)

    @Synchronized
    fun getPlaylists(): PlaylistResult<List<Playlist>> = withCollection {
        PlaylistResult.Success(it.playlists)
    }

    @Synchronized
    fun getPlaylist(id: String): PlaylistResult<Playlist> = withCollection { collection ->
        collection.playlists.find { it.id == id }?.let { PlaylistResult.Success(it) }
            ?: failure(PlaylistError.PLAYLIST_NOT_FOUND)
    }

    @Synchronized
    fun create(name: String): PlaylistResult<Playlist> = withCollection { collection ->
        val normalizedName = name.trim()
        nameError(normalizedName, collection.playlists)?.let { return@withCollection failure(it) }

        val id = try {
            idFactory()
        } catch (_: Exception) {
            return@withCollection failure(PlaylistError.STORAGE_WRITE_FAILED)
        }
        if (!isUuid(id) || collection.playlists.any { it.id == id }) {
            return@withCollection failure(PlaylistError.STORAGE_WRITE_FAILED)
        }
        val playlist = Playlist(id = id, name = normalizedName)
        persist(collection.copy(playlists = collection.playlists + playlist), playlist)
    }

    @Synchronized
    fun rename(id: String, name: String): PlaylistResult<Playlist> = withCollection { collection ->
        val playlist = collection.playlists.find { it.id == id }
            ?: return@withCollection failure(PlaylistError.PLAYLIST_NOT_FOUND)
        val normalizedName = name.trim()
        nameError(normalizedName, collection.playlists, excludingId = id)?.let {
            return@withCollection failure(it)
        }
        if (playlist.name == normalizedName) return@withCollection PlaylistResult.Success(playlist)

        val updated = playlist.copy(name = normalizedName)
        persist(collection.replace(updated), updated)
    }

    @Synchronized
    fun delete(id: String): PlaylistResult<Unit> = withCollection { collection ->
        if (collection.playlists.none { it.id == id }) {
            return@withCollection failure(PlaylistError.PLAYLIST_NOT_FOUND)
        }
        persist(collection.copy(playlists = collection.playlists.filterNot { it.id == id }), Unit)
    }

    @Synchronized
    fun addTrack(id: String, music: Music): PlaylistResult<Playlist> = withCollection { collection ->
        val playlist = collection.playlists.find { it.id == id }
            ?: return@withCollection failure(PlaylistError.PLAYLIST_NOT_FOUND)
        val mediaId = music.id
        if (mediaId == null || mediaId <= 0 || music.duration < 0) {
            return@withCollection failure(PlaylistError.INVALID_TRACK)
        }
        if (playlist.tracks.any { it.mediaId == mediaId }) {
            return@withCollection failure(PlaylistError.DUPLICATE_TRACK)
        }

        val track = PlaylistTrack(
            mediaId = mediaId,
            title = music.title,
            displayName = music.displayName,
            artist = music.artist,
            album = music.album,
            duration = music.duration
        )
        val updated = playlist.copy(tracks = playlist.tracks + track)
        persist(collection.replace(updated), updated)
    }

    @Synchronized
    fun removeTrack(id: String, mediaId: Long): PlaylistResult<Playlist> = withCollection { collection ->
        val playlist = collection.playlists.find { it.id == id }
            ?: return@withCollection failure(PlaylistError.PLAYLIST_NOT_FOUND)
        if (playlist.tracks.none { it.mediaId == mediaId }) {
            return@withCollection failure(PlaylistError.TRACK_NOT_FOUND)
        }
        val updated = playlist.copy(tracks = playlist.tracks.filterNot { it.mediaId == mediaId })
        persist(collection.replace(updated), updated)
    }

    /**
     * Pass the complete successful scan, before UI hiding or metadata deduplication.
     * An exact ID plus filename/duration guard prevents obvious recycled-ID associations.
     * Renamed/re-encoded files may require removal and re-adding; cross-ID guessing is avoided.
     * Resolution never deletes unavailable memberships or rewrites their saved order.
     */
    fun resolve(playlist: Playlist, library: List<Music>): List<ResolvedPlaylistTrack> {
        val candidatesById = library.filter { it.id != null }.groupBy { it.id }
        return playlist.tracks.map { saved ->
            val music = candidatesById[saved.mediaId]?.firstOrNull {
                it.displayName == saved.displayName && it.duration == saved.duration
            }
            ResolvedPlaylistTrack(saved = saved, music = music)
        }
    }

    private fun PlaylistCollection.replace(updated: Playlist) = copy(
        playlists = playlists.map { if (it.id == updated.id) updated else it }
    )

    private fun nameError(
        name: String,
        playlists: List<Playlist>,
        excludingId: String? = null
    ): PlaylistError? = when {
        name.isEmpty() -> PlaylistError.EMPTY_NAME
        playlists.any { it.id != excludingId && it.name.equals(name, ignoreCase = true) } ->
            PlaylistError.DUPLICATE_NAME
        else -> null
    }

    private fun <T> withCollection(action: (PlaylistCollection) -> PlaylistResult<T>): PlaylistResult<T> {
        val collection = try {
            val json = storage.read()
            val decoded = if (json == null) PlaylistCollection() else adapter.fromJson(json)
            if (decoded == null || !isValid(decoded)) {
                return failure(PlaylistError.STORAGE_READ_FAILED)
            }
            decoded
        } catch (_: Exception) {
            return failure(PlaylistError.STORAGE_READ_FAILED)
        }
        return action(collection)
    }

    private fun <T> persist(collection: PlaylistCollection, value: T): PlaylistResult<T> = try {
        if (storage.write(adapter.toJson(collection))) PlaylistResult.Success(value)
        else failure(PlaylistError.STORAGE_WRITE_FAILED)
    } catch (_: Exception) {
        failure(PlaylistError.STORAGE_WRITE_FAILED)
    }

    private fun isValid(collection: PlaylistCollection): Boolean {
        if (collection.schemaVersion != 1) return false
        val playlists = collection.playlists
        if (playlists.map { it.id }.distinct().size != playlists.size) return false
        return playlists.all { playlist ->
            isUuid(playlist.id) && playlist.name == playlist.name.trim() &&
                nameError(playlist.name, playlists, playlist.id) == null &&
                playlist.tracks.all { it.mediaId > 0 && it.duration >= 0 } &&
                playlist.tracks.map { it.mediaId }.distinct().size == playlist.tracks.size
        }
    }

    private fun isUuid(value: String): Boolean = try {
        UUID.fromString(value).toString().equals(value, ignoreCase = true)
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun failure(error: PlaylistError) = PlaylistResult.Failure(error)
}

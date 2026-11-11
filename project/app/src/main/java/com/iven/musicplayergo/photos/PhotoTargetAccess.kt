package com.iven.musicplayergo.photos

import android.content.Context
import android.os.Bundle
import android.provider.MediaStore
import com.iven.musicplayergo.covers.CoverTarget
import com.iven.musicplayergo.playlists.PlaylistRepositories
import com.iven.musicplayergo.playlists.PlaylistResult

fun CoverTarget.toPhotoBundle() = Bundle().apply {
    when (val target = this@toPhotoBundle) {
        is CoverTarget.Playlist -> putString("playlist", target.playlistId)
        is CoverTarget.Album -> { putLong("album", target.albumId); putString("artist", target.artistSnapshot); putString("title", target.albumTitleSnapshot) }
    }
}
fun Bundle.photoTarget(): CoverTarget = getString("playlist")?.let { CoverTarget.Playlist(it) }
    ?: CoverTarget.Album(getLong("album"), getString("artist"), getString("title"))

/** Rechecks the actual target, including album snapshots, before every write. */
fun targetExists(context: Context, target: CoverTarget): Boolean = try {
    when (target) {
        is CoverTarget.Playlist -> PlaylistRepositories.get(context).getPlaylist(target.playlistId) is PlaylistResult.Success
        is CoverTarget.Album -> context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM),
            "${MediaStore.Audio.Media.ALBUM_ID} = ?", arrayOf(target.albumId.toString()), null)?.use { c ->
                var found = false
                while (c.moveToNext()) if (c.getString(0) == target.artistSnapshot && c.getString(1) == target.albumTitleSnapshot) found = true
                found
            } ?: false
    }
} catch (_: Exception) { false }

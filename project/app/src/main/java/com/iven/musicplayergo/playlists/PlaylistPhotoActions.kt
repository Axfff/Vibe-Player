package com.iven.musicplayergo.playlists

import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.iven.musicplayergo.R
import com.iven.musicplayergo.covers.CoverError
import com.iven.musicplayergo.covers.CoverRepositories
import com.iven.musicplayergo.covers.CoverResult
import com.iven.musicplayergo.covers.CoverTarget
import com.iven.musicplayergo.photos.PhotoRepositories
import com.iven.musicplayergo.photos.PhotoSourcesDialogFragment
import com.iven.musicplayergo.photos.cleanupDeletedPlaylistPhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The page and the empty-library dialog must expose the same scoped authorization actions. */
internal fun Fragment.bindPlaylistPhotoSources(button: View, selected: () -> Playlist?) {
    button.setOnClickListener {
        val playlist = selected() ?: return@setOnClickListener
        if (!childFragmentManager.isStateSaved && childFragmentManager.findFragmentByTag("playlist-photo-sources") == null) {
            PhotoSourcesDialogFragment.newInstance(CoverTarget.Playlist(playlist.id), playlist.name)
                .show(childFragmentManager, "playlist-photo-sources")
        }
    }
}

/** Playlist deletion has already committed. Try both cleanups and keep other targets untouched. */
internal fun Fragment.cleanupPlaylistArtwork(id: String, feedback: (String) -> Unit) {
    val context = requireContext().applicationContext
    lifecycleScope.launch {
        val target = CoverTarget.Playlist(id)
        val failed = withContext(Dispatchers.IO) {
            cleanupDeletedPlaylistPhotos(
                { PhotoRepositories.get(context).deleteTarget(target) },
                {
                    val result = CoverRepositories.get(context).removeCover(target)
                    check(result is CoverResult.Success || result is CoverResult.Failure && result.error == CoverError.COVER_NOT_FOUND)
                })
        }
        if (isAdded && failed.isNotEmpty()) feedback(context.getString(R.string.playlist_artwork_cleanup_failed))
    }
}

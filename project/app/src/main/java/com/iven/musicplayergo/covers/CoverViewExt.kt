package com.iven.musicplayergo.covers

import android.widget.ImageView
import coil.Coil
import coil.request.ImageRequest
import com.iven.musicplayergo.R
import com.iven.musicplayergo.extensions.toAlbumArtURI

fun ImageView.loadCover(target: CoverTarget) {
    val source = RepositoryCoverResolver(CoverRepositories.get(context)).resolve(target)
    val data = when (source) {
        is CoverDisplaySource.CustomFile -> source.file
        is CoverDisplaySource.MediaStoreAlbum -> source.albumId.toAlbumArtURI()
        CoverDisplaySource.DefaultAlbum, CoverDisplaySource.DefaultPlaylist -> null
    }
    val fallback = if (target is CoverTarget.Playlist) R.drawable.ic_custom_playlists else R.drawable.ic_music_note_cover_alt
    Coil.imageLoader(context).enqueue(
        ImageRequest.Builder(context)
            .data(data)
            .target(this)
            .placeholder(fallback)
            .error(fallback)
            .fallback(fallback)
            .build()
    )
}


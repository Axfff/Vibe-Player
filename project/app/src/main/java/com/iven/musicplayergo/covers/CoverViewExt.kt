package com.iven.musicplayergo.covers

import android.widget.ImageView
import coil.Coil
import coil.request.ImageRequest
import coil.size.Scale
import com.iven.musicplayergo.R
import com.iven.musicplayergo.GoPreferences
import com.iven.musicplayergo.extensions.toAlbumArtURI

fun ImageView.loadCover(target: CoverTarget) {
    val source = RepositoryCoverResolver(CoverRepositories.get(context)).resolve(target)
    val data = when (source) {
        is CoverDisplaySource.CustomFile -> source.file
        is CoverDisplaySource.MediaStoreAlbum -> if (GoPreferences.getPrefsInstance().isCovers) source.albumId.toAlbumArtURI() else null
        CoverDisplaySource.DefaultAlbum, CoverDisplaySource.DefaultPlaylist -> null
    }
    val fallback = if (target is CoverTarget.Playlist) R.drawable.ic_custom_playlists else R.drawable.ic_music_note_cover_alt
    Coil.imageLoader(context).enqueue(
        ImageRequest.Builder(context)
            .data(data)
            .target(this)
            // Details can grow after a small decode. Photos must fill the new bounds;
            // placeholder/error icons keep their natural size, including recycled views.
            .scale(Scale.FILL)
            .listener(
                onStart = { scaleType = ImageView.ScaleType.CENTER_INSIDE },
                onError = { _, _ -> scaleType = ImageView.ScaleType.CENTER_INSIDE },
                onSuccess = { _, _ -> scaleType = ImageView.ScaleType.CENTER_CROP }
            )
            .placeholder(fallback)
            .error(fallback)
            .fallback(fallback)
            .build()
    )
}


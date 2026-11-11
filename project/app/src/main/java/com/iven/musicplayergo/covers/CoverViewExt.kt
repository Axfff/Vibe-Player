package com.iven.musicplayergo.covers

import android.widget.ImageView
import coil.Coil
import coil.request.ImageRequest
import com.iven.musicplayergo.R

fun ImageView.loadCover(target: CoverTarget) {
    val fallback = if (target is CoverTarget.Playlist) R.drawable.ic_custom_playlists else R.drawable.ic_music_note_cover_alt
    Coil.imageLoader(context).enqueue(
        ImageRequest.Builder(context)
            .data(target)
            .target(this)
            .placeholder(fallback)
            .error(fallback)
            .fallback(fallback)
            .build()
    )
}


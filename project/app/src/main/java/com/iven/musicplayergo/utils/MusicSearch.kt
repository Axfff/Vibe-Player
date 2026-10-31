package com.iven.musicplayergo.utils

import com.iven.musicplayergo.models.Music

/** Search the selected display field without changing the library's ordering. */
internal fun filterMusicByQuery(
    query: String?,
    musicList: List<Music>?,
    showDisplayName: Boolean
): List<Music> {
    val searchText = query.orEmpty()
    if (searchText.isEmpty()) return musicList.orEmpty().toList()

    return musicList.orEmpty().filter { song ->
        val text = if (showDisplayName) song.displayName else song.title
        text?.contains(searchText, ignoreCase = true) == true
    }
}

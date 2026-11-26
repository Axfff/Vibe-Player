package com.iven.musicplayergo.library

import com.iven.musicplayergo.models.Music

/** Locate a saved recording while allowing MediaStore IDs and playback state to change. */
internal fun findSavedMusic(song: Music?, library: List<Music>?): Music? {
    if (song == null) return null
    return library?.find { candidate ->
        song.artist == candidate.artist && song.title == candidate.title &&
            song.displayName == candidate.displayName &&
            song.track == candidate.track && song.album == candidate.album &&
            song.year == candidate.year && song.duration == candidate.duration
    }
}

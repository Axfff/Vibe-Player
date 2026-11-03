package com.iven.musicplayergo.playlists

/**
 * Minimal persistence boundary for the pure Kotlin repository.
 * A null read means no saved document. A failed write must preserve the previous document.
 */
interface PlaylistStorage {
    fun read(): String?
    fun write(json: String): Boolean
}

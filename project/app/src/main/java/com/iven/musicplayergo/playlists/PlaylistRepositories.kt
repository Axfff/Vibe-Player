package com.iven.musicplayergo.playlists

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/** Android entry point. This singleton also keeps all domain mutations on one repository lock. */
object PlaylistRepositories {
    @Volatile
    private var instance: PlaylistRepository? = null

    fun get(context: Context): PlaylistRepository = instance ?: synchronized(this) {
        instance ?: PlaylistRepository(
            SharedPreferencesPlaylistStorage(
                PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            )
        ).also { instance = it }
    }
}

/** Only [KEY] is read or changed; existing queue, favorites and other settings are separate. */
internal class SharedPreferencesPlaylistStorage(
    private val preferences: SharedPreferences
) : PlaylistStorage {
    override fun read(): String? = preferences.getString(KEY, null)

    override fun write(json: String): Boolean {
        val previous = read()
        val written = try {
            preferences.edit().putString(KEY, json).commit()
        } catch (_: Exception) {
            false
        }
        if (!written) {
            // commit() can update the in-memory preferences even when the disk write fails.
            // Restore the prior value before reporting failure; retain other preference keys.
            try {
                preferences.edit().apply {
                    if (previous == null) remove(KEY) else putString(KEY, previous)
                }.commit()
            } catch (_: Exception) {
                // The caller still receives failure; a failed disk must never report success.
            }
        }
        return written
    }

    companion object {
        const val KEY = "custom_playlists_v1"
    }
}

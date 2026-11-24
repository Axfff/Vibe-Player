package com.iven.musicplayergo.regression

import android.app.Application
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.res.Resources
import android.database.MatrixCursor
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.iven.musicplayergo.GoConstants
import com.iven.musicplayergo.GoPreferences
import com.iven.musicplayergo.MusicViewModel
import com.iven.musicplayergo.models.Music
import com.iven.musicplayergo.utils.Lists
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/** Exercises the production scanner against controlled provider rows, without touching device media. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29) // ContentResolver.wrap(ContentProvider).
class MediaDateAddedRegressionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val preferences get() = GoPreferences.getPrefsInstance()
    private val stored get() = PreferenceManager.getDefaultSharedPreferences(context)
    private val keys = listOf("queue_songs_pref", "favorite_songs_pref", "is_queue_pref", "to_restore_song_pref")
    private lateinit var previous: Map<String, String?>
    private var previousFilters: Set<String>? = null
    private var hadFilters = false
    private lateinit var application: Application
    private lateinit var model: MusicViewModel
    private val songs = listOf(song(11L, "First", 100), song(12L, "Second", 200))

    @Before fun setUp() = onMain {
        check(context.packageName.endsWith(".debug")) { "Use the isolated debug app" }
        previous = keys.filter { stored.contains(it) }.associateWith { stored.getString(it, null) }
        hadFilters = stored.contains("strings_filter_pref")
        previousFilters = stored.getStringSet("strings_filter_pref", null)?.toSet()
        check(stored.edit().apply { keys.forEach { remove(it) } }
            .putStringSet("strings_filter_pref", emptySet()).commit())
        val provider = AudioProvider(songs)
        provider.attachInfo(context, ProviderInfo().apply { authority = "media" })
        val resolver = ContentResolver.wrap(provider)
        val delegate = context.applicationContext as Application
        application = object : Application() {
            override fun getContentResolver(): ContentResolver = resolver
            override fun checkPermission(permission: String, pid: Int, uid: Int) = PackageManager.PERMISSION_GRANTED
            override fun getResources(): Resources = delegate.resources
            override fun getApplicationContext(): Context = delegate
        }
        model = MusicViewModel(application)
    }

    @After fun tearDown() = onMain {
        if (::model.isInitialized) model.cancel()
        if (::previous.isInitialized) {
            check(stored.edit().apply {
                keys.forEach { key -> if (key in previous) putString(key, previous[key]) else remove(key) }
                if (hadFilters) putStringSet("strings_filter_pref", previousFilters) else remove("strings_filter_pref")
            }.commit())
        }
    }

    @Test fun scannerUsesAdditionDatesForBothSongsAndFolderSortingDirections() {
        val scanned = requireNotNull(model.queryForMusic(application))
        assertEquals("Modification timestamps must not become addition dates", songs, scanned)
        for (sort in listOf(GoConstants.DATE_ADDED_SORTING, GoConstants.DATE_ADDED_SORTING_INV)) {
            val expected = if (sort == GoConstants.DATE_ADDED_SORTING) listOf(12L, 11L) else listOf(11L, 12L)
            assertEquals(expected, Lists.getSortedMusicListForAllMusic(sort, scanned)?.map { it.id })
            assertEquals(expected, Lists.getSortedMusicListForFolder(sort, scanned)?.map { it.id })
        }
        assertEquals("Sorting must preserve source order", songs, scanned)
    }

    @Test fun scanMigratesSavedModificationDatesWithoutLosingPlaybackState() {
        val first = songs[0].copy(id = 1L, albumId = 3L, dateAdded = 300,
            startFrom = 120, launchedBy = GoConstants.ALBUM_VIEW)
        val second = songs[1].copy(id = 2L, albumId = 3L, dateAdded = 50,
            startFrom = 230, launchedBy = GoConstants.FOLDER_VIEW)
        val missing = first.copy(title = "Missing", displayName = "missing.mp3")
        onMain {
            preferences.queue = listOf(second, missing, first)
            preferences.favorites = listOf(first, second, missing)
            preferences.isQueue = first
            preferences.latestPlayedSong = second
            model.getDeviceMusic()
        }
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (!onMain { model.playlistLibraryState.value?.hasLoaded == true } && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(20)
        }
        onMain {
            assertTrue("The production scan must finish successfully", model.playlistLibraryState.value?.hasLoaded == true)
            fun refreshed(saved: Music) = songs.single { it.title == saved.title }
                .copy(startFrom = saved.startFrom, launchedBy = saved.launchedBy)
            assertEquals(listOf(refreshed(second), refreshed(first)), preferences.queue)
            assertEquals(listOf(refreshed(first), refreshed(second)), preferences.favorites)
            assertEquals(refreshed(first), preferences.isQueue)
            assertEquals(refreshed(second), preferences.latestPlayedSong)
        }
    }

    private class AudioProvider(private val songs: List<Music>) : ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?): MatrixCursor {
            assertEquals(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, uri)
            val columns = requireNotNull(projection)
            return MatrixCursor(columns).apply {
                songs.forEach { song ->
                    val values = mapOf(
                        MediaStore.Audio.Media.ARTIST to song.artist, MediaStore.Audio.Media.YEAR to song.year,
                        MediaStore.Audio.Media.TRACK to song.track, MediaStore.Audio.Media.TITLE to song.title,
                        MediaStore.Audio.Media.DISPLAY_NAME to song.displayName, MediaStore.Audio.Media.DURATION to song.duration,
                        MediaStore.Audio.Media.ALBUM to song.album, MediaStore.Audio.Media.ALBUM_ID to song.albumId,
                        MediaStore.Audio.Media.BUCKET_DISPLAY_NAME to song.relativePath, MediaStore.Audio.Media._ID to song.id,
                        MediaStore.Audio.Media.DATE_ADDED to song.dateAdded,
                        MediaStore.Audio.Media.DATE_MODIFIED to if (song.id == 11L) 300 else 50
                    )
                    addRow(columns.map { column -> requireNotNull(values[column]) { "Unexpected column $column" } })
                }
            }
        }
        override fun getType(uri: Uri): String = "vnd.android.cursor.dir/audio"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Read-only fixture")
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("Read-only fixture")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = error("Read-only fixture")
    }

    private fun <T> onMain(block: () -> T): T {
        val result = AtomicReference<Result<T>>()
        instrumentation.runOnMainSync { result.set(runCatching(block)) }
        return result.get().getOrThrow()
    }

    private fun song(id: Long, title: String, dateAdded: Int) = Music("Artist", 2026, id.toInt(), title,
        "$title.mp3", 1_000L, "Album", 7L, "Music", id, GoConstants.ARTIST_VIEW, 0, dateAdded)
}

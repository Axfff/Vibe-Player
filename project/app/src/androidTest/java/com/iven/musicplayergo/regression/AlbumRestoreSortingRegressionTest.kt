package com.iven.musicplayergo.regression

import android.app.Application
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iven.musicplayergo.GoConstants
import com.iven.musicplayergo.GoPreferences
import com.iven.musicplayergo.MusicViewModel
import com.iven.musicplayergo.extensions.findRestoreSongs
import com.iven.musicplayergo.models.Album
import com.iven.musicplayergo.models.Music
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/** Exercises the real restore helper with synthetic metadata, without scanning or playback. */
@RunWith(AndroidJUnit4::class)
class AlbumRestoreSortingRegressionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val preferences get() = GoPreferences.getPrefsInstance()
    private lateinit var model: MusicViewModel
    private var previousVisualization: String? = null
    private var hadVisualization = false

    private val songs = listOf(
        song(1, 3, "Alpha", "c.mp3", 20),
        song(2, 1, "Charlie", "a.mp3", 30),
        song(3, 2, "Bravo", "b.mp3", 10)
    )

    @Before fun setUp() = onMain {
        check(context.packageName.endsWith(".debug")) { "Use the isolated debug app" }
        val stored = PreferenceManager.getDefaultSharedPreferences(context)
        hadVisualization = stored.contains("song_visual_pref")
        previousVisualization = stored.getString("song_visual_pref", null)
        preferences.songsVisualization = "0" // Title mode.
        model = MusicViewModel(context.applicationContext as Application)
        model.deviceMusicByAlbum = mapOf("Selected" to songs, "Other" to listOf(song(99)))
        model.deviceMusicByFolder = mapOf("Selected folder" to songs)
        model.deviceAlbumsByArtist = mutableMapOf(
            "Artist" to listOf(Album("Selected", "2026", songs.toMutableList(), 3000L))
        )
    }

    @After fun tearDown() = onMain {
        if (::model.isInitialized) model.cancel()
        val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
        if (hadVisualization) editor.putString("song_visual_pref", previousVisualization)
        else editor.remove("song_visual_pref")
        check(editor.commit())
    }

    @Test fun albumTrackSortingRestoresBothDirectionsWithoutMutatingTheSource() = onMain {
        assertOrder(listOf(2L, 3L, 1L), restored(GoConstants.TRACK_SORTING))
        assertOrder(listOf(1L, 3L, 2L), restored(GoConstants.TRACK_SORTING_INVERTED))
        assertOrder(listOf(1L, 2L, 3L), songs)
    }

    @Test fun albumAlphabeticalSortingUsesTheSelectedTitleOrFilenameInBothDirections() = onMain {
        assertOrder(listOf(1L, 3L, 2L), restored(GoConstants.ASCENDING_SORTING))
        assertOrder(listOf(2L, 3L, 1L), restored(GoConstants.DESCENDING_SORTING))
        preferences.songsVisualization = GoConstants.FN
        assertOrder(listOf(2L, 3L, 1L), restored(GoConstants.ASCENDING_SORTING))
        assertOrder(listOf(1L, 3L, 2L), restored(GoConstants.DESCENDING_SORTING))
    }

    @Test fun albumRestoreKeepsItsMembershipAndHandlesMissingSources() = onMain {
        assertSame(songs, restored(GoConstants.DEFAULT_SORTING))
        assertNull(songs.first().copy(album = "Missing").findRestoreSongs(GoConstants.TRACK_SORTING, model))
        model.deviceMusicByAlbum = emptyMap()
        assertNull(restored(GoConstants.TRACK_SORTING))
        model.deviceMusicByAlbum = null
        assertNull(restored(GoConstants.TRACK_SORTING))
    }

    @Test fun folderRestoreRetainsFilenameAndDateSortingInTitleMode() = onMain {
        val fromFolder = songs.first().copy(launchedBy = GoConstants.FOLDER_VIEW)
        assertOrder(listOf(2L, 3L, 1L), fromFolder.findRestoreSongs(GoConstants.ASCENDING_SORTING, model))
        assertOrder(listOf(1L, 3L, 2L), fromFolder.findRestoreSongs(GoConstants.DESCENDING_SORTING, model))
        assertOrder(listOf(2L, 1L, 3L), fromFolder.findRestoreSongs(GoConstants.DATE_ADDED_SORTING, model))
        assertOrder(listOf(3L, 1L, 2L), fromFolder.findRestoreSongs(GoConstants.DATE_ADDED_SORTING_INV, model))
    }

    @Test fun artistRestoreStillSortsTheSelectedAlbumsTracks() = onMain {
        val fromArtist = songs.first().copy(launchedBy = GoConstants.ARTIST_VIEW)
        assertOrder(listOf(2L, 3L, 1L), fromArtist.findRestoreSongs(GoConstants.TRACK_SORTING, model))
        assertOrder(listOf(1L, 3L, 2L), fromArtist.findRestoreSongs(GoConstants.TRACK_SORTING_INVERTED, model))
    }

    private fun restored(sorting: Int) = songs.first().findRestoreSongs(sorting, model)

    private fun assertOrder(expected: List<Long>, actual: List<Music>?) {
        assertEquals(expected, actual?.map { it.id })
    }

    private fun onMain(block: () -> Unit) {
        val result = AtomicReference<Result<Unit>>()
        instrumentation.runOnMainSync { result.set(runCatching(block)) }
        result.get().getOrThrow()
    }

    private fun song(
        id: Long,
        track: Int = 1,
        title: String = "Other",
        displayName: String = "other.mp3",
        dateAdded: Int = 0
    ) = Music(
        "Artist", 2026, track, title, displayName, 1000L, "Selected", 10L,
        "Selected folder", id, GoConstants.ALBUM_VIEW, 0, dateAdded
    )
}

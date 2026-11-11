package com.iven.musicplayergo.regression

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iven.musicplayergo.MusicViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises saved playback repair through the public scanner and real preferences. */
@RunWith(AndroidJUnit4::class)
class SavedPlaybackFallbackRegressionTest {
    @Test fun deletedLastSongWithEmptyQueueFallsBackToAvailableMusic() =
        deletedLastSongFallsBack(inactiveQueue = false)

    @Test fun deletedLastSongWithInactiveQueueFallsBackToAvailableMusic() =
        deletedLastSongFallsBack(inactiveQueue = true)

    private fun deletedLastSongFallsBack(inactiveQueue: Boolean) {
        RegressionFixture.open().use { f ->
            f.preferences.latestPlayedSong = f.tracks.getValue("A")
            f.preferences.queue = if (inactiveQueue) listOf(f.tracks.getValue("B")) else emptyList()
            f.preferences.isQueue = null
            f.deleteAudio("A")
            f.deleteAudio("C")

            scan(f) { model ->
                assertEquals(listOf(f.tracks.getValue("B")), model.deviceMusicFiltered)
                assertEquals("A deleted song must be replaced after a successful scan",
                    f.tracks.getValue("B"), f.preferences.latestPlayedSong)
            }
        }
    }

    @Test fun deletedLastSongInActiveQueueUsesFirstRemainingQueuedSong() {
        RegressionFixture.open().use { f ->
            val first = f.tracks.getValue("C").copy(startFrom = 4_000)
            val second = f.tracks.getValue("B")
            f.preferences.latestPlayedSong = f.tracks.getValue("A")
            f.preferences.queue = listOf(first, second)
            f.preferences.isQueue = second
            f.deleteAudio("A")

            scan(f) {
                assertEquals(first, f.preferences.latestPlayedSong)
                assertEquals(listOf(first, second), f.preferences.queue)
                assertEquals(second, f.preferences.isQueue)
            }
        }
    }

    @Test fun deletedLastSongIsClearedWhenTheSuccessfulScanIsEmpty() {
        RegressionFixture.open().use { f ->
            val saved = f.tracks.getValue("A")
            f.preferences.latestPlayedSong = saved
            f.preferences.queue = emptyList()
            f.preferences.isQueue = null
            f.tracks.keys.forEach(f::deleteAudio)

            try {
                scan(f) { model ->
                    assertTrue(model.deviceMusicFiltered!!.isEmpty())
                    assertNull(f.preferences.latestPlayedSong)
                }
            } finally {
                // The fixture's separate Activity still holds its pre-scan restore flag.
                // Restore its preference only after assertions so it can shut down safely.
                f.preferences.latestPlayedSong = saved
            }
        }
    }

    @Test fun existingLastSongKeepsItsResumePosition() {
        RegressionFixture.open().use { f ->
            val saved = f.tracks.getValue("A").copy(startFrom = 7_000)
            f.preferences.latestPlayedSong = saved
            f.preferences.queue = emptyList()
            f.preferences.isQueue = null

            scan(f) { assertEquals(saved, f.preferences.latestPlayedSong) }
        }
    }

    private fun scan(f: RegressionFixture, check: (MusicViewModel) -> Unit) {
        val model = MusicViewModel(f.context.applicationContext as Application)
        try {
            f.onMain { model.getDeviceMusic() }
            f.await("saved playback scan completes", 20_000) {
                f.onMain { model.playlistLibraryState.value?.let { it.hasLoaded && !it.isLoading } == true }
            }
            f.onMain {
                assertFalse(model.playlistLibraryState.value!!.refreshFailed)
                check(model)
            }
        } finally {
            f.onMain { model.cancel() }
        }
    }
}

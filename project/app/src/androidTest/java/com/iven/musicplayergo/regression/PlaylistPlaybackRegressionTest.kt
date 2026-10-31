package com.iven.musicplayergo.regression

import android.app.UiAutomation
import android.content.res.Configuration
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iven.musicplayergo.GoConstants
import com.iven.musicplayergo.extensions.toContentUri
import com.iven.musicplayergo.player.MediaPlayerHolder
import com.iven.musicplayergo.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Android integration coverage for RG01–RG03 in docs/testing/custom-playlists.md. */
@RunWith(AndroidJUnit4::class)
class PlaylistPlaybackRegressionTest {

    @Test
    fun rg01_playlistClearsLiveAndPersistedQueue_andNaturallyStopsAfterItsLastSong() {
        RegressionFixture.open().use { f ->
            f.onActivity {
                it.onSongSelected(f.tracks.getValue("A"), f.songs, GoConstants.ARTIST_VIEW)
            }
            f.expectPlaying("A")
            f.onActivity { it.onAddToQueue(f.tracks.getValue("B")) }
            f.onMain {
                assertEquals(listOf(f.tracks.getValue("B").id), f.holder.queueSongs.map { it.id })
                // Model a previously persisted queue as well as the currently live queue.
                f.preferences.queue = f.holder.queueSongs.toList()
                f.preferences.isQueue = f.holder.isQueue
                assertTrue(!f.preferences.queue.isNullOrEmpty())
            }

            val playlist = f.createPlaylist("RG01 Order", "C", "A")
            f.playPlaylist(playlist.id)
            f.expectPlaying("C")
            assertQueueCleared(f)
            f.record("RG01-playlist-replaced-old-queue")

            finishNaturally(f, "C")
            f.expectPlaying("A")
            finishNaturally(f, "A")
            f.await("playlist stopped on A without entering old B") {
                f.onMain {
                    f.holder.currentSong?.id == f.tracks.getValue("A").id &&
                        f.holder.state == GoConstants.PAUSED && !f.player.isPlaying &&
                        f.preferences.hasCompletedPlayback
                }
            }
            assertQueueCleared(f)
            f.scenario.recreate()
            f.await("recreated activity restores ended playlist") {
                f.onMain { f.holder.currentSong?.id == f.tracks.getValue("A").id }
            }
            assertQueueCleared(f)
            f.onMain { assertFalse(f.player.isPlaying) }
            f.record("RG01-ended-on-A-old-B-not-restored")
        }
    }

    @Test
    fun rg02_editingSavedMembershipPreservesRunningSnapshot_andNextPlayUsesTheEdit() {
        RegressionFixture.open().use { f ->
            val playlist = f.createPlaylist("RG02 Order", "C", "A", "B")
            f.playPlaylist(playlist.id)
            f.expectPlaying("C")

            val edited = RegressionFixture.success(
                f.repository.removeTrack(playlist.id, f.tracks.getValue("A").id!!)
            )
            assertEquals(
                listOf(f.tracks.getValue("C").id, f.tracks.getValue("B").id),
                edited.tracks.map { it.mediaId }
            )
            f.onActivity {
                // Library sorting/refresh callbacks must not mutate a running playlist.
                it.onUpdatePlayingAlbumSongs(listOf(f.tracks.getValue("C"), f.tracks.getValue("B")))
            }
            f.onMain {
                assertEquals(3, f.holder.getCurrentAlbumSize())
                f.holder.skip(isNext = true)
            }
            f.expectPlaying("A")
            f.onMain { f.holder.skip(isNext = true) }
            f.expectPlaying("B")
            f.record("RG02-original-snapshot-C-A-B-retained")

            f.playPlaylist(playlist.id)
            f.expectPlaying("C")
            f.onMain {
                assertEquals(2, f.holder.getCurrentAlbumSize())
                f.holder.skip(isNext = true)
            }
            f.expectPlaying("B")
            f.songs.forEach { music ->
                f.context.contentResolver.openFileDescriptor(music.id!!.toContentUri(), "r").use {
                    assertTrue("Editing membership retains ${music.displayName}'s audio", it != null)
                }
            }
            f.record("RG02-next-play-uses-edited-C-B")
        }
    }

    @Test
    fun rg03_playingPortraitLandscapeRecreationPreservesPlayerPositionAndNextSong() {
        withPortraitFixture { f ->
            startOnA(f)
            val originalPlayer = f.player
            seekTo(f, 5_000)
            var before = playbackCheckpoint(f)

            rotate(f, UiAutomation.ROTATION_FREEZE_90, Configuration.ORIENTATION_LANDSCAPE)
            assertPlayingPreserved(f, originalPlayer, before)
            f.record("RG03-playing-landscape")
            before = playbackCheckpoint(f)
            rotate(f, UiAutomation.ROTATION_FREEZE_0, Configuration.ORIENTATION_PORTRAIT)
            assertPlayingPreserved(f, originalPlayer, before)
            f.record("RG03-playing-portrait")

            f.onMain { f.holder.skip(isNext = true) }
            f.expectPlaying("B")
            f.record("RG03-playing-rotation-next-is-B")
        }
    }

    @Test
    fun rg03_pausedPortraitLandscapeRecreationPreservesPositionUntilExplicitResume() {
        withPortraitFixture { f ->
            startOnA(f)
            seekTo(f, 7_000)
            f.onMain { f.holder.pauseMediaPlayer() }
            val originalPlayer = f.player
            val before = f.onMain { f.player.currentPosition }

            rotate(f, UiAutomation.ROTATION_FREEZE_90, Configuration.ORIENTATION_LANDSCAPE)
            assertPausedPreserved(f, originalPlayer, before)
            f.record("RG03-paused-landscape")
            rotate(f, UiAutomation.ROTATION_FREEZE_0, Configuration.ORIENTATION_PORTRAIT)
            assertPausedPreserved(f, originalPlayer, before)
            f.record("RG03-paused-portrait")

            f.onMain { f.holder.resumeMediaPlayer() }
            f.expectPlaying("A")
            f.onMain { f.holder.skip(isNext = true) }
            f.expectPlaying("B")
            f.record("RG03-paused-rotation-resume-next-is-B")
        }
    }

    @Test
    fun rg03_backgroundAndReturnPreservesPlayingAndPausedSessions() {
        RegressionFixture.open().use { f ->
            startOnA(f)
            seekTo(f, 4_000)
            val originalPlayer = f.player
            val before = playbackCheckpoint(f)
            f.scenario.moveToState(Lifecycle.State.CREATED)
            // The Activity is stopped; verify the real service keeps advancing the decoder.
            f.await("background audio continues advancing") {
                f.onMain { f.player.isPlaying && f.player.currentPosition >= before.position + 500 }
            }
            f.scenario.moveToState(Lifecycle.State.RESUMED)
            assertPlayingPreserved(f, originalPlayer, before)
            f.record("RG03-return-from-playing-background")

            f.onMain { f.holder.pauseMediaPlayer() }
            val pausedPosition = f.onMain { f.player.currentPosition }
            f.scenario.moveToState(Lifecycle.State.CREATED)
            SystemClock.sleep(500)
            f.onMain {
                assertFalse(f.player.isPlaying)
                assertTrue(abs(f.player.currentPosition - pausedPosition) <= 300)
            }
            f.scenario.moveToState(Lifecycle.State.RESUMED)
            assertPausedPreserved(f, originalPlayer, pausedPosition)
            f.onMain { f.holder.resumeMediaPlayer() }
            f.expectPlaying("A")
            f.onMain { f.holder.skip(isNext = true) }
            f.expectPlaying("B")
            f.record("RG03-background-return-next-is-B")
        }
    }

    private fun assertQueueCleared(f: RegressionFixture) {
        f.onMain {
            assertTrue(f.holder.queueSongs.isEmpty())
            assertNull(f.holder.isQueue)
            assertFalse(f.holder.isQueueStarted)
            assertFalse(f.holder.canRestoreQueue)
            assertNull(f.holder.restoreQueueSong)
            assertNull(f.preferences.queue)
            assertNull(f.preferences.isQueue)
            assertEquals(MediaPlayerHolder.PLAYLIST_SOURCE, f.holder.launchedBy)
        }
    }

    private fun finishNaturally(f: RegressionFixture, label: String) {
        f.onMain {
            assertEquals(f.tracks.getValue(label).id, f.holder.currentSong?.id)
            f.holder.seekTo((f.tracks.getValue(label).duration - 400).toInt(), true, false)
        }
    }

    private fun startOnA(f: RegressionFixture) {
        val playlist = f.createPlaylist("RG03 Order", "C", "A", "B")
        f.playPlaylist(playlist.id)
        f.expectPlaying("C")
        f.onMain { f.holder.skip(isNext = true) }
        f.expectPlaying("A")
    }

    private fun seekTo(f: RegressionFixture, position: Int) {
        f.onMain { f.holder.seekTo(position, true, false) }
        f.await("seek to $position") {
            f.onMain { f.player.currentPosition >= position && f.player.currentPosition < position + 2_000 }
        }
    }

    private fun rotate(f: RegressionFixture, rotation: Int, orientation: Int) {
        lateinit var oldActivity: MainActivity
        f.onActivity { oldActivity = it }
        assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(rotation))
        f.await("real Activity recreation after display rotation", 15_000) {
            var recreated = false
            f.onActivity {
                recreated = it !== oldActivity && it.resources.configuration.orientation == orientation
            }
            recreated
        }
        f.await("service rebound and library available after rotation") {
            f.onMain {
                f.viewModel.playlistLibraryState.value?.hasLoaded == true &&
                    f.holder.currentSong?.id == f.tracks.getValue("A").id
            }
        }
    }

    private data class PlaybackCheckpoint(val position: Int, val elapsedRealtime: Long)

    private fun playbackCheckpoint(f: RegressionFixture): PlaybackCheckpoint = f.onMain {
        PlaybackCheckpoint(f.player.currentPosition, SystemClock.elapsedRealtime())
    }

    private fun assertPlayingPreserved(f: RegressionFixture, player: Any, before: PlaybackCheckpoint) {
        f.expectPlaying("A")
        f.onMain {
            assertSame("Rebinding retains the prepared Android MediaPlayer", player, f.player)
            val position = f.player.currentPosition
            val elapsed = SystemClock.elapsedRealtime() - before.elapsedRealtime
            assertTrue("Lifecycle changes must not rewind playback", position >= before.position - 300)
            assertTrue(
                "Lifecycle changes must not jump forward: before=${before.position}, " +
                    "after=$position, elapsed=$elapsed",
                position <= before.position + elapsed + 1_000
            )
            assertEquals(MediaPlayerHolder.PLAYLIST_SOURCE, f.holder.launchedBy)
            assertEquals(3, f.holder.getCurrentAlbumSize())
        }
    }

    private fun assertPausedPreserved(f: RegressionFixture, player: Any, position: Int) {
        f.onMain {
            assertSame(player, f.player)
            assertEquals(f.tracks.getValue("A").id, f.holder.currentSong?.id)
            assertFalse("A paused session stays silent through recreation", f.player.isPlaying)
            assertEquals(GoConstants.PAUSED, f.holder.state)
            assertTrue("Paused position survives recreation", abs(f.player.currentPosition - position) <= 300)
            assertEquals(MediaPlayerHolder.PLAYLIST_SOURCE, f.holder.launchedBy)
            assertEquals(3, f.holder.getCurrentAlbumSize())
        }
    }

    private fun withPortraitFixture(block: (RegressionFixture) -> Unit) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        assertTrue(automation.setRotation(UiAutomation.ROTATION_FREEZE_0))
        try {
            RegressionFixture.open().use(block)
        } finally {
            automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
        }
    }
}

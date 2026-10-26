package com.iven.musicplayergo.regression

import android.content.Intent
import android.media.MediaPlayer
import android.os.SystemClock
import android.support.v4.media.session.MediaControllerCompat
import android.util.Log
import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iven.musicplayergo.GoConstants
import com.iven.musicplayergo.player.MediaButtonReceiver
import com.iven.musicplayergo.playlists.Playlist
import com.iven.musicplayergo.playlists.PlaylistResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** RG05: real MediaStore files, MediaPlayer, service callbacks and main-looper recovery. */
@RunWith(AndroidJUnit4::class)
class PlaylistRecoveryRegressionTest {

    /** RED on the inherited service route: an explicit Pause used to call resumeOrPause(). */
    @Test
    fun mediaSessionPauseDoesNotResumePausedPlaylist() = RegressionFixture.open().use { f ->
        f.play("A", "B", "C")
        f.expectPlaying("A")
        f.onMain { f.holder.pauseMediaPlayer() }
        assertSilent(f, "initial pause")
        val service = f.service

        f.onMain {
            // MediaSession.Callback.onMediaButtonEvent delegates to this service method.
            assertTrue(service.handleMediaIntent(mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)))
        }

        assertSilent(f, "explicit MediaSession Pause while paused")
        assertCurrent(f, "A")
        f.record("RG05-media-session-pause-remains-paused")
    }

    @Test
    fun rapidExplicitMediaSessionCommandsNeverToggleOrSkip() = RegressionFixture.open().use { f ->
        f.play("A", "B", "C")
        f.expectPlaying("A")
        val now = SystemClock.uptimeMillis()
        val service = f.service
        f.onMain {
            service.handleMediaIntent(mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY, now))
            service.handleMediaIntent(mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY, now + 30))
        }
        assertCurrent(f, "A")
        f.expectPlaying("A")

        f.onMain {
            service.handleMediaIntent(mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE, now + 60))
            service.handleMediaIntent(mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE, now + 90))
        }
        assertSilent(f, "two rapid explicit Pause commands")
        assertCurrent(f, "A")
        f.record("RG05-rapid-explicit-media-session-commands")
    }

    @Test
    fun exportedReceiverExplicitCommandsAreIdempotent() = RegressionFixture.open().use { f ->
        f.play("A", "B", "C")
        f.expectPlaying("A")
        f.onMain {
            val receiver = MediaButtonReceiver()
            repeat(2) { receiver.onReceive(f.context, mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)) }
        }
        assertSilent(f, "receiver repeated Pause")
        assertCurrent(f, "A")
        f.onMain {
            val receiver = MediaButtonReceiver()
            repeat(2) { receiver.onReceive(f.context, mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)) }
        }
        f.expectPlaying("A")
        assertCurrent(f, "A")
        f.record("RG05-exported-receiver-explicit-commands")
    }

    @Test
    fun explicitCommandClearsAnEarlierHeadsetClickWithoutChangingToggleBehavior() = RegressionFixture.open().use { f ->
        f.play("A", "B", "C")
        f.expectPlaying("A")
        val service = f.service
        val now = SystemClock.uptimeMillis()
        f.onMain {
            service.handleMediaIntent(mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, now))
        }
        assertSilent(f, "ordinary toggle pauses A")
        f.onMain {
            service.handleMediaIntent(mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE, now + 30))
            service.handleMediaIntent(mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, now + 60))
        }
        // A fresh toggle resumes A; it must not combine with the earlier click to skip B.
        f.expectPlaying("A")
        assertCurrent(f, "A")
        f.record("RG05-explicit-command-clears-headset-click-window")
    }

    @Test
    fun missingMiddleFileIsSkippedByManualNextWithoutRescanning() = RegressionFixture.open().use { f ->
        val saved = f.createPlaylist("Manual recovery", "A", "B", "C")
        f.playPlaylist(saved.id)
        f.expectPlaying("A")
        f.deleteAudio("B")

        f.onMain { f.holder.skip(isNext = true) }

        f.expectPlaying("C")
        assertRuntimeSnapshot(f, "A", "C")
        assertSavedMembership(f, saved)
        f.record("RG05-missing-middle-manual-next")
    }

    @Test
    fun missingMiddleFileIsSkippedAfterNaturalCompletionWithoutRescanning() = RegressionFixture.open().use { f ->
        val saved = f.createPlaylist("Natural recovery", "A", "B", "C")
        f.playPlaylist(saved.id)
        f.expectPlaying("A")
        f.deleteAudio("B")
        f.onMain {
            f.preferences.continueOnEnd = true
            // Let the real completion callback advance A -> missing B -> C.
            f.holder.seekTo((f.tracks.getValue("A").duration - 500).toInt(), true, false)
        }

        f.expectPlaying("C")
        assertRuntimeSnapshot(f, "A", "C")
        assertSavedMembership(f, saved)
        f.record("RG05-missing-middle-natural-completion")
    }

    @Test
    fun allMissingFilesStopAfterFiniteAttemptsEvenWhenListsRepeat() = RegressionFixture.open().use { f ->
        val saved = f.createPlaylist("Unavailable recovery", "A", "B", "C")
        f.playPlaylist(saved.id)
        f.expectPlaying("A")
        listOf("A", "B", "C").forEach(f::deleteAudio)
        val before = f.onMain { privateField<Long>(f.holder, "mediaAttempt") }
        f.onMain { f.preferences.onListEnded = GoConstants.CONTINUE }

        // Reuse the successful cached scan. All three source URIs now fail to open.
        f.playPlaylist(saved.id)
        f.await("all unavailable entries exhaust") {
            f.onMain { privateField<Boolean>(f.holder, "playlistExhausted") }
        }

        assertSilent(f, "all files unavailable with list continuation")
        val attempts = f.onMain { privateField<Long>(f.holder, "mediaAttempt") - before }
        assertTrue("Recovery must stay bounded by the three members; attempts=$attempts", attempts <= 4)
        f.onMain {
            assertNull(privateField<Runnable?>(f.holder, "playlistRecovery"))
            assertEquals(0, privateField<List<*>>(f.holder, "mPlayingSongs").size)
        }
        assertSavedMembership(f, saved)
        f.record("RG05-all-files-unavailable-bounded-stop")
    }

    @Test
    fun receiverPauseBeforePostedRecoveryPreventsUnexpectedAudio() = RegressionFixture.open().use { f ->
        val saved = f.createPlaylist("Pause recovery", "A", "B", "C")
        f.playPlaylist(saved.id)
        f.expectPlaying("A")
        f.deleteAudio("B")
        f.onMain {
            f.holder.skip(isNext = true)
            assertNotNull("Missing B must schedule real recovery", privateField<Runnable?>(f.holder, "playlistRecovery"))
            MediaButtonReceiver().onReceive(f.context, mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE))
        }

        awaitPreparedC(f)
        assertSilent(f, "receiver Pause before posted recovery")
        assertSavedMembership(f, saved)
        f.onMain { MediaButtonReceiver().onReceive(f.context, mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)) }
        f.expectPlaying("C")
        f.record("RG05-receiver-pause-before-posted-recovery")
    }

    @Test
    fun mediaSessionPauseBeforePostedRecoveryPreventsUnexpectedAudio() = RegressionFixture.open().use { f ->
        val saved = f.createPlaylist("Session pause recovery", "A", "B", "C")
        f.playPlaylist(saved.id)
        f.expectPlaying("A")
        f.deleteAudio("B")
        val service = f.service
        f.onMain {
            f.holder.skip(isNext = true)
            assertNotNull("Missing B must schedule real recovery", privateField<Runnable?>(f.holder, "playlistRecovery"))
            service.handleMediaIntent(mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE))
        }

        awaitPreparedC(f)
        assertSilent(f, "MediaSession Pause before posted recovery")
        assertSavedMembership(f, saved)
        f.record("RG05-media-session-pause-before-posted-recovery")
    }

    @Test
    fun stopBeforePostedRecoveryCancelsItWhileServiceIsStillBound() = RegressionFixture.open().use { f ->
        val saved = f.createPlaylist("Stop recovery", "A", "B", "C")
        f.playPlaylist(saved.id)
        f.expectPlaying("A")
        f.deleteAudio("B")
        val attempt = f.onMain {
            f.holder.skip(isNext = true)
            assertNotNull("Missing B must schedule real recovery", privateField<Runnable?>(f.holder, "playlistRecovery"))
            val failedAttempt = privateField<Long>(f.holder, "mediaAttempt")
            // Exercise the same Stop cleanup while retaining the Activity binding;
            // fromUser only controls the separate UI-close callback.
            f.holder.stopPlaybackService(stopPlayback = true, fromUser = false, fromFocus = false)
            assertNull(privateField<Runnable?>(f.holder, "playlistRecovery"))
            failedAttempt
        }

        assertSilent(f, "Stop while failed-file recovery is queued")
        f.onMain {
            assertEquals("No replacement player may start after Stop", attempt, privateField<Long>(f.holder, "mediaAttempt"))
            assertFalse(f.holder.isPlay)
        }
        assertSavedMembership(f, saved)
        f.record("RG05-stop-before-posted-recovery")
    }

    @Test
    fun mediaSessionStopDestroysActivityAndCannotRestartAudio(): Unit = RegressionFixture.open().use { f ->
        val saved = f.createPlaylist("Media key stop recovery", "A", "B", "C")
        f.playPlaylist(saved.id)
        f.expectPlaying("A")
        f.deleteAudio("B")
        val service = f.service
        val stoppedHolder = f.holder
        val session = checkNotNull(service.getMediaSession())
        val controller = MediaControllerCompat(f.context, session.sessionToken)

        f.onMain {
            stoppedHolder.skip(isNext = true)
            assertNotNull("Missing B must schedule real recovery", privateField<Runnable?>(stoppedHolder, "playlistRecovery"))
            // Deliver a real media key through MediaSession.Callback.onMediaButtonEvent.
            assertTrue(controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP)))
        }
        f.await("MediaSession Stop destroys the Activity and releases its service") {
            f.scenario.state == Lifecycle.State.DESTROYED && service.getMediaSession() == null
        }

        // Observe the captured holder: looking it up again would create a fresh singleton.
        repeat(8) {
            f.onMain {
                assertNull(privateField<Runnable?>(stoppedHolder, "playlistRecovery"))
                assertFalse("Stopped MediaSession must not restart audio", runCatching {
                    privateField<MediaPlayer>(stoppedHolder, "mediaPlayer").isPlaying
                }.getOrDefault(false))
            }
            SystemClock.sleep(75)
        }
        assertSavedMembership(f, saved)
        Log.i("PlaylistRegression", "RG05 MediaSession Stop destroyed Activity and released service; no late audio")
        Unit
    }

    private fun awaitPreparedC(f: RegressionFixture) {
        f.await("recovery prepares C") {
            f.onMain {
                f.holder.currentSong?.id == f.tracks.getValue("C").id &&
                    privateField<Runnable?>(f.holder, "playlistRecovery") == null &&
                    runCatching { f.player.duration > 0 }.getOrDefault(false)
            }
        }
    }

    private fun assertCurrent(f: RegressionFixture, label: String) = f.onMain {
        assertEquals("Current track must remain $label", f.tracks.getValue(label).id, f.holder.currentSong?.id)
    }

    private fun assertRuntimeSnapshot(f: RegressionFixture, vararg labels: String) = f.onMain {
        val snapshot = privateField<List<com.iven.musicplayergo.models.Music>>(f.holder, "mPlayingSongs")
        assertEquals(labels.map { f.tracks.getValue(it).id }, snapshot.map { it.id })
    }

    private fun assertSavedMembership(f: RegressionFixture, expected: Playlist) {
        val result = f.repository.getPlaylist(expected.id)
        assertTrue("Saved playlist must remain readable: $result", result is PlaylistResult.Success)
        assertEquals(expected, (result as PlaylistResult.Success<Playlist>).value)
    }

    private fun assertSilent(f: RegressionFixture, label: String) {
        // Sample beyond the main-handler turn so delayed recovery cannot make a false pass.
        repeat(8) {
            f.onMain {
                assertFalse("Unexpected real MediaPlayer audio: $label", runCatching { f.player.isPlaying }.getOrDefault(false))
            }
            SystemClock.sleep(75)
        }
    }

    private fun mediaKey(code: Int, eventTime: Long = SystemClock.uptimeMillis()) = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
        putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, code, 0))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> privateField(target: Any, name: String): T = target.javaClass.getDeclaredField(name).run {
        isAccessible = true
        get(target) as T
    }
}

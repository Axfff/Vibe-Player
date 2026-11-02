package com.iven.musicplayergo.regression

import android.os.Looper
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iven.musicplayergo.player.MediaPlayerInterface
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Progress reads must not race MediaPlayer replacement/prepare during playlist skips. */
@RunWith(AndroidJUnit4::class)
class PlaybackProgressThreadRegressionTest {
    @Test fun progressUsesPlayerThreadAcrossSkipsAndStopsWhenPaused() {
        RegressionFixture.open().use { f ->
            val callbacks = AtomicInteger()
            val wrongThread = AtomicBoolean(false)
            val original = f.onMain { f.holder.mediaPlayerInterface }
            f.onMain {
                f.holder.mediaPlayerInterface = object : MediaPlayerInterface by original {
                    override fun onPositionChanged(position: Int) {
                        if (Looper.myLooper() != Looper.getMainLooper()) wrongThread.set(true)
                        callbacks.incrementAndGet()
                        original.onPositionChanged(position)
                    }
                }
            }
            try {
                f.play("C", "A", "B")
                f.expectPlaying("C")
                f.await("real scheduled progress update") { callbacks.get() > 0 }
                assertFalse("Progress must use the same main thread as player preparation and UI", wrongThread.get())
                for (label in listOf("A", "B")) {
                    f.onMain { f.holder.skip(isNext = true) }
                    f.expectPlaying(label)
                }
                val before = callbacks.get()
                f.await("progress still updates after replacing the player") { callbacks.get() > before }
                assertFalse("No background MediaPlayer reads after skips", wrongThread.get())
                f.onMain { f.holder.pauseMediaPlayer() }
                val pausedCount = callbacks.get()
                SystemClock.sleep(1_200)
                assertEquals("Paused playback must not dispatch progress", pausedCount, callbacks.get())
                f.onMain { assertFalse(f.player.isPlaying) }
            } finally {
                f.onMain { f.holder.mediaPlayerInterface = original }
            }
        }
    }
}

package com.iven.musicplayergo.regression

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Issue #14: observe the real external-effect protocol alongside advancing Android playback.
 *
 * A receiver represents the external controller's session registry. These assertions cover
 * the broadcasts and their actual MediaPlayer session IDs, not audible equalizer processing.
 */
@RunWith(AndroidJUnit4::class)
class PlaylistAudioEffectRegressionTest {

    @Test
    fun manualNextBalancesAudioEffectSessionsThroughServiceShutdown() {
        AudioEffectEvents().use { effects ->
            val sessions = mutableListOf<Int>()
            RegressionFixture.open().use { f ->
                sessions += f.onMain { f.player.audioSessionId }
                f.play("A", "B", "C")
                f.expectPlaying("A")
                sessions += f.onMain { f.player.audioSessionId }

                f.onMain { f.holder.skip(isNext = true) }
                f.expectPlaying("B")
                sessions += f.onMain { f.player.audioSessionId }
                f.record("PR14-external-effect-manual-next")
            }

            effects.assertBalancedSessions(sessions)
        }
    }

    @Test
    fun naturalCompletionBalancesAudioEffectSessionsThroughServiceShutdown() {
        AudioEffectEvents().use { effects ->
            val sessions = mutableListOf<Int>()
            RegressionFixture.open().use { f ->
                sessions += f.onMain { f.player.audioSessionId }
                f.play("A", "B", "C")
                f.expectPlaying("A")
                sessions += f.onMain { f.player.audioSessionId }

                f.onMain {
                    // Seek close to the real WAV's end, then let MediaPlayer deliver completion.
                    f.holder.seekTo((f.tracks.getValue("A").duration - 500).toInt(), true, false)
                }
                f.expectPlaying("B")
                sessions += f.onMain { f.player.audioSessionId }
                f.record("PR14-external-effect-natural-completion")
            }

            effects.assertBalancedSessions(sessions)
        }
    }

    @Test
    fun missingFileRecoveryClosesFailedSessionAndRegistersSurvivingTrack() {
        AudioEffectEvents().use { effects ->
            val sessions = mutableListOf<Int>()
            RegressionFixture.open().use { f ->
                sessions += f.onMain { f.player.audioSessionId }
                f.play("A", "B", "C")
                f.expectPlaying("A")
                sessions += f.onMain { f.player.audioSessionId }
                f.deleteAudio("B")

                // Failed B posts recovery on the main looper; inspect its actual player before
                // that queued task replaces it. No scan or playback callback is substituted.
                sessions += f.onMain {
                    f.holder.skip(isNext = true)
                    f.player.audioSessionId
                }
                f.expectPlaying("C")
                sessions += f.onMain { f.player.audioSessionId }
                f.record("PR14-external-effect-missing-file-recovery")
            }

            effects.assertBalancedSessions(sessions)
        }
    }

    private data class EffectEvent(
        val action: String?,
        val sessionId: Int,
        val contentType: Int,
        val packageName: String?
    )

    /** Register before Activity/service creation, and retain application Context past teardown. */
    private class AudioEffectEvents : AutoCloseable {
        private val instrumentation = InstrumentationRegistry.getInstrumentation()
        private val context = instrumentation.targetContext.applicationContext
        private val events = mutableListOf<EffectEvent>()
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val owner = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
                if (owner != this@AudioEffectEvents.context.packageName) return
                synchronized(events) {
                    events += EffectEvent(
                        intent.action,
                        intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1),
                        intent.getIntExtra(AudioEffect.EXTRA_CONTENT_TYPE, -1),
                        owner
                    )
                }
            }
        }

        init {
            val filter = IntentFilter().apply {
                addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
        }

        fun assertBalancedSessions(sessions: List<Int>) {
            assertTrue("Every observed real player must have a valid audio session: $sessions", sessions.all { it > 0 })
            val finalSession = sessions.last()
            val deadline = SystemClock.elapsedRealtime() + 5_000
            // Service teardown sends the final CLOSE asynchronously; do not unregister early.
            while (snapshot().none {
                    it.action == AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION &&
                        it.sessionId == finalSession
                } && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(50)
            }
            instrumentation.waitForIdleSync()
            val observed = snapshot()
            Log.i(RegressionFixture.TAG, "PR14 actual-sessions=$sessions, effect-events=$observed")
            observed.forEach {
                assertEquals("Effect broadcast identifies its owning app", context.packageName, it.packageName)
                assertEquals("Effect broadcast identifies music content", AudioEffect.CONTENT_TYPE_MUSIC, it.contentType)
            }
            val expected = sessions.flatMap { id ->
                listOf(
                    AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION to id,
                    AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION to id
                )
            }
            assertEquals(
                "Every replacement must close the previous session, open its own, and close on shutdown",
                expected,
                observed.map { it.action to it.sessionId }
            )
        }

        private fun snapshot(): List<EffectEvent> = synchronized(events) { events.toList() }

        override fun close() {
            context.unregisterReceiver(receiver)
        }
    }
}

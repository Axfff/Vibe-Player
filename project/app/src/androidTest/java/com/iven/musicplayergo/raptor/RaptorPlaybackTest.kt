package com.iven.musicplayergo.raptor

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iven.musicplayergo.regression.RegressionFixture
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class RaptorPlaybackTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val track = File.createTempFile("raptor-playback-", ".wav", context.cacheDir)

    @After
    fun removeTrack() {
        track.delete()
    }

    @Test
    fun usesAudioPositionAndPausesAndRestartsWithoutDrift() = withGeneratedTrack { playback, uri ->
        playback.prepare(uri, 2.0)
        assertTrue(playback.play())
        await { playback.positionSeconds > 0.2 }
        playback.pause()
        delay(80)
        val pausedAt = playback.positionSeconds
        delay(160)
        assertEquals(pausedAt, playback.positionSeconds, 0.025)

        playback.rewind()
        assertEquals(0.0, playback.positionSeconds, 0.001)
        assertTrue(playback.play())
        await { playback.positionSeconds > 0.05 }
    }

    @Test
    fun longAudioLoopsInsideTheAnalyzedPrefix() = withGeneratedTrack { playback, uri ->
        playback.prepare(uri, 0.6)
        assertTrue(playback.play())
        await { playback.positionSeconds > 0.3 }
        await { playback.positionSeconds < 0.15 }
        await { playback.positionSeconds in 0.2..0.6 }
    }

    @Test
    fun closingARequestedLoadCancelsItAndAllowsAReplacement() = withGeneratedTrack { playback, uri ->
        val load = launch(start = CoroutineStart.UNDISPATCHED) { playback.prepare(uri, 2.0) }
        playback.close()
        load.join()
        assertTrue(load.isCancelled)
        assertFalse(playback.play())

        val missing = Uri.fromFile(File(track.path + ".missing"))
        assertTrue(runCatching { playback.prepare(missing, 2.0) }.exceptionOrNull() is IOException)
        playback.prepare(uri, 2.0)
        assertTrue(playback.play())
    }

    @Test
    fun losingForegroundAudioFocusPausesPlayback() = runBlocking {
        val uri = writeTrack()
        withContext(Dispatchers.Main) {
            var interrupted = false
            val playback = RaptorPlayback(context, { interrupted = true }, { error("Playback failed") })
            val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val takeover = AudioFocusRequestCompat.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener { }
                .build()
            try {
                playback.muted = true
                playback.prepare(uri, 2.0)
                assertTrue(playback.play())
                await { playback.positionSeconds > 0.05 }
                assertEquals(
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
                    AudioManagerCompat.requestAudioFocus(manager, takeover)
                )
                await { interrupted }
                delay(80)
                val stoppedAt = playback.positionSeconds
                delay(160)
                assertEquals(stoppedAt, playback.positionSeconds, 0.025)
            } finally {
                AudioManagerCompat.abandonAudioFocusRequest(manager, takeover)
                playback.close()
            }
        }
    }

    @Test
    fun restartingLibraryWithFocusDisabledYieldsGameAudio() {
        RegressionFixture.open().use { fixture ->
            fixture.preferences.isFocusEnabled = false
            runBlocking {
                val uri = writeTrack()
                var interruptions = 0
                val playback = withContext(Dispatchers.Main) {
                    RaptorPlayback(context, { interruptions++ }, { error("Playback failed") })
                }
                try {
                    withContext(Dispatchers.Main) {
                        playback.muted = true
                        playback.prepare(uri, 2.0)
                        assertTrue(playback.play())
                        await { playback.positionSeconds > 0.05 }
                        assertFalse(playback.yieldToLibrary())
                    }
                    fixture.play("A")
                    fixture.expectPlaying("A")
                    withContext(Dispatchers.Main) {
                        assertTrue(playback.yieldToLibrary())
                        assertEquals(1, interruptions)
                        assertFalse(playback.yieldToLibrary())
                        delay(80)
                        val pausedAt = playback.positionSeconds
                        delay(160)
                        assertEquals(pausedAt, playback.positionSeconds, 0.025)
                        assertTrue(fixture.player.isPlaying)
                    }
                } finally {
                    withContext(Dispatchers.Main) { playback.close() }
                }
            }
        }
    }

    private fun withGeneratedTrack(
        check: suspend kotlinx.coroutines.CoroutineScope.(RaptorPlayback, Uri) -> Unit
    ) = runBlocking {
        val uri = writeTrack()
        withContext(Dispatchers.Main) {
            val playback = RaptorPlayback(context, {}, { error("Playback failed") })
            try {
                playback.muted = true
                check(playback, uri)
            } finally {
                playback.close()
            }
        }
    }

    private suspend fun await(condition: () -> Boolean) {
        withTimeout(5_000) {
            while (!condition()) delay(12)
        }
    }

    private fun writeTrack(): Uri {
        val sampleRate = 24_000
        val samples = sampleRate * 2
        val pcmBytes = samples * 2
        val wav = ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36 + pcmBytes)
        wav.put("WAVEfmt ".toByteArray()).putInt(16)
        wav.putShort(1).putShort(1).putInt(sampleRate)
        wav.putInt(sampleRate * 2).putShort(2).putShort(16)
        wav.put("data".toByteArray()).putInt(pcmBytes)
        repeat(samples) { wav.putShort((4000 * sin(2 * PI * 220 * it / sampleRate)).toInt().toShort()) }
        track.writeBytes(wav.array())
        return Uri.fromFile(track)
    }
}

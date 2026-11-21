package com.iven.musicplayergo.analysis

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class RaptorTrackAnalyzerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(context.cacheDir, "raptor-audio-${System.nanoTime()}").apply { mkdirs() }

    @After fun cleanup() {
        directory.deleteRecursively()
    }

    @Test fun bundledModelAndDecoderAnalyzeBoundedLocalExcerpt() = runBlocking {
        val audio = waveFile("rhythm.wav", seconds = 32) { frame ->
            val phase = ((frame - 6000) % (RATE / 2)).toDouble() / RATE
            if (frame >= 6000 && phase < 0.07) {
                0.7 * sin(2 * PI * 100 * phase) * exp(-55 * phase)
            } else 0.0
        }
        val result = RaptorTrackAnalyzer(context).analyze(audio)
        assertEquals(30.0, result.durationSeconds, 0.001)
        assertTrue(result.reachedDurationLimit)
        assertEquals(120, result.bpm)
        assertEquals(60, result.beats.size)
        result.beats.forEachIndexed { i, time ->
            assertEquals(0.25 + i * 0.5, time, 0.025)
        }
        assertEquals(listOf(0.0, 8.0, 16.0, 24.0), result.scenes.map { it.startSeconds })
        val webConfidences = listOf(0.868092489798392, 0.868092489798392, 0.868092489798392, 0.8774028734291136)
        result.scenes.zip(webConfidences).forEach { (scene, webConfidence) ->
            assertEquals("groove", scene.theme)
            assertEquals(webConfidence, scene.confidence, 1e-6)
        }
    }

    @Test fun silenceHasNoBeatsAndUsesNeutralScene() = runBlocking {
        val result = RaptorTrackAnalyzer(context).analyze(waveFile("silence.wav", seconds = 4) { 0.0 })
        assertEquals(4.0, result.durationSeconds, 0.001)
        assertFalse(result.reachedDurationLimit)
        assertNull(result.bpm)
        assertTrue(result.beats.isEmpty())
        assertEquals(listOf(RaptorSceneWindow(0.0, "neutral", 0.0)), result.scenes)
    }

    private fun waveFile(name: String, seconds: Int, sample: (Int) -> Double): Uri {
        val frames = RATE * seconds
        val dataBytes = frames * Short.SIZE_BYTES
        val wave = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()).putInt(36 + dataBytes)
            put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
            put("data".toByteArray()).putInt(dataBytes)
            repeat(frames) { putShort((sample(it) * Short.MAX_VALUE).roundToInt().toShort()) }
        }
        return Uri.fromFile(File(directory, name).apply { writeBytes(wave.array()) })
    }

    private companion object {
        const val RATE = 24_000
    }
}

package com.iven.musicplayergo.analysis

import java.util.concurrent.CancellationException
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioResamplingTest {
    @Test fun alreadyAtTargetRateCopiesOnlyDecodedSamples() {
        val source = floatArrayOf(0.1f, -0.2f, 0.3f, 0.9f)
        val result = resampleTo16k(source, 3, 16000) {}
        assertArrayEquals(floatArrayOf(0.1f, -0.2f, 0.3f), result, 0f)
        source[0] = 1f
        assertEquals(0.1f, result[0], 0f)
    }

    @Test fun downsamplingPreservesConstantAndRejectsAboveNyquistEnergy() {
        val constant = resampleTo16k(FloatArray(4800) { 0.25f }, 4800, 48000) {}
        assertEquals(1600, constant.size)
        assertTrue(constant.all { kotlin.math.abs(it - 0.25f) < 1e-5f })

        val highFrequency = FloatArray(4800) { i -> sin(2 * PI * 12000 * i / 48000).toFloat() }
        val output = resampleTo16k(highFrequency, highFrequency.size, 48000) {}
        val interior = output.sliceArray(30 until output.size - 30)
        val rms = sqrt(interior.map { it.toDouble() * it }.average())
        assertTrue("12 kHz input should be suppressed below 16 kHz output Nyquist: $rms", rms < 0.05)
    }

    @Test fun ringingDoesNotProduceOutOfRangePcm() {
        val step = FloatArray(4410) { if (it < 2205) -1f else 1f }
        val output = resampleTo16k(step, step.size, 44100) {}
        assertTrue(output.isNotEmpty())
        assertTrue(output.all { it.isFinite() && it in -1f..1f })
    }

    @Test fun rejectsInvalidLengthAndHonoursCancellation() {
        assertIllegalArgument { resampleTo16k(FloatArray(4), 0, 44100) {} }
        assertIllegalArgument { resampleTo16k(FloatArray(4), 5, 44100) {} }
        assertIllegalArgument { resampleTo16k(FloatArray(4), 4, 0) {} }
        var checked = false
        try {
            resampleTo16k(FloatArray(4800), 4800, 48000) {
                checked = true
                throw CancellationException("cancelled")
            }
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            assertTrue(checked)
        }
    }

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}

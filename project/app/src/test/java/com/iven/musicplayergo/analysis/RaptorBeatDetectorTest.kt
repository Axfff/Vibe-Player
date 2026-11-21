package com.iven.musicplayergo.analysis

import java.util.concurrent.CancellationException
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RaptorBeatDetectorTest {
    @Test
    fun followsOffsetBeatGridsAcrossRatesAndCueStrengths() {
        var state = 1234567L
        fun random(): Double {
            state = (state * 1664525 + 1013904223) and 0xffffffffL
            return state / 4294967296.0
        }

        val sampleRates = intArrayOf(12_000, 24_000, 44_100, 48_000)
        repeat(100) { trial ->
            val bpm = 65 + (random() * 120).toInt()
            val expected = grid(bpm, random() * 1.2, 24)
            val rate = sampleRates[(random() * sampleRates.size).toInt()]
            val accents = expected.map { Accent(it, 0.1 + 0.3 * random(), 90.0) }
            val signal = fixture(expected.last() + 0.2, accents, sampleRate = rate)
            val analysis = detectRaptorBeats(arrayOf(signal), rate)
            assertTrue("Trial $trial expected $bpm BPM, got ${analysis.bpm}", abs((analysis.bpm ?: 0) - bpm) <= 1)
            assertAligned(expected, analysis.beats)
        }
    }

    @Test
    fun preservesBeatPositionsWhenVolumeRateAndStereoPolarityChange() {
        val expected = grid(111, 0.0, 26)
        val accents = expected.map(::Accent)
        val duration = expected.last() + 0.3
        val baseline = detectRaptorBeats(arrayOf(fixture(duration, accents)), RATE)
        val signal = fixture(duration, accents, sampleRate = 44_100, gain = 0.08)
        val inverted = FloatArray(signal.size) { -signal[it] }
        val transformed = detectRaptorBeats(arrayOf(signal, inverted), 44_100)
        assertEquals(111, baseline.bpm)
        assertEquals(baseline.bpm, transformed.bpm)
        assertAligned(baseline.beats, transformed.beats, tolerance = 0.018)
    }

    @Test
    fun matchesWebTimestampsWithSyncopationAndMissingKicks() {
        val expected = grid(108, 0.56, 28)
        val accents = expected.flatMapIndexed { index, time ->
            val syncopation = Accent(time + 0.19, 0.21, 2400.0)
            if (index == 13 || index == 14) listOf(syncopation) else listOf(Accent(time), syncopation)
        }
        val actual = detectRaptorBeats(arrayOf(fixture(expected.last() + 0.3, accents)), RATE)
        assertEquals(108, actual.bpm)
        assertAligned(expected, actual.beats)

        // Reference timestamps from detectBeats in demos/beat-runner/public/beat.js.
        val webBeats = listOf(
            0.560, 1.110, 1.670, 2.220, 2.780, 3.340, 3.890, 4.450, 5.000, 5.560,
            6.110, 6.670, 7.220, 7.776666667, 8.333333333, 8.890, 9.450, 10.000,
            10.560, 11.110, 11.670, 12.220, 12.780, 13.340, 13.890, 14.450, 15.000, 15.560
        )
        assertAligned(webBeats, actual.beats, tolerance = 0.000001)
    }

    @Test
    fun reportsUnknownForSilenceShortTracksSingleOnsetsAndSustainedBass() {
        val noBeats = RaptorBeatAnalysis(emptyList(), null)
        val sustainedBass = FloatArray(8 * RATE) { index ->
            val time = index.toDouble() / RATE
            (0.3 * minOf(1.0, time / 0.1) * sin(2 * PI * 26 * time)).toFloat()
        }
        val samples = listOf(
            fixture(0.9, listOf(Accent(0.0))),
            fixture(4.0, emptyList()),
            fixture(4.0, listOf(Accent(0.72))),
            sustainedBass
        )
        for (signal in samples) assertEquals(noBeats, detectRaptorBeats(arrayOf(signal), RATE))
    }

    @Test
    fun rejectsMismatchedInputAndChecksCancellationDuringAnalysis() {
        assertThrows(IllegalArgumentException::class.java) { detectRaptorBeats(emptyArray(), RATE) }
        assertThrows(IllegalArgumentException::class.java) { detectRaptorBeats(arrayOf(FloatArray(1)), 0) }
        assertThrows(IllegalArgumentException::class.java) {
            detectRaptorBeats(arrayOf(FloatArray(1), FloatArray(2)), RATE)
        }

        val signal = fixture(5.0, grid(120, 0.0, 10).map(::Accent))
        var checks = 0
        assertThrows(CancellationException::class.java) {
            detectRaptorBeats(arrayOf(signal), RATE) {
                if (++checks == 3) throw CancellationException("analysis dismissed")
            }
        }
        assertEquals(3, checks)
    }

    private data class Accent(val time: Double, val strength: Double = 1.0, val hz: Double = 100.0)

    private fun grid(bpm: Int, start: Double, count: Int): List<Double> =
        List(count) { start + it * 60.0 / bpm }

    private fun fixture(
        duration: Double,
        accents: List<Accent>,
        sampleRate: Int = RATE,
        gain: Double = 1.0
    ): FloatArray {
        val signal = FloatArray((duration * sampleRate).roundToInt())
        for ((time, strength, hz) in accents) {
            val first = (time * sampleRate).roundToInt()
            val size = minOf((0.07 * sampleRate).roundToInt(), signal.size - first)
            for (index in 0 until size) {
                val offset = index.toDouble() / sampleRate
                signal[first + index] =
                    (signal[first + index] + gain * strength * sin(2 * PI * hz * offset) * exp(-55 * offset)).toFloat()
            }
        }
        return signal
    }

    private fun assertAligned(expected: List<Double>, actual: List<Double>, tolerance: Double = 0.025) {
        assertEquals(expected.size, actual.size)
        for ((index, beat) in expected.withIndex()) {
            assertEquals("Beat ${index + 1}", beat, actual[index], tolerance)
        }
    }

    private companion object {
        const val RATE = 24_000
    }
}

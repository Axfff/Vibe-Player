package com.iven.musicplayergo.analysis

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

class RaptorStyleModelTest {
    private val json = File("../../demos/beat-runner/public/model.json").readText()
    private val model = RaptorStyleModel.fromJson(json)

    @Test
    fun matchesBrowserForStereoSectionsAndPartialFinalWindow() {
        val sampleRate = 24_000
        val audio = channelPair(sampleRate)

        assertPrediction(
            model.predict(audio, sampleRate),
            "drift",
            drift = 0.9682681007383181,
            groove = 0.031702041415749706,
            surge = 0.00002985784593212247,
        )
        assertPrediction(
            model.predict(audio, sampleRate, startSeconds = 8.0),
            "groove",
            drift = 0.0010689442236854968,
            groove = 0.998929330746175,
            surge = 0.0000017250301395019729,
        )
        assertPrediction(
            model.predict(audio, sampleRate, startSeconds = 16.0),
            "groove",
            drift = 0.0032792250590023024,
            groove = 0.9967196230901206,
            surge = 0.0000011518508769951625,
        )
    }

    @Test
    fun matchesBrowserAtFractionalResamplingRate() {
        val sampleRate = 44_100
        assertPrediction(
            model.predict(channelPair(sampleRate), sampleRate),
            "drift",
            drift = 0.9649620745606713,
            groove = 0.03500397323244985,
            surge = 0.00003395220687870725,
        )
    }

    @Test
    fun matchesBrowserForHighEnergyMono() {
        val sampleRate = 22_050
        val samples = FloatArray(sampleRate * 8) { i ->
            val time = i.toDouble() / sampleRate
            val hash = (i + 1) * 0x45d9f3b
            val mixed = ((hash ushr 16) xor hash) * 0x45d9f3b
            val noise = (mixed.toUInt().toDouble() / 4_294_967_295.0) * 2 - 1
            val envelope = 0.5 + 0.5 * exp(-8 * (time * 2.4 - floor(time * 2.4)))
            (envelope * (0.25 * noise + 0.14 * sin(2 * PI * 92 * time) + 0.07 * sin(2 * PI * 440 * time))).toFloat()
        }
        assertPrediction(
            model.predict(arrayOf(samples), sampleRate),
            "surge",
            drift = 0.023141463520319094,
            groove = 0.1626487808771241,
            surge = 0.8142097556025567,
        )
    }

    @Test
    fun returnsZeroConfidenceForShortSilentAndInvalidInput() {
        val sampleRate = 8000
        val note = FloatArray(sampleRate) { (0.18 * sin(2 * PI * 200 * it / sampleRate)).toFloat() }
        val silent = FloatArray(sampleRate)
        val antiPhase = FloatArray(note.size) { -note[it] }
        val onlyOneActiveFrame = FloatArray(sampleRate).apply { this[0] = 0.8f }
        val nonFinite = note.copyOf().apply { this[200] = Float.NaN }

        val predictions = listOf(
            model.predict(emptyArray(), sampleRate),
            model.predict(arrayOf(note.copyOf(200)), sampleRate),
            model.predict(arrayOf(silent), sampleRate),
            model.predict(arrayOf(note, antiPhase), sampleRate),
            model.predict(arrayOf(onlyOneActiveFrame), sampleRate),
            model.predict(arrayOf(nonFinite), sampleRate),
            model.predict(arrayOf(note, note.copyOf(20)), sampleRate),
            model.predict(arrayOf(note), sampleRate, startSeconds = 1.0),
            model.predict(arrayOf(note), sampleRate, startSeconds = Double.NaN),
            model.predict(arrayOf(note), 0),
        )
        predictions.forEach {
            assertEquals("groove", it.style)
            assertEquals(0.0, it.confidence, 0.0)
            assertEquals(1.0, it.probabilities.values.sum(), 1e-12)
        }
        assertEquals(model.predict(arrayOf(note), sampleRate), model.predict(arrayOf(note), sampleRate, -1.0))
        assertEquals(model.predict(arrayOf(note), sampleRate), model.predict(arrayOf(note, note), sampleRate))
    }

    @Test
    fun rejectsWrongVersionClassOrderDimensionsAndScale() {
        val adapter = Moshi.Builder().build().adapter(Any::class.java)
        val artifact = adapter.fromJson(json) as Map<*, *>
        val invalid = listOf(
            null,
            artifact + ("format" to "v2"),
            artifact + ("windowSeconds" to 9),
            artifact + ("styleNames" to listOf("surge", "groove", "drift")),
            artifact + ("weights" to listOf(emptyList<Any>())),
            artifact + ("weights" to null),
            artifact + ("weights" to List(3) { listOf(0) }),
            artifact + ("scale" to List(17) { 0 }),
            artifact + ("bias" to "broken"),
            artifact + ("mean" to List(17) { "1" }),
        )
        invalid.forEach { fixture ->
            assertThrows(IllegalArgumentException::class.java) { RaptorStyleModel.fromJson(adapter.serializeNulls().toJson(fixture)) }
        }
    }

    private fun channelPair(sampleRate: Int): Array<FloatArray> {
        val left = FloatArray(17 * sampleRate) { i ->
            val time = i.toDouble() / sampleRate
            val harmonics = 0.09 * sin(2 * PI * 110 * time) + 0.02 * sin(2 * PI * 330 * time)
            val lead = 0.18 * sin(2 * PI * 180 * time) + 0.06 * sin(2 * PI * 960 * time)
            val pulse = exp(-10 * (time * 2.375 - floor(time * 2.375)))
            (if (time < 8) harmonics * (0.75 + 0.25 * sin(2 * PI * 1.4 * time)) else lead * pulse).toFloat()
        }
        val right = FloatArray(17 * sampleRate) { i ->
            val time = i.toDouble() / sampleRate
            val pulse = exp(-10 * (time * 2.375 - floor(time * 2.375)))
            val frequency = if (time < 8) 164 else 1428
            (0.06 * sin(2 * PI * frequency * time) * (if (time < 8) 0.9 else pulse)).toFloat()
        }
        return arrayOf(left, right)
    }

    private fun assertPrediction(
        actual: RaptorStylePrediction,
        style: String,
        drift: Double,
        groove: Double,
        surge: Double,
    ) {
        // Probabilities from predictStyle in the browser for the same synthetic Float32 PCM.
        assertEquals(style, actual.style)
        assertEquals(drift, actual.probabilities.getValue("drift"), 1e-8)
        assertEquals(groove, actual.probabilities.getValue("groove"), 1e-8)
        assertEquals(surge, actual.probabilities.getValue("surge"), 1e-8)
        assertEquals(1.0, actual.probabilities.values.sum(), 1e-12)
        assertEquals(actual.probabilities.getValue(style), actual.confidence, 0.0)
    }
}

package com.iven.musicplayergo.analysis

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Log-mel input expected by the official Discogs-EffNet ONNX model.
 * Input is 16 kHz mono PCM in [-1, 1]. Only complete patches are emitted.
 */
internal object EffnetFeatures {
    const val SAMPLE_RATE = 16_000
    const val FRAMES = 128
    const val BANDS = 96
    const val PATCH_HOP = 62
    private const val FFT_SIZE = 512
    private const val FRAME_HOP = 256
    private const val PATCH_SAMPLES = FRAMES * FRAME_HOP

    private val window = DoubleArray(FFT_SIZE) { i ->
        0.5 - 0.5 * cos(2.0 * PI * i / (FFT_SIZE - 1))
    }
    private val filters: Array<DoubleArray> = run {
        val logStep = ln(6.4) / 27.0
        val highMel = 15.0 + ln(8.0) / logStep
        val frequencies = DoubleArray(BANDS + 2) { i ->
            val mel = highMel * i / (BANDS + 1)
            if (mel < 15.0) mel / 0.015 else 1000.0 * exp(logStep * (mel - 15.0))
        }
        Array(BANDS) { band ->
            val lo = frequencies[band]
            val mid = frequencies[band + 1]
            val hi = frequencies[band + 2]
            DoubleArray(FFT_SIZE / 2 + 1) { bin ->
                val hz = bin * SAMPLE_RATE.toDouble() / FFT_SIZE
                max(0.0, min((hz - lo) / (mid - lo), (hi - hz) / (hi - mid))) * 2.0 / (hi - lo)
            }
        }
    }

    fun patchCount(sampleCount: Int): Int =
        if (sampleCount < PATCH_SAMPLES) 0
        else 1 + (sampleCount - PATCH_SAMPLES) / (PATCH_HOP * FRAME_HOP)

    /** Compute one patch starting at an absolute mel-frame index. */
    fun patch(samples: FloatArray, startFrame: Int, checkCancelled: () -> Unit = {}): FloatArray {
        require(startFrame >= 0 && (startFrame.toLong() + FRAMES) * FRAME_HOP <= samples.size) {
            "Not enough samples for a complete patch"
        }
        val output = FloatArray(FRAMES * BANDS)
        val real = DoubleArray(FFT_SIZE)
        val imaginary = DoubleArray(FFT_SIZE)
        val power = DoubleArray(FFT_SIZE / 2 + 1)
        for (frame in 0 until FRAMES) {
            checkCancelled()
            val start = (startFrame + frame) * FRAME_HOP - FFT_SIZE / 2
            for (n in 0 until FFT_SIZE) {
                val index = start + n
                val sample = if (index in samples.indices) samples[index].toDouble() else 0.0
                require(sample.isFinite()) { "Non-finite PCM sample" }
                real[n] = sample * window[n]
                imaginary[n] = 0.0
            }
            fft(real, imaginary)
            for (bin in power.indices) power[bin] = real[bin] * real[bin] + imaginary[bin] * imaginary[bin]
            for (band in 0 until BANDS) {
                var energy = 0.0
                for (bin in power.indices) energy += power[bin] * filters[band][bin]
                output[frame * BANDS + band] = log10(1.0 + 10_000.0 * energy).toFloat()
            }
        }
        return output
    }

    private fun fft(real: DoubleArray, imaginary: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var temp = real[i]; real[i] = real[j]; real[j] = temp
                temp = imaginary[i]; imaginary[i] = imaginary[j]; imaginary[j] = temp
            }
        }
        var length = 2
        while (length <= n) {
            val angle = -2.0 * PI / length
            val wr0 = cos(angle); val wi0 = sin(angle)
            for (i in 0 until n step length) {
                var wr = 1.0; var wi = 0.0
                for (offset in 0 until length / 2) {
                    val a = i + offset; val b = a + length / 2
                    val tr = wr * real[b] - wi * imaginary[b]
                    val ti = wr * imaginary[b] + wi * real[b]
                    real[b] = real[a] - tr; imaginary[b] = imaginary[a] - ti
                    real[a] += tr; imaginary[a] += ti
                    val next = wr * wr0 - wi * wi0
                    wi = wr * wi0 + wi * wr0; wr = next
                }
            }
            length = length shl 1
        }
    }
}

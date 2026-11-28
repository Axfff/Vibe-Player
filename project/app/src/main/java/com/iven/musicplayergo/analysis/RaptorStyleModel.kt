package com.iven.musicplayergo.analysis

import com.squareup.moshi.Moshi
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal data class RaptorStylePrediction(
    val style: String,
    val confidence: Double,
    val probabilities: Map<String, Double>,
)

/** Uses the feature order and v1 weights shared with the Raptor Radio web model. */
internal class RaptorStyleModel private constructor(
    private val mean: DoubleArray,
    private val scale: DoubleArray,
    private val weights: Array<DoubleArray>,
    private val bias: DoubleArray,
) {
    fun predict(
        channels: Array<FloatArray>,
        sampleRate: Int,
        startSeconds: Double = 0.0,
    ): RaptorStylePrediction {
        val features = styleFeatures(channels, sampleRate, startSeconds)
            ?: return RaptorStylePrediction("groove", 0.0, styles.associateWith { 1.0 / styles.size })
        for (i in features.indices) {
            features[i] = ((features[i] - mean[i]) / scale[i]).coerceIn(-5.0, 5.0)
        }
        val logits = DoubleArray(styles.size) { row ->
            var logit = bias[row]
            for (i in features.indices) logit += features[i] * weights[row][i]
            logit
        }
        val winner = logits.indices.maxBy { logits[it] }
        val peak = logits[winner]
        val likelihoods = DoubleArray(styles.size) { exp(logits[it] - peak) }
        val total = likelihoods.sum()
        return RaptorStylePrediction(
            styles[winner],
            likelihoods[winner] / total,
            styles.indices.associate { styles[it] to likelihoods[it] / total },
        )
    }

    companion object {
        const val WINDOW_SECONDS = 8
        const val MIN_SCENE_CONFIDENCE = 0.45
        private const val FEATURE_COUNT = 17
        private val styles = listOf("drift", "groove", "surge")
        private val adapter = Moshi.Builder().build().adapter(Any::class.java)

        fun fromJson(json: String): RaptorStyleModel {
            val model = adapter.fromJson(json) as? Map<*, *> ?: invalidModel()
            if (model["format"] != "beat-runner-style-v1" ||
                model["windowSeconds"] != WINDOW_SECONDS.toDouble() ||
                model["styleNames"] != styles
            ) invalidModel()
            val mean = readVector(model["mean"], FEATURE_COUNT)
            val scale = readVector(model["scale"], FEATURE_COUNT)
            if (scale.any { it <= 0 }) invalidModel()
            val bias = readVector(model["bias"], styles.size)
            val rows = model["weights"] as? List<*> ?: invalidModel()
            if (rows.size != styles.size) invalidModel()
            val weights = Array(rows.size) { readVector(rows[it], FEATURE_COUNT) }
            return RaptorStyleModel(mean, scale, weights, bias)
        }

        private fun readVector(value: Any?, size: Int): DoubleArray {
            val items = value as? List<*> ?: invalidModel()
            if (items.size != size) invalidModel()
            return DoubleArray(size) { (items[it] as? Double)?.takeIf(Double::isFinite) ?: invalidModel() }
        }

        private fun invalidModel(): Nothing = throw IllegalArgumentException("Scene model format is invalid")
    }
}

private const val STYLE_SAMPLE_RATE = 8000
private const val FFT_SIZE = 512
private const val HOP = 256
private const val MIN_SECONDS = 0.65
private val bandLimits = intArrayOf(4, 11, 32, 80, 128, FFT_SIZE / 2)
private val hann = FloatArray(FFT_SIZE) { (0.5 - 0.5 * cos(2 * PI * it / (FFT_SIZE - 1))).toFloat() }
private val reverseBits = IntArray(FFT_SIZE) {
    Integer.reverse(it) ushr Integer.numberOfLeadingZeros(FFT_SIZE - 1)
}
private val twiddleReal = DoubleArray(FFT_SIZE / 2) { cos(-2 * PI * it / FFT_SIZE) }
private val twiddleImaginary = DoubleArray(FFT_SIZE / 2) { sin(-2 * PI * it / FFT_SIZE) }

private fun monoSamples(channels: Array<FloatArray>, sampleRate: Int, startSeconds: Double): FloatArray? {
    val length = channels.firstOrNull()?.size ?: return null
    if (!startSeconds.isFinite() || sampleRate <= 0 || channels.any { it.size != length }) return null
    val start = floor(startSeconds.coerceAtLeast(0.0) * sampleRate).toInt()
    if (start >= length) return null
    val stride = sampleRate.toDouble() / STYLE_SAMPLE_RATE
    val count = floor(minOf((length - start) / stride, (RaptorStyleModel.WINDOW_SECONDS * STYLE_SAMPLE_RATE).toDouble())).toInt()
    if (count < MIN_SECONDS * STYLE_SAMPLE_RATE) return null

    return FloatArray(count) { i ->
        val first = start + floor(i * stride).toInt()
        val next = start + floor((i + 1) * stride).toInt()
        val last = minOf(length, maxOf(next, first + 1))
        var sum = 0.0
        for (channel in channels) {
            for (j in first until last) sum += channel[j]
        }
        (sum / ((last - first) * channels.size)).toFloat()
    }
}

private fun fft(real: DoubleArray, imaginary: DoubleArray) {
    var length = 2
    while (length <= FFT_SIZE) {
        val half = length / 2
        val step = FFT_SIZE / length
        for (start in 0 until FFT_SIZE step length) {
            for (i in 0 until half) {
                val angle = i * step
                val left = start + i
                val right = left + half
                val offsetReal = real[right] * twiddleReal[angle] - imaginary[right] * twiddleImaginary[angle]
                val offsetImaginary = real[right] * twiddleImaginary[angle] + imaginary[right] * twiddleReal[angle]
                real[right] = real[left] - offsetReal
                imaginary[right] = imaginary[left] - offsetImaginary
                real[left] += offsetReal
                imaginary[left] += offsetImaginary
            }
        }
        length *= 2
    }
}

private fun quantile(ordered: List<Double>, fraction: Double): Double =
    ordered[(fraction * (ordered.size - 1)).roundToInt()]

private fun moments(values: List<Double>): Pair<Double, Double> {
    val mean = values.average()
    val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
    return mean to sqrt(variance)
}

private fun styleFeatures(channels: Array<FloatArray>, sampleRate: Int, startSeconds: Double): DoubleArray? {
    val samples = monoSamples(channels, sampleRate, startSeconds) ?: return null
    val power = samples.sumOf { it.toDouble() * it } / samples.size
    if (!power.isFinite() || power < 2.5e-7) return null

    val levels = mutableListOf<Double>()
    val centroids = mutableListOf<Double>()
    val fluxes = mutableListOf<Double>()
    val flatnesses = mutableListOf<Double>()
    val changes = mutableListOf<Double>()
    val bandTotals = DoubleArray(bandLimits.size)
    val real = DoubleArray(FFT_SIZE)
    val imaginary = DoubleArray(FFT_SIZE)
    val magnitude = DoubleArray(FFT_SIZE / 2)
    val lastMagnitude = DoubleArray(FFT_SIZE / 2)
    var quietFrames = 0

    for (start in 0..(samples.size - FFT_SIZE) step HOP) {
        imaginary.fill(0.0)
        var energy = 0.0
        for (i in 0 until FFT_SIZE) {
            val value = samples[start + i].toDouble()
            energy += value * value
            real[reverseBits[i]] = value * hann[i]
        }
        val level = maxOf(-80.0, 10 * log10(energy / FFT_SIZE + 1e-12))
        levels.lastOrNull()?.let { changes.add(abs(level - it)) }
        levels.add(level)
        if (level < -65) {
            quietFrames++
            continue
        }

        fft(real, imaginary)
        var magnitudeTotal = 0.0
        var spectralLog = 0.0
        var weighted = 0.0
        var spectralPower = 0.0
        for (bin in 1 until magnitude.size) {
            val amplitude = hypot(real[bin], imaginary[bin])
            magnitude[bin] = amplitude
            magnitudeTotal += amplitude
            spectralLog += ln(amplitude + 1e-8)
            weighted += bin * amplitude
            spectralPower += amplitude * amplitude
        }

        centroids.add(weighted / magnitudeTotal / magnitude.size)
        flatnesses.add(exp(spectralLog / (magnitude.size - 1)) / (magnitudeTotal / (magnitude.size - 1)))
        val canMeasureFlux = centroids.size > 1
        var band = 0
        var flux = 0.0
        for (bin in 1 until magnitude.size) {
            if (bin >= bandLimits[band]) band++
            bandTotals[band] += (magnitude[bin] * magnitude[bin]) / spectralPower
            val normalized = magnitude[bin] / magnitudeTotal
            if (canMeasureFlux) flux += maxOf(0.0, normalized - lastMagnitude[bin])
            lastMagnitude[bin] = normalized
        }
        if (canMeasureFlux) fluxes.add(flux)
    }

    if (centroids.size < 3) return null
    levels.sort()
    fluxes.sort()
    changes.sort()
    val (meanCentroid, spreadCentroid) = moments(centroids)
    val (meanFlatness, spreadFlatness) = moments(flatnesses)
    return doubleArrayOf(
        10 * log10(power),
        quantile(levels, 0.5),
        quantile(levels, 0.9) - quantile(levels, 0.1),
        quietFrames.toDouble() / levels.size,
        meanCentroid,
        spreadCentroid,
        meanFlatness,
        spreadFlatness,
        fluxes.average(),
        quantile(fluxes, 0.9),
        changes.average(),
        quantile(changes, 0.9),
        *(1 until bandTotals.size).map { log10(bandTotals[it] / centroids.size + 1e-6) }.toDoubleArray(),
    )
}

package com.iven.musicplayergo.analysis

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln1p
import kotlin.math.roundToInt

internal data class RaptorBeatAnalysis(val beats: List<Double>, val bpm: Int?)

private const val MIN_PERIOD_SECONDS = 0.31
private const val MAX_PERIOD_SECONDS = 0.95
private const val HOP_SECONDS = 0.01

private data class OnsetEnvelope(val strengths: FloatArray, val frameSeconds: Double)
private data class Peak(val frame: Int, val strength: Float)

/**
 * Estimates the dominant tempo and beat positions in seconds from PCM start.
 *
 * Mirrors demos/beat-runner/public/beat.js. Uses up to two channels, multi-band onsets,
 * autocorrelation and an onset path constrained by tempo (Ellis, 2007).
 */
internal fun detectRaptorBeats(
    channels: Array<FloatArray>,
    sampleRate: Int,
    checkCancelled: () -> Unit = {}
): RaptorBeatAnalysis {
    require(sampleRate in 8_000..192_000)
    require(channels.isNotEmpty())
    val inputs = channels.take(2)
    val length = inputs.first().size
    require(inputs.all { it.size == length })
    checkCancelled()

    val noBeats = RaptorBeatAnalysis(emptyList(), null)
    if (length.toDouble() / sampleRate < 1.2) return noBeats

    val (envelope, frameSeconds) = onsetEnvelope(inputs, sampleRate, checkCancelled)
    val peaks = collectPeaks(envelope, frameSeconds)
    if (peaks.size < 3) return noBeats
    val period = estimatePeriod(peaks, envelope.size, frameSeconds, checkCancelled) ?: return noBeats
    val frames = followBeatPath(peaks, period, checkCancelled)
    if (frames.isEmpty()) return noBeats
    val secondsPerBeat = (frames.last() - frames.first()) * frameSeconds / (frames.size - 1)
    return RaptorBeatAnalysis(frames.map { it * frameSeconds }, (60 / secondsPerBeat).roundToInt())
}

private fun onsetEnvelope(
    channels: List<FloatArray>,
    sampleRate: Int,
    checkCancelled: () -> Unit
): OnsetEnvelope {
    val length = channels.first().size
    val stride = maxOf(1, sampleRate / 12_000)
    val hop = maxOf(1, (sampleRate * HOP_SECONDS / stride).roundToInt()) * stride
    val frameSeconds = hop.toDouble() / sampleRate
    val frameCount = (length - 1) / hop + 1
    val lowEnergy = FloatArray(frameCount)
    val midEnergy = FloatArray(frameCount)
    val highEnergy = FloatArray(frameCount)
    val lowAlpha = 1 - exp(-2 * PI * 150 * stride / sampleRate)
    val midAlpha = 1 - exp(-2 * PI * 1500 * stride / sampleRate)
    val scale = stride.toDouble() / (hop * channels.size)

    // Filtering channels separately preserves beat cues in phase-inverted stereo.
    for (samples in channels) {
        var low = 0.0
        var smooth = 0.0
        for (frame in 0 until frameCount) {
            if (frame % 64 == 0) checkCancelled()
            var lowSum = 0.0
            var midSum = 0.0
            var highSum = 0.0
            val end = minOf(length, (frame + 1) * hop)
            for (i in frame * hop until end step stride) {
                val sample = samples[i].toDouble()
                low += lowAlpha * (sample - low)
                smooth += midAlpha * (sample - smooth)
                val mid = smooth - low
                val high = sample - smooth
                lowSum += low * low
                midSum += mid * mid
                highSum += high * high
            }
            lowEnergy[frame] = (lowEnergy[frame] + lowSum * scale).toFloat()
            midEnergy[frame] = (midEnergy[frame] + midSum * scale).toFloat()
            highEnergy[frame] = (highEnergy[frame] + highSum * scale).toFloat()
        }
    }

    var peakEnergy = 0.0
    for (frame in 0 until frameCount) {
        peakEnergy = maxOf(peakEnergy, lowEnergy[frame].toDouble() + midEnergy[frame] + highEnergy[frame])
    }
    val envelope = FloatArray(frameCount)
    if (peakEnergy < 1e-9) return OnsetEnvelope(envelope, frameSeconds)

    var lastLow = 0.0
    var lastMid = 0.0
    var lastHigh = 0.0
    var previousBass = 0.0
    val bassWindow = (0.06 / frameSeconds).roundToInt()
    var nextBass = (0 until bassWindow).sumOf { lowEnergy.getOrElse(it) { 0f }.toDouble() }
    for (i in 0 until frameCount) {
        val low = ln1p(40 * lowEnergy[i].toDouble() / peakEnergy)
        val mid = ln1p(40 * midEnergy[i].toDouble() / peakEnergy)
        val high = ln1p(40 * highEnergy[i].toDouble() / peakEnergy)

        // Wider bass windows distinguish attacks from a sustained wave cycle.
        val bassAttack = if (nextBass > 1.3 * previousBass) maxOf(0.0, low - lastLow) else 0.0
        envelope[i] = (0.62 * bassAttack + 0.25 * maxOf(0.0, mid - lastMid) +
            0.13 * maxOf(0.0, high - lastHigh)).toFloat()
        previousBass += lowEnergy[i].toDouble() - lowEnergy.getOrElse(i - bassWindow) { 0f }
        nextBass += lowEnergy.getOrElse(i + bassWindow) { 0f }.toDouble() - lowEnergy[i]
        lastLow = low
        lastMid = mid
        lastHigh = high
    }
    return OnsetEnvelope(envelope, frameSeconds)
}

private fun collectPeaks(envelope: FloatArray, frameSeconds: Double): List<Peak> {
    val maximum = envelope.maxOrNull() ?: 0f
    val sum = envelope.sumOf { it.toDouble() }
    val floor = maxOf(0.025, maximum * 0.05, 2.8 * sum / envelope.size)
    val minGap = (0.065 / frameSeconds).roundToInt()
    val peaks = mutableListOf<Peak>()
    for (frame in envelope.indices) {
        val strength = envelope[frame]
        if (strength < floor || strength < envelope.getOrElse(frame - 1) { 0f } ||
            strength <= envelope.getOrElse(frame + 1) { 0f }
        ) continue
        val previous = peaks.lastOrNull()
        if (previous == null || frame - previous.frame >= minGap) {
            peaks.add(Peak(frame, strength))
        } else if (strength > previous.strength) {
            peaks[peaks.lastIndex] = Peak(frame, strength)
        }
    }
    return peaks
}

private fun estimatePeriod(
    peaks: List<Peak>,
    frameCount: Int,
    frameSeconds: Double,
    checkCancelled: () -> Unit
): Double? {
    val peakTrain = FloatArray(frameCount)
    val strengths = peaks.map { it.strength }.sorted()
    val cap = strengths[((strengths.size - 1) * 0.9).toInt()]
    for ((frame, strength) in peaks) {
        for (offset in -2..2) {
            val index = frame + offset
            if (index in peakTrain.indices) {
                peakTrain[index] = (minOf(strength, cap) * (1.0 - abs(offset) / 3.0)).toFloat()
            }
        }
    }

    val minLag = (MIN_PERIOD_SECONDS / frameSeconds).roundToInt()
    val maxLag = (MAX_PERIOD_SECONDS / frameSeconds).roundToInt()
    val correlations = DoubleArray(2 * maxLag + 1)
    val sumSquares = peakTrain.sumOf { it.toDouble() * it }
    for (lag in minLag..2 * maxLag) {
        if (lag % 32 == 0) checkCancelled()
        var dot = 0.0
        for (frame in lag until frameCount) {
            dot += peakTrain[frame].toDouble() * peakTrain[frame - lag]
        }
        correlations[lag] = dot / sumSquares
    }

    var best = 0
    var bestScore = 0.0
    val scores = DoubleArray(maxLag + 1)
    for (lag in minLag..maxLag) {
        val harmonicSupport = 0.2 * correlations[2 * lag]
        // Favor the first lag when the pulse also correlates at its multiples.
        val longPeriodPenalty = 0.15 * (lag - minLag) / (maxLag - minLag)
        scores[lag] = correlations[lag] + harmonicSupport - longPeriodPenalty
        if (scores[lag] > bestScore) {
            best = lag
            bestScore = scores[lag]
        }
    }
    if (bestScore < 0.2) return null
    if (best == minLag || best == maxLag) return best.toDouble()
    val before = scores[best - 1]
    val after = scores[best + 1]
    return best + 0.5 * (before - after) / (before + after - 2 * bestScore)
}

private fun followBeatPath(
    peaks: List<Peak>,
    period: Double,
    checkCancelled: () -> Unit
): List<Double> {
    val scores = DoubleArray(peaks.size)
    val previous = IntArray(peaks.size) { -1 }
    val strengths = peaks.map { it.strength }.sorted()
    val typical = strengths[(strengths.size * 0.75).toInt()]
    var best = 0
    for (i in peaks.indices) {
        if (i % 128 == 0) checkCancelled()
        var continuation = 0.0
        for (j in i - 1 downTo 0) {
            val elapsed = (peaks[i].frame - peaks[j].frame) / period
            if (elapsed > 3.25) break
            val intervals = elapsed.roundToInt()
            val error = abs(elapsed - intervals)
            if (intervals == 0 || error > 0.23) continue
            val score = scores[j] - 0.38 * (intervals - 1) - 8 * error * error
            if (score > continuation) {
                continuation = score
                previous[i] = j
            }
        }
        scores[i] = continuation + minOf(1.5, peaks[i].strength.toDouble() / typical)
        if (scores[i] > scores[best]) best = i
    }

    val anchors = mutableListOf<Int>()
    var index = best
    while (index >= 0) {
        anchors.add(peaks[index].frame)
        index = previous[index]
    }
    if (anchors.size < 3) return emptyList()
    anchors.reverse()

    val frames = mutableListOf(anchors[0].toDouble())
    for (i in 1 until anchors.size) {
        val gap = anchors[i] - anchors[i - 1]
        val intervals = (gap / period).roundToInt()
        for (step in 1..intervals) frames.add(anchors[i - 1] + step.toDouble() * gap / intervals)
    }
    return frames
}

package com.iven.musicplayergo.analysis

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

internal data class AudioExcerpt(val samples: FloatArray, val reachedDurationLimit: Boolean)

/** Decode a bounded excerpt of a local track. This must not be run on the main thread. */
internal class AndroidAudioDecoder(private val context: Context) {
    suspend fun decode(uri: Uri, maxSeconds: Int = 30): AudioExcerpt = withContext(Dispatchers.IO) {
        require(uri.scheme == "content" || uri.scheme == "file") { "Only local content and file URIs are supported" }
        require(maxSeconds in 1..60) { "Excerpt duration must be between 1 and 60 seconds" }
        val coroutine = currentCoroutineContext()
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var started = false
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("No audio track")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: throw IOException("Missing audio MIME type")
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            started = true
            var sourceRate = 0
            var channels = 0
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var samples = FloatArray(0)
            var count = 0
            var inputDone = false
            var outputDone = false
            var lastProgress = SystemClock.elapsedRealtime()
            val info = MediaCodec.BufferInfo()
            while (!outputDone) {
                coroutine.ensureActive()
                if (SystemClock.elapsedRealtime() - lastProgress > 15_000) throw IOException("Audio decoder stalled")
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val input = decoder.getInputBuffer(index) ?: throw IOException("Missing decoder input")
                        input.clear()
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime.coerceAtLeast(0), 0)
                            extractor.advance()
                        }
                        lastProgress = SystemClock.elapsedRealtime()
                    }
                }
                when (val index = decoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val decoded = decoder.outputFormat
                        val rate = decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val countChannels = decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        val pcm = if (decoded.containsKey(MediaFormat.KEY_PCM_ENCODING)) decoded.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                        if (rate !in 8_000..192_000 || countChannels !in 1..8 ||
                            pcm !in listOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_FLOAT)) {
                            throw IOException("Unsupported decoder PCM format")
                        }
                        if (count > 0 && (rate != sourceRate || countChannels != channels || pcm != encoding)) {
                            throw IOException("Audio format changed during decoding")
                        }
                        if (samples.isEmpty() || (count == 0 && rate != sourceRate)) {
                            samples = FloatArray(rate * maxSeconds)
                        }
                        sourceRate = rate
                        channels = countChannels
                        encoding = pcm
                        lastProgress = SystemClock.elapsedRealtime()
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (index >= 0) {
                        try {
                            if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                if (sourceRate == 0) throw IOException("Decoder did not report its output format")
                                val bytesPerSample = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                                val frameBytes = bytesPerSample * channels
                                if (info.size % frameBytes != 0) throw IOException("Incomplete PCM frame")
                                val output = decoder.getOutputBuffer(index) ?: throw IOException("Missing decoder output")
                                output.order(ByteOrder.LITTLE_ENDIAN)
                                output.position(info.offset)
                                output.limit(info.offset + info.size)
                                val frames = min(info.size / frameBytes, samples.size - count)
                                repeat(frames) {
                                    var sum = 0f
                                    repeat(channels) {
                                        val value = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) output.float else output.short / 32768f
                                        if (!value.isFinite()) throw IOException("Non-finite decoded sample")
                                        sum += value
                                    }
                                    samples[count++] = (sum / channels).coerceIn(-1f, 1f)
                                }
                                if (count == samples.size) outputDone = true
                            }
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                            lastProgress = SystemClock.elapsedRealtime()
                        } finally {
                            decoder.releaseOutputBuffer(index, false)
                        }
                    }
                }
            }
            if (count == 0 || sourceRate == 0) throw IOException("No decodable audio")
            AudioExcerpt(resampleTo16k(samples, count, sourceRate) { coroutine.ensureActive() }, count == samples.size)
        } finally {
            if (started) runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }
}

/** Windowed-sinc anti-alias resampler for the bounded mono excerpt. */
internal fun resampleTo16k(input: FloatArray, count: Int, sourceRate: Int, checkCancelled: () -> Unit): FloatArray {
    require(count in 1..input.size && sourceRate > 0)
    if (sourceRate == 16_000) return input.copyOf(count)
    val outputLength = (count.toLong() * 16_000 / sourceRate).toInt()
    val output = FloatArray(outputLength)
    val cutoff = min(1.0, 16_000.0 / sourceRate) * 0.95
    val radius = 16
    for (i in output.indices) {
        if (i % 1024 == 0) checkCancelled()
        val position = i.toDouble() * sourceRate / 16_000
        val center = floor(position).toInt()
        var weighted = 0.0
        var normalization = 0.0
        for (j in (center - radius + 1)..(center + radius)) {
            if (j !in 0 until count) continue
            val distance = position - j
            val argument = PI * distance * cutoff
            val sinc = if (argument == 0.0) 1.0 else sin(argument) / argument
            val window = 0.5 + 0.5 * cos(PI * distance / radius)
            val weight = cutoff * sinc * window
            weighted += input[j] * weight
            normalization += weight
        }
        // A sinc interpolator can overshoot on sharp transients; keep PCM in range.
        output[i] = if (normalization == 0.0) 0f else (weighted / normalization).toFloat().coerceIn(-1f, 1f)
    }
    return output
}

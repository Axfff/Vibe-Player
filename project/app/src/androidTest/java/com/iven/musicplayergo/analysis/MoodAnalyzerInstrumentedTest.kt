package com.iven.musicplayergo.analysis

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sqrt

/** Uses optional local-only test assets; no recording or model graph is committed. */
@RunWith(AndroidJUnit4::class)
class MoodAnalyzerInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val assets = instrumentation.context.assets
    private val workDir = File(context.cacheDir, "mood-instrumentation-${System.nanoTime()}").apply { mkdirs() }

    @After fun cleanup() { workDir.deleteRecursively() }

    @Test fun missingAndCorruptModelsReturnExplicitFailure() = runBlocking {
        val absent = File(workDir, "absent.onnx")
        val bogus = File(workDir, "bad.onnx").apply { writeText("not a model") }
        val uri = Uri.fromFile(File(workDir, "missing.wav"))
        assertEquals(MoodResult.Unavailable(MoodUnavailableReason.MISSING_MODEL),
            MoodAnalyzer(context, MoodModelFiles(absent, bogus)).analyze(uri))
        assertEquals(MoodResult.Unavailable(MoodUnavailableReason.INVALID_MODEL),
            MoodAnalyzer(context, MoodModelFiles(bogus, bogus)).analyze(uri))
    }

    @Test fun realModelsAndDecodedTrackMatchEssentiaAcrossAllThreePatches() = runBlocking {
        requireLocalFixture()
        val models = modelFiles()
        val audio = copyAsset("audio16.wav")
        val expected = JSONObject(assets.open("mood-vibe-reference.json").bufferedReader().use { it.readText() })
        val result = MoodAnalyzer(context, models).analyze(Uri.fromFile(audio))
        assertTrue("Expected real inference result, got $result", result is MoodResult.Available)
        result as MoodResult.Available
        assertEquals(3, result.patchCount)
        assertEquals(56, result.scores.size)
        val actual = result.scores.associate { it.label to it.score }
        val labels = expected.getJSONArray("labels")
        val scores = expected.getJSONArray("mean_scores")
        for (i in 0 until labels.length()) {
            val label = labels.getString(i)
            assertEquals("track mean for $label", scores.getDouble(i).toFloat(), actual.getValue(label), 3e-4f)
        }
    }

    @Test fun stereo44100WavIsDecodedAndResampledLikeTheReference() = runBlocking {
        requireLocalFixture()
        val decoder = AndroidAudioDecoder(context)
        val reference = decoder.decode(Uri.fromFile(copyAsset("audio16.wav"))).samples
        val stereoAudio = copyAsset("audio44100-stereo.wav")
        val converted = decoder.decode(Uri.fromFile(stereoAudio)).samples
        assertEquals(80000, reference.size)
        assertEquals(80000, converted.size)
        var dot = 0.0
        var refEnergy = 0.0
        var testEnergy = 0.0
        for (i in reference.indices) {
            dot += reference[i].toDouble() * converted[i]
            refEnergy += reference[i].toDouble() * reference[i]
            testEnergy += converted[i].toDouble() * converted[i]
        }
        val correlation = dot / sqrt(refEnergy * testEnergy)
        assertTrue("resampled and reference audio should correlate: $correlation", correlation > 0.97)
        assertTrue(converted.all { it.isFinite() && it in -1f..1f })
        val result = MoodAnalyzer(context, modelFiles()).analyze(Uri.fromFile(stereoAudio))
        assertTrue("Expected 44.1 kHz stereo inference result, got $result", result is MoodResult.Available)
        result as MoodResult.Available
        assertEquals(3, result.patchCount)
        assertEquals(56, result.scores.size)
    }

    @Test fun silenceAndInaccessibleAudioFallBackWhenModelsArePresent() = runBlocking {
        requireLocalFixture()
        val analyzer = MoodAnalyzer(context, modelFiles())
        val silence = File(workDir, "silence.wav").apply { writeBytes(silentPcmWav()) }
        assertEquals(MoodResult.Unavailable(MoodUnavailableReason.SILENT_AUDIO), analyzer.analyze(Uri.fromFile(silence)))
        assertEquals(MoodResult.Unavailable(MoodUnavailableReason.AUDIO_UNAVAILABLE),
            analyzer.analyze(Uri.fromFile(File(workDir, "does-not-exist.wav"))))
        assertEquals(MoodResult.Unavailable(MoodUnavailableReason.AUDIO_UNAVAILABLE),
            analyzer.analyze(Uri.parse("https://example.invalid/audio.wav")))
    }

    private fun requireLocalFixture() {
        val available = assets.list("")?.toSet().orEmpty()
        val required = setOf("backbone.onnx", "head.onnx", "audio16.wav", "audio44100-stereo.wav")
        assumeTrue("Supply -PmoodTestAssetsDir with locally obtained ONNX models and test WAVs", available.containsAll(required))
    }

    private fun modelFiles() = MoodModelFiles(copyAsset("backbone.onnx"), copyAsset("head.onnx"))

    private fun copyAsset(name: String): File = File(workDir, name).also { file ->
        assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
    }

    private fun silentPcmWav(): ByteArray {
        val count = 3 * 16000
        return ByteBuffer.allocate(44 + count * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(36 + count * 2)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); putInt(16); putShort(1); putShort(1)
            putInt(16000); putInt(32000); putShort(2); putShort(16)
            put("data".toByteArray(Charsets.US_ASCII)); putInt(count * 2)
        }.array()
    }
}

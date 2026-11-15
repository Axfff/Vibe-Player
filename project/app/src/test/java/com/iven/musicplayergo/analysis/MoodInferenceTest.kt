package com.iven.musicplayergo.analysis

import kotlinx.coroutines.runBlocking
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodInferenceTest {
    @Test fun insufficientSilentAndInvalidAudioDoesNotReachTheModel() = runBlocking {
        val cases = listOf(
            FloatArray(100) to MoodUnavailableReason.TOO_SHORT,
            FloatArray(32768) to MoodUnavailableReason.SILENT_AUDIO,
            FloatArray(32768) { if (it == 100) Float.NaN else 0.1f } to MoodUnavailableReason.INVALID_AUDIO,
            FloatArray(32768) { if (it == 100) 1.1f else 0.1f } to MoodUnavailableReason.INVALID_AUDIO,
        )
        cases.forEach { (samples, expected) ->
            var invoked = false
            val result = analyzePcm(samples) {
                invoked = true
                FloatArray(56)
            }
            assertEquals(MoodResult.Unavailable(expected), result)
            assertTrue("the model must not be used for unusable audio", !invoked)
        }
    }

    @Test fun averagesAllPatchesAndKeepsModelLabelMapping() = runBlocking {
        val samples = FloatArray(32768 + 62 * 256) { 0.1f }
        var calls = 0
        val result = analyzePcm(samples) { patch ->
            assertEquals(128 * 96, patch.size)
            calls++
            FloatArray(56) { index ->
                when (index) {
                    5 -> if (calls == 1) 0.8f else 0.4f // calm
                    18 -> 0.5f // energetic
                    else -> 0.1f
                }
            }
        } as MoodResult.Available
        assertEquals(2, calls)
        assertEquals(2, result.patchCount)
        assertEquals(samples.size / 16000f, result.analyzedSeconds, 0f)
        assertEquals(56, result.scores.size)
        assertEquals("calm", result.scores[0].label)
        assertEquals(0.6f, result.scores[0].score, 1e-6f)
        assertEquals("energetic", result.scores[1].label)
        assertEquals(0.5f, result.scores[1].score, 1e-6f)
    }

    @Test fun audibleSamplesOnlyInUnprocessedTailDoNotHideSilence() = runBlocking {
        val audio = FloatArray(32768 + 100) { if (it >= 32768) 1f else 0f }
        var called = false
        val result = analyzePcm(audio) { called = true; FloatArray(56) }
        assertEquals(MoodResult.Unavailable(MoodUnavailableReason.SILENT_AUDIO), result)
        assertTrue(!called)
    }

    @Test fun skipsSilentPatchesWhenARecordingAlsoContainsAudibleMusic() = runBlocking {
        // Three overlapping windows: the first is silent; the latter two contain audio.
        val samples = FloatArray(32768 + 2 * 62 * 256) { if (it < 32768) 0f else 0.1f }
        var calls = 0
        val result = analyzePcm(samples) { calls++; FloatArray(56) { 0.5f } } as MoodResult.Available
        assertEquals(2, calls)
        assertEquals(2, result.patchCount)
        assertEquals(1, result.skippedSilentPatchCount)
        assertEquals(0.5f, result.scores.first().score, 0f)
    }

    @Test fun rejectsMalformedOrNonfiniteModelOutputs() = runBlocking {
        val audio = FloatArray(32768) { 0.1f }
        listOf(FloatArray(55), FloatArray(56) { Float.NaN }, FloatArray(56) { 1.1f }).forEach { bad ->
            try {
                analyzePcm(audio) { bad }
                throw AssertionError("Expected malformed model output to fail")
            } catch (_: IllegalStateException) {
                // The public inference facade converts this to INFERENCE_FAILED.
            }
        }
    }

    @Test fun propagatesCancellationFromInference() = runBlocking {
        try {
            analyzePcm(FloatArray(32768) { 0.1f }) { throw CancellationException("cancelled") }
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            // Cancellation must not be converted into a prediction.
        }
    }
}

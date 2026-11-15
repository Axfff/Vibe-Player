package com.iven.musicplayergo.analysis

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Input framing contract for the 16 kHz EffNet frontend. */
class EffnetFeaturesTest {
    @Test fun patchCountRequiresACompleteWindowAndAdvancesByTheDocumentedHop() {
        assertEquals(0, EffnetFeatures.patchCount(0))
        assertEquals(0, EffnetFeatures.patchCount(32767))
        assertEquals(1, EffnetFeatures.patchCount(32768))
        assertEquals(1, EffnetFeatures.patchCount(32768 + 62 * 256 - 1))
        assertEquals(2, EffnetFeatures.patchCount(32768 + 62 * 256))
        assertEquals(3, EffnetFeatures.patchCount(32768 + 2 * 62 * 256))
    }

    @Test fun silentInputProducesFiniteZeroLogMelFeatures() {
        val patch = EffnetFeatures.patch(FloatArray(32768), 0)
        assertEquals(128 * 96, patch.size)
        assertTrue(patch.all { it == 0.0f && it.isFinite() })
    }

    @Test fun sameWindowIsDeterministicAndOffsetSelectsDifferentAudio() {
        val audio = FloatArray(32768 + 62 * 256) { index ->
            if (index < 62 * 256) 0f else if (index % 2 == 0) 0.25f else -0.25f
        }
        val first = EffnetFeatures.patch(audio, 0)
        val again = EffnetFeatures.patch(audio, 0)
        val next = EffnetFeatures.patch(audio, 62)
        assertArrayEquals(first, again, 0f)
        assertTrue("the second patch must use the requested start frame", !first.contentEquals(next))
    }

    @Test fun melPatchesMatchIndependentEssentiaReference() {
        // Golden values from Essentia 2.1b6.dev1173 FrameGenerator(512, 256)
        // and TensorflowInputMusiCNN on this deterministic PCM waveform.
        val audio = FloatArray(56000) { i ->
            ((((i * 17) % 4096 - 2048) * 8 + ((i * 31) % 2048 - 1024) * 4) / 32768.0).toFloat()
        }
        val bands = intArrayOf(0, 8, 24, 48, 72, 95)
        val first = EffnetFeatures.patch(audio, 0)
        val second = EffnetFeatures.patch(audio, 62)
        val cases = listOf(
            Triple(first, 0, floatArrayOf(5.408337f, 4.138459f, 3.025258f, 2.633205f, 1.798511f, 1.573163f)),
            Triple(first, 62, floatArrayOf(4.964820f, 4.530686f, 3.573587f, 3.296007f, 2.245984f, 1.995824f)),
            Triple(first, 127, floatArrayOf(4.954474f, 4.186716f, 3.639339f, 2.363299f, 2.118652f, 1.853766f)),
            Triple(second, 0, floatArrayOf(4.964820f, 4.530686f, 3.573587f, 3.296007f, 2.245984f, 1.995824f)),
            Triple(second, 127, floatArrayOf(4.972613f, 4.635773f, 3.485737f, 2.330601f, 2.398981f, 2.069595f)),
        )
        cases.forEach { (patch, frame, expected) ->
            bands.forEachIndexed { index, band ->
                assertEquals("frame=$frame band=$band", expected[index], patch[frame * 96 + band], 1e-4f)
            }
        }
    }

    @Test fun rejectsNegativeStartsAndIncompleteWindows() {
        assertIllegalArgument { EffnetFeatures.patch(FloatArray(32768), -1) }
        assertIllegalArgument { EffnetFeatures.patch(FloatArray(32767), 0) }
        assertIllegalArgument { EffnetFeatures.patch(FloatArray(32768), 62) }
    }

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}

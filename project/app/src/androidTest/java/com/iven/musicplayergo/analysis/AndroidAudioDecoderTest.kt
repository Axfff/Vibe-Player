package com.iven.musicplayergo.analysis

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidAudioDecoderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val decoder = AndroidAudioDecoder(context)
    private val files = mutableListOf<File>()

    @After
    fun cleanFixtures() {
        files.forEach { it.delete() }
    }

    @Test
    fun nativeStereoKeepsBothChannelsAndLegacyDecoderKeepsItsMonoMix() = runBlocking {
        val uri = wave(16_000, 8_000, shortArrayOf(8192, -4096))

        val native = decoder.decodeNative(uri)
        assertEquals(16_000, native.sampleRate)
        assertEquals(2, native.channels.size)
        assertEquals(8_000, native.channels[0].size)
        assertEquals(8_000, native.channels[1].size)
        assertFalse(native.reachedDurationLimit)
        assertTrue(native.channels[0].all { it == 0.25f })
        assertTrue(native.channels[1].all { it == -0.125f })

        val legacy = decoder.decode(uri)
        assertFalse(legacy.reachedDurationLimit)
        assertEquals(8_000, legacy.samples.size)
        assertTrue(legacy.samples.all { it == 0.0625f })
    }

    @Test
    fun nativeMonoPreservesSourceRateAndCapsTheFrames() = runBlocking {
        val uri = wave(44_100, 60_000, shortArrayOf(4096))

        val native = decoder.decodeNative(uri, maxSeconds = 1)
        assertEquals(44_100, native.sampleRate)
        assertEquals(1, native.channels.size)
        assertEquals(44_100, native.channels.single().size)
        assertTrue(native.reachedDurationLimit)
        assertTrue(native.channels.single().all { it == 0.125f })
    }

    @Test
    fun decoderRejectsRemoteInputAndInvalidExcerptLimits() = runBlocking {
        val remote = Uri.parse("https://example.invalid/audio.wav")
        val local = Uri.fromFile(File(context.cacheDir, "unused.wav"))

        assertTrue(runCatching { decoder.decodeNative(remote) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { decoder.decodeNative(local, maxSeconds = 0) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { decoder.decodeNative(local, maxSeconds = 61) }.exceptionOrNull() is IllegalArgumentException)
    }

    private fun wave(sampleRate: Int, frames: Int, channelValues: ShortArray): Uri {
        val frameBytes = channelValues.size * 2
        val pcmBytes = frames * frameBytes
        val pcm = ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        pcm.put("RIFF".toByteArray(Charsets.US_ASCII))
        pcm.putInt(36 + pcmBytes)
        pcm.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        pcm.putInt(16)
        pcm.putShort(1)
        pcm.putShort(channelValues.size.toShort())
        pcm.putInt(sampleRate)
        pcm.putInt(sampleRate * frameBytes)
        pcm.putShort(frameBytes.toShort())
        pcm.putShort(16)
        pcm.put("data".toByteArray(Charsets.US_ASCII))
        pcm.putInt(pcmBytes)
        repeat(frames) { channelValues.forEach { pcm.putShort(it) } }

        val file = File.createTempFile("decoder-", ".wav", context.cacheDir)
        files.add(file)
        file.writeBytes(pcm.array())
        return Uri.fromFile(file)
    }
}

package com.iven.musicplayergo.covers

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.graphics.drawable.toBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iven.musicplayergo.extensions.waitForCover
import com.iven.musicplayergo.regression.RegressionFixture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class CoverFallbackRegressionTest {
    @Test fun corruptReadableCustomCoverFallsBackToRealEmbeddedArtInViewAndPlaybackLoader() {
        RegressionFixture.open().use { f ->
            val resolver = f.context.contentResolver
            val album = "Embedded cover ${UUID.randomUUID()}"
            val uri = requireNotNull(resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "$album.mp3")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/CoverFallback/")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }))
            var target: CoverTarget.Album? = null
            val repo = CoverRepositories.get(f.context)
            try {
                resolver.openOutputStream(uri)!!.use { it.write(taggedAudio(album)) }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
                var albumId = 0L
                f.await("real embedded artwork indexed") {
                    resolver.query(uri, arrayOf(MediaStore.Audio.Media.ALBUM_ID), null, null, null)?.use {
                        if (it.moveToFirst()) albumId = it.getLong(0)
                    }
                    albumId > 0
                }
                target = CoverTarget.Album(albumId, "Vibe regression fixtures", album)
                val image = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
                val saved = try {
                    (repo.setCover(target, CoverImageInput.CroppedBitmap(image), CoverSource.USER_SELECTED) as CoverResult.Success).value
                } finally { image.recycle() }
                val file = repo.coverFile(saved)
                file.writeText("readable but not a WebP image")
                assertTrue(file.isFile && file.canRead())
                f.preferences.isCovers = true
                val view = f.onMain {
                    var result: ImageView? = null
                    f.onActivity { activity ->
                        result = ImageView(activity).apply {
                            activity.findViewById<ViewGroup>(android.R.id.content).addView(this, ViewGroup.LayoutParams(100, 100))
                            loadCover(target)
                        }
                    }
                    result!!
                }
                f.await("ImageView retries MediaStore after custom decode failure") {
                    f.onMain { view.drawable?.toBitmap(16, 16)?.getPixel(8, 8)?.let(::green) == true }
                }
                val received = AtomicBoolean(false)
                f.onMain { albumId.waitForCover(f.context) { bitmap, error ->
                    received.set(!error && bitmap != null && green(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)))
                } }
                f.await("playback loader retries the same embedded artwork") { received.get() }

                f.preferences.isCovers = false
                f.onMain { view.loadCover(target) }
                f.await("disabled embedded artwork falls through to the default") {
                    f.onMain { view.drawable?.toBitmap(16, 16)?.getPixel(8, 8)?.let { !green(it) } == true }
                }
                val defaultReceived = AtomicBoolean(false)
                f.onMain { albumId.waitForCover(f.context) { bitmap, error -> defaultReceived.set(error && bitmap == null) } }
                f.await("playback loader reports no artwork when embedded art is disabled") { defaultReceived.get() }
                f.onMain { (view.parent as ViewGroup).removeView(view) }
            } finally {
                target?.let { repo.removeCover(it) }
                resolver.delete(uri, null, null)
            }
        }
    }

    /** Reuse the existing synthetic tone; add an original green PNG as ID3v2.3 APIC artwork. */
    private fun taggedAudio(album: String): ByteArray {
        val original = InstrumentationRegistry.getInstrumentation().context.assets.open("playlist-display-A.mp3").use { it.readBytes() }
        val oldSize = (6..9).fold(0) { n, i -> (n shl 7) or (original[i].toInt() and 127) }
        fun frame(name: String, bytes: ByteArray) = name.toByteArray(Charsets.US_ASCII) +
            ByteBuffer.allocate(4).putInt(bytes.size).array() + byteArrayOf(0, 0) + bytes
        fun text(name: String, value: String) = frame(name, byteArrayOf(0) + value.toByteArray(Charsets.ISO_8859_1))
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val png = try { ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() }
            finally { bitmap.recycle() }
        val frames = text("TIT2", "Embedded cover regression") + text("TPE1", "Vibe regression fixtures") + text("TALB", album) +
            frame("APIC", byteArrayOf(0) + "image/png".toByteArray() + byteArrayOf(0, 3, 0) + png)
        val size = byteArrayOf(((frames.size shr 21) and 127).toByte(), ((frames.size shr 14) and 127).toByte(),
            ((frames.size shr 7) and 127).toByte(), (frames.size and 127).toByte())
        return "ID3".toByteArray() + byteArrayOf(3, 0, 0) + size + frames + original.copyOfRange(10 + oldSize, original.size)
    }
    private fun green(color: Int) = Color.green(color) > 200 && Color.red(color) < 40 && Color.blue(color) < 40
}

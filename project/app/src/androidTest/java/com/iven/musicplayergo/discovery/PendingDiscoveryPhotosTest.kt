package com.iven.musicplayergo.discovery

import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Model-independent checks safe to run alone: only exact rows created here are removed. */
@RunWith(AndroidJUnit4::class)
class PendingDiscoveryPhotosTest {
    private val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver

    @Before fun modernMediaStore() { assumeTrue(Build.VERSION.SDK_INT >= 29) }

    @Test fun pendingJpegSupportsOwnerMimeAndImageReadsUntilExactUriCleanup() {
        val photos = PendingDiscoveryPhotos(resolver)
        val bytes = jpeg()
        try {
            val uri = photos.insert("readable.jpg") { ByteArrayInputStream(bytes) }
            assertEquals("image/jpeg", resolver.getType(uri))
            assertArrayEquals(bytes, resolver.openInputStream(uri)!!.use { it.readBytes() })
            val bitmap = resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
            assertNotNull(bitmap)
            assertEquals(16, bitmap!!.width)
            bitmap.recycle()
            assertPrivateAndExpiring(uri)
            val cleanup = photos.cleanup()
            assertEquals(PendingDiscoveryPhotos.Cleanup(1, 1, emptyList()), cleanup)
            assertAbsent(uri)
            assertEquals(PendingDiscoveryPhotos.Cleanup(0, 0, emptyList()), photos.cleanup())
        } finally { assertTrue(photos.cleanup().errors.isEmpty()) }
    }

    @Test fun skippedTeardownLeavesPrivateExpiringRowInsteadOfPublishedPhoto() {
        // Discard the owner's in-memory registry, as when instrumentation cannot run @After.
        // Finally removes this test's exact URI so the regression itself leaves nothing behind.
        val bytes = jpeg()
        val uri = PendingDiscoveryPhotos(resolver).insert("interrupted.jpg") { ByteArrayInputStream(bytes) }
        try {
            assertPrivateAndExpiring(uri)
            assertArrayEquals(bytes, resolver.openInputStream(uri)!!.use { it.readBytes() })
        } finally {
            assertEquals(1, resolver.delete(uri, null, null))
            assertAbsent(uri)
        }
    }

    @Test fun copyFailureIsRegisteredAndCleanupPreservesOtherExistingPhotos() {
        val existing = PendingDiscoveryPhotos(resolver)
        val photos = PendingDiscoveryPhotos(resolver)
        val bytes = jpeg()
        try {
            val kept = existing.insert("existing.jpg") { ByteArrayInputStream(bytes) }
            // A pre-existing row outside the manager under test. Keep even this stand-in private.
            try {
                photos.insert("failed.jpg") { throw IOException("fixture read failure") }
                fail("Copy must report failure")
            } catch (_: IOException) { }
            val failed = photos.createdUris.single()
            assertPrivateAndExpiring(failed)
            assertEquals(PendingDiscoveryPhotos.Cleanup(1, 1, emptyList()), photos.cleanup())
            assertAbsent(failed)
            assertArrayEquals(bytes, resolver.openInputStream(kept)!!.use { it.readBytes() })
            assertEquals(1L, column(kept, MediaStore.Images.Media.IS_PENDING))
        } finally {
            assertTrue(photos.cleanup().errors.isEmpty())
            assertTrue(existing.cleanup().errors.isEmpty())
        }
    }

    @Test fun interruptionDuringCopyStillLeavesPendingRowAndTeardownCanRemoveIt() {
        val photos = PendingDiscoveryPhotos(resolver)
        try {
            try {
                photos.insert("partial.jpg") {
                    object : ByteArrayInputStream(jpeg()) {
                        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = throw InterruptedCopy()
                    }
                }
                fail("Simulated interruption must escape")
            } catch (_: InterruptedCopy) { }
            val partial = photos.createdUris.single()
            assertPrivateAndExpiring(partial)
            assertEquals(PendingDiscoveryPhotos.Cleanup(1, 1, emptyList()), photos.cleanup())
            assertAbsent(partial)
        } finally { assertTrue(photos.cleanup().errors.isEmpty()) }
    }

    private fun assertPrivateAndExpiring(uri: Uri) {
        assertEquals(1L, column(uri, MediaStore.Images.Media.IS_PENDING))
        assertTrue("Android must retain a finite pending expiration",
            column(uri, MediaStore.Images.Media.DATE_EXPIRES) > System.currentTimeMillis() / 1000)
        resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media._ID}=? AND ${MediaStore.Images.Media.IS_PENDING}=0",
            arrayOf(ContentUris.parseId(uri).toString()), null)!!.use {
            assertEquals("Fixture must not enter the published media set", 0, it.count)
        }
    }

    private fun column(uri: Uri, name: String): Long =
        resolver.query(uri, arrayOf(name), null, null, null)!!.use { assertTrue(it.moveToFirst()); it.getLong(0) }

    private fun assertAbsent(uri: Uri) {
        resolver.query(uri, arrayOf(MediaStore.Images.Media._ID), null, null, null)!!.use { assertEquals(0, it.count) }
    }

    private fun jpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        return try { ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it); it.toByteArray() } }
        finally { bitmap.recycle() }
    }

    private class InterruptedCopy : Error()
}

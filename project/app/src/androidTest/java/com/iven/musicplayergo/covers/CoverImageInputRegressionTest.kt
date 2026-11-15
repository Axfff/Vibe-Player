package com.iven.musicplayergo.covers

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CoverImageInputRegressionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun pickerDecoderSamplesLargeValidImageBeforeAllocatingPreview() {
        val bitmap = Bitmap.createBitmap(4096, 1024, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        val bytes = png(bitmap)
        bitmap.recycle()
        withImage(bytes) { uri ->
            val decoded = success(CoverImageDecoder(context).decode(uri))
            try {
                assertEquals(2048, decoded.width)
                assertEquals(512, decoded.height)
                assertEquals(Color.CYAN, decoded.getPixel(100, 100))
            } finally { decoded.recycle() }
        }
    }

    @Test fun pickerAndDirectImporterEnforceEncodedByteAndPixelBounds() {
        val bitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
        val bytes = png(bitmap)
        bitmap.recycle()
        withImage(bytes) { uri ->
            assertEquals(CoverResult.Failure(CoverError.IMAGE_TOO_LARGE), CoverImageDecoder(context, maxBytes = bytes.size - 1L).decode(uri))
            assertEquals(CoverResult.Failure(CoverError.IMAGE_TOO_LARGE), CoverImageDecoder(context, maxPixels = 100).decode(uri))
        }
    }

    @Test fun unsupportedOrCorruptContentsCannotEnterTheCropPreview() {
        withImage("<html>not a picture</html>".toByteArray()) { uri ->
            assertEquals(CoverResult.Failure(CoverError.IMAGE_DECODE_FAILED), CoverImageDecoder(context).decode(uri))
        }
        withImage(byteArrayOf(1, 2, 3), "image/gif") { uri ->
            assertEquals(CoverResult.Failure(CoverError.UNSUPPORTED_IMAGE_TYPE), CoverImageDecoder(context).decode(uri))
        }
        assertEquals(CoverResult.Failure(CoverError.SOURCE_NOT_READABLE),
            CoverImageDecoder(context).decode(Uri.parse("file:///not-authorized")))
    }

    @Test fun constrainedViewportExportsTheSameSquareWithoutStretching() {
        val bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { color = Color.RED }
        Canvas(bitmap).apply { drawColor(Color.WHITE); drawRect(110f, 60f, 190f, 140f, paint) }
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                for ((width, height) in listOf(180 to 350, 420 to 150)) {
                    val view = CropImageView(context)
                    view.setCropBitmap(bitmap)
                    view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST))
                    assertEquals(minOf(width, height), view.measuredWidth)
                    assertEquals(view.measuredWidth, view.measuredHeight)
                    view.layout(0, 0, view.measuredWidth, view.measuredHeight)
                    val exported = view.export(200)
                    try {
                        val horizontal = (0 until 200).count { Color.green(exported.getPixel(it, 100)) < 50 }
                        val vertical = (0 until 200).count { Color.green(exported.getPixel(100, it)) < 50 }
                        assertEquals("A square in the photo must stay square", horizontal, vertical)
                        assertTrue(horizontal in 78..82)
                    } finally { exported.recycle() }
                }
            }
        } finally { bitmap.recycle() }
    }

    private fun png(bitmap: Bitmap) = ByteArrayOutputStream().also {
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
    }.toByteArray()

    private fun withImage(bytes: ByteArray, mime: String = "image/png", block: (Uri) -> Unit) {
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "cover-input-${UUID.randomUUID()}.${if (mime == "image/gif") "gif" else "png"}")
                put(MediaStore.Images.Media.MIME_TYPE, mime)
            }))
        try {
            requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(bytes) }
            block(uri)
        } finally { context.contentResolver.delete(uri, null, null) }
    }
    private fun <T> success(result: CoverResult<T>): T = (result as CoverResult.Success).value
}

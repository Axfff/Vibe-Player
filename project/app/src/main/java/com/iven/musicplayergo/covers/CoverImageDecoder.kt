package com.iven.musicplayergo.covers

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** The picker preview and direct import use the same bounded decoder on every supported API. */
internal class CoverImageDecoder(
    private val context: Context,
    private val maxBytes: Long = 20L * 1024 * 1024,
    private val maxPixels: Long = 40_000_000,
    private val maxEdge: Int = 2048
) {
    fun decode(uri: Uri): CoverResult<Bitmap> {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return failure(CoverError.SOURCE_NOT_READABLE)
        return try {
            val resolver = context.contentResolver
            val types = setOf("image/jpeg", "image/png", "image/webp")
            if (resolver.getType(uri) !in types) return failure(CoverError.UNSUPPORTED_IMAGE_TYPE)
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) return failure(CoverError.IMAGE_TOO_LARGE)
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            } ?: return failure(CoverError.SOURCE_NOT_READABLE)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return failure(CoverError.IMAGE_DECODE_FAILED)
            if (bounds.outMimeType !in types) return failure(CoverError.UNSUPPORTED_IMAGE_TYPE)
            if (bounds.outWidth.toLong() * bounds.outHeight > maxPixels) return failure(CoverError.IMAGE_TOO_LARGE)
            var sample = 1
            while ((maxOf(bounds.outWidth, bounds.outHeight).toLong() + sample - 1) / sample > maxEdge) sample *= 2
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return failure(CoverError.IMAGE_DECODE_FAILED)
            val orientation = runCatching {
                ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
            }.getOrDefault(1)
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
            }
            if (matrix.isIdentity) CoverResult.Success(bitmap) else {
                try { CoverResult.Success(Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)) }
                finally { bitmap.recycle() }
            }
        } catch (_: OutOfMemoryError) {
            failure(CoverError.IMAGE_TOO_LARGE)
        } catch (_: Exception) {
            failure(CoverError.SOURCE_NOT_READABLE)
        }
    }

    private fun failure(error: CoverError): CoverResult<Bitmap> = CoverResult.Failure(error)
}

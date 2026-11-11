package com.iven.musicplayergo.discovery

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object DiscoveryPhoto {
    /** Read once after consent, cap bytes/pixels before decoding, retain no file/URI grant. */
    fun prepare(resolver: ContentResolver, uri: Uri): ByteArray {
        val bytes = resolver.openInputStream(uri)?.use { it.readBytesBounded(5 * 1024 * 1024) }
            ?: throw DiscoveryFailure("invalid_image")
        return sanitize(bytes)
    }

    fun sanitize(bytes: ByteArray): ByteArray {
        if (bytes.isEmpty() || bytes.size > 5 * 1024 * 1024) throw DiscoveryFailure("image_byte_limit")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outMimeType !in listOf("image/jpeg", "image/png", "image/webp")) throw DiscoveryFailure("invalid_image")
        if (bounds.outWidth.toLong() * bounds.outHeight > 20_000_000) throw DiscoveryFailure("image_pixel_limit")
        // Animated formats are not accepted as a single-photo input.
        if (bytes.toString(Charsets.ISO_8859_1).let { it.startsWith("RIFF") && it.contains("ANIM") || it.startsWith("\u0089PNG") && it.contains("acTL") }) throw DiscoveryFailure("invalid_image")
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 2560) inSampleSize *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: throw DiscoveryFailure("invalid_image")
        var oriented: Bitmap? = null
        var scaled: Bitmap? = null
        var clean: Bitmap? = null
        try {
            val exif = ExifInterface(ByteArrayInputStream(bytes))
            val transform = Matrix().apply {
                if (exif.isFlipped) postScale(-1f, 1f)
                postRotate(exif.rotationDegrees.toFloat())
            }
            oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, transform, true)
            val ratio = minOf(1f, 1280f / maxOf(oriented.width, oriented.height))
            scaled = Bitmap.createScaledBitmap(oriented, maxOf(1, (oriented.width * ratio).toInt()), maxOf(1, (oriented.height * ratio).toInt()), true)
            clean = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
            Canvas(clean).apply { drawColor(Color.WHITE); drawBitmap(scaled, 0f, 0f, null) }
            return ByteArrayOutputStream().use { output ->
                if (!clean.compress(Bitmap.CompressFormat.JPEG, 85, output)) throw DiscoveryFailure("invalid_image")
                output.toByteArray()
            }
        } finally {
            listOfNotNull(clean, scaled, oriented, decoded).distinct().forEach { it.recycle() }
        }
    }
}

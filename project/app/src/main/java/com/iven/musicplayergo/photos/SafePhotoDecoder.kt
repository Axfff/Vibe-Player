package com.iven.musicplayergo.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Shared bounded decode for thumbnails, model inputs and candidate crops; no GPS is requested. */
class SafePhotoDecoder(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    fun bytes(uri: Uri): ByteArray {
        require(uri.scheme == "content" && resolver.getType(uri) in PhotoAccessManager.MIME_TYPES) { "不支持的图片格式" }
        resolver.openAssetFileDescriptor(uri, "r")?.use { require(it.length <= MAX_BYTES) { "图片超过 20 MiB" } }
        return resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream(); val buffer = ByteArray(16 * 1024)
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                val count = input.read(buffer); if (count < 0) break
                require(output.size().toLong() + count <= MAX_BYTES) { "图片超过 20 MiB" }
                output.write(buffer, 0, count)
            }; output.toByteArray()
        } ?: error("图片暂不可读取")
    }
    fun decode(bytes: ByteArray, edge: Int = 1024): Bitmap {
        require(bytes.size <= MAX_BYTES)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 40_000_000) { "图片损坏或像素过大" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > edge * 2) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("无法解码图片")
        val orientation = ByteArrayInputStream(bytes).use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }
        val matrix = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f); 3 -> setRotate(180f); 4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }; 6 -> setRotate(90f)
                7 -> { setRotate(270f); postScale(-1f, 1f) }; 8 -> setRotate(270f)
            }
        }
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { if (it !== bitmap) bitmap.recycle() }
    }
    companion object { const val MAX_BYTES = 20L * 1024 * 1024 }
}

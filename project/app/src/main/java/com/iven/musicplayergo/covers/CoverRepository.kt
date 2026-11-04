package com.iven.musicplayergo.covers

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.preference.PreferenceManager
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

sealed class CoverImageInput {
    data class ContentUri(val uri: Uri) : CoverImageInput()
    data class CroppedBitmap(val bitmap: Bitmap) : CoverImageInput()
}

interface CustomCoverRepository {
    fun getCover(target: CoverTarget): CoverResult<CustomCover?>
    fun setCover(target: CoverTarget, input: CoverImageInput, source: CoverSource): CoverResult<CustomCover>
    fun removeCover(target: CoverTarget): CoverResult<Unit>
    fun hasCover(target: CoverTarget): Boolean
    fun coverFile(cover: CustomCover): File
}

class AndroidCustomCoverRepository internal constructor(
    private val context: Context,
    private val records: CoverRecordRepository,
    private val root: File = File(context.filesDir, "custom-covers"),
    private val maxInputBytes: Long = 20L * 1024 * 1024,
    private val maxPixels: Long = 40_000_000,
    private val outputSize: Int = 1024
) : CustomCoverRepository {
    override fun getCover(target: CoverTarget) = records.getCover(target)
    override fun hasCover(target: CoverTarget) = records.hasCover(target)
    override fun coverFile(cover: CustomCover) = File(File(root, cover.targetType.name.lowercase()), cover.localFileName)

    @Synchronized
    override fun setCover(
        target: CoverTarget,
        input: CoverImageInput,
        source: CoverSource
    ): CoverResult<CustomCover> {
        if (!target.isValid()) return CoverResult.Failure(CoverError.INVALID_TARGET)
        val bitmapResult = when (input) {
            is CoverImageInput.ContentUri -> decodeUri(input.uri)
            is CoverImageInput.CroppedBitmap -> validateBitmap(input.bitmap)
        }
        val bitmap = when (bitmapResult) {
            is CoverResult.Success -> bitmapResult.value
            is CoverResult.Failure -> return bitmapResult
        }
        val normalized = normalizeSquare(bitmap)
        if (bitmap !== normalized && input is CoverImageInput.ContentUri) bitmap.recycle()

        val directory = File(root, target.typeDirectory())
        if (!directory.exists() && !directory.mkdirs()) {
            normalized.recycleIfOwned(input)
            return CoverResult.Failure(CoverError.STORAGE_WRITE_FAILED)
        }
        val finalName = target.safeId() + ".webp"
        val finalFile = File(directory, finalName)
        val temp = File(directory, finalName + ".tmp")
        val backup = File(directory, finalName + ".bak")
        try {
            FileOutputStream(temp).use { output ->
                if (!normalized.compress(Bitmap.CompressFormat.WEBP, 90, output)) {
                    return CoverResult.Failure(CoverError.STORAGE_WRITE_FAILED)
                }
                output.fd.sync()
            }
            if (finalFile.exists() && !finalFile.renameTo(backup)) {
                return CoverResult.Failure(CoverError.STORAGE_WRITE_FAILED)
            }
            if (!temp.renameTo(finalFile)) {
                backup.renameTo(finalFile)
                return CoverResult.Failure(CoverError.STORAGE_WRITE_FAILED)
            }
            return when (val saved = records.putCover(target, finalName, source)) {
                is CoverResult.Success -> {
                    backup.delete()
                    saved
                }
                is CoverResult.Failure -> {
                    finalFile.delete()
                    backup.renameTo(finalFile)
                    saved
                }
            }
        } catch (_: Exception) {
            if (!finalFile.exists()) backup.renameTo(finalFile)
            return CoverResult.Failure(CoverError.STORAGE_WRITE_FAILED)
        } finally {
            temp.delete()
            normalized.recycleIfOwned(input)
        }
    }

    @Synchronized
    override fun removeCover(target: CoverTarget): CoverResult<Unit> {
        val existing = when (val result = records.getCover(target)) {
            is CoverResult.Failure -> return result
            is CoverResult.Success -> result.value ?: return CoverResult.Failure(CoverError.COVER_NOT_FOUND)
        }
        val file = coverFile(existing)
        val pendingDelete = File(file.parentFile, file.name + ".delete")
        if (file.exists() && !file.renameTo(pendingDelete)) {
            return CoverResult.Failure(CoverError.STORAGE_WRITE_FAILED)
        }
        return when (val removed = records.removeCover(target)) {
            is CoverResult.Failure -> {
                pendingDelete.renameTo(file)
                removed
            }
            is CoverResult.Success -> {
                pendingDelete.delete()
                CoverResult.Success(Unit)
            }
        }
    }

    private fun decodeUri(uri: Uri): CoverResult<Bitmap> {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return CoverResult.Failure(CoverError.SOURCE_NOT_READABLE)
        val mime = context.contentResolver.getType(uri)
        if (mime !in setOf("image/jpeg", "image/png", "image/webp")) {
            return CoverResult.Failure(CoverError.UNSUPPORTED_IMAGE_TYPE)
        }
        val length = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: -1L
        if (length > maxInputBytes) return CoverResult.Failure(CoverError.IMAGE_TOO_LARGE)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (!decodeStream(uri, bounds) || bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return CoverResult.Failure(CoverError.IMAGE_DECODE_FAILED)
        }
        val pixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
        if (pixels > maxPixels) return CoverResult.Failure(CoverError.IMAGE_TOO_LARGE)
        var sample = 1
        while (bounds.outWidth / sample > outputSize * 2 || bounds.outHeight / sample > outputSize * 2) sample *= 2
        val decoded = try {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        } catch (_: Exception) { null } ?: return CoverResult.Failure(CoverError.SOURCE_NOT_READABLE)
        return CoverResult.Success(applyExif(uri, decoded))
    }

    private fun decodeStream(uri: Uri, options: BitmapFactory.Options): Boolean = try {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        true
    } catch (_: Exception) { false }

    private fun applyExif(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
                ?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { bitmap.recycle() }
    }

    private fun validateBitmap(bitmap: Bitmap): CoverResult<Bitmap> =
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) CoverResult.Failure(CoverError.IMAGE_DECODE_FAILED)
        else if (bitmap.width.toLong() * bitmap.height > maxPixels) CoverResult.Failure(CoverError.IMAGE_TOO_LARGE)
        else CoverResult.Success(bitmap)

    private fun normalizeSquare(bitmap: Bitmap): Bitmap {
        val edge = minOf(bitmap.width, bitmap.height)
        val cropped = Bitmap.createBitmap(bitmap, (bitmap.width - edge) / 2, (bitmap.height - edge) / 2, edge, edge)
        if (edge == outputSize) return cropped
        return Bitmap.createScaledBitmap(cropped, outputSize, outputSize, true).also {
            if (cropped !== bitmap) cropped.recycle()
        }
    }

    private fun Bitmap.recycleIfOwned(input: CoverImageInput) {
        if (input is CoverImageInput.ContentUri && !isRecycled) recycle()
    }

    private fun CoverTarget.typeDirectory() = when (this) {
        is CoverTarget.Album -> "album"
        is CoverTarget.Playlist -> "playlist"
    }
    private fun CoverTarget.safeId() = when (this) {
        is CoverTarget.Album -> albumId.toString()
        is CoverTarget.Playlist -> playlistId.lowercase()
    }
}

object CoverRepositories {
    @Volatile private var instance: CustomCoverRepository? = null

    fun get(context: Context): CustomCoverRepository = instance ?: synchronized(this) {
        instance ?: run {
            val app = context.applicationContext
            val prefs = PreferenceManager.getDefaultSharedPreferences(app)
            AndroidCustomCoverRepository(app, CoverRecordRepository(SharedPreferencesCoverStorage(prefs)))
        }.also { instance = it }
    }
}

internal class SharedPreferencesCoverStorage(private val preferences: SharedPreferences) : CoverRecordStorage {
    override fun read(): String? = preferences.getString(KEY, null)
    override fun write(json: String): Boolean {
        val previous = read()
        val success = try { preferences.edit().putString(KEY, json).commit() } catch (_: Exception) { false }
        if (!success) try {
            preferences.edit().apply { if (previous == null) remove(KEY) else putString(KEY, previous) }.commit()
        } catch (_: Exception) { }
        return success
    }
    companion object { const val KEY = "custom_covers_v1" }
}


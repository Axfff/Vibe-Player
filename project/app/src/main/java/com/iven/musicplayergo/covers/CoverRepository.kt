package com.iven.musicplayergo.covers

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import androidx.preference.PreferenceManager
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

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
    @Synchronized
    override fun coverFile(cover: CustomCover): File {
        val file = File(File(root, cover.targetType.name.lowercase()), cover.localFileName)
        // Recover a transaction interrupted by the previous importer before it committed metadata.
        if (!file.exists()) {
            listOf(".bak", ".delete").firstOrNull { suffix ->
                val pending = File(file.parentFile, file.name + suffix)
                pending.isFile && pending.renameTo(file)
            }
        }
        return file
    }

    @Synchronized
    override fun setCover(
        target: CoverTarget,
        input: CoverImageInput,
        source: CoverSource
    ): CoverResult<CustomCover> {
        if (!target.isValid()) return CoverResult.Failure(CoverError.INVALID_TARGET)
        val previous = when (val result = records.getCover(target)) {
            is CoverResult.Failure -> return result
            is CoverResult.Success -> result.value
        }
        val bitmapResult = when (input) {
            is CoverImageInput.ContentUri -> CoverImageDecoder(context, maxInputBytes, maxPixels, outputSize * 2).decode(input.uri)
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
            if (normalized !== bitmap || input is CoverImageInput.ContentUri) normalized.recycle()
            return CoverResult.Failure(CoverError.STORAGE_WRITE_FAILED)
        }
        // Immutable names keep the old record readable until the new record is durably committed.
        val finalName = target.safeId() + "-" + UUID.randomUUID() + ".webp"
        val finalFile = File(directory, finalName)
        val temp = File(directory, finalName + ".tmp")
        try {
            FileOutputStream(temp).use { output ->
                if (!normalized.compress(Bitmap.CompressFormat.WEBP, 90, output)) {
                    return CoverResult.Failure(CoverError.STORAGE_WRITE_FAILED)
                }
                output.fd.sync()
            }
            if (!temp.renameTo(finalFile)) {
                return CoverResult.Failure(CoverError.STORAGE_WRITE_FAILED)
            }
            return when (val saved = records.putCover(target, finalName, source)) {
                is CoverResult.Success -> {
                    previous?.let { runCatching { coverFile(it).delete() } }
                    saved
                }
                is CoverResult.Failure -> {
                    finalFile.delete()
                    saved
                }
            }
        } catch (_: Exception) {
            return CoverResult.Failure(CoverError.STORAGE_WRITE_FAILED)
        } finally {
            temp.delete()
            if (normalized !== bitmap || input is CoverImageInput.ContentUri) normalized.recycle()
        }
    }

    @Synchronized
    override fun removeCover(target: CoverTarget): CoverResult<Unit> {
        val existing = when (val result = records.getCover(target)) {
            is CoverResult.Failure -> return result
            is CoverResult.Success -> result.value ?: return CoverResult.Failure(CoverError.COVER_NOT_FOUND)
        }
        val file = coverFile(existing)
        return when (val removed = records.removeCover(target)) {
            is CoverResult.Failure -> removed
            is CoverResult.Success -> {
                runCatching { file.delete() }
                CoverResult.Success(Unit)
            }
        }
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
            val storage = SharedPreferencesCoverStorage(
                app.getSharedPreferences(SharedPreferencesCoverStorage.FILE_NAME, Context.MODE_PRIVATE),
                PreferenceManager.getDefaultSharedPreferences(app)
            )
            runCatching { storage.read() } // Retry on subsequent reads if migration could not commit.
            AndroidCustomCoverRepository(app, CoverRecordRepository(storage))
        }.also { instance = it }
    }
}

internal class SharedPreferencesCoverStorage(
    private val preferences: SharedPreferences,
    private val legacy: SharedPreferences? = null
) : CoverRecordStorage {
    override fun read(): String? {
        val current = preferences.getString(KEY, null)
        val old = legacy?.getString(KEY, null)
        if (old != null) {
            // Never discard the only durable copy. Other default preferences are untouched.
            if (current == null && !preferences.edit().putString(KEY, old).commit()) {
                preferences.edit().remove(KEY).commit()
                throw java.io.IOException("Cover metadata migration failed")
            }
            if (!requireNotNull(legacy).edit().remove(KEY).commit()) {
                legacy.edit().putString(KEY, old).commit()
                throw java.io.IOException("Legacy cover cleanup failed")
            }
        }
        return current ?: old
    }
    override fun write(json: String): Boolean {
        val previous = read()
        val success = try { preferences.edit().putString(KEY, json).commit() } catch (_: Exception) { false }
        if (!success) try {
            preferences.edit().apply { if (previous == null) remove(KEY) else putString(KEY, previous) }.commit()
        } catch (_: Exception) { }
        return success
    }
    companion object {
        const val KEY = "custom_covers_v1"
        const val FILE_NAME = "custom_covers"
    }
}


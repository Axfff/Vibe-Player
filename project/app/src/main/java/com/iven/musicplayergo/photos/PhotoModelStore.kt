package com.iven.musicplayergo.photos

import com.iven.musicplayergo.R
import com.iven.musicplayergo.preferences.UiText
import com.iven.musicplayergo.preferences.UiTextException
import android.content.Context
import android.system.Os
import com.iven.musicplayergo.analysis.MoodAnalyzer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** Only these exact, previously evaluated model versions can become active. */
enum class PhotoModel(val title: UiText, val filename: String, val bytes: Long, val sha256: String, val source: String, val license: UiText) {
    MUSIC(UiText.of(R.string.photo_model_music), "discogs-effnet-bsdynamic-1.onnx", 18027718, MoodAnalyzer.BACKBONE_SHA256,
        "https://essentia.upf.edu/models/feature-extractors/discogs-effnet/discogs-effnet-bsdynamic-1.onnx", UiText.of(R.string.photo_license_audio)),
    MOOD(UiText.of(R.string.photo_model_mood), "mtg_jamendo_moodtheme-discogs-effnet-1.onnx", 2739322, MoodAnalyzer.HEAD_SHA256,
        "https://essentia.upf.edu/models/classification-heads/mtg_jamendo_moodtheme/mtg_jamendo_moodtheme-discogs-effnet-1.onnx", UiText.of(R.string.photo_license_audio)),
    PHOTO(UiText.of(R.string.photo_model_photo), OnnxPhotoFeatureExtractor.FILENAME, 158993255, OnnxPhotoFeatureExtractor.SHA256,
        "https://github.com/Axfff/Vibe-Player/blob/feat/scoped-photo-recommendations/docs/development/photo-scene-model.md", UiText.of(R.string.photo_license_image));
}

enum class PhotoModelStatus(val label: UiText) { MISSING(UiText.of(R.string.photo_model_missing)), UPDATE_REQUIRED(UiText.of(R.string.photo_model_update)), READY(UiText.of(R.string.photo_model_ready)), INVALID(UiText.of(R.string.photo_model_invalid)), UNREADABLE(UiText.of(R.string.photo_model_unreadable)) }

/** App-private copies; no persisted source URI or photo-library permission is needed. */
class PhotoModelStore(private val directory: File) {
    constructor(context: Context) : this(File(context.noBackupFilesDir, "photo-models"))
    fun file(model: PhotoModel) = File(directory, model.filename)

    suspend fun inspect(): Map<PhotoModel, PhotoModelStatus> = withContext(Dispatchers.IO) {
        lock.withLock {
            // Recover incomplete imports after process death; active imports share this lock.
            directory.listFiles()?.filter { it.name.startsWith("import-") && it.name.endsWith(".part") }?.forEach { it.delete() }
            PhotoModel.values().associateWith { model ->
                val file = file(model)
                if (!file.exists()) {
                    if (model == PhotoModel.PHOTO && listOf("mobilenetv2-7.onnx", "tinyclip-scenes-v2.onnx", "tinyclip-atmospheres-v3.onnx").any { File(directory, it).exists() }) PhotoModelStatus.UPDATE_REQUIRED
                    else PhotoModelStatus.MISSING
                }
                else try {
                    if (file.length() == model.bytes && file.inputStream().use { digest(it) } == model.sha256)
                        PhotoModelStatus.READY else PhotoModelStatus.INVALID
                } catch (e: CancellationException) { throw e }
                  catch (_: Exception) { PhotoModelStatus.UNREADABLE }
            }
        }
    }

    suspend fun import(model: PhotoModel, open: () -> InputStream, progress: suspend (Int) -> Unit = {}) =
        install(model.filename, model.bytes, model.sha256, open, progress)

    /** Also exercised with tiny known bytes in isolated installer tests, not fake ONNX weights. */
    internal suspend fun install(filename: String, bytes: Long, sha256: String, open: () -> InputStream, progress: suspend (Int) -> Unit = {}) = withContext(Dispatchers.IO) {
        require(filename.matches(Regex("[a-zA-Z0-9._-]+")) && filename !in setOf(".", "..") && bytes > 0)
        lock.withLock {
            if (!directory.isDirectory && !directory.mkdirs()) throw UiTextException(UiText.of(R.string.photo_model_directory_failed))
            val staging = File.createTempFile("import-", ".part", directory)
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var count = 0L
                var percent = -1
                open().use { input -> staging.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        count += n
                        if (count > bytes) throw UiTextException(UiText.of(R.string.photo_model_too_large))
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                        val next = (count * 100 / bytes).toInt()
                        if (next != percent) { percent = next; progress(percent) }
                    }
                    if (count != bytes || digest.digest().hex() != sha256) throw UiTextException(UiText.of(R.string.photo_model_checksum_failed))
                    output.fd.sync()
                } }
                currentCoroutineContext().ensureActive()
                // Same filesystem, atomic replacement: readers see either complete version.
                Os.rename(staging.absolutePath, File(directory, filename).absolutePath)
            } finally { staging.delete() }
        }
    }

    private suspend fun digest(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
        return digest.digest().hex()
    }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
    companion object { private val lock = Mutex() }
}

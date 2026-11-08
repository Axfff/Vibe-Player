package com.iven.musicplayergo.photos

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
enum class PhotoModel(val title: String, val filename: String, val bytes: Long, val sha256: String, val source: String, val license: String) {
    MUSIC("音乐特征", "discogs-effnet-bsdynamic-1.onnx", 18027718, MoodAnalyzer.BACKBONE_SHA256,
        "https://essentia.upf.edu/models/feature-extractors/discogs-effnet/discogs-effnet-bsdynamic-1.onnx", "CC BY-NC-SA 4.0（非商业用途）"),
    MOOD("音乐氛围", "mtg_jamendo_moodtheme-discogs-effnet-1.onnx", 2739322, MoodAnalyzer.HEAD_SHA256,
        "https://essentia.upf.edu/models/classification-heads/mtg_jamendo_moodtheme/mtg_jamendo_moodtheme-discogs-effnet-1.onnx", "CC BY-NC-SA 4.0（非商业用途）"),
    PHOTO("照片内容", OnnxPhotoFeatureExtractor.FILENAME, 33305014, OnnxPhotoFeatureExtractor.SHA256,
        "https://github.com/Axfff/Vibe-Player/blob/feat/scoped-photo-recommendations/docs/development/photo-scene-model.md", "MIT（TinyCLIP；本项目转换为 ONNX）");
}

enum class PhotoModelStatus(val label: String) { MISSING("未导入"), UPDATE_REQUIRED("检测到旧版照片模型，请导入新版；音乐模型与已保存封面不受影响"), READY("已校验，可用"), INVALID("文件不完整或版本不匹配，请重新导入"), UNREADABLE("无法读取，请重新导入") }

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
                    if (model == PhotoModel.PHOTO && File(directory, "mobilenetv2-7.onnx").exists()) PhotoModelStatus.UPDATE_REQUIRED
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
            check(directory.isDirectory || directory.mkdirs()) { "无法创建模型目录，请检查可用空间。" }
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
                        check(count <= bytes) { "文件过大，与所需模型不匹配；原模型保留。" }
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                        val next = (count * 100 / bytes).toInt()
                        if (next != percent) { percent = next; progress(percent) }
                    }
                    check(count == bytes && digest.digest().hex() == sha256) {
                        "文件不完整或版本不匹配。请从模型说明中的官方来源获取对应文件；原模型保留。"
                    }
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

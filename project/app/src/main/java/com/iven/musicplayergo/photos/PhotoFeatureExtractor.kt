package com.iven.musicplayergo.photos

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.*
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import java.security.MessageDigest

interface PhotoFeatureExtractor : Closeable { suspend fun extract(photo: ScopedPhoto): PhotoFeatures }

/** Official ONNX Model Zoo MobileNet v2-7; no weights or downloads are bundled. */
class OnnxPhotoFeatureExtractor(context: Context, model: File) : PhotoFeatureExtractor {
    private val decoder = SafePhotoDecoder(context)
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    init {
        check(model.isFile) { "图片模型缺失；请按开发文档配置 MobileNet v2-7，或手动选择封面。" }
        check(model.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(16384)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            digest.digest().hex() == SHA256
        }) { "图片模型校验失败" }
        session = OrtSession.SessionOptions().use { options -> options.setIntraOpNumThreads(1); environment.createSession(model.absolutePath, options) }
    }
    override suspend fun extract(photo: ScopedPhoto): PhotoFeatures = withContext(Dispatchers.IO) {
        ensureActive()
        val bytes = decoder.bytes(Uri.parse(photo.uri))
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
        val key = "${photo.id}:$fingerprint:$VERSION"
        synchronized(cache) { cache[key] }?.let { return@withContext it.copy(photo = photo) }
        val bitmap = decoder.decode(bytes, 256)
        try {
            val edge = minOf(bitmap.width, bitmap.height)
            val cropped = Bitmap.createBitmap(bitmap, (bitmap.width-edge)/2, (bitmap.height-edge)/2, edge, edge)
            val scaled = Bitmap.createScaledBitmap(cropped, 224, 224, true)
            if (cropped !== bitmap && cropped !== scaled) cropped.recycle()
            val input = FloatArray(3 * 224 * 224)
            try {
                val pixels = IntArray(224 * 224); scaled.getPixels(pixels, 0, 224, 0, 0, 224, 224)
                val means = floatArrayOf(.485f, .456f, .406f); val stds = floatArrayOf(.229f, .224f, .225f)
                for (c in 0..2) for (i in pixels.indices) input[c*pixels.size+i] = (((pixels[i] shr (16-c*8)) and 255)/255f-means[c])/stds[c]
            } finally { if (scaled !== bitmap) scaled.recycle() }
            ensureActive()
            val labels = OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), longArrayOf(1,3,224,224)).use { tensor ->
                session.run(mapOf(session.inputNames.single() to tensor)).use { result ->
                    @Suppress("UNCHECKED_CAST") val logits = (result[0].value as Array<FloatArray>).single()
                    val probabilities = PhotoRanker.softmax(logits)
                    // Verified ImageNet synset indices; unsupported concepts are intentionally absent.
                    LABELS.entries.map { (name, indices) -> PhotoLabel(name, indices.sumOf { probabilities[it].toDouble() }.toFloat()) }
                }
            }
            ensureActive()
            PhotoFeatures(photo, fingerprint, VERSION, labels).also { synchronized(cache) {
                cache[key] = it
                while (cache.size > 256) cache.remove(cache.keys.first())
            } }
        } finally { bitmap.recycle() }
    }
    override fun close() = session.close()
    companion object {
        const val VERSION = "mobilenetv2-7-c1c51358-center224-v1"
        const val SHA256 = "c1c513582d56afceff8516c73804e484c81c6a830712ab6d682253f4a3cd042f"
        val LABELS = linkedMapOf("alp" to listOf(970), "cliff" to listOf(972), "lakeside" to listOf(975),
            "seashore" to listOf(978), "valley" to listOf(979), "volcano" to listOf(980), "daisy" to listOf(985),
            "butterfly" to (321..326).toList(), "canoe" to listOf(472), "volleyball" to listOf(890))
        private val cache = linkedMapOf<String, PhotoFeatures>()
        fun clearCache() = synchronized(cache) { cache.clear() }
    }
}
private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }

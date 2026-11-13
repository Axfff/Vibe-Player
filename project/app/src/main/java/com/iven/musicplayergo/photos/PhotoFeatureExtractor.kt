package com.iven.musicplayergo.photos

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import kotlinx.coroutines.*
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import java.security.MessageDigest

interface PhotoFeatureExtractor : Closeable { suspend fun extract(photo: ScopedPhoto): PhotoFeatures }

/** Construct, use and close in one dispatcher ownership scope, including cancellation
 * while the blocking native constructor runs or while the caller is suspended. */
internal suspend fun <T> withPhotoExtractor(create: () -> PhotoFeatureExtractor,
    block: suspend (PhotoFeatureExtractor) -> T): T = withContext(Dispatchers.IO) {
    create().use { extractor -> ensureActive(); block(extractor) }
}

/** Pinned TinyCLIP image tower + fixed scene prototypes; weights stay outside the APK. */
class OnnxPhotoFeatureExtractor(context: Context, model: File) : PhotoFeatureExtractor {
    private val decoder = SafePhotoDecoder(context)
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    init {
        check(model.isFile) { "Photo model missing" }
        check(model.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(16384)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            digest.digest().hex() == SHA256
        }) { "Photo model checksum mismatch" }
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
            val input = PhotoSceneInput.encode(bitmap)
            ensureActive()
            val cues = OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), longArrayOf(2,3,224,224)).use { tensor ->
                session.run(mapOf(session.inputNames.single() to tensor)).use { result ->
                    @Suppress("UNCHECKED_CAST") val scores = result[0].value as Array<FloatArray>
                    require(scores.size == 2 && scores.all { it.size == 44 })
                    val scenes = Array(2) { scores[it].copyOfRange(0, 40) }
                    PhotoScenePolicy.labels(scenes) to PhotoAtmospherePolicy.cues(Array(2) { scores[it].copyOfRange(40, 44) }, scenes)
                }
            }
            ensureActive()
            PhotoFeatures(photo, fingerprint, VERSION, cues.first, cues.second).also { synchronized(cache) {
                cache[key] = it
                while (cache.size > 256) cache.remove(cache.keys.first())
            } }
        } finally { bitmap.recycle() }
    }
    override fun close() = session.close()
    companion object {
        const val VERSION = "tinyclip-atmospheres-v4-c6811b6a-dual224"
        const val FILENAME = "tinyclip-atmospheres-v4.onnx"
        const val SHA256 = "c6811b6a8127a3091e2fc21e0a5b48f0234de40afa30472028b15ab215092dd9"
        private val cache = linkedMapOf<String, PhotoFeatures>()
        fun clearCache() = synchronized(cache) { cache.clear() }
    }
}

/** NCHW RGB: central square and aspect-preserving whole image, with neutral padding. */
internal object PhotoSceneInput {
    fun encode(bitmap: Bitmap): FloatArray {
        val input = FloatArray(2 * 3 * 224 * 224)
        for (view in 0..1) {
            val scaled = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
            var viewSource = bitmap
            try {
                val canvas = Canvas(scaled)
                canvas.drawColor(Color.rgb(123, 117, 104))
                val source: Rect; val destination: Rect
                if (view == 0) {
                    val edge = minOf(bitmap.width, bitmap.height)
                    val left = (bitmap.width-edge)/2; val top = (bitmap.height-edge)/2
                    // Materialize the crop before filtering, so pixels outside it cannot bleed in.
                    viewSource = Bitmap.createBitmap(bitmap,left,top,edge,edge)
                    source = Rect(0,0,edge,edge); destination = Rect(0,0,224,224)
                } else {
                    val ratio = 224f / maxOf(bitmap.width, bitmap.height)
                    val width = (bitmap.width * ratio).toInt().coerceIn(1,224)
                    val height = (bitmap.height * ratio).toInt().coerceIn(1,224)
                    val left = (224-width)/2; val top = (224-height)/2
                    source = Rect(0,0,bitmap.width,bitmap.height); destination = Rect(left,top,left+width,top+height)
                }
                canvas.drawBitmap(viewSource, source, destination, Paint(Paint.FILTER_BITMAP_FLAG))
                val pixels = IntArray(224 * 224); scaled.getPixels(pixels, 0, 224, 0, 0, 224, 224)
                val means = floatArrayOf(.48145466f,.4578275f,.40821073f)
                val stds = floatArrayOf(.26862954f,.26130258f,.27577711f)
                for (c in 0..2) for (i in pixels.indices) input[(view*3+c)*pixels.size+i] = (((pixels[i] shr (16-c*8)) and 255)/255f-means[c])/stds[c]
            } finally { scaled.recycle(); if (viewSource !== bitmap) viewSource.recycle() }
        }
        return input
    }
}
private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }

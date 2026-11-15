package com.iven.musicplayergo.analysis

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import java.security.MessageDigest

/** Paths to the two official ONNX graphs. Model files are not included in the app. */
data class MoodModelFiles(val backbone: File, val moodTheme: File)

/**
 * Analyzes at most the first 30 seconds of a locally accessible track. No audio or
 * inference data leaves the device. The caller owns model provisioning and must
 * handle an unavailable result by allowing a manual choice.
 */
class MoodAnalyzer(context: Context, private val models: MoodModelFiles) {
    private val appContext = context.applicationContext

    suspend fun analyze(audioUri: Uri): MoodResult = withContext(Dispatchers.IO) {
        if (!models.backbone.isFile || !models.moodTheme.isFile) {
            return@withContext MoodResult.Unavailable(MoodUnavailableReason.MISSING_MODEL)
        }
        val validModels = try {
            sha256(models.backbone) == BACKBONE_SHA256 && sha256(models.moodTheme) == HEAD_SHA256
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        if (!validModels) return@withContext MoodResult.Unavailable(MoodUnavailableReason.INVALID_MODEL)

        val excerpt = try {
            AndroidAudioDecoder(appContext).decode(audioUri)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return@withContext MoodResult.Unavailable(MoodUnavailableReason.AUDIO_UNAVAILABLE)
        }
        classifyPcm(excerpt.samples)?.let { return@withContext MoodResult.Unavailable(it) }
        try {
            val environment = OrtEnvironment.getEnvironment()
            OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(1)
                environment.createSession(models.backbone.absolutePath, options).use { backbone ->
                    environment.createSession(models.moodTheme.absolutePath, options).use { head ->
                        val result = analyzePcm(excerpt.samples) { patch -> scorePatch(environment, backbone, head, patch) }
                        if (result is MoodResult.Available) result.copy(reachedDurationLimit = excerpt.reachedDurationLimit) else result
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: LinkageError) {
            MoodResult.Unavailable(MoodUnavailableReason.INFERENCE_FAILED)
        } catch (_: Exception) {
            MoodResult.Unavailable(MoodUnavailableReason.INFERENCE_FAILED)
        }
    }

    private fun scorePatch(
        environment: OrtEnvironment,
        backbone: OrtSession,
        head: OrtSession,
        patch: FloatArray
    ): FloatArray {
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(patch), longArrayOf(1, 128, 96)).use { input ->
            backbone.run(mapOf("melspectrogram" to input), setOf("embeddings")).use { result ->
                @Suppress("UNCHECKED_CAST")
                val embeddings = result[0].value as Array<FloatArray>
                check(embeddings.size == 1 && embeddings[0].size == 1280) { "Unexpected embedding shape" }
                OnnxTensor.createTensor(environment, embeddings).use { embedding ->
                    head.run(mapOf("embeddings" to embedding), setOf("activations")).use { output ->
                        @Suppress("UNCHECKED_CAST")
                        val scores = output[0].value as Array<FloatArray>
                        check(scores.size == 1) { "Unexpected score shape" }
                        return scores[0].copyOf()
                    }
                }
            }
        }
    }

    private suspend fun sha256(file: File): String {
        val coroutine = currentCoroutineContext()
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                coroutine.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    internal companion object {
        const val BACKBONE_SHA256 = "a280825b334797cf677939db8cd5762c0392aedd0ca6415dbc1cd083f045e43c"
        const val HEAD_SHA256 = "7d6270acaa5f4bba4b115a0d6849aca05ed6bd153dcb6d9da4f6ab9f99ef10ff"
    }
}

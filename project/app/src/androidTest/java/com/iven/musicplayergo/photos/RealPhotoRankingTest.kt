package com.iven.musicplayergo.photos

import android.content.ContentValues
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iven.musicplayergo.analysis.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Optional real inference; reads only neutral case inputs and media, never evaluation labels. */
@RunWith(AndroidJUnit4::class)
class RealPhotoRankingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val assets = instrumentation.context.assets
    @Test fun fourFrozenCasesUseRealAudioAndImageInference() = runBlocking {
        assumeTrue("Supply optional neutral photo-test fixtures and licensed model files", assets.list("")!!.contains("photo-cases"))
        check(android.os.Build.HARDWARE in setOf("ranchu","goldfish")) { "Fixture model replacement is only allowed on a disposable emulator" }
        val directory = File(context.noBackupFilesDir, "photo-models").apply { mkdirs() }
        for (name in PhotoModel.values().map { it.filename }) {
            assets.open("photo-models/$name").use { input -> File(directory, name).outputStream().use { input.copyTo(it) } }
        }
        val results = JSONArray()
        // Separate neutral pools can be replayed without changing labels, model or ranking rules.
        val pool = InstrumentationRegistry.getArguments().getString("photoPool", "photo-cases")
        require(pool.matches(Regex("photo-[a-z-]+")))
        for (index in 1..4) {
            OnnxPhotoFeatureExtractor.clearCache()
            val caseId = "case_0$index"
            val data = JSONObject(assets.open("$pool/$caseId.json").bufferedReader().use { it.readText() })
            val audio = data.getJSONObject("audio")
            val audioFile = File(context.cacheDir, "photo-ranking-audio.mp3")
            assets.open("$pool/" + audio.getString("path")).use { input -> audioFile.outputStream().use { input.copyTo(it) } }
            val started = SystemClock.elapsedRealtime()
            val mood = MoodAnalyzer(context, MoodModelFiles(File(directory,"discogs-effnet-bsdynamic-1.onnx"),
                File(directory,"mtg_jamendo_moodtheme-discogs-effnet-1.onnx"))).analyze(Uri.fromFile(audioFile))
            assertTrue("Real audio unavailable: $mood", mood is MoodResult.Available)
            mood as MoodResult.Available
            val audioMs = SystemClock.elapsedRealtime() - started
            val photos = data.getJSONArray("photos"); val features = mutableListOf<PhotoFeatures>(); val uris = mutableListOf<Uri>()
            val failures = JSONArray(); val coldStarted = SystemClock.elapsedRealtime()
            var cachedMs = 0L
            try {
                OnnxPhotoFeatureExtractor(context, File(directory,OnnxPhotoFeatureExtractor.FILENAME)).use { extractor ->
                    for (i in 0 until photos.length()) {
                        val item=photos.getJSONObject(i); val id=item.getString("asset_id")
                        val uri = if (android.os.Build.VERSION.SDK_INT >= 29) context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME,"scoped-evaluation-$id.jpg");put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
                        })!!.also { uris += it } else {
                            // Older MediaStore requires a broad write permission the app deliberately does not request.
                            // Read the exact public bytes through a read-only provider in the test APK.
                            Uri.parse("content://${instrumentation.context.packageName}.photo-fixtures/$pool/${item.getString("path")}")
                        }
                        if (android.os.Build.VERSION.SDK_INT >= 29) assets.open("$pool/"+item.getString("path")).use { input -> context.contentResolver.openOutputStream(uri)!!.use { input.copyTo(it) } }
                        try { features += extractor.extract(ScopedPhoto(id,uri.toString())) }
                        catch(e: Exception) { failures.put(JSONObject().put("photo_id",id).put("error_type",e.javaClass.simpleName)
                            .put("error_message",e.message).put("origin",e.stackTrace.firstOrNull()?.toString())) }
                    }
                    val cachedStarted=SystemClock.elapsedRealtime()
                    for (feature in features) assertEquals(feature,extractor.extract(feature.photo))
                    cachedMs=SystemClock.elapsedRealtime()-cachedStarted
                }
                assertEquals("Every valid fixture must complete inference: $failures", 0, failures.length())
                assertEquals(photos.length(), features.size)
                val ranking=PhotoRanker.rank(mood.scores,features)
                assertEquals("Candidate input order must not influence actual rankings", ranking, PhotoRanker.rank(mood.scores,features.reversed()))
                assertTrue(ranking.all { r -> features.any { it.photo.id==r.features.photo.id } })
                results.put(JSONObject().put("case_id",caseId).put("audio_id",audio.getString("asset_id"))
                    .put("photo_ids",JSONArray(ranking.map { it.features.photo.id })).put("audio_ms",audioMs)
                    .put("cold_photo_ms",SystemClock.elapsedRealtime()-coldStarted-cachedMs).put("cached_photo_ms",cachedMs)
                    .put("decoded_seconds",mood.decodedSeconds).put("analyzed_seconds",mood.analyzedSeconds)
                    .put("music_scores",JSONArray(mood.scores.map { JSONObject().put("label",it.label).put("score",it.score) }))
                    .put("image_features",JSONArray(features.map { f -> JSONObject().put("photo_id",f.photo.id).put("fingerprint",f.fingerprint)
                        .put("labels",JSONArray(f.labels.map { JSONObject().put("label",it.label).put("confidence",it.confidence) }))
                        .put("atmospheres",JSONArray(f.atmospheres.map { JSONObject().put("label",it.label).put("support",it.confidence) })) }))
                    .put("ranked",JSONArray(ranking.map { JSONObject().put("photo_id",it.features.photo.id).put("score",it.score).put("reasons",JSONArray(it.reasons)) }))
                    .put("failures",failures))
            } finally { uris.forEach { context.contentResolver.delete(it,null,null) }; audioFile.delete() }
        }
        File(context.filesDir,"photo-ranking-results.json").writeText(JSONObject().put("cases",results)
            .put("pool",pool).put("rule_version",PhotoRanker.VERSION).put("image_model",OnnxPhotoFeatureExtractor.VERSION).toString(2))
        assertEquals(4,results.length())
    }
}

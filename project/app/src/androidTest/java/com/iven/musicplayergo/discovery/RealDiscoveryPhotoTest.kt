package com.iven.musicplayergo.discovery

import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iven.musicplayergo.photos.*
import com.iven.musicplayergo.R
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in real phone inference/catalog evidence; never replaces models or clears user data. */
@RunWith(AndroidJUnit4::class)
class RealDiscoveryPhotoTest {
    @Test fun importedModelProducesEditableIntentAndRealCatalogResults() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("discoveryReal") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val files = instrumentation.context.assets.list("photo-discovery/media/images")!!.sorted()
        assertTrue("Supply public JPEG fixtures separately", files.isNotEmpty())
        val evidence = JSONArray()
        var first: PhotoIntent? = null
        var firstUri: Uri? = null
        for (name in files) {
            val uri = Uri.parse("content://${instrumentation.context.packageName}.photo-fixtures/photo-discovery/media/images/$name")
            val started = SystemClock.elapsedRealtime()
            val features = withPhotoExtractor({ OnnxPhotoFeatureExtractor(context, PhotoModelStore(context).file(PhotoModel.PHOTO)) }) {
                it.extract(ScopedPhoto("discovery-selected", uri.toString()))
            }
            val policy = DiscoveryPhotoPolicy.suggest(features)
            if (policy != null) {
                val real = DiscoveryLocalPhoto(context).analyze(uri)
                assertEquals(policy, real)
                assertTrue(real.intent.valid())
                if (first == null) { first = real.intent; firstUri = uri }
            }
            evidence.put(JSONObject().put("file", name).put("fingerprint", features.fingerprint)
                .put("model", features.modelVersion).put("elapsed_ms", SystemClock.elapsedRealtime() - started)
                .put("atmospheres", JSONArray(features.atmospheres.map { JSONObject().put("label", it.label).put("support", it.confidence) }))
                .put("suggestion", policy?.description ?: "uncertain; manual input required"))
        }
        assertNotNull("At least one real fixture must yield a usable suggestion", first)
        val photo = first!!
        val client = DiscoveryClient("http://127.0.0.1:8765")
        try {
            val status = client.status()
            assertEquals("unconfigured", status.vision.mode)
            // Explicit user edit must win over the picture suggestion.
            val query = DiscoveryQuery(MusicIntent("focused", listOf("piano"), "piano"), "water", "US", emptyList(), photo)
            val serialized = JSONObject(DiscoveryJson.query(query))
            assertEquals("focused", serialized.getJSONObject("intent").getString("targetMood"))
            assertEquals("photo", serialized.getString("inputMode"))
            val results = client.discover(status.token, query)
            assertTrue("Live catalog returned no songs: ${results.shortage}", results.tracks.isNotEmpty())
            assertTrue(results.tracks.all { it.valid() })
            assertEquals(results.tracks.size, results.tracks.map { it.ref }.distinct().size)
            val output = JSONObject().put("photos", evidence).put("query", serialized)
                .put("tracks", JSONArray(results.tracks.map { JSONObject().put("id", it.ref.key).put("title", it.title)
                    .put("artist", it.artist).put("url", it.officialUrl) }))
                .put("cloud_vision_mode", status.vision.mode).put("ui_flow_verified", false)
            File(context.filesDir, "discovery-real-evidence.json").writeText(output.toString(2))
        } finally { client.cancel() }
        ActivityScenario.launch(DiscoveryActivity::class.java).use { scenario ->
            await(scenario) { !it.model.busy }
            scenario.onActivity {
                it.findViewById<EditText>(R.id.discovery_query).setText("piano")
                it.model.selectPhoto(firstUri)
                it.model.analyzeLocal()
            }
            await(scenario) { !it.model.busy && it.model.photo != null }
            scenario.onActivity {
                assertEquals("piano", it.model.intent.text)
                assertEquals(first, it.model.photo)
                assertFalse(it.findViewById<CheckBox>(R.id.discovery_confirm).isChecked)
                it.findViewById<View>(R.id.discovery_apply_photo).performClick()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertEquals("piano", it.model.intent.text)
                assertEquals("photo_applied", it.model.message)
                it.findViewById<Spinner>(R.id.discovery_mood).setSelection(5)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertEquals("focused", it.model.intent.mood)
                it.findViewById<CheckBox>(R.id.discovery_confirm).isChecked = true
                it.findViewById<View>(R.id.discovery_search).performClick()
            }
            await(scenario) { !it.model.busy && it.model.results?.tracks?.isNotEmpty() == true }
            scenario.onActivity {
                assertTrue(it.model.results!!.tracks.all { track -> track.valid() })
                File(context.filesDir, "discovery-real-ui-evidence.json").writeText(JSONObject()
                    .put("local_photo", true).put("user_override", it.model.intent.mood)
                    .put("text_retained", it.model.intent.text).put("real_results", it.model.results!!.tracks.size)
                    .put("external_navigation_verified", false).put("cold_restart_verified", false).toString(2))
            }
        }
    }

    private fun await(scenario: ActivityScenario<DiscoveryActivity>, predicate: (DiscoveryActivity) -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 65_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            scenario.onActivity { ready = predicate(it) }
            if (ready) return
            SystemClock.sleep(50)
        }
        scenario.onActivity { fail("Real Discovery step timed out: ${it.model.message}") }
    }
}

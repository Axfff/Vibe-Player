package com.iven.musicplayergo.discovery

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iven.musicplayergo.photos.*
import com.iven.musicplayergo.R
import com.iven.musicplayergo.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Opt-in real phone inference/catalog evidence; never replaces models or clears user data. */
@RunWith(AndroidJUnit4::class)
class RealDiscoveryPhotoTest {
    private val createdPhotos = mutableListOf<Uri>()

    @Test fun importedModelProducesEditableIntentAndRealCatalogResults() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("discoveryReal") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val files = instrumentation.context.assets.list("photo-discovery/media/images")!!.sorted()
        assertTrue("Supply public JPEG fixtures separately", files.isNotEmpty())
        // Use the exported launcher like a foreground user. Some phones reject
        // ActivityScenario's internal screen launch while another app is on top.
        val launch = instrumentation.uiAutomation.executeShellCommand(
            "am start -W -n ${context.packageName}/${MainActivity::class.java.name}")
        val launchResult = ParcelFileDescriptor.AutoCloseInputStream(launch).bufferedReader().use { it.readText() }
        assertTrue("Normal app launch failed: $launchResult", launchResult.contains("Status: ok"))
        val evidence = JSONArray()
        var first: PhotoIntent? = null
        var firstUri: Uri? = null
        for (name in files) {
            val uri = fixtureUri(name)
            assertEquals("Fixture must expose its real JPEG type", "image/jpeg", context.contentResolver.getType(uri))
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
                .put("suggestion", policy?.description?.resolve(context) ?: "uncertain; manual input required"))
        }
        assertNotNull("At least one real fixture must yield a usable suggestion", first)
        val photo = first!!
        val client = DeviceDiscoveryCatalog()
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
                .put("cloud_vision_mode", status.vision.mode).put("catalog_transport", "phone_https_itunes")
                .put("ui_flow_verified", false)
            File(context.filesDir, "discovery-real-evidence.json").writeText(output.toString(2))
        } finally { client.cancel() }
        ActivityScenario.launch(DiscoveryActivity::class.java).use { scenario ->
            await(scenario) { !it.model.busy }
            scenario.onActivity {
                assertFalse("Default discovery must not require a development service", it.model.customService)
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
                    .put("catalog_transport", "phone_https_itunes").put("custom_service", it.model.customService)
                    .put("text_retained", it.model.intent.text).put("real_results", it.model.results!!.tracks.size)
                    .put("external_navigation_verified", false).put("cold_restart_verified", false).toString(2))
            }
        }
    }

    private fun fixtureUri(name: String): Uri {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val path = "photo-discovery/media/images/$name"
        if (Build.VERSION.SDK_INT < 29) {
            // Legacy devices cannot insert media without broad storage permission.
            return Uri.parse("content://${instrumentation.context.packageName}.photo-fixtures/$path")
        }
        val resolver = instrumentation.targetContext.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "discovery-test-${UUID.randomUUID()}-$name")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VibeDiscoveryTest")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }))
        // Register immediately: @After also removes a row when copying/assertions fail.
        createdPhotos += uri
        instrumentation.context.assets.open(path).use { input ->
            checkNotNull(resolver.openOutputStream(uri)).use { output -> input.copyTo(output) }
        }
        check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) == 1)
        return uri
    }

    @After fun removeOnlyPhotosCreatedByThisTest() {
        if (createdPhotos.isEmpty()) return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val failures = mutableListOf<String>()
        var deleted = 0
        for (uri in createdPhotos) {
            runCatching {
                check(context.contentResolver.delete(uri, null, null) == 1) { "Fixture row was not removed: $uri" }
                deleted++
            }.onFailure { failures += it.toString() }
        }
        File(context.filesDir, "discovery-real-cleanup.json").writeText(JSONObject()
            .put("created", createdPhotos.size).put("deleted", deleted)
            .put("errors", JSONArray(failures)).toString(2))
        assertTrue("Fixture cleanup failed: $failures", failures.isEmpty())
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

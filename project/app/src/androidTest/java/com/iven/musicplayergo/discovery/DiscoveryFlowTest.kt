package com.iven.musicplayergo.discovery

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iven.musicplayergo.R
import com.iven.musicplayergo.regression.RegressionFixture
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class DiscoveryFlowTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun prepare(server: DiscoveryFixtureServer) {
        File(context.noBackupFilesDir, "discovery-collection-v1.json").delete()
        context.getSharedPreferences("discovery-settings", 0).edit().clear().putString("endpoint", server.endpoint).putBoolean("custom_service", true).commit()
    }

    @Test fun confirmRealTransportResultsOpenSaveRestartRestoreAndExclude() {
        DiscoveryFixtureServer().use { server ->
            prepare(server)
            ActivityScenario.launch(DiscoveryActivity::class.java).use { scenario ->
                await(scenario) { !it.model.busy && it.model.status != null }
                scenario.onActivity { activity ->
                    assertFalse(activity.findViewById<View>(R.id.discovery_search).isEnabled)
                    activity.findViewById<EditText>(R.id.discovery_query).setText("piano")
                    activity.findViewById<CheckBox>(R.id.discovery_confirm).isChecked = true
                    activity.findViewById<View>(R.id.discovery_search).performClick()
                    activity.findViewById<View>(R.id.discovery_search).performClick()
                    assertTrue(activity.model.busy)
                }
                await(scenario) { !it.model.busy && it.model.results?.tracks?.size == 2 }
                assertEquals(1, server.requests.count { it.path == "/v1/discover" })
                val opened = AtomicReference<Intent>()
                val monitor = object : Instrumentation.ActivityMonitor() {
                    override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                        if (intent.action == Intent.ACTION_VIEW) { opened.set(intent); return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null) }
                        return null
                    }
                }
                instrumentation.addMonitor(monitor)
                try {
                    scenario.onActivity { activity ->
                        val rows = activity.findViewById<LinearLayout>(R.id.discovery_results)
                        assertEquals(2, rows.childCount)
                        rows.getChildAt(0).findViewById<View>(R.id.discovery_track_open).performClick()
                        rows.getChildAt(0).findViewById<View>(R.id.discovery_track_save).performClick()
                        assertEquals(1, activity.model.collection.saved.size)
                        assertEquals("piano", activity.model.collection.saved.single().intent.text)
                        assertFalse(activity.findViewById<LinearLayout>(R.id.discovery_results).getChildAt(0).findViewById<View>(R.id.discovery_track_save).isEnabled)
                        activity.findViewById<LinearLayout>(R.id.discovery_results).getChildAt(1).findViewById<View>(R.id.discovery_track_exclude).performClick()
                        assertEquals(listOf(DiscoveryRef("itunes", "456")), activity.model.collection.excluded)
                    }
                    assertEquals("https://music.apple.com/us/album/fixture/99?i=123", opened.get().dataString)
                    assertTrue(opened.get().hasCategory(Intent.CATEGORY_BROWSABLE))
                    assertNull(opened.get().getPackage()) // Browser can handle it without a music app.
                } finally { instrumentation.removeMonitor(monitor) }
                scenario.recreate()
                await(scenario) { !it.model.busy }
                scenario.onActivity { assertEquals(1, it.model.collection.saved.size) }
            }
            // A new Activity creates a new ViewModel/repository reading the actual persisted file.
            ActivityScenario.launch(DiscoveryActivity::class.java).use { scenario ->
                await(scenario) { !it.model.busy }
                scenario.onActivity { activity ->
                    activity.findViewById<View>(R.id.discovery_collection).performClick()
                    val rows = activity.findViewById<LinearLayout>(R.id.discovery_results)
                    assertEquals(1, rows.childCount)
                    assertTrue(rows.getChildAt(0).findViewById<TextView>(R.id.discovery_track_title).text.contains("123"))
                    rows.getChildAt(0).findViewById<View>(R.id.discovery_track_save).performClick()
                    assertTrue(activity.model.collection.saved.isEmpty())
                    activity.findViewById<View>(R.id.discovery_clear_exclusions).performClick()
                    assertTrue(activity.model.collection.excluded.isEmpty())
                    activity.findViewById<View>(R.id.discovery_collection).performClick()
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.discovery_form).visibility)
                    activity.openOfficial(DiscoveryTrack(DiscoveryRef("itunes", "123"), "Bad", "Bad", "https://evil.test"))
                    assertEquals("unsafe_link", activity.model.message)
                }
            }
        }
    }

    @Test fun cancellationRejectsLateResultAndFailureDoesNotFillWithOldOrMockSongs() {
        DiscoveryFixtureServer().use { server ->
            prepare(server)
            server.handler = { request -> if (request.path.endsWith("status")) DiscoveryFixtureServer.Response(DiscoveryFixtureServer.status()) else DiscoveryFixtureServer.Response(DiscoveryFixtureServer.results(), delay = 900) }
            ActivityScenario.launch(DiscoveryActivity::class.java).use { scenario ->
                await(scenario) { !it.model.busy }
                scenario.onActivity { it.model.discover() }
                awaitRequests(server, "/v1/discover")
                scenario.onActivity { activity ->
                    activity.findViewById<View>(R.id.discovery_cancel).performClick()
                    assertFalse(activity.model.busy)
                    assertEquals("cancelled", activity.model.message)
                }
                server.handler = { request -> DiscoveryFixtureServer.Response(if (request.path.endsWith("status")) DiscoveryFixtureServer.status() else DiscoveryFixtureServer.results(JSONArray().put(DiscoveryFixtureServer.row("999")))) }
                scenario.onActivity { it.model.discover() }
                await(scenario) { !it.model.busy && it.model.results?.tracks?.singleOrNull()?.ref?.id == "999" }
                SystemClock.sleep(1100)
                scenario.onActivity { assertEquals(listOf("999"), it.model.results!!.tracks.map { track -> track.ref.id }) }
                for (code in listOf(429, 500)) {
                    server.handler = { request -> if (request.path.endsWith("status")) DiscoveryFixtureServer.Response(DiscoveryFixtureServer.status()) else DiscoveryFixtureServer.Response("failure", code) }
                    scenario.onActivity { it.model.discover() }
                    await(scenario) { !it.model.busy && it.model.message == "http_$code" }
                    scenario.onActivity { assertNull(it.model.results) }
                }
                server.handler = { request -> DiscoveryFixtureServer.Response(if (request.path.endsWith("status")) DiscoveryFixtureServer.status() else DiscoveryFixtureServer.results(JSONArray())) }
                scenario.onActivity { it.model.discover() }
                await(scenario) { !it.model.busy && it.model.message == "no_results" }
                scenario.onActivity { activity ->
                    activity.model.endpoint = "http://untrusted.example"
                    activity.model.connect()
                    assertEquals("invalid_endpoint", activity.model.message)
                    activity.model.intent = MusicIntent(text = "https://example.org")
                    activity.model.discover()
                    assertEquals("invalid_input", activity.model.message)
                }
            }
        }
    }

    @Test fun pickerCancelRefuseAndUnconfiguredModelNeverUpload() {
        DiscoveryFixtureServer().use { server ->
            prepare(server)
            ActivityScenario.launch(DiscoveryActivity::class.java).use { scenario ->
                await(scenario) { !it.model.busy }
                val monitor = object : Instrumentation.ActivityMonitor() {
                    override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? =
                        if (intent.action in listOf("android.provider.action.PICK_IMAGES", Intent.ACTION_OPEN_DOCUMENT)) Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null) else null
                }
                instrumentation.addMonitor(monitor)
                try { scenario.onActivity { it.findViewById<View>(R.id.discovery_pick_photo).performClick() } }
                finally { instrumentation.removeMonitor(monitor) }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    assertNull(activity.model.photoUri)
                    activity.model.selectPhoto(Uri.parse("content://fixture/never-read"))
                    activity.findViewById<View>(R.id.discovery_analyze_photo).performClick()
                    assertEquals("manual_model_blocked", activity.model.message)
                    activity.findViewById<View>(R.id.discovery_manual).performClick()
                    assertNull(activity.model.photoUri)
                }
                assertTrue(server.requests.none { it.path.contains("photo") })
            }
        }
    }

    @Test fun consentedFixturePhotoRequiresConfirmationAndUserOverrideWins() {
        DiscoveryFixtureServer().use { server ->
            prepare(server)
            server.handler = { request -> DiscoveryFixtureServer.Response(when {
                request.path.endsWith("status") -> DiscoveryFixtureServer.status("live")
                request.path.contains("photo") -> DiscoveryFixtureServer.photo()
                else -> DiscoveryFixtureServer.results()
            }) }
            val file = File(context.cacheDir, "discovery-consent-test.png")
            Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888).apply {
                file.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }; recycle()
            }
            try {
                ActivityScenario.launch(DiscoveryActivity::class.java).use { scenario ->
                    await(scenario) { !it.model.busy && it.model.status?.canAnalyze == true }
                    scenario.onActivity { activity ->
                        activity.model.selectPhoto(Uri.fromFile(file))
                        activity.findViewById<View>(R.id.discovery_analyze_photo).performClick()
                        dialog().findViewById<View>(android.R.id.button2).performClick()
                    }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        assertNull(activity.model.photoUri)
                        assertTrue(server.requests.none { it.path.contains("photo") })
                        activity.findViewById<EditText>(R.id.discovery_query).setText("jazz")
                        activity.model.selectPhoto(Uri.fromFile(file))
                        activity.findViewById<View>(R.id.discovery_analyze_photo).performClick()
                        dialog().findViewById<View>(android.R.id.button1).performClick()
                    }
                    await(scenario) { !it.model.busy && it.model.photo != null }
                    scenario.onActivity { activity ->
                        assertEquals("jazz", activity.model.intent.text)
                        assertEquals("calm", activity.model.intent.mood)
                        activity.findViewById<View>(R.id.discovery_apply_photo).performClick()
                    }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        assertEquals("dreamy", activity.model.intent.mood)
                        activity.findViewById<Spinner>(R.id.discovery_mood).setSelection(2)
                        activity.findViewById<EditText>(R.id.discovery_query).setText("dance")
                    }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        assertFalse(activity.findViewById<CheckBox>(R.id.discovery_confirm).isChecked)
                        activity.findViewById<CheckBox>(R.id.discovery_confirm).isChecked = true
                        activity.findViewById<View>(R.id.discovery_search).performClick()
                    }
                    await(scenario) { !it.model.busy && it.model.results != null }
                    val query = JSONObject(server.requests.last { it.path == "/v1/discover" }.body)
                    assertEquals("energetic", query.getJSONObject("intent").getString("targetMood"))
                    assertEquals("dance", query.getJSONObject("intent").getString("text"))
                    assertEquals("photo", query.getString("inputMode"))
                    assertEquals("true", server.requests.single { it.path.contains("photo") }.headers["x-cloud-consent"])
                }
            } finally { file.delete() }
        }
    }

    @Test fun discoveryEntryAndSavedScreenDoNotChangeLocalPlayback() {
        DiscoveryFixtureServer().use { server ->
            prepare(server)
            RegressionFixture.open().use { fixture ->
                fixture.onActivity { activity ->
                    activity.onSongSelected(fixture.songs.first(), fixture.songs, com.iven.musicplayergo.GoConstants.ARTIST_VIEW)
                }
                fixture.await("local playback starts") { fixture.onMain { fixture.holder.isPlaying } }
                val original = fixture.onMain { fixture.player }
                fixture.onActivity { activity -> activity.findViewById<View>(R.id.library_discovery).performClick() }
                instrumentation.waitForIdleSync()
                assertTrue(fixture.onMain { fixture.holder.isPlaying })
                assertSame(original, fixture.onMain { fixture.player })
                instrumentation.runOnMainSync {
                    androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                        .filterIsInstance<DiscoveryActivity>().forEach { activity ->
                            activity.findViewById<View>(R.id.discovery_collection).performClick()
                            assertTrue(activity.model.collection.saved.isEmpty())
                            activity.finish()
                        }
                }
            }
        }
    }

    @Test fun corruptCollectionIsVisibleAndPreservedWhileManualDiscoveryStillWorks() {
        DiscoveryFixtureServer().use { server ->
            prepare(server)
            val file = File(context.noBackupFilesDir, "discovery-collection-v1.json")
            file.writeText("broken collection fixture")
            ActivityScenario.launch(DiscoveryActivity::class.java).use { scenario ->
                await(scenario) { !it.model.busy }
                scenario.onActivity { activity ->
                    assertEquals("collection_read_failed", activity.model.collectionError)
                    activity.findViewById<View>(R.id.discovery_collection).performClick()
                    assertEquals(activity.getString(R.string.discovery_storage_error), activity.findViewById<TextView>(R.id.discovery_message).text)
                    activity.findViewById<View>(R.id.discovery_collection).performClick()
                    activity.model.discover()
                }
                await(scenario) { !it.model.busy && it.model.results != null }
                scenario.onActivity { activity ->
                    assertFalse(activity.findViewById<LinearLayout>(R.id.discovery_results).getChildAt(0).findViewById<View>(R.id.discovery_track_save).isEnabled)
                    activity.model.save(activity.model.results!!.tracks.first())
                    assertEquals("collection_unreadable", activity.model.message)
                    assertEquals("broken collection fixture", file.readText())
                }
            }
            file.delete()
        }
    }

    @Test fun localPhotoFailureNeverUploadsOrChangesIntentAndCancellationRemainsManual() {
        DiscoveryFixtureServer().use { server ->
            prepare(server)
            ActivityScenario.launch(DiscoveryActivity::class.java).use { scenario ->
                await(scenario) { !it.model.busy }
                scenario.onActivity { activity ->
                    activity.model.intent = MusicIntent("energetic", listOf("rock"), "my search")
                    activity.model.analyzeLocal() // no selected image
                    assertFalse(activity.model.busy)
                    activity.model.selectPhoto(Uri.parse("content://unreadable/discovery"))
                    activity.findViewById<View>(R.id.discovery_analyze_local).performClick()
                    dialog().findViewById<View>(android.R.id.button2).performClick()
                    assertNull(activity.model.photo)
                    activity.findViewById<View>(R.id.discovery_analyze_local).performClick()
                    dialog().findViewById<View>(android.R.id.button1).performClick()
                }
                await(scenario) { !it.model.busy }
                scenario.onActivity { activity ->
                    assertTrue(activity.model.message in setOf("local_photo_model_missing", "local_photo_failed"))
                    assertEquals("my search", activity.model.intent.text)
                    assertNull(activity.model.photo)
                    activity.model.analyzeLocal()
                    activity.model.analyzeLocal() // duplicate tap does not start another job
                    activity.model.useManual()
                    assertNull(activity.model.photoUri)
                    assertNull(activity.model.localPhotoDescription)
                    assertFalse(activity.model.busy)
                }
                assertTrue(server.requests.none { it.path.contains("photo") })
            }
        }
    }

    private fun dialog(): View = android.view.inspector.WindowInspector.getGlobalWindowViews()
        .last { it.findViewById<View>(android.R.id.button1) != null }

    private fun await(scenario: ActivityScenario<DiscoveryActivity>, check: (DiscoveryActivity) -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            scenario.onActivity { ready = check(it) }
            if (ready) return
            SystemClock.sleep(40)
        }
        scenario.onActivity { fail("Discovery condition timed out: ${it.model.message}") }
    }
    private fun awaitRequests(server: DiscoveryFixtureServer, path: String) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (server.requests.none { it.path == path } && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(20)
        assertTrue(server.requests.any { it.path == path })
    }
}

package com.iven.musicplayergo.discovery

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iven.musicplayergo.R
import com.iven.musicplayergo.utils.Theming
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Disposable-emulator fixtures. Fresh ViewModel + framework Bundle is not a physical process kill. */
@RunWith(AndroidJUnit4::class)
class DiscoveryRecoveryTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val collectionFile get() = File(context.noBackupFilesDir, "discovery-collection-v1.json")

    private fun prepare(server: DiscoveryFixtureServer): ByteArray {
        // A pre-existing legacy entry has no optional tags, and must not be rewritten on restore/failure.
        val track = DiscoveryTrack(DiscoveryRef("itunes", "999"), "Kept song", "Kept artist", "https://music.apple.com/us/song/999")
        collectionFile.writeText(DiscoveryJson.encode(DiscoveryCollection(
            listOf(SavedDiscovery(track, MusicIntent(text = "original intent"), "JP")), listOf(DiscoveryRef("itunes", "998")))))
        context.getSharedPreferences("discovery-settings", Context.MODE_PRIVATE).edit().clear()
            .putString("endpoint", server.endpoint).putBoolean("custom_service", true).commit()
        return collectionFile.readBytes()
    }

    private fun compactAndScroll(scenario: ActivityScenario<DiscoveryActivity>) {
        scenario.onActivity { activity ->
            val density = activity.resources.displayMetrics.density
            activity.window.setLayout((360 * density).toInt(), (640 * density).toInt())
            activity.findViewById<EditText>(R.id.discovery_query).setText("piano")
            if (!activity.findViewById<View>(R.id.discovery_options).isShown) activity.findViewById<View>(R.id.discovery_options_toggle).performClick()
        }
        instrumentation.waitForIdleSync()
        scenario.onActivity { it.findViewById<ScrollView>(R.id.discovery_scroll).fullScroll(View.FOCUS_DOWN) }
        await(scenario) { it.findViewById<ScrollView>(R.id.discovery_scroll).let { scroll -> scroll.scrollY > 0 && !scroll.canScrollVertically(1) } }
        instrumentation.waitForIdleSync()
        scenario.onActivity {
            // Resizing can deliver spinner selection callbacks, which correctly invalidate consent.
            // Confirm only after the input/layout callbacks settle, as a user would.
            it.findViewById<CheckBox>(R.id.discovery_confirm).isChecked = true
            assertTrue(it.findViewById<View>(R.id.discovery_search).isEnabled)
            fullyVisible(it.findViewById(R.id.discovery_search))
            it.findViewById<View>(R.id.discovery_search).performClick()
        }
    }

    private fun fullyVisible(view: View) {
        val rect = Rect()
        assertTrue("Missing viewport intersection for ${view.id}", view.getGlobalVisibleRect(rect))
        assertEquals("Clipped height for ${view.id}", view.height, rect.height())
        assertEquals("Clipped width for ${view.id}", view.width, rect.width())
        assertTrue(view.width > 0 && view.height > 0)
    }

    @Test fun scrolledBusyCancelAndRetryKeepFeedbackInTheViewport() {
        DiscoveryFixtureServer().use { server ->
            val original = prepare(server)
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            server.handler = { request ->
                if (request.path.endsWith("status")) DiscoveryFixtureServer.Response(DiscoveryFixtureServer.status())
                else { entered.countDown(); release.await(15, TimeUnit.SECONDS); DiscoveryFixtureServer.Response(DiscoveryFixtureServer.results()) }
            }
            try {
                ActivityScenario.launch(DiscoveryActivity::class.java).use { scenario ->
                    await(scenario) { !it.model.busy }
                    compactAndScroll(scenario)
                    assertTrue(entered.await(5, TimeUnit.SECONDS))
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        assertTrue(activity.model.busy)
                        assertTrue(activity.findViewById<ScrollView>(R.id.discovery_scroll).scrollY > 0)
                        fullyVisible(activity.findViewById(R.id.discovery_cancel))
                        fullyVisible(activity.findViewById(R.id.discovery_message))
                        assertEquals(activity.getString(R.string.discovery_loading), activity.findViewById<TextView>(R.id.discovery_message).text)
                    }
                    screenshot("busy-at-options")
                    scenario.onActivity { it.findViewById<View>(R.id.discovery_cancel).performClick() }
                    await(scenario) { !it.model.busy && it.model.message == "cancelled" }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        fullyVisible(activity.findViewById(R.id.discovery_message))
                        assertEquals(activity.getString(R.string.discovery_cancelled), activity.findViewById<TextView>(R.id.discovery_message).text)
                        assertFalse(activity.findViewById<View>(R.id.discovery_cancel).isShown)
                        assertTrue(activity.findViewById<View>(R.id.discovery_search).isEnabled)
                        assertNull(activity.model.results)
                    }
                    screenshot("cancelled-at-options")
                    release.countDown()
                    server.handler = { request -> DiscoveryFixtureServer.Response(if (request.path.endsWith("status")) DiscoveryFixtureServer.status() else eightResults()) }
                    scenario.onActivity { it.findViewById<View>(R.id.discovery_search).performClick() }
                    await(scenario) { !it.model.busy && it.model.results?.tracks?.size == 8 }
                    scenario.onActivity {
                        assertFalse(it.findViewById<View>(R.id.discovery_feedback).isShown)
                        assertEquals(8, it.findViewById<LinearLayout>(R.id.discovery_results).childCount)
                    }
                }
                assertArrayEquals(original, collectionFile.readBytes())
            } finally { release.countDown() }
        }
    }

    @Test fun scrolledHttpFailureAndRealDeadlineTimeoutKeepFeedbackAndData() {
        DiscoveryFixtureServer().use { server ->
            val original = prepare(server)
            ActivityScenario.launch(DiscoveryActivity::class.java).use { scenario ->
                await(scenario) { !it.model.busy }
                for (timeout in listOf(false, true)) {
                    server.handler = { request ->
                        if (request.path.endsWith("status")) DiscoveryFixtureServer.Response(DiscoveryFixtureServer.status())
                        else if (timeout) DiscoveryFixtureServer.Response(eightResults(), delay = 60_000)
                        else DiscoveryFixtureServer.Response("unavailable fixture", 503)
                    }
                    compactAndScroll(scenario)
                    await(scenario, if (timeout) 55_000 else 10_000) { !it.model.busy && it.model.message == if (timeout) "timeout" else "http_503" }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        assertTrue(activity.findViewById<ScrollView>(R.id.discovery_scroll).scrollY > 0)
                        fullyVisible(activity.findViewById(R.id.discovery_message))
                        fullyVisible(activity.findViewById(R.id.discovery_search))
                        assertTrue(activity.findViewById<View>(R.id.discovery_search).isEnabled)
                        assertEquals(activity.getString(if (timeout) R.string.discovery_network_error else R.string.discovery_retry_later),
                            activity.findViewById<TextView>(R.id.discovery_message).text)
                        assertNull(activity.model.results)
                        assertEquals(0, activity.findViewById<LinearLayout>(R.id.discovery_results).childCount)
                    }
                    screenshot(if (timeout) "timeout-at-options" else "error-at-options")
                    assertArrayEquals(original, collectionFile.readBytes())
                }
            }
        }
    }

    @Test fun freshViewModelUsesRestoredBundleButReturnsToFormWithoutTransientResults() {
        DiscoveryFixtureServer().use { server ->
            val original = prepare(server)
            server.handler = { request -> DiscoveryFixtureServer.Response(if (request.path.endsWith("status")) DiscoveryFixtureServer.status() else eightResults()) }
            ActivityScenario.launch(DiscoveryActivity::class.java).use { scenario ->
                await(scenario) { !it.model.busy }
                compactAndScroll(scenario)
                await(scenario) { !it.model.busy && it.model.results != null }
                lateinit var oldModel: DiscoveryViewModel
                scenario.onActivity {
                    oldModel = it.model
                    assertFalse(it.findViewById<View>(R.id.discovery_form).isShown)
                }
                scenario.recreate()
                scenario.onActivity {
                    assertSame(oldModel, it.model) // control: ordinary rotation retains actual results
                    assertEquals(8, it.model.results!!.tracks.size)
                    val saved = Bundle()
                    instrumentation.callActivityOnSaveInstanceState(it, saved)
                    assertFalse(saved.getBoolean("editing"))
                    it.viewModelStore.clear() // fresh ViewModel on recreation; do not reuse the old results
                }
                scenario.recreate()
                await(scenario) { !it.model.busy }
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    assertNotSame(oldModel, it.model)
                    assertNull(it.model.results)
                    assertTrue(it.findViewById<View>(R.id.discovery_form).isShown)
                    assertTrue(it.findViewById<View>(R.id.discovery_search_actions).isShown)
                    assertEquals(it.getString(R.string.discovery_heading), it.findViewById<TextView>(R.id.discovery_heading).text)
                    assertEquals("piano", it.findViewById<EditText>(R.id.discovery_query).text.toString())
                    assertEquals("piano", it.model.intent.text)
                    assertEquals("original intent", it.model.collection.saved.single().intent.text)
                    assertEquals("JP", it.model.collection.saved.single().market)
                    assertEquals(listOf(DiscoveryRef("itunes", "998")), it.model.collection.excluded)
                }
                screenshot("fresh-model-form")
                assertArrayEquals(original, collectionFile.readBytes())
                assertEquals(1, server.requests.count { it.path == "/v1/discover" })
            }
        }
    }

    @Test fun restoredCollectionRemainsUsableAndBackReturnsToTheForm() {
        DiscoveryFixtureServer().use { server ->
            val original = prepare(server)
            ActivityScenario.launch(DiscoveryActivity::class.java).use { scenario ->
                await(scenario) { !it.model.busy }
                scenario.onActivity { it.model.discover() }
                await(scenario) { !it.model.busy && it.model.results != null }
                lateinit var oldModel: DiscoveryViewModel
                scenario.onActivity {
                    it.findViewById<View>(R.id.discovery_collection).performClick()
                    oldModel = it.model
                    it.viewModelStore.clear()
                }
                scenario.recreate()
                await(scenario) { !it.model.busy }
                scenario.onActivity {
                    assertNotSame(oldModel, it.model)
                    assertEquals(it.getString(R.string.discovery_collection_heading), it.findViewById<TextView>(R.id.discovery_heading).text)
                    assertEquals(1, it.findViewById<LinearLayout>(R.id.discovery_results).childCount)
                    assertEquals("999", it.model.collection.saved.single().track.ref.id)
                    it.onBackPressedDispatcher.onBackPressed()
                    assertTrue(it.findViewById<View>(R.id.discovery_form).isShown)
                    assertFalse(it.findViewById<View>(R.id.discovery_results).isShown)
                }
                assertArrayEquals(original, collectionFile.readBytes())
            }
        }
    }

    @Test fun aRealEmptyResponseKeepsItsExplanationAcrossNormalRecreation() {
        DiscoveryFixtureServer().use { server ->
            prepare(server)
            server.handler = { request -> DiscoveryFixtureServer.Response(if (request.path.endsWith("status")) DiscoveryFixtureServer.status() else DiscoveryFixtureServer.results(JSONArray())) }
            ActivityScenario.launch(DiscoveryActivity::class.java).use { scenario ->
                await(scenario) { !it.model.busy }
                scenario.onActivity { it.model.discover() }
                await(scenario) { !it.model.busy && it.model.message == "no_results" }
                scenario.recreate()
                scenario.onActivity {
                    assertNotNull(it.model.results)
                    assertFalse(it.findViewById<View>(R.id.discovery_form).isShown)
                    assertEquals(it.getString(R.string.discovery_results_heading), it.findViewById<TextView>(R.id.discovery_heading).text)
                    assertTrue(it.findViewById<TextView>(R.id.discovery_message).text.contains(it.getString(R.string.discovery_no_results)))
                    it.findViewById<View>(R.id.discovery_edit).performClick()
                    assertTrue(it.findViewById<View>(R.id.discovery_form).isShown)
                }
            }
        }
    }

    @Test fun compactLargeTextFeedbackKeepsMessageAndActionsUnclipped() {
        ActivityScenario.launch(DiscoveryActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for (night in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
                    val config = Configuration(activity.resources.configuration).apply { fontScale = 1.3f; uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night }
                    val large = ContextThemeWrapper(activity.createConfigurationContext(config), Theming.resolveTheme(activity))
                    val root = LayoutInflater.from(large).inflate(R.layout.activity_discovery, null)
                    root.findViewById<View>(R.id.discovery_feedback).visibility = View.VISIBLE
                    val message = root.findViewById<TextView>(R.id.discovery_message).apply { visibility = View.VISIBLE }
                    val cancel = root.findViewById<TextView>(R.id.discovery_cancel)
                    for (busy in listOf(true, false)) {
                        message.setText(if (busy) R.string.discovery_loading else R.string.discovery_network_error)
                        cancel.visibility = if (busy) View.VISIBLE else View.GONE
                        val width = (360 * large.resources.displayMetrics.density).toInt()
                        val height = (560 * large.resources.displayMetrics.density).toInt()
                        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                        root.layout(0, 0, width, height)
                        assertTrue(root.findViewById<View>(R.id.discovery_scroll).height > 0)
                        for (text in listOf(message, root.findViewById<TextView>(R.id.discovery_search)) + if (busy) listOf(cancel) else emptyList()) {
                            val rect = Rect(0, 0, text.width, text.height)
                            (root as android.view.ViewGroup).offsetDescendantRectToMyCoords(text, rect)
                            assertTrue(rect.left >= 0 && rect.top >= 0 && rect.right <= width && rect.bottom <= height)
                            assertTrue(text.layout.height <= text.height - text.compoundPaddingTop - text.compoundPaddingBottom)
                            assertEquals(0, text.layout.getEllipsisCount(text.layout.lineCount - 1))
                        }
                    }
                }
            }
        }
    }

    private fun eightResults() = DiscoveryFixtureServer.results(JSONArray().apply { (101..108).forEach { put(DiscoveryFixtureServer.row(it.toString())) } })
    private fun screenshot(name: String) {
        if (InstrumentationRegistry.getArguments().getString("discoveryFeedbackScreenshots") != "true") return
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { File(context.getExternalFilesDir(null), "pr69-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
    private fun await(scenario: ActivityScenario<DiscoveryActivity>, millis: Long = 10_000, check: (DiscoveryActivity) -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + millis
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            scenario.onActivity { ready = check(it) }
            if (ready) return
            SystemClock.sleep(40)
        }
        scenario.onActivity { fail("Discovery recovery timed out: ${it.model.message}") }
    }
}

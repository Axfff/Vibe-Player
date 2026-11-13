package com.iven.musicplayergo.discovery

import android.os.SystemClock
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iven.musicplayergo.R
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Synthetic transport and disposable app storage; not a live-catalog/model claim. */
@RunWith(AndroidJUnit4::class)
class DiscoveryPresentationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun prepare(endpoint: String? = null) {
        File(context.noBackupFilesDir, "discovery-collection-v1.json").delete()
        context.getSharedPreferences("discovery-settings", 0).edit().clear()
            .putString("endpoint", endpoint).putBoolean("custom_service", endpoint != null).commit()
    }

    @Test fun firstUseHidesAdvancedCopyButRetainsExplicitConfirmationAndEdits() {
        prepare()
        ActivityScenario.launch(DiscoveryActivity::class.java).use { scenario ->
            await(scenario) { !it.model.busy }
            scenario.onActivity { activity ->
                assertFalse(activity.findViewById<View>(R.id.discovery_options).isShown)
                assertFalse(activity.findViewById<View>(R.id.discovery_provider_status).isShown)
                assertFalse(activity.findViewById<View>(R.id.discovery_manual).isShown)
                assertFalse(activity.findViewById<View>(R.id.discovery_photo_status).isShown)
                assertFalse(activity.findViewById<View>(R.id.discovery_message).isShown)
                assertTrue(activity.findViewById<View>(R.id.discovery_search_actions).isShown)
                assertFalse(activity.findViewById<View>(R.id.discovery_search).isEnabled)
                activity.findViewById<View>(R.id.discovery_options_toggle).performClick()
                assertEquals(activity.getString(R.string.discovery_expanded),
                    ViewCompat.getStateDescription(activity.findViewById(R.id.discovery_options_toggle)))
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.discovery_options).visibility)
                activity.findViewById<CheckBox>(R.id.discovery_confirm).isChecked = true
                assertTrue(activity.findViewById<View>(R.id.discovery_search).isEnabled)
                activity.findViewById<EditText>(R.id.discovery_query).setText("jazz")
                assertFalse(activity.findViewById<CheckBox>(R.id.discovery_confirm).isChecked)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals("jazz", activity.findViewById<EditText>(R.id.discovery_query).text.toString())
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.discovery_form).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.discovery_options).visibility)
                activity.findViewById<View>(R.id.discovery_options_toggle).performClick()
                assertEquals(View.GONE, activity.findViewById<View>(R.id.discovery_options).visibility)
            }
        }
    }

    @Test fun compactResultsPreserveDetailsSaveCollectionAndEditingAcrossRecreation() {
        DiscoveryFixtureServer().use { server ->
            prepare(server.endpoint)
            server.handler = { request -> DiscoveryFixtureServer.Response(if (request.path.endsWith("status"))
                DiscoveryFixtureServer.status() else DiscoveryFixtureServer.results(JSONArray().apply {
                    (1..8).forEach { put(DiscoveryFixtureServer.row((100 + it).toString())) }
                })) }
            ActivityScenario.launch(DiscoveryActivity::class.java).use { scenario ->
                await(scenario) { !it.model.busy }
                scenario.onActivity { activity ->
                    activity.findViewById<EditText>(R.id.discovery_query).setText("piano")
                    activity.findViewById<CheckBox>(R.id.discovery_confirm).isChecked = true
                    activity.findViewById<View>(R.id.discovery_search).performClick()
                }
                await(scenario) { !it.model.busy && it.model.results?.tracks?.size == 8 }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    assertEquals(View.GONE, activity.findViewById<View>(R.id.discovery_form).visibility)
                    assertEquals(View.GONE, activity.findViewById<View>(R.id.discovery_search_actions).visibility)
                    assertEquals(View.GONE, activity.findViewById<View>(R.id.discovery_message).visibility)
                    val row = rows(activity).getChildAt(0)
                    assertEquals("Apple Music · 101", row.findViewById<TextView>(R.id.discovery_track_identity).text.toString())
                    assertFalse(row.findViewById<View>(R.id.discovery_track_source).isShown)
                    assertFalse(row.findViewById<View>(R.id.discovery_track_reason).isShown)
                    assertTrue(row.height < 340 * activity.resources.displayMetrics.density)
                    assertTrue(row.findViewById<View>(R.id.discovery_track_open).contentDescription.contains("Fixture piano 101"))
                    row.findViewById<View>(R.id.discovery_track_details).performClick()
                    assertEquals(View.VISIBLE, row.findViewById<View>(R.id.discovery_track_detail_content).visibility)
                    assertTrue(row.findViewById<TextView>(R.id.discovery_track_source).text.contains("?i=101"))
                    row.findViewById<View>(R.id.discovery_track_save).performClick()
                    assertFalse(rows(activity).getChildAt(0).findViewById<View>(R.id.discovery_track_save).isEnabled)
                    assertEquals(View.VISIBLE, rows(activity).getChildAt(0).findViewById<View>(R.id.discovery_track_detail_content).visibility)
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertEquals(View.GONE, activity.findViewById<View>(R.id.discovery_form).visibility)
                    assertEquals(View.VISIBLE, rows(activity).getChildAt(0).findViewById<View>(R.id.discovery_track_detail_content).visibility)
                    activity.findViewById<View>(R.id.discovery_collection).performClick()
                    assertEquals(1, rows(activity).childCount)
                    assertEquals(activity.getString(R.string.discovery_collection_heading), activity.findViewById<TextView>(R.id.discovery_heading).text)
                    activity.onBackPressedDispatcher.onBackPressed()
                    assertEquals(8, rows(activity).childCount)
                    activity.findViewById<View>(R.id.discovery_edit).performClick()
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.discovery_form).visibility)
                    assertEquals("piano", activity.findViewById<EditText>(R.id.discovery_query).text.toString())
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.discovery_form).visibility)
                    assertEquals("piano", activity.model.intent.text)
                    activity.findViewById<View>(R.id.discovery_about).performClick()
                    val dialog = android.view.inspector.WindowInspector.getGlobalWindowViews()
                        .last { it.findViewById<View>(android.R.id.button1) != null }
                    assertTrue(dialog.findViewById<TextView>(android.R.id.message).text.contains("subscription"))
                    dialog.findViewById<View>(android.R.id.button1).performClick()
                }
                assertEquals(1, server.requests.count { it.path == "/v1/discover" })
            }
        }
    }

    @Test fun emptyCollectionShortagesAndFailuresRemainVisibleWithAnEditRoute() {
        DiscoveryFixtureServer().use { server ->
            prepare(server.endpoint)
            ActivityScenario.launch(DiscoveryActivity::class.java).use { scenario ->
                await(scenario) { !it.model.busy }
                scenario.onActivity { activity ->
                    activity.findViewById<View>(R.id.discovery_collection).performClick()
                    assertEquals(activity.getString(R.string.discovery_collection_empty), activity.findViewById<TextView>(R.id.discovery_message).text)
                    assertTrue(activity.findViewById<View>(R.id.discovery_message).isShown)
                    activity.findViewById<View>(R.id.discovery_collection).performClick()
                    activity.model.discover()
                }
                await(scenario) { !it.model.busy && it.model.results != null }
                scenario.onActivity { activity ->
                    assertTrue(activity.findViewById<TextView>(R.id.discovery_message).text.contains("Only 2"))
                    assertTrue(activity.findViewById<View>(R.id.discovery_message).isShown)
                    val row = rows(activity).getChildAt(0)
                    row.findViewById<View>(R.id.discovery_track_details).performClick()
                    row.findViewById<View>(R.id.discovery_track_details).performClick()
                    assertEquals(View.GONE, row.findViewById<View>(R.id.discovery_track_detail_content).visibility)
                    activity.onBackPressedDispatcher.onBackPressed()
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.discovery_form).visibility)
                }
                server.handler = { request -> if (request.path.endsWith("status")) DiscoveryFixtureServer.Response(DiscoveryFixtureServer.status())
                    else DiscoveryFixtureServer.Response("busy", 503) }
                scenario.onActivity { it.model.discover() }
                await(scenario) { !it.model.busy && it.model.message == "http_503" }
                scenario.onActivity { activity ->
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.discovery_message).visibility)
                    assertEquals(activity.getString(R.string.discovery_retry_later), activity.findViewById<TextView>(R.id.discovery_message).text.toString())
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.discovery_form).visibility)
                    assertEquals(0, rows(activity).childCount)
                }
            }
        }
    }

    private fun rows(activity: DiscoveryActivity) = activity.findViewById<LinearLayout>(R.id.discovery_results)
    private fun await(scenario: ActivityScenario<DiscoveryActivity>, check: (DiscoveryActivity) -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            scenario.onActivity { ready = check(it) }
            if (ready) return
            SystemClock.sleep(40)
        }
        scenario.onActivity { fail("Discovery presentation timed out: " + it.model.message) }
    }
}

package com.iven.musicplayergo.raptor

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iven.musicplayergo.GoConstants
import com.iven.musicplayergo.R
import com.iven.musicplayergo.analysis.RaptorTrackAnalyzer
import com.iven.musicplayergo.extensions.toContentUri
import com.iven.musicplayergo.regression.RegressionFixture
import com.iven.musicplayergo.ui.MainActivity
import com.iven.musicplayergo.utils.Popups
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RaptorGameLaunchTest {
    private data class DemoTrack(val resource: Int, val bpm: Int, val beats: Int, val theme: String)

    @Test
    fun packagedMusicDecodesIntoTheExpectedTempoAndSceneWindows() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val analyzer = RaptorTrackAnalyzer(context)
        val tracks = listOf(
            DemoTrack(R.raw.raptor_afterglow, 86, 32, "drift"),
            DemoTrack(R.raw.raptor_signal_hop, 113, 48, "groove"),
            DemoTrack(R.raw.raptor_meteor_fm, 145, 64, "surge"),
        )
        for (track in tracks) {
            val uri = Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(context.packageName)
                .appendPath(track.resource.toString())
                .build()
            val analysis = analyzer.analyze(uri)
            assertEquals(track.bpm, analysis.bpm)
            assertEquals(track.beats, analysis.beats.size)
            assertTrue(analysis.durationSeconds in 20.0..30.0)
            assertTrue(analysis.scenes.size >= 3)
            assertTrue(analysis.scenes.all { it.theme == track.theme })
        }
    }

    @Test
    fun libraryLauncherOpensTheGameWithoutChoosingLocalAudio() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertLaunch(RaptorRadioActivity.intent(context)) {
                scenario.onActivity { activity ->
                    assertTrue(activity.findViewById<View>(R.id.library_raptor_radio).performClick())
                }
            }
        }
    }

    @Test
    fun songMenuLaunchesTheGameWithTheSelectedTrack() {
        RegressionFixture.open().use { fixture ->
            val song = fixture.tracks.getValue("A")
            val songUri = checkNotNull(song.id).toContentUri()
            val expectedTitle = song.title ?: song.displayName
                ?: fixture.context.getString(R.string.raptor_analysis_unknown_track)
            val songGame = RaptorRadioActivity.intent(fixture.context, songUri, expectedTitle)
            assertLaunch(songGame) {
                fixture.onActivity { activity ->
                    val anchor = activity.findViewById<View>(android.R.id.content)
                    val popup = Popups.createSongPopup(activity, anchor, song, GoConstants.ARTIST_VIEW)
                    popup.show()
                    try {
                        assertTrue(popup.menu.findItem(R.id.raptor_play_song).isVisible)
                        assertTrue(popup.menu.performIdentifierAction(R.id.raptor_play_song, 0))
                    } finally {
                        popup.dismiss()
                    }
                }
            }
        }
    }

    private fun assertLaunch(expected: Intent, launch: () -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(RaptorRadioActivity::class.java.name, null, false)
        var game: Activity? = null
        try {
            launch()
            game = checkNotNull(monitor.waitForActivityWithTimeout(5_000)) { "Raptor Radio did not launch" }
            assertTrue(expected.filterEquals(game.intent))
            val extras = expected.extras
            assertEquals(extras?.keySet(), game.intent.extras?.keySet())
            extras?.keySet()?.forEach { key ->
                assertEquals(expected.getStringExtra(key), game.intent.getStringExtra(key))
            }
        } finally {
            game?.let { instrumentation.runOnMainSync { it.finish() } }
            instrumentation.removeMonitor(monitor)
            instrumentation.waitForIdleSync()
        }
    }
}

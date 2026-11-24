package com.iven.musicplayergo.regression

import android.os.SystemClock
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.ListView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iven.musicplayergo.R
import com.iven.musicplayergo.dialogs.NowPlaying
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackSpeedMenuTest {
    @Test
    fun adjacentFastSpeedsApplyAndPersistTheirDisplayedValues() {
        RegressionFixture.open().use { fixture ->
            fixture.onMain { fixture.preferences.playbackSpeedMode = "1" }
            fixture.play("A", "B")
            fixture.expectPlaying("A")

            // The activity's controls share a 500 ms click debounce.
            SystemClock.sleep(550)
            fixture.onActivity {
                assertTrue(it.findViewById<View>(R.id.playing_song_container).performClick())
            }
            fixture.await("Now Playing sheet") { fixture.onMain { nowPlaying(fixture)?.view != null } }

            listOf(R.string.speed_8 to 2.25F, R.string.speed_9 to 2.5F).forEach { (label, speed) ->
                selectSpeed(fixture, fixture.context.getString(label))
                fixture.onMain {
                    assertEquals("Remember the displayed speed", speed, fixture.preferences.latestPlaybackSpeed, 0F)
                    assertEquals("Apply the displayed speed to the real player", speed, fixture.player.playbackParams.speed, 0F)
                    assertTrue("Playback continues", fixture.player.isPlaying)
                }
            }
        }
    }

    private fun selectSpeed(fixture: RegressionFixture, title: String) {
        fixture.onMain {
            val button = nowPlaying(fixture)!!.requireView().findViewById<View>(R.id.np_playback_speed)
            assertTrue(button.isShown)
            assertTrue(button.performClick())
        }
        lateinit var list: ListView
        var position = -1
        fixture.await("Playback speed popup") {
            fixture.onMain {
                val match = WindowInspector.getGlobalWindowViews().flatMap(::descendants)
                    .filterIsInstance<ListView>().firstOrNull { candidate ->
                        (0 until candidate.count).any { (candidate.adapter.getItem(it) as? MenuItem)?.title?.toString() == title }
                    } ?: return@onMain false
                list = match
                position = (0 until list.count).first { (list.adapter.getItem(it) as? MenuItem)?.title?.toString() == title }
                list.setSelection(position)
                true
            }
        }
        fixture.await("Speed row $title is laid out") {
            fixture.onMain { list.getChildAt(position - list.firstVisiblePosition)?.isShown == true }
        }
        fixture.onMain {
            val row = list.getChildAt(position - list.firstVisiblePosition)
            assertTrue(list.performItemClick(row, position, list.adapter.getItemId(position)))
        }
        fixture.await("Playback speed popup dismissed") { fixture.onMain { !list.isAttachedToWindow } }
    }

    private fun nowPlaying(fixture: RegressionFixture): NowPlaying? {
        var sheet: NowPlaying? = null
        fixture.onActivity { sheet = it.supportFragmentManager.findFragmentByTag(NowPlaying.TAG_MODAL) as? NowPlaying }
        return sheet
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}

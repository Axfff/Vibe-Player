package com.iven.musicplayergo.raptor

import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iven.musicplayergo.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RaptorRadioActivityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun thePackagedSoundtracksProduceDifferentTemposAndScenes() {
        launchGame().use { scenario ->
            expectTrack(scenario, R.id.raptor_game_afterglow, 86, R.string.raptor_analysis_drift)

            scenario.onActivity { it.click(R.id.raptor_game_stage) }
            await(scenario, "the soundtrack started with the run") { it.score() >= 5 }
            scenario.onActivity { it.click(R.id.raptor_game_signal_hop) }
            expectTrack(scenario, R.id.raptor_game_signal_hop, 113, R.string.raptor_analysis_groove)

            scenario.onActivity { it.click(R.id.raptor_game_meteor_fm) }
            expectTrack(scenario, R.id.raptor_game_meteor_fm, 145, R.string.raptor_analysis_surge)
        }
    }

    @Test
    fun aRunSupportsJumpPauseBackgroundCollisionAndRetry() {
        launchGame().use { scenario ->
            expectTrack(scenario, R.id.raptor_game_afterglow, 86, R.string.raptor_analysis_drift)
            var jumped = false
            var clearedFirstObstacle = false
            scenario.onActivity { activity ->
                // Score 30 places the first crystal inside the clearance window. A UI watcher
                // also pauses immediately after clearance, ahead of the next obstacle.
                activity.findViewById<TextView>(R.id.raptor_game_score).doAfterTextChanged {
                    val score = numberIn(it)
                    if (!jumped && score >= 30) {
                        val jump = activity.findViewById<View>(R.id.raptor_game_jump)
                        jumped = jump.isEnabled && jump.performClick()
                    }
                    if (jumped && !clearedFirstObstacle && score >= 38) {
                        val pause = activity.findViewById<View>(R.id.raptor_game_pause)
                        clearedFirstObstacle = pause.isEnabled && pause.performClick()
                    }
                }
                activity.startOrResumeRun()
            }

            await(scenario, "the first obstacle was cleared and the run was paused") {
                clearedFirstObstacle && it.readyToClick(R.id.raptor_game_primary) &&
                    it.label(R.id.raptor_game_primary) == it.getString(R.string.raptor_game_resume)
            }
            val pausedScore = readScore(scenario)
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            expectPrimary(scenario, R.string.raptor_game_resume)
            assertEquals("A paused run survives background and foreground transitions", pausedScore, readScore(scenario))
            scenario.onActivity { activity ->
                val initialLabel = activity.label(R.id.raptor_game_mute)
                activity.click(R.id.raptor_game_mute)
                assertNotEquals(initialLabel, activity.label(R.id.raptor_game_mute))
                activity.click(R.id.raptor_game_mute)
                assertEquals(initialLabel, activity.label(R.id.raptor_game_mute))
                activity.startOrResumeRun()
                assertEquals(pausedScore, activity.score())
            }

            expectPrimary(scenario, R.string.raptor_game_retry)
            scenario.onActivity { activity ->
                val crashScore = activity.score()
                assertTrue("The run reached the next obstacle", crashScore > pausedScore)
                assertTrue("The best score records the run", numberIn(activity.label(R.id.raptor_game_best)) >= crashScore)
                activity.startOrResumeRun()
                assertEquals("Retry resets the displayed score", 0, activity.score())
            }
        }
    }

    @Test
    fun unavailableLocalAudioCanBeReplacedWithABundledTrack() {
        val missing = Uri.parse("content://com.iven.musicplayergo.missing-fixture/audio.wav")
        launchGame(missing).use { scenario ->
            await(scenario, "the missing track error") { activity ->
                activity.label(R.id.raptor_game_status) == activity.getString(R.string.raptor_game_load_error) &&
                    !activity.readyToClick(R.id.raptor_game_primary)
            }

            scenario.onActivity { it.click(R.id.raptor_game_afterglow) }
            expectTrack(scenario, R.id.raptor_game_afterglow, 86, R.string.raptor_analysis_drift)
        }
    }

    private fun launchGame(uri: Uri? = null): ActivityScenario<RaptorRadioActivity> =
        ActivityScenario.launch(RaptorRadioActivity.intent(context, uri, uri?.let { "Unavailable audio" }))

    private fun expectTrack(
        scenario: ActivityScenario<RaptorRadioActivity>,
        button: Int,
        bpm: Int,
        scene: Int,
    ) {
        await(scenario, "tempo $bpm and scene ${context.getString(scene)}") { activity ->
            activity.readyToClick(R.id.raptor_game_primary) &&
                activity.label(R.id.raptor_game_primary) == activity.getString(R.string.raptor_game_start) &&
                numberIn(activity.label(R.id.raptor_game_tempo)) == bpm &&
                activity.label(R.id.raptor_game_scene).contains(activity.getString(scene), ignoreCase = true) &&
                activity.findViewById<CompoundButton>(button).isChecked
        }
        assertEquals(0, readScore(scenario))
    }

    private fun expectPrimary(scenario: ActivityScenario<RaptorRadioActivity>, label: Int) {
        await(scenario, "enabled ${context.getString(label)} control") { activity ->
            activity.readyToClick(R.id.raptor_game_primary) &&
                activity.label(R.id.raptor_game_primary) == activity.getString(label)
        }
    }

    private fun readScore(scenario: ActivityScenario<RaptorRadioActivity>): Int {
        var score = -1
        scenario.onActivity { score = it.score() }
        return score
    }

    private fun await(
        scenario: ActivityScenario<RaptorRadioActivity>,
        description: String,
        predicate: (RaptorRadioActivity) -> Boolean,
    ) {
        val end = SystemClock.elapsedRealtime() + 12_000
        do {
            var found = false
            scenario.onActivity { found = predicate(it) }
            if (found) return
            SystemClock.sleep(25)
        } while (SystemClock.elapsedRealtime() < end)
        var state = ""
        scenario.onActivity { activity ->
            state = "Primary=${activity.label(R.id.raptor_game_primary)}, " +
                "score=${activity.score()}, tempo=${activity.label(R.id.raptor_game_tempo)}, " +
                "scene=${activity.label(R.id.raptor_game_scene)}, status=${activity.label(R.id.raptor_game_status)}"
        }
        throw AssertionError("Timed out waiting for $description. $state")
    }

    private fun RaptorRadioActivity.score(): Int = numberIn(label(R.id.raptor_game_score))

    private fun RaptorRadioActivity.label(id: Int): String =
        findViewById<TextView>(id).text.toString()

    private fun RaptorRadioActivity.readyToClick(id: Int): Boolean =
        findViewById<View>(id).let { it.isShown && it.isEnabled }

    private fun RaptorRadioActivity.startOrResumeRun() {
        click(R.id.raptor_game_primary)
        assertTrue("Pause is enabled while running", readyToClick(R.id.raptor_game_pause))
        assertTrue("Jump is enabled while running", readyToClick(R.id.raptor_game_jump))
    }

    private fun RaptorRadioActivity.click(id: Int) {
        assertTrue("Expected an enabled ${resources.getResourceEntryName(id)} control", readyToClick(id))
        assertTrue("Expected ${resources.getResourceEntryName(id)} to respond", findViewById<View>(id).performClick())
    }

    private fun numberIn(text: CharSequence?): Int =
        text?.let { Regex("\\p{Nd}+").find(it)?.value?.toIntOrNull() } ?: -1
}

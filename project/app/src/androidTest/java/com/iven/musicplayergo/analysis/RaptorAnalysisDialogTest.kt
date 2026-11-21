package com.iven.musicplayergo.analysis

import android.content.DialogInterface
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iven.musicplayergo.GoConstants
import com.iven.musicplayergo.R
import com.iven.musicplayergo.models.Music
import com.iven.musicplayergo.regression.RegressionFixture
import com.iven.musicplayergo.ui.MainActivity
import com.iven.musicplayergo.utils.Popups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RaptorAnalysisDialogTest {
    @Test
    fun songPopupShowsAnalysisAndRecoversWhenTheTrackIsMissing() {
        RegressionFixture.open().use { fixture ->
            lateinit var activity: MainActivity
            fixture.onActivity { activity = it }
            val song = fixture.tracks.getValue("A")
            openSongAnalysis(fixture, song)
            val noBeat = fixture.context.getString(R.string.raptor_analysis_no_beat)
            fixture.await("scene analysis from MediaStore audio") {
                fixture.onMain {
                    val dialog = activity.analysisDialog() ?: return@onMain false
                    dialog.text(R.id.raptor_analysis_summary).contains(noBeat) &&
                        dialog.text(R.id.raptor_analysis_scenes).lines().size == 4
                }
            }
            fixture.onActivity {
                val result = checkNotNull(it.analysisDialog())
                assertEquals(song.title, result.text(R.id.raptor_analysis_track))
                assertEquals(View.GONE, result.findViewById<View>(R.id.raptor_analysis_progress)?.visibility)
                result.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
            }
            fixture.await("analysis dialog closed") { fixture.onMain { activity.analysisDialog() == null } }

            fixture.deleteAudio("A")
            openSongAnalysis(fixture, song)
            fixture.await("missing track error") {
                fixture.onMain {
                    activity.analysisDialog()?.text(R.id.raptor_analysis_summary) ==
                        fixture.context.getString(R.string.raptor_analysis_error)
                }
            }
            fixture.onActivity {
                val result = checkNotNull(it.analysisDialog())
                assertEquals(View.GONE, result.findViewById<View>(R.id.raptor_analysis_scenes)?.visibility)
                assertTrue(result.getButton(DialogInterface.BUTTON_NEGATIVE).isEnabled)
            }
        }
    }

    private fun openSongAnalysis(fixture: RegressionFixture, song: Music) {
        fixture.onActivity { activity ->
            val anchor = activity.findViewById<View>(android.R.id.content)
            val popup = Popups.createSongPopup(activity, anchor, song, GoConstants.ARTIST_VIEW)
            popup.show()
            try {
                val analyze = popup.menu.findItem(R.id.analyze_music)
                assertTrue(analyze.isVisible && analyze.isEnabled)
                assertTrue(popup.menu.performIdentifierAction(R.id.analyze_music, 0))
            } finally {
                popup.dismiss()
            }
        }
    }

    private fun MainActivity.analysisDialog(): AlertDialog? =
        (supportFragmentManager.findFragmentByTag(RaptorAnalysisDialogFragment.TAG) as? RaptorAnalysisDialogFragment)
            ?.dialog as? AlertDialog

    private fun AlertDialog.text(id: Int): String =
        findViewById<TextView>(id)?.text?.toString().orEmpty()
}

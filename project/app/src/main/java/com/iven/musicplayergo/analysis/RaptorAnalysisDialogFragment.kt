package com.iven.musicplayergo.analysis

import android.app.Dialog
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.os.ConfigurationCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.iven.musicplayergo.R
import com.iven.musicplayergo.databinding.RaptorAnalysisDialogBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class RaptorAnalysisDialogFragment : DialogFragment() {
    private var binding: RaptorAnalysisDialogBinding? = null
    private var analysis: Job? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val views = RaptorAnalysisDialogBinding.inflate(layoutInflater)
        binding = views
        views.raptorAnalysisTrack.text = requireArguments().getString(TRACK_TITLE)
        views.raptorAnalysisSummary.setText(R.string.raptor_analysis_loading)
        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.raptor_analysis_title)
            .setView(views.root)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    override fun onStart() {
        super.onStart()
        val views = binding ?: return
        if (analysis != null) return
        val appContext = requireContext().applicationContext
        val uri = Uri.parse(requireNotNull(requireArguments().getString(TRACK_URI)))
        analysis = lifecycleScope.launch {
            try {
                val result = RaptorTrackAnalyzer(appContext).analyze(uri)
                render(views, result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                views.raptorAnalysisSummary.setText(R.string.raptor_analysis_error)
            }
            views.raptorAnalysisProgress.isVisible = false
            (dialog as? AlertDialog)?.getButton(DialogInterface.BUTTON_NEGATIVE)
                ?.setText(R.string.raptor_analysis_close)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        analysis?.cancel()
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        analysis?.cancel()
        analysis = null
        binding = null
        super.onDestroyView()
    }

    private fun render(views: RaptorAnalysisDialogBinding, result: RaptorTrackAnalysis) {
        val locale = ConfigurationCompat.getLocales(resources.configuration)[0] ?: Locale.getDefault()
        val seconds = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }
        val percent = NumberFormat.getPercentInstance(locale)
        val durationLabel = if (result.reachedDurationLimit) {
            R.string.raptor_analysis_excerpt
        } else {
            R.string.raptor_analysis_duration
        }
        val duration = getString(durationLabel, seconds.format(result.durationSeconds))
        val tempo = result.bpm?.let { bpm ->
            val count = result.beats.size
            resources.getQuantityString(R.plurals.raptor_analysis_tempo, count, bpm, count)
        } ?: getString(R.string.raptor_analysis_no_beat)
        views.raptorAnalysisSummary.text = "$duration\n$tempo"
        views.raptorAnalysisStyleLabel.isVisible = result.scenes.isNotEmpty()
        views.raptorAnalysisScenes.isVisible = result.scenes.isNotEmpty()
        views.raptorAnalysisScenes.text = result.scenes.joinToString("\n") { scene ->
            val label = getString(when (scene.theme) {
                "drift" -> R.string.raptor_analysis_drift
                "groove" -> R.string.raptor_analysis_groove
                "surge" -> R.string.raptor_analysis_surge
                else -> R.string.raptor_analysis_neutral
            })
            val prediction = when {
                scene.confidence <= 0.0 -> label
                scene.theme == "neutral" -> getString(R.string.raptor_analysis_low_confidence, label)
                else -> getString(R.string.raptor_analysis_confidence, label, percent.format(scene.confidence))
            }
            getString(R.string.raptor_analysis_scene, seconds.format(scene.startSeconds), prediction)
        }
    }

    companion object {
        const val TAG = "raptor-music-analysis"
        private const val TRACK_URI = "track_uri"
        private const val TRACK_TITLE = "track_title"

        fun newInstance(uri: Uri, songTitle: String) = RaptorAnalysisDialogFragment().apply {
            arguments = bundleOf(TRACK_URI to uri.toString(), TRACK_TITLE to songTitle)
        }
    }
}

package com.iven.musicplayergo.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.iven.musicplayergo.MusicViewModel
import com.iven.musicplayergo.R
import com.iven.musicplayergo.databinding.LibraryActionsBinding
import com.iven.musicplayergo.ui.MainActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/** Visible independently of the player controls and replaceable library/error page. */
class LibraryActionsFragment : Fragment() {
    private var binding: LibraryActionsBinding? = null
    private lateinit var music: MusicViewModel
    private lateinit var scanner: LibraryScanViewModel
    private var refreshScheduled = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return LibraryActionsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arrangeActionButtons()
        val owner = ViewModelProvider(requireActivity())
        music = owner[MusicViewModel::class.java]
        scanner = owner[LibraryScanViewModel::class.java]
        binding?.libraryOpenPlaylists?.setOnClickListener { (activity as? MainActivity)?.openPlaylists() }
        binding?.libraryScanMusic?.setOnClickListener { scanner.startScan() }
        binding?.libraryImportHelp?.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.library_import_help)
                .setMessage(R.string.library_import_instructions)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        scanner.state.observe(viewLifecycleOwner) { renderAndRefresh() }
        music.playlistLibraryState.observe(viewLifecycleOwner) { renderAndRefresh() }
    }

    private fun arrangeActionButtons() {
        val views = binding ?: return
        val configuration = resources.configuration
        if (configuration.screenWidthDp >= 600) {
            views.libraryFeedbackRow.orientation = LinearLayout.HORIZONTAL
            views.libraryScanStatus.layoutParams = (views.libraryScanStatus.layoutParams as LinearLayout.LayoutParams).apply {
                width = 0
                weight = 1f
            }
        }
        if (configuration.fontScale >= 1.3f && configuration.screenWidthDp < 600) {
            views.libraryActionButtons.orientation = LinearLayout.VERTICAL
            listOf(views.libraryOpenPlaylists, views.libraryScanMusic).forEach { button ->
                button.layoutParams = (button.layoutParams as LinearLayout.LayoutParams).apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    weight = 0f
                    marginStart = 0
                }
            }
        }
    }

    private fun renderAndRefresh() {
        val views = binding ?: return
        val state = scanner.state.value ?: LibraryScanState()
        val library = music.playlistLibraryState.value
        val readingLibrary = library?.isLoading == true
        // A concurrent library query must finish before the post-index read, or refresh would be dropped.
        if (!readingLibrary && state.refreshPending && !refreshScheduled) {
            refreshScheduled = true
            viewLifecycleOwner.lifecycleScope.launch {
                // A library observer can fire before its scanJob returns. Defer to avoid
                // refreshPlaylistLibrary() rejecting this read as an already-running scan.
                yield()
                refreshScheduled = false
                if (binding != null && music.playlistLibraryState.value?.isLoading != true &&
                    scanner.consumeRefreshRequest()) {
                    music.refreshPlaylistLibrary()
                }
            }
        }
        views.libraryScanMusic.isEnabled = !state.running && !state.refreshPending && !readingLibrary
        views.libraryScanProgress.isVisible = state.running || state.refreshPending ||
            (state.report != null && readingLibrary)
        val report = state.report
        val message = when {
            state.running -> getString(R.string.library_scan_running)
            state.permissionDenied -> getString(R.string.library_scan_permission)
            state.failed -> getString(R.string.library_scan_failed)
            state.refreshPending || (report != null && readingLibrary) -> getString(R.string.library_scan_refreshing)
            report != null && library?.refreshFailed == true -> getString(R.string.library_scan_refresh_failed)
            report?.outcome == MediaScanOutcome.TIMED_OUT -> getString(R.string.library_scan_timeout)
            report?.outcome == MediaScanOutcome.NO_CANDIDATES -> getString(R.string.library_scan_no_candidates)
            report?.outcome == MediaScanOutcome.PARTIAL -> getString(R.string.library_scan_partial, report.indexed, report.attempted)
            report != null -> getString(R.string.library_scan_complete, report.indexed)
            else -> ""
        }
        views.libraryScanStatus.text = message
        views.libraryScanStatus.isVisible = message.isNotEmpty()
    }

    override fun onDestroyView() {
        refreshScheduled = false
        binding = null
        super.onDestroyView()
    }
}

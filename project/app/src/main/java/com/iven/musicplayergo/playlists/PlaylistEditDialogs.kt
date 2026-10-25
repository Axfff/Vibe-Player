package com.iven.musicplayergo.playlists

import android.app.Dialog
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.iven.musicplayergo.MusicViewModel
import com.iven.musicplayergo.PlaylistLibraryState
import com.iven.musicplayergo.R
import com.iven.musicplayergo.databinding.PlaylistNameDialogBinding
import com.iven.musicplayergo.databinding.PlaylistSongPickerBinding
import com.iven.musicplayergo.models.Music
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** No constructor callbacks: child dialogs can be recreated by FragmentManager. */
class PlaylistNameDialogFragment : DialogFragment() {
    private var binding: PlaylistNameDialogBinding? = null
    private var saving = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = true
        val views = PlaylistNameDialogBinding.inflate(layoutInflater)
        binding = views
        val playlistId = arguments?.getString(ID)
        views.playlistNameInput.setText(savedInstanceState?.getString(INPUT) ?: arguments?.getString(NAME).orEmpty())
        val alert = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (playlistId == null) R.string.playlist_create else R.string.playlist_rename)
            .setView(views.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(if (playlistId == null) R.string.playlist_create else R.string.playlist_save, null)
            .create()
        alert.setOnShowListener {
            alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { saveName(alert, playlistId) }
            views.playlistNameInput.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    saveName(alert, playlistId)
                    true
                } else false
            }
        }
        views.playlistNameInput.doAfterTextChanged { views.root.error = null }
        return alert
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(INPUT, binding?.playlistNameInput?.text?.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun saveName(alert: AlertDialog, playlistId: String?) {
        val views = binding ?: return
        if (saving) return
        val enteredName = views.playlistNameInput.text?.toString().orEmpty()
        val repository = PlaylistRepositories.get(requireContext())
        saving = true
        isCancelable = false
        views.playlistNameInput.isEnabled = false
        alert.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        alert.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (playlistId == null) repository.create(enteredName)
                else repository.rename(playlistId, enteredName)
            }
            saving = false
            isCancelable = true
            when (result) {
                is PlaylistResult.Success -> {
                    (parentFragment as? PlaylistsDialogFragment)?.onPlaylistSaved(
                        result.value.id,
                        getString(if (playlistId == null) R.string.playlist_created else R.string.playlist_renamed)
                    )
                    dismissAfterSavedChange()
                }
                is PlaylistResult.Failure -> {
                    binding?.root?.error = requireContext().playlistErrorMessage(result.error)
                    binding?.playlistNameInput?.isEnabled = true
                    alert.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    alert.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = true
                }
            }
        }
    }

    companion object {
        private const val ID = "playlist_id"
        private const val NAME = "playlist_name"
        private const val INPUT = "playlist_name_input"
        fun newInstance(id: String?, name: String?) = PlaylistNameDialogFragment().apply {
            arguments = bundleOf(ID to id, NAME to name)
        }
    }
}

class PlaylistDeleteDialogFragment : DialogFragment() {
    private var saving = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = true
        val id = requireArguments().getString(ID).orEmpty()
        val name = requireArguments().getString(NAME).orEmpty()
        val message = getString(R.string.playlist_delete_message, name)
        val alert = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.playlist_delete_title)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.playlist_delete, null)
            .create()
        alert.setOnShowListener {
            alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (saving) return@setOnClickListener
                saving = true
                isCancelable = false
                alert.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                alert.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
                val repository = PlaylistRepositories.get(requireContext())
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) { repository.delete(id) }
                    saving = false
                    isCancelable = true
                    when (result) {
                        is PlaylistResult.Success -> {
                            (parentFragment as? PlaylistsDialogFragment)?.onPlaylistDeleted(id)
                            dismissAfterSavedChange()
                        }
                        is PlaylistResult.Failure -> {
                            alert.setMessage(requireContext().playlistErrorMessage(result.error) + "\n\n" + message)
                            alert.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                            alert.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = true
                        }
                    }
                }
            }
        }
        return alert
    }

    companion object {
        private const val ID = "playlist_id"
        private const val NAME = "playlist_name"
        fun newInstance(id: String, name: String) = PlaylistDeleteDialogFragment().apply {
            arguments = bundleOf(ID to id, NAME to name)
        }
    }
}

/** Adds one song per click, making the order of insertion explicit and reproducible. */
class PlaylistSongPickerDialogFragment : DialogFragment() {
    private var binding: PlaylistSongPickerBinding? = null
    private lateinit var musicViewModel: MusicViewModel
    private lateinit var repository: PlaylistRepository
    private lateinit var songsAdapter: PlaylistMusicAdapter
    private var libraryState = PlaylistLibraryState()
    private var visibleSongs: List<Music> = emptyList()
    private var playlist: Playlist? = null
    private var query = ""
    private var loading = false
    private var saving = false
    private var storageError: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = PlaylistRepositories.get(requireContext())
        musicViewModel = ViewModelProvider(requireActivity())[MusicViewModel::class.java]
        libraryState = musicViewModel.playlistLibraryState.value ?: PlaylistLibraryState()
        updateVisibleSongs()
        query = savedInstanceState?.getString(QUERY).orEmpty()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = true
        val views = PlaylistSongPickerBinding.inflate(layoutInflater)
        binding = views
        songsAdapter = PlaylistMusicAdapter(::addSong)
        views.playlistPickerRows.adapter = songsAdapter
        views.playlistSongSearch.setText(query)
        views.playlistSongSearch.doAfterTextChanged {
            query = it?.toString().orEmpty()
            render()
        }
        val alert = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.playlist_add_song)
            .setView(views.root)
            .setNegativeButton(R.string.playlist_done, null)
            .setNeutralButton(R.string.playlist_refresh, null)
            .create()
        alert.setOnShowListener {
            alert.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                loadPlaylist()
                musicViewModel.refreshPlaylistLibrary()
            }
            render()
        }
        musicViewModel.playlistLibraryState.observe(this) { state ->
            libraryState = state
            updateVisibleSongs()
            render()
        }
        loadPlaylist()
        return alert
    }

    override fun onStart() {
        super.onStart()
        // Keep a bounded, scrollable song list instead of an unbounded alert-dialog list.
        binding?.root?.let { root ->
            root.layoutParams = root.layoutParams.apply {
                height = (resources.displayMetrics.heightPixels * 0.55).toInt()
            }
        }
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        if (!libraryState.hasLoaded && !libraryState.isLoading) musicViewModel.refreshPlaylistLibrary()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(QUERY, query)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        binding?.playlistPickerRows?.adapter = null
        binding = null
        super.onDestroyView()
    }

    private fun loadPlaylist() {
        if (loading || saving) return
        val id = requireArguments().getString(ID).orEmpty()
        loading = true
        storageError = null
        render()
        lifecycleScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.getPlaylist(id) }) {
                is PlaylistResult.Success -> playlist = result.value
                is PlaylistResult.Failure -> storageError = requireContext().playlistErrorMessage(result.error)
            }
            loading = false
            render()
        }
    }

    private fun addSong(music: Music) {
        val selected = playlist ?: return
        if (loading || saving || !libraryIsReady() || storageError != null) return
        saving = true
        isCancelable = false
        render()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { repository.addTrack(selected.id, music) }
            saving = false
            isCancelable = true
            when (result) {
                is PlaylistResult.Success -> {
                    playlist = result.value
                    (parentFragment as? PlaylistsDialogFragment)?.onPlaylistSaved(result.value.id)
                    showFeedback(getString(R.string.playlist_track_added,
                        requireContext().playlistTrackTitle(music.title, music.displayName)))
                }
                is PlaylistResult.Failure -> showFeedback(requireContext().playlistErrorMessage(result.error))
            }
            render()
        }
    }

    private fun libraryIsReady() = libraryState.hasLoaded && !libraryState.isLoading && !libraryState.refreshFailed

    private fun updateVisibleSongs() {
        if (!libraryIsReady()) return
        visibleSongs = musicViewModel.deviceMusicFiltered.orEmpty()
            .filter { it.id != null && it.id > 0 }
            .distinctBy { it.id }
            .sortedBy { (it.title ?: it.displayName.orEmpty()).lowercase(Locale.getDefault()) }
    }

    private fun render() {
        val views = binding ?: return
        if (!::songsAdapter.isInitialized) return
        val selected = playlist
        selected?.let { dialog?.setTitle(getString(R.string.playlist_add_to, it.name)) }
        // Visibility filters affect selection only. The parent resolves saved entries against the complete scan.
        val candidates = if (libraryIsReady()) visibleSongs else emptyList()
        val searched = candidates.filter { music ->
            query.isBlank() || listOf(music.title, music.displayName, music.artist).any { it?.contains(query, ignoreCase = true) == true }
        }
        val addedIds = selected?.tracks?.map { it.mediaId }?.toSet().orEmpty()
        songsAdapter.actionsEnabled = selected != null && !loading && !saving && libraryIsReady() && storageError == null
        songsAdapter.submitList(searched.map { music ->
            PlaylistMusicRow(music, music.id?.let { it in addedIds } == true)
        })
        views.playlistPickerProgress.isVisible = loading || saving || libraryState.isLoading
        views.playlistPickerStatus.text = when {
            storageError != null -> storageError.orEmpty()
            saving -> getString(R.string.playlist_saving)
            loading -> getString(R.string.playlist_loading)
            libraryState.isLoading -> getString(R.string.playlist_library_loading)
            libraryState.refreshFailed -> getString(R.string.playlist_library_failure)
            !libraryState.hasLoaded -> getString(R.string.playlist_library_unknown)
            candidates.isEmpty() -> getString(R.string.playlist_no_visible_songs)
            searched.isEmpty() -> getString(R.string.playlist_no_search_results)
            else -> getString(R.string.playlist_picker_hint)
        }
        (dialog as? AlertDialog)?.let { alert ->
            alert.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = !saving
            alert.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled = !loading && !saving && !libraryState.isLoading
        }
    }

    private fun showFeedback(message: String) {
        binding?.root?.let { Snackbar.make(it, message, Snackbar.LENGTH_LONG).show() }
    }

    companion object {
        private const val ID = "playlist_id"
        private const val QUERY = "song_query"
        fun newInstance(id: String) = PlaylistSongPickerDialogFragment().apply {
            arguments = bundleOf(ID to id)
        }
    }
}

/** Persistence is already complete; backgrounding during the IO must not crash dismissal. */
private fun DialogFragment.dismissAfterSavedChange() {
    if (parentFragmentManager.isStateSaved) dismissAllowingStateLoss() else dismiss()
}

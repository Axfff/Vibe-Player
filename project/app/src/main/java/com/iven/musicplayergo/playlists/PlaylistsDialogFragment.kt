package com.iven.musicplayergo.playlists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.iven.musicplayergo.MusicViewModel
import com.iven.musicplayergo.PlaylistLibraryState
import com.iven.musicplayergo.R
import com.iven.musicplayergo.databinding.PlaylistSheetBinding
import com.iven.musicplayergo.ui.MediaControlInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A restorable entry point; navigation is saved by playlist ID, not by a mutable model object. */
class PlaylistsDialogFragment : BottomSheetDialogFragment() {
    private var binding: PlaylistSheetBinding? = null
    private lateinit var musicViewModel: MusicViewModel
    private lateinit var repository: PlaylistRepository
    private lateinit var playlistAdapter: PlaylistListAdapter
    private lateinit var tracksAdapter: PlaylistTracksAdapter
    private var playlists: List<Playlist> = emptyList()
    private var selectedPlaylistId: String? = null
    private var libraryState = PlaylistLibraryState()
    private var loadingPlaylists = false
    private var mutating = false
    private var readError: String? = null
    private var readJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedPlaylistId = savedInstanceState?.getString(SELECTED_PLAYLIST)
        repository = PlaylistRepositories.get(requireContext())
        musicViewModel = ViewModelProvider(requireActivity())[MusicViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return PlaylistSheetBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = binding ?: return
        loadingPlaylists = false
        mutating = false
        playlistAdapter = PlaylistListAdapter { playlist ->
            if (!mutating) {
                selectedPlaylistId = playlist.id
                render()
            }
        }
        tracksAdapter = PlaylistTracksAdapter(::removeTrack)
        views.playlistRows.adapter = playlistAdapter
        views.playlistClose.setOnClickListener { dismiss() }
        views.playlistBack.setOnClickListener {
            selectedPlaylistId = null
            render()
        }
        views.playlistPrimary.setOnClickListener {
            if (selectedPlaylistId == null) showNameDialog(null) else playSelectedPlaylist()
        }
        views.playlistAdd.setOnClickListener {
            val playlist = currentPlaylist() ?: return@setOnClickListener
            if (childFragmentManager.findFragmentByTag(ADD_DIALOG) == null) {
                PlaylistSongPickerDialogFragment.newInstance(playlist.id)
                    .show(childFragmentManager, ADD_DIALOG)
            }
        }
        views.playlistRename.setOnClickListener { showNameDialog(currentPlaylist()) }
        views.playlistDelete.setOnClickListener {
            val playlist = currentPlaylist() ?: return@setOnClickListener
            if (childFragmentManager.findFragmentByTag(DELETE_DIALOG) == null) {
                PlaylistDeleteDialogFragment.newInstance(playlist.id, playlist.name)
                    .show(childFragmentManager, DELETE_DIALOG)
            }
        }
        views.playlistRefresh.setOnClickListener {
            reloadPlaylists()
            musicViewModel.refreshPlaylistLibrary()
        }

        val initialBottomPadding = views.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(views.root) { root, insets ->
            root.setPadding(root.paddingLeft, root.paddingTop, root.paddingRight,
                initialBottomPadding + insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
            insets
        }

        musicViewModel.playlistLibraryState.observe(viewLifecycleOwner) { state ->
            libraryState = state
            render()
        }
        reloadPlaylists()
        libraryState = musicViewModel.playlistLibraryState.value ?: PlaylistLibraryState()
        if (!libraryState.hasLoaded && !libraryState.isLoading) musicViewModel.refreshPlaylistLibrary()
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.let { sheetDialog ->
            sheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
                sheet.layoutParams = sheet.layoutParams.apply {
                    height = (resources.displayMetrics.heightPixels * 0.9).toInt()
                }
            }
            sheetDialog.behavior.skipCollapsed = true
            sheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        binding?.root?.let(ViewCompat::requestApplyInsets)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(SELECTED_PLAYLIST, selectedPlaylistId)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        readJob?.cancel()
        binding?.playlistRows?.adapter = null
        binding = null
        super.onDestroyView()
    }

    internal fun onPlaylistSaved(id: String, message: String? = null) {
        selectedPlaylistId = id
        reloadPlaylists()
        message?.let(::showFeedback)
    }

    internal fun onPlaylistDeleted(id: String) {
        if (selectedPlaylistId == id) selectedPlaylistId = null
        reloadPlaylists()
        showFeedback(getString(R.string.playlist_deleted))
    }

    private fun currentPlaylist() = playlists.firstOrNull { it.id == selectedPlaylistId }

    private fun reloadPlaylists() {
        if (binding == null) return
        readJob?.cancel()
        loadingPlaylists = true
        readError = null
        render()
        readJob = viewLifecycleOwner.lifecycleScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.getPlaylists() }) {
                is PlaylistResult.Success -> {
                    playlists = result.value
                    if (selectedPlaylistId != null && currentPlaylist() == null) selectedPlaylistId = null
                }
                is PlaylistResult.Failure -> readError = requireContext().playlistErrorMessage(result.error)
            }
            loadingPlaylists = false
            render()
        }
    }

    private fun showNameDialog(playlist: Playlist?) {
        if (childFragmentManager.findFragmentByTag(NAME_DIALOG) == null) {
            PlaylistNameDialogFragment.newInstance(playlist?.id, playlist?.name)
                .show(childFragmentManager, NAME_DIALOG)
        }
    }

    private fun removeTrack(track: PlaylistTrack) {
        val playlist = currentPlaylist() ?: return
        if (mutating || loadingPlaylists || readError != null) return
        mutating = true
        render()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { repository.removeTrack(playlist.id, track.mediaId) }
            mutating = false
            when (result) {
                is PlaylistResult.Success -> {
                    reloadPlaylists()
                    showFeedback(getString(R.string.playlist_track_removed))
                }
                is PlaylistResult.Failure -> {
                    showFeedback(requireContext().playlistErrorMessage(result.error))
                    render()
                }
            }
        }
    }

    private fun playSelectedPlaylist() {
        val playlist = currentPlaylist() ?: return
        if (!libraryIsReady() || loadingPlaylists || mutating || readError != null) return
        // Resolve against all scanned media, including tracks hidden from the add-song picker.
        val songs = repository.resolve(playlist, libraryState.songs).mapNotNull { it.music }
        if (songs.isEmpty()) {
            showFeedback(getString(R.string.playlist_no_playable_tracks))
            return
        }
        val controls = activity as? MediaControlInterface
        if (controls == null) {
            showFeedback(getString(R.string.playlist_error_playback))
            return
        }
        controls.onPlaylistSelected(songs.toList())
        dismiss()
    }

    private fun libraryIsReady() = libraryState.hasLoaded && !libraryState.isLoading && !libraryState.refreshFailed

    private fun render() {
        val views = binding ?: return
        if (!::playlistAdapter.isInitialized) return
        val selected = currentPlaylist()
        val detail = selected != null
        val storageReady = !loadingPlaylists && !mutating && readError == null
        val resolved = if (selected != null && libraryIsReady()) repository.resolve(selected, libraryState.songs) else null
        val playableCount = resolved?.count { it.isAvailable } ?: 0
        val unavailableCount = resolved?.count { !it.isAvailable } ?: 0

        views.playlistHeading.text = selected?.name ?: getString(R.string.playlist_title)
        views.playlistBack.isVisible = detail
        views.playlistBack.isEnabled = !mutating
        views.playlistSummary.text = if (selected == null) getString(R.string.playlist_collection_hint) else {
            resources.getQuantityString(R.plurals.playlist_track_count, selected.tracks.size, selected.tracks.size) +
                "\n" + getString(R.string.playlist_order_hint)
        }
        views.playlistPrimary.setText(if (detail) R.string.playlist_play else R.string.playlist_create)
        views.playlistPrimary.isEnabled = storageReady && (!detail || (libraryIsReady() && playableCount > 0))
        views.playlistAdd.isVisible = detail
        views.playlistAdd.isEnabled = storageReady && libraryIsReady()
        views.playlistRename.isVisible = detail
        views.playlistRename.isEnabled = storageReady
        views.playlistDelete.isVisible = detail
        views.playlistDelete.isEnabled = storageReady
        views.playlistRefresh.isEnabled = !loadingPlaylists && !mutating && !libraryState.isLoading
        views.playlistProgress.isVisible = loadingPlaylists || mutating || libraryState.isLoading
        tracksAdapter.actionsEnabled = storageReady

        val status = when {
            readError != null -> readError.orEmpty()
            mutating -> getString(R.string.playlist_saving)
            loadingPlaylists -> getString(R.string.playlist_loading)
            libraryState.isLoading -> getString(R.string.playlist_library_loading)
            libraryState.refreshFailed -> getString(R.string.playlist_library_failure)
            !libraryState.hasLoaded -> getString(R.string.playlist_library_unknown)
            selected != null && selected.tracks.isNotEmpty() && playableCount == 0 -> getString(R.string.playlist_no_playable_tracks)
            unavailableCount > 0 -> resources.getQuantityString(R.plurals.playlist_unavailable_count, unavailableCount, unavailableCount)
            else -> ""
        }
        views.playlistStatus.text = status
        views.playlistStatus.isVisible = status.isNotEmpty()
        val isEmpty = if (selected == null) playlists.isEmpty() else selected.tracks.isEmpty()
        views.playlistEmpty.isVisible = isEmpty && !loadingPlaylists && readError == null
        views.playlistEmpty.setText(if (detail) R.string.playlist_no_tracks else R.string.playlist_no_playlists)
        views.playlistRows.isVisible = !isEmpty

        if (selected == null) {
            if (views.playlistRows.adapter !== playlistAdapter) views.playlistRows.adapter = playlistAdapter
            playlistAdapter.submitList(playlists)
        } else {
            if (views.playlistRows.adapter !== tracksAdapter) views.playlistRows.adapter = tracksAdapter
            tracksAdapter.submitList(selected.tracks.mapIndexed { index, track ->
                PlaylistTrackRow(track, index + 1, resolved?.getOrNull(index)?.isAvailable)
            })
        }
    }

    private fun showFeedback(message: String) {
        binding?.root?.let { Snackbar.make(it, message, Snackbar.LENGTH_LONG).show() }
    }

    companion object {
        private const val SELECTED_PLAYLIST = "selected_playlist"
        private const val NAME_DIALOG = "playlist_name"
        private const val DELETE_DIALOG = "playlist_delete"
        private const val ADD_DIALOG = "playlist_add"
    }
}

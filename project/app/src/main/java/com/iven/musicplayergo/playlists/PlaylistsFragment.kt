package com.iven.musicplayergo.playlists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.iven.musicplayergo.MusicViewModel
import com.iven.musicplayergo.PlaylistLibraryState
import com.iven.musicplayergo.R
import com.iven.musicplayergo.covers.CoverCropDialogFragment
import com.iven.musicplayergo.covers.CoverRepositories
import com.iven.musicplayergo.covers.CoverResult
import com.iven.musicplayergo.covers.CoverTarget
import com.iven.musicplayergo.covers.loadCover
import com.iven.musicplayergo.databinding.PlaylistSheetBinding
import com.iven.musicplayergo.ui.MediaControlInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The persistent bottom-navigation page for managing saved playlists. */
class PlaylistsFragment : Fragment(), PlaylistEditorHost {
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        PlaylistSheetBinding.inflate(inflater, container, false).also { binding = it }.root

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
        views.playlistRows.layoutManager = LinearLayoutManager(requireContext())
        views.playlistRows.adapter = playlistAdapter
        views.playlistClose.isVisible = false
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
                PlaylistSongPickerDialogFragment.newInstance(playlist.id).show(childFragmentManager, ADD_DIALOG)
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
        views.playlistSetCover.setOnClickListener {
            val id = selectedPlaylistId ?: return@setOnClickListener
            if (childFragmentManager.findFragmentByTag(COVER_DIALOG) == null) {
                CoverCropDialogFragment.forPlaylist(id).show(childFragmentManager, COVER_DIALOG)
            }
        }
        views.playlistRemoveCover.setOnClickListener { removePlaylistCover() }
        childFragmentManager.setFragmentResultListener(CoverCropDialogFragment.RESULT_KEY, viewLifecycleOwner) { _, _ ->
            playlistAdapter.notifyDataSetChanged()
            render()
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

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(SELECTED_PLAYLIST, selectedPlaylistId)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        // ViewPager preloads its tabs, so the initial read can precede a playlist mutation.
        // Reload when this page becomes visible rather than presenting that stale snapshot.
        if (binding != null && !loadingPlaylists && !mutating) reloadPlaylists()
    }

    override fun onDestroyView() {
        readJob?.cancel()
        binding?.playlistRows?.adapter = null
        binding = null
        super.onDestroyView()
    }

    override fun onPlaylistSaved(id: String, message: String?) {
        selectedPlaylistId = id
        reloadPlaylists()
        message?.let(::showFeedback)
    }

    override fun onPlaylistDeleted(id: String) {
        if (selectedPlaylistId == id) selectedPlaylistId = null
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val target = CoverTarget.Playlist(id)
            if (CoverRepositories.get(requireContext()).hasCover(target)) {
                CoverRepositories.get(requireContext()).removeCover(target)
            }
        }
        reloadPlaylists()
        showFeedback(getString(R.string.playlist_deleted))
    }

    internal fun navigateBackToList(): Boolean {
        if (selectedPlaylistId == null) return false
        selectedPlaylistId = null
        render()
        return true
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
        views.playlistCoverActions.isVisible = detail
        if (selected != null) {
            val target = CoverTarget.Playlist(selected.id)
            val hasCover = CoverRepositories.get(requireContext()).hasCover(target)
            views.playlistCover.loadCover(target)
            views.playlistSetCover.setText(if (hasCover) R.string.cover_change else R.string.cover_set)
            views.playlistSetCover.isEnabled = storageReady
            views.playlistRemoveCover.isVisible = hasCover
            views.playlistRemoveCover.isEnabled = storageReady
        }
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

    private fun removePlaylistCover() {
        val id = selectedPlaylistId ?: return
        if (mutating) return
        mutating = true
        render()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                CoverRepositories.get(requireContext()).removeCover(CoverTarget.Playlist(id))
            }
            mutating = false
            when (result) {
                is CoverResult.Success -> {
                    playlistAdapter.notifyDataSetChanged()
                    showFeedback(getString(R.string.cover_removed))
                }
                is CoverResult.Failure -> showFeedback(getString(R.string.cover_error_save, result.error.name))
            }
            render()
        }
    }

    private companion object {
        const val SELECTED_PLAYLIST = "selected_playlist"
        const val NAME_DIALOG = "playlist_name"
        const val DELETE_DIALOG = "playlist_delete"
        const val ADD_DIALOG = "playlist_add"
        const val COVER_DIALOG = "playlist_cover"
    }
}

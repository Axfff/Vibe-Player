package com.iven.musicplayergo.playlists

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.iven.musicplayergo.R
import com.iven.musicplayergo.databinding.PlaylistItemBinding
import com.iven.musicplayergo.databinding.PlaylistTrackItemBinding
import com.iven.musicplayergo.models.Music

internal class PlaylistListAdapter(
    private val onOpen: (Playlist) -> Unit
) : ListAdapter<Playlist, PlaylistListAdapter.Holder>(object : DiffUtil.ItemCallback<Playlist>() {
    override fun areItemsTheSame(oldItem: Playlist, newItem: Playlist) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Playlist, newItem: Playlist) = oldItem == newItem
}) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        PlaylistItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: PlaylistItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onOpen(getItem(position))
            }
        }

        fun bind(playlist: Playlist) {
            binding.playlistRowTitle.text = playlist.name
            binding.playlistRowSubtitle.text = binding.root.resources.getQuantityString(
                R.plurals.playlist_track_count, playlist.tracks.size, playlist.tracks.size
            )
        }
    }
}

/** Null availability means that a successful scan has not yet established the result. */
internal data class PlaylistTrackRow(
    val saved: PlaylistTrack,
    val position: Int,
    val available: Boolean?
)

internal class PlaylistTracksAdapter(
    private val onRemove: (PlaylistTrack) -> Unit
) : ListAdapter<PlaylistTrackRow, PlaylistTracksAdapter.Holder>(object : DiffUtil.ItemCallback<PlaylistTrackRow>() {
    override fun areItemsTheSame(oldItem: PlaylistTrackRow, newItem: PlaylistTrackRow) =
        oldItem.saved.mediaId == newItem.saved.mediaId
    override fun areContentsTheSame(oldItem: PlaylistTrackRow, newItem: PlaylistTrackRow) = oldItem == newItem
}) {
    var actionsEnabled = true
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        PlaylistTrackItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: PlaylistTrackItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.playlistTrackAction.setOnClickListener {
                val position = bindingAdapterPosition
                if (actionsEnabled && position != RecyclerView.NO_POSITION) onRemove(getItem(position).saved)
            }
        }

        fun bind(row: PlaylistTrackRow) = with(binding) {
            val context = root.context
            val title = context.playlistTrackTitle(row.saved.title, row.saved.displayName)
            playlistTrackTitle.text = context.getString(R.string.playlist_position_title, row.position, title)
            playlistTrackSubtitle.text = context.playlistTrackArtist(row.saved.artist)
            playlistTrackStatus.isVisible = row.available != true
            playlistTrackStatus.setText(
                if (row.available == false) R.string.playlist_unavailable
                else R.string.playlist_availability_unknown
            )
            playlistTrackAction.setText(R.string.playlist_remove)
            playlistTrackAction.contentDescription = context.getString(R.string.playlist_remove_description, title)
            playlistTrackAction.isEnabled = actionsEnabled
        }
    }
}

internal data class PlaylistMusicRow(val music: Music, val alreadyAdded: Boolean)

internal class PlaylistMusicAdapter(
    private val onAdd: (Music) -> Unit
) : ListAdapter<PlaylistMusicRow, PlaylistMusicAdapter.Holder>(object : DiffUtil.ItemCallback<PlaylistMusicRow>() {
    override fun areItemsTheSame(oldItem: PlaylistMusicRow, newItem: PlaylistMusicRow) = oldItem.music.id == newItem.music.id
    override fun areContentsTheSame(oldItem: PlaylistMusicRow, newItem: PlaylistMusicRow) = oldItem == newItem
}) {
    var actionsEnabled = false
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        PlaylistTrackItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: PlaylistTrackItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.playlistTrackAction.setOnClickListener {
                val position = bindingAdapterPosition
                if (actionsEnabled && position != RecyclerView.NO_POSITION) onAdd(getItem(position).music)
            }
        }

        fun bind(row: PlaylistMusicRow) = with(binding) {
            val context = root.context
            val title = context.playlistTrackTitle(row.music.title, row.music.displayName)
            playlistTrackTitle.text = title
            playlistTrackSubtitle.text = context.playlistTrackArtist(row.music.artist)
            playlistTrackStatus.setText(R.string.playlist_in_playlist)
            playlistTrackStatus.isVisible = row.alreadyAdded
            playlistTrackAction.setText(R.string.playlist_add)
            playlistTrackAction.contentDescription = context.getString(R.string.playlist_add_description, title)
            playlistTrackAction.isEnabled = actionsEnabled
        }
    }
}

internal fun Context.playlistTrackTitle(title: String?, displayName: String?): String =
    title?.takeIf { it.isNotBlank() } ?: displayName?.takeIf { it.isNotBlank() }
    ?: getString(R.string.playlist_unknown_title)

internal fun Context.playlistTrackArtist(artist: String?): String =
    artist?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: getString(R.string.playlist_unknown_artist)

internal fun Context.playlistErrorMessage(error: PlaylistError): String = getString(when (error) {
    PlaylistError.EMPTY_NAME -> R.string.playlist_error_empty_name
    PlaylistError.DUPLICATE_NAME -> R.string.playlist_error_duplicate_name
    PlaylistError.PLAYLIST_NOT_FOUND -> R.string.playlist_error_not_found
    PlaylistError.DUPLICATE_TRACK -> R.string.playlist_error_duplicate_track
    PlaylistError.INVALID_TRACK -> R.string.playlist_error_invalid_track
    PlaylistError.TRACK_NOT_FOUND -> R.string.playlist_error_track_not_found
    PlaylistError.STORAGE_READ_FAILED -> R.string.playlist_error_read
    PlaylistError.STORAGE_WRITE_FAILED -> R.string.playlist_error_write
})

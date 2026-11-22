package com.iven.musicplayergo.utils

import android.app.Activity
import android.view.Gravity
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.iven.musicplayergo.GoConstants
import com.iven.musicplayergo.GoPreferences
import com.iven.musicplayergo.R
import com.iven.musicplayergo.analysis.RaptorAnalysisDialogFragment
import com.iven.musicplayergo.extensions.enablePopupIcons
import com.iven.musicplayergo.extensions.setTitle
import com.iven.musicplayergo.extensions.setTitleColor
import com.iven.musicplayergo.extensions.toContentUri
import com.iven.musicplayergo.models.Music
import com.iven.musicplayergo.player.MediaPlayerHolder
import com.iven.musicplayergo.raptor.RaptorRadioActivity
import com.iven.musicplayergo.ui.MediaControlInterface
import com.iven.musicplayergo.ui.UIControlInterface

object Popups {

    @JvmStatic
    fun showPopupForSongs(
        activity: Activity,
        itemView: View?,
        song: Music?,
        launchedBy: String
    ) {
        itemView?.let { createSongPopup(activity, it, song, launchedBy).show() }
    }

    internal fun createSongPopup(
        activity: Activity,
        anchor: View,
        song: Music?,
        launchedBy: String
    ): PopupMenu {
        val mediaControlInterface = activity as MediaControlInterface
        return PopupMenu(activity, anchor).apply {
            inflate(R.menu.popup_songs)

            val songUri = song?.id?.takeIf { it > 0 }?.toContentUri()
            val title = song?.title ?: song?.displayName
                ?: activity.getString(R.string.raptor_analysis_unknown_track)
            val fragments = (activity as? AppCompatActivity)?.supportFragmentManager
            menu.findItem(R.id.analyze_music).isVisible = songUri != null && fragments != null
            menu.findItem(R.id.raptor_play_song).isVisible = songUri != null
            menu.findItem(R.id.song_title).setTitle(activity.resources, song?.title)
            menu.enablePopupIcons(activity.resources)
            gravity = Gravity.END

            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.favorites_add -> {
                        Lists.addToFavorites(
                            activity,
                            song,
                            canRemove = false,
                            0,
                            launchedBy
                        )
                        (activity as UIControlInterface).onFavoriteAddedOrRemoved()
                    }
                    R.id.analyze_music -> {
                        if (songUri != null && fragments != null && !fragments.isStateSaved) {
                            val tag = RaptorAnalysisDialogFragment.TAG
                            if (fragments.findFragmentByTag(tag) == null) {
                                RaptorAnalysisDialogFragment.newInstance(songUri, title).show(fragments, tag)
                            }
                        }
                    }
                    R.id.raptor_play_song -> if (songUri != null) {
                        activity.startActivity(RaptorRadioActivity.intent(activity, songUri, title))
                    }
                    else -> mediaControlInterface.onAddToQueue(song)
                }
                true
            }
        }
    }

    @JvmStatic
    fun showPopupForPlaybackSpeed(activity: Activity, view: View) {

        PopupMenu(activity, view).apply {
            inflate(R.menu.popup_speed)
            gravity = Gravity.END

            if (GoPreferences.getPrefsInstance().playbackSpeedMode != GoConstants.PLAYBACK_SPEED_ONE_ONLY) {
                menu.findItem(getSelectedPlaybackItem(GoPreferences.getPrefsInstance().latestPlaybackSpeed)).setTitleColor(
                    Theming.resolveThemeColor(activity.resources)
                )
            }

            setOnMenuItemClickListener { menuItem ->
                val playbackSpeed = when (menuItem.itemId) {
                    R.id.speed_0 -> 0.25F
                    R.id.speed_1 -> 0.5F
                    R.id.speed_2 -> 0.75F
                    R.id.speed_3 -> 1.0F
                    R.id.speed_4 -> 1.25F
                    R.id.speed_5 -> 1.5F
                    R.id.speed_6 -> 1.75F
                    R.id.speed_7 -> 2.0F
                    R.id.speed_8 -> 2.5F
                    else -> 2.5F
                }
                if (GoPreferences.getPrefsInstance().playbackSpeedMode != GoConstants.PLAYBACK_SPEED_ONE_ONLY) {
                    menu.findItem(getSelectedPlaybackItem(playbackSpeed)).setTitleColor(
                        Theming.resolveThemeColor(activity.resources)
                    )
                }
                MediaPlayerHolder.getInstance().setPlaybackSpeed(playbackSpeed)
                return@setOnMenuItemClickListener true
            }
            show()
        }
    }

    private fun getSelectedPlaybackItem(playbackSpeed: Float) = when (playbackSpeed) {
        0.25F -> R.id.speed_0
        0.5F -> R.id.speed_1
        0.75F -> R.id.speed_2
        1.0F -> R.id.speed_3
        1.25F -> R.id.speed_4
        1.5F -> R.id.speed_5
        1.75F -> R.id.speed_6
        2.0F -> R.id.speed_7
        2.25F -> R.id.speed_8
        else -> R.id.speed_9
    }
}

package com.iven.musicplayergo.regression

import android.media.MediaPlayer
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.preference.ListPreference
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.iven.musicplayergo.GoConstants
import com.iven.musicplayergo.R
import com.iven.musicplayergo.fragments.AllMusicFragment
import com.iven.musicplayergo.playlists.PlaylistsDialogFragment
import com.iven.musicplayergo.preferences.PreferencesFragment
import com.iven.musicplayergo.preferences.SettingsFragment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Reproductions of PR #3's search-refresh and playlist presentation review findings. */
@RunWith(AndroidJUnit4::class)
class PlaylistPresentationRegressionTest {
    @Test fun searchClearedAfterPlaylistRefreshRestoresShuffleCountAndPlayback() {
        RegressionFixture.open().use { f ->
            selectTab(f, GoConstants.SONGS_TAB)
            awaitSongRows(f, 3)
            f.onMain {
                assertTrue(songs(f).findViewHolderForAdapterPosition(0)!!.itemView.performClick())
            }
            f.expectPlaying("A")

            f.onMain {
                val searchItem = songFragment(f).requireView()
                    .findViewById<Toolbar>(R.id.search_toolbar).menu.findItem(R.id.action_search)
                searchItem.expandActionView()
                (searchItem.actionView as SearchView).apply {
                    isIconified = false
                    setQuery("no-song-has-this-regression-query", false)
                }
            }
            awaitSongRows(f, 0)
            refreshThroughPlaylistUi(f)
            f.onMain {
                assertEquals("Refresh keeps the active search", 0, songs(f).adapter!!.itemCount)
                assertEquals("0", shuffle(f).text.toString())
                assertFalse(shuffle(f).isEnabled)
            }
            f.record("PR3-search-refresh-no-matches")

            f.onMain { search(f).setQuery("", false); search(f).clearFocus() }
            awaitSongRows(f, 3)
            f.onMain {
                assertEquals("Shuffle count follows the restored Songs results", "3", shuffle(f).text.toString())
                assertTrue("Clearing search must re-enable Shuffle", shuffle(f).isEnabled)
                assertTrue(shuffle(f).performClick())
            }
            val ids = f.songs.map { it.id }.toSet()
            f.await("Shuffle starts a real queue containing the restored Songs results") {
                f.onMain {
                    f.holder.isQueueStarted && f.holder.queueSongs.map { it.id }.toSet() == ids &&
                        f.holder.currentSong?.id in ids && f.holder.isPlaying && f.player.isPlaying
                }
            }
            val position = f.onMain { f.player.currentPosition }
            f.await("shuffled audio position advances") {
                f.onMain { f.player.isPlaying && f.player.currentPosition >= position + 100 }
            }
            f.record("PR3-search-cleared-shuffle-restored-and-playing")
        }
    }

    @Test fun titleFilenamePreferenceUpdatesRetainedSongsAndMiniPlayerWithoutReorderingPlaylist() {
        RegressionFixture.open().use { f ->
            selectTab(f, GoConstants.SONGS_TAB)
            awaitSongRows(f, 3)
            val retainedRows = f.onMain { songs(f) }
            val filenameTitles = f.songs.map { it.displayName!!.substringBeforeLast('.') }.toSet()
            val metadataTitles = f.songs.map { it.title!! }.toSet()
            assertTrue("Fixture metadata and filenames must be visibly distinct", filenameTitles.intersect(metadataTitles).isEmpty())
            assertSongTitles(f, filenameTitles, retainedRows)

            // The saved order deliberately differs from both library display sort orders.
            val playlist = f.createPlaylist("Presentation order", "C", "A", "B")
            f.playPlaylist(playlist.id)
            f.expectPlaying("C")
            f.onMain { f.player.seekTo(5_000) }
            f.await("playlist seeks away from zero") { f.onMain { f.player.currentPosition >= 4_800 } }
            val originalPlayer = f.player
            assertMiniPlayer(f, f.tracks.getValue("C").displayName!!.substringBeforeLast('.'))

            listOf("0" to metadataTitles, GoConstants.FN to filenameTitles).forEach { (value, expectedRows) ->
                val before = checkpoint(f)
                selectTab(f, GoConstants.SETTINGS_TAB)
                selectVisualizationInSettings(f, value)
                val current = f.tracks.getValue("C")
                assertMiniPlayer(f, if (value == GoConstants.FN) current.displayName!!.substringBeforeLast('.') else current.title!!)
                selectTab(f, GoConstants.SONGS_TAB)
                assertSongTitles(f, expectedRows, retainedRows)
                assertPlaybackPreserved(f, originalPlayer, before)
                f.record("PR3-playlist-presentation-${if (value == GoConstants.FN) "filename" else "title"}")
            }
            f.onMain { f.holder.skip(isNext = true) }
            f.expectPlaying("A")
            f.onMain { f.holder.skip(isNext = true) }
            f.expectPlaying("B")
            assertEquals("Changing display settings retains saved membership order", playlist.tracks,
                RegressionFixture.success(f.repository.getPlaylist(playlist.id)).tracks)
            f.record("PR3-presentation-keeps-snapshot-C-A-B")
        }
    }

    private fun selectVisualizationInSettings(f: RegressionFixture, value: String) {
        f.await("Settings preference listener resumed") {
            f.onMain { preferences(f).lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        }
        val key = f.context.getString(R.string.song_visual_pref)
        val position = f.onMain {
            val fragment = preferences(f)
            val preference = fragment.findPreference<ListPreference>(key)!!
            assertNotEquals("Each selection must persist a different value", value, preference.value)
            (fragment.listView.adapter as PreferenceGroupAdapter)
                .getPreferenceAdapterPosition(preference).also { fragment.listView.scrollToPosition(it) }
        }
        assertTrue(position >= 0)
        f.await("Song visualization preference row laid out") {
            f.onMain { preferences(f).listView.findViewHolderForAdapterPosition(position) != null }
        }
        f.onMain {
            assertTrue(preferences(f).listView.findViewHolderForAdapterPosition(position)!!.itemView.performClick())
        }
        f.await("real ListPreference selection dialog") {
            f.onMain { preferences(f).parentFragmentManager.findFragmentByTag(PREFERENCE_DIALOG) is DialogFragment }
        }
        f.onMain {
            val fragment = preferences(f)
            val preference = fragment.findPreference<ListPreference>(key)!!
            val dialog = (fragment.parentFragmentManager.findFragmentByTag(PREFERENCE_DIALOG) as DialogFragment)
                .requireDialog() as AlertDialog
            val index = preference.findIndexOfValue(value)
            assertTrue(index >= 0)
            assertTrue(dialog.listView.performItemClick(dialog.listView.getChildAt(index), index,
                dialog.listView.adapter.getItemId(index)))
        }
        f.await("ListPreference persists selection and closes its dialog") {
            f.onMain {
                f.preferences.songsVisualization == value &&
                    preferences(f).parentFragmentManager.findFragmentByTag(PREFERENCE_DIALOG) == null
            }
        }
    }

    private fun refreshThroughPlaylistUi(f: RegressionFixture) {
        f.onActivity { assertTrue(it.findViewById<View>(R.id.playlists_button).performClick()) }
        f.await("Playlists ready for refresh") {
            f.onMain { playlistSheet(f)?.view?.findViewById<View>(R.id.playlist_refresh)?.isEnabled == true }
        }
        val before = f.onMain { f.viewModel.playlistLibraryState.value }
        f.onMain { assertTrue(playlistSheet(f)!!.requireView().findViewById<View>(R.id.playlist_refresh).performClick()) }
        f.await("successful refresh through Playlists") {
            f.onMain {
                val state = f.viewModel.playlistLibraryState.value
                state != null && state !== before && state.hasLoaded && !state.isLoading && !state.refreshFailed &&
                    playlistSheet(f)!!.requireView().findViewById<View>(R.id.playlist_refresh).isEnabled
            }
        }
        f.onMain { assertTrue(playlistSheet(f)!!.requireView().findViewById<View>(R.id.playlist_close).performClick()) }
        f.await("Playlists closed") { f.onMain { playlistSheet(f) == null } }
    }

    private fun selectTab(f: RegressionFixture, tab: String) {
        f.onActivity {
            val index = f.preferences.activeTabs.indexOf(tab)
            assertTrue(index >= 0)
            it.findViewById<ViewPager2>(R.id.view_pager2).setCurrentItem(index, false)
        }
    }

    private fun awaitSongRows(f: RegressionFixture, count: Int) = f.await("Songs shows $count rows") {
        f.onMain {
            songFragment(f).view?.findViewById<RecyclerView>(R.id.all_music_rv)?.let {
                it.isShown && it.adapter?.itemCount == count &&
                    (count == 0 || it.findViewHolderForAdapterPosition(0) != null)
            } == true
        }
    }

    private fun assertSongTitles(f: RegressionFixture, expected: Set<String>, retainedRows: RecyclerView) {
        f.await("existing Songs rows display the selected visualization") {
            f.onMain {
                val rows = songs(f)
                assertSame("Display changes must update the already-created Songs list", retainedRows, rows)
                rows.adapter?.itemCount == expected.size && (0 until expected.size).mapNotNull {
                    rows.findViewHolderForAdapterPosition(it)?.itemView?.findViewById<TextView>(R.id.title)?.text?.toString()
                }.toSet() == expected
            }
        }
    }

    private fun assertMiniPlayer(f: RegressionFixture, expected: String) {
        f.await("mini-player title updates to $expected") {
            var actual: String? = null
            f.onActivity { actual = it.findViewById<TextView>(R.id.playing_song).text.toString() }
            actual == expected
        }
    }

    private data class Checkpoint(val position: Int, val elapsed: Long)
    private fun checkpoint(f: RegressionFixture) = f.onMain { Checkpoint(f.player.currentPosition, SystemClock.elapsedRealtime()) }
    private fun assertPlaybackPreserved(f: RegressionFixture, originalPlayer: MediaPlayer, before: Checkpoint) = f.onMain {
        assertSame("A display setting cannot replace the active player", originalPlayer, f.player)
        assertTrue(f.holder.isPlaylistPlayback)
        assertTrue(f.holder.isPlaying && f.player.isPlaying)
        assertEquals(f.tracks.getValue("C").id, f.holder.currentSong?.id)
        assertEquals(3, f.holder.getCurrentAlbumSize())
        val elapsed = SystemClock.elapsedRealtime() - before.elapsed
        assertTrue("A display change cannot restart or jump the playing position",
            f.player.currentPosition >= before.position - 150 && f.player.currentPosition <= before.position + elapsed + 1_500)
    }

    private fun songFragment(f: RegressionFixture): AllMusicFragment {
        lateinit var fragment: AllMusicFragment
        f.onActivity { fragment = it.supportFragmentManager.fragments.filterIsInstance<AllMusicFragment>().single() }
        return fragment
    }

    private fun preferences(f: RegressionFixture): PreferencesFragment {
        lateinit var fragment: PreferencesFragment
        f.onActivity {
            fragment = it.supportFragmentManager.fragments.filterIsInstance<SettingsFragment>().single().getPreferencesFragment()!!
        }
        return fragment
    }

    private fun playlistSheet(f: RegressionFixture): PlaylistsDialogFragment? {
        var fragment: PlaylistsDialogFragment? = null
        f.onActivity { fragment = it.supportFragmentManager.findFragmentByTag("custom_playlists") as? PlaylistsDialogFragment }
        return fragment
    }

    private fun songs(f: RegressionFixture) = songFragment(f).requireView().findViewById<RecyclerView>(R.id.all_music_rv)
    private fun shuffle(f: RegressionFixture) = songFragment(f).requireView().findViewById<ExtendedFloatingActionButton>(R.id.shuffle_fab)
    private fun search(f: RegressionFixture) = songFragment(f).requireView().findViewById<Toolbar>(R.id.search_toolbar)
        .menu.findItem(R.id.action_search).actionView as SearchView

    companion object {
        private const val PREFERENCE_DIALOG = "androidx.preference.PreferenceFragment.DIALOG"
    }
}

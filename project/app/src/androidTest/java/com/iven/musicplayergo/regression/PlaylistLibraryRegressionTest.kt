package com.iven.musicplayergo.regression

import android.app.Application
import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.iven.musicplayergo.GoConstants
import com.iven.musicplayergo.R
import com.iven.musicplayergo.dialogs.NowPlaying
import com.iven.musicplayergo.dialogs.RecyclerSheet
import com.iven.musicplayergo.fragments.DetailsFragment
import com.iven.musicplayergo.playlists.PlaylistNameDialogFragment
import com.iven.musicplayergo.playlists.PlaylistResult
import com.iven.musicplayergo.playlists.PlaylistSongPickerDialogFragment
import com.iven.musicplayergo.playlists.PlaylistsDialogFragment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real MediaStore scans and UI regression checks. No production scan/player state is mocked. */
@RunWith(AndroidJUnit4::class)
class PlaylistLibraryRegressionTest {
    @Test fun rg04_missingAlbumDetailSurvivesThreeRefreshes() =
        missingDetail(GoConstants.ALBUM_VIEW, allAudio = false)

    @Test fun rg04_missingFolderDetailSurvivesThreeRefreshes() =
        missingDetail(GoConstants.FOLDER_VIEW, allAudio = false)

    @Test fun rg04_emptyLibraryKeepsOldDetailAndPlaylistsNavigable() =
        missingDetail(GoConstants.ALBUM_VIEW, allAudio = true)

    private fun missingDetail(source: String, allAudio: Boolean) {
        RegressionFixture.open().use { f ->
            val saved = f.createPlaylist("RG04 saved", "A", "B")
            val b = f.tracks.getValue("B")
            f.onActivity { activity ->
                activity.onArtistOrFolderSelected(
                    if (source == GoConstants.ALBUM_VIEW) b.album!! else b.relativePath!!,
                    source
                )
            }
            f.await("populated detail page") {
                f.onMain { detail(f)?.view?.findViewById<RecyclerView>(R.id.songs_rv)?.adapter?.itemCount == 1 }
            }
            val initialMenus = f.onMain { detail(f)!!.requireView().findViewById<Toolbar>(R.id.details_toolbar).menu.size() }
            val initialScrollChildren = f.onMain { detail(f)!!.requireView().findViewById<RecyclerView>(R.id.songs_rv).parent.let { it as android.view.ViewGroup }.childCount }
            repeat(3) {
                refreshThroughPlaylistUi(f)
                f.onMain {
                    val root = detail(f)!!.requireView()
                    assertEquals(1, root.findViewById<RecyclerView>(R.id.songs_rv).adapter!!.itemCount)
                    assertEquals("Refresh cannot duplicate the menu", initialMenus,
                        root.findViewById<Toolbar>(R.id.details_toolbar).menu.size())
                    assertEquals("Refresh cannot append duplicate scroll controls", initialScrollChildren,
                        (root.findViewById<RecyclerView>(R.id.songs_rv).parent as android.view.ViewGroup).childCount)
                }
            }
            f.record("RG04-$source-before-${if (allAudio) "empty" else "missing"}")
            if (allAudio) f.tracks.keys.toList().forEach(f::deleteAudio) else f.deleteAudio("B")
            repeat(3) { iteration ->
                refreshThroughPlaylistUi(f)
                f.onMain {
                    assertFalse(f.viewModel.playlistLibraryState.value!!.refreshFailed)
                    assertTrue(f.viewModel.playlistLibraryState.value!!.hasLoaded)
                    val oldDetail = detail(f) ?: error("Refresh discarded the open detail page")
                    val root = oldDetail.requireView()
                    assertEquals(0, root.findViewById<RecyclerView>(R.id.songs_rv).adapter!!.itemCount)
                    val toolbar = root.findViewById<Toolbar>(R.id.details_toolbar)
                    assertEquals(f.context.getString(R.string.folder_info, 0), toolbar.subtitle.toString())
                    assertEquals("Stale source must not retain menu actions", 0, toolbar.menu.size())
                }
                f.record("RG04-$source-refresh-${iteration + 1}-${if (allAudio) "empty" else "missing"}")
            }
            if (allAudio) assertTrue(f.onMain { f.viewModel.playlistLibrary.isEmpty() })
            // The existing toolbar's Back action must still close its stale details.
            f.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            f.await("Back leaves missing details") { f.onMain { detail(f) == null } }
            openPlaylists(f)
            selectPlaylist(f, saved.name)
            renameSelected(f, "RG04 renamed")
            assertEquals("RG04 renamed", success(f.repository.getPlaylist(saved.id)).name)
            f.record("RG04-$source-playlists-still-manageable")
        }
    }

    @Test fun rg06_failedQueryPreservesLastGoodScanAndRecovers() =
        liveScanFailureAndRecovery(denyPermission = false)

    @Test fun rg06_deniedMediaPermissionCannotBeSuccessfulEmptyScan() =
        liveScanFailureAndRecovery(denyPermission = true)

    private fun liveScanFailureAndRecovery(denyPermission: Boolean) {
        val targetApplication = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val app = SwitchableQueryApplication(targetApplication)
        RegressionFixture.open(scanApplication = app).use { f ->
            val playlist = f.createPlaylist("RG06 saved", "A", "B")
            val baseline = f.onMain { f.viewModel.playlistLibraryState.value!!.songs }
            assertEquals(3, baseline.size)
            openPlaylists(f)
            selectPlaylist(f, playlist.name)
            assertTrackStatus(f, null, expectedRows = 2)
            app.rejectQuery = !denyPermission
            app.denyMediaPermission = denyPermission
            clickSheet(f, R.id.playlist_refresh)
            f.await("actual query exception classified as failure in the live activity") {
                f.onMain {
                    f.viewModel.playlistLibraryState.value?.let { it.refreshFailed && !it.isLoading } == true &&
                        sheet(f).requireView().findViewById<View>(R.id.playlist_refresh).isEnabled
                }
            }
            f.onMain {
                val failed = f.viewModel.playlistLibraryState.value!!
                assertTrue(failed.hasLoaded)
                assertEquals("Failure cannot replace the last good songs with an empty scan", baseline, failed.songs)
                val root = sheet(f).requireView()
                assertEquals(f.context.getString(R.string.playlist_library_failure),
                    root.findViewById<TextView>(R.id.playlist_status).text.toString())
                assertFalse(root.findViewById<View>(R.id.playlist_primary).isEnabled)
                assertFalse(root.findViewById<View>(R.id.playlist_add).isEnabled)
                assertTrue(root.findViewById<View>(R.id.playlist_rename).isEnabled)
                assertTrue(root.findViewById<View>(R.id.playlist_delete).isEnabled)
            }
            assertTrackStatus(f, f.context.getString(R.string.playlist_availability_unknown), expectedRows = 2)
            assertEquals(playlist.tracks, success(f.repository.getPlaylist(playlist.id)).tracks)
            if (denyPermission) assertEquals("Denied permission must be checked before querying MediaStore", 0,
                app.resolverAccessesWhileDenied)
            f.record("RG06-live-${if (denyPermission) "denied-permission" else "failed-query"}-unknown-and-disabled-play")
            app.rejectQuery = false
            app.denyMediaPermission = false
            clickSheet(f, R.id.playlist_refresh)
            f.await("live query and selected playlist recover after provider access resumes") {
                f.onMain {
                    f.viewModel.playlistLibraryState.value?.let { it.hasLoaded && !it.isLoading && !it.refreshFailed } == true &&
                        sheet(f).requireView().findViewById<View>(R.id.playlist_primary).isEnabled
                }
            }
            assertEquals(baseline.map { it.id }, f.onMain { f.viewModel.playlistLibrary.map { it.id } })
            assertTrackStatus(f, null, expectedRows = 2)
            clickSheet(f, R.id.playlist_primary)
            f.expectPlaying("A")
            assertTrue(f.onMain { f.holder.isPlaylistPlayback })
            f.record("RG06-live-${if (denyPermission) "permission" else "query"}-recovers-and-plays")
            // Runtime-permission denial is also exercised separately: revoking it kills
            // instrumentation, whereas this controlled access exception preserves the UI.
        }
    }

    @Test fun rg06_successfulEmptyScanPreservesMembershipAndManagement() {
        RegressionFixture.open().use { f ->
            val study = f.createPlaylist("Study", "A", "B")
            val running = f.createPlaylist("Running", "B", "C")
            val originalB = f.tracks.getValue("B")
            assertEquals("Empty-library fixture must contain only its three audio files", 3,
                f.onMain { f.viewModel.playlistLibrary.size })
            f.tracks.keys.toList().forEach(f::deleteAudio)
            refreshThroughPlaylistUi(f)
            f.onMain {
                val state = f.viewModel.playlistLibraryState.value!!
                assertTrue(state.hasLoaded)
                assertFalse(state.refreshFailed)
                assertTrue(state.songs.isEmpty())
            }
            assertEquals(study.tracks, success(f.repository.getPlaylist(study.id)).tracks)
            assertEquals(running.tracks, success(f.repository.getPlaylist(running.id)).tracks)

            // Reach the new action on the real no-music screen, then inspect actual rows.
            openPlaylists(f)
            selectPlaylist(f, study.name)
            f.onMain {
                val root = sheet(f).requireView()
                assertFalse(root.findViewById<View>(R.id.playlist_primary).isEnabled)
                assertTrue(root.findViewById<View>(R.id.playlist_rename).isEnabled)
                assertTrue(root.findViewById<View>(R.id.playlist_delete).isEnabled)
            }
            assertTrackStatus(f, f.context.getString(R.string.playlist_unavailable), expectedRows = 2)
            f.record("RG06-successful-empty-unavailable-retained")
            renameSelected(f, "Focus")
            assertEquals("Focus", success(f.repository.getPlaylist(study.id)).name)

            // Delete through the confirmation dialog, not by mutating saved storage.
            clickSheet(f, R.id.playlist_delete)
            f.await("delete confirmation") { f.onMain { sheet(f).childFragmentManager.findFragmentByTag("playlist_delete") is DialogFragment } }
            f.onMain {
                val dialog = sheet(f).childFragmentManager.findFragmentByTag("playlist_delete") as DialogFragment
                (dialog.requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            f.await("playlist deleted on empty library") { f.repository.getPlaylist(study.id) is PlaylistResult.Failure }
            f.await("playlist collection after deletion") {
                f.onMain { sheet(f).requireView().findViewById<TextView>(R.id.playlist_heading).text.toString() == f.context.getString(R.string.playlist_title) }
            }
            clickSheet(f, R.id.playlist_primary)
            fillNameDialog(f, "Empty after scan")
            f.await("empty playlist created without music") {
                success(f.repository.getPlaylists()).any { it.name == "Empty after scan" && it.tracks.isEmpty() }
            }
            closePlaylists(f)

            // A same-named file with a different MediaStore identity must not be guessed.
            val replacement = f.restoreAudio("B")
            assertNotEquals(originalB.id, replacement.id)
            f.refreshLibrary()
            val persisted = success(f.repository.getPlaylist(running.id))
            val resolved = f.repository.resolve(persisted, f.onMain { f.viewModel.playlistLibrary })
            assertEquals(originalB.id, resolved[0].saved.mediaId)
            assertFalse("Same filename with a new ID cannot silently replace saved B", resolved[0].isAvailable)
            assertEquals(running.tracks, persisted.tracks)
            f.record("RG06-same-name-new-id-not-guessed")
        }
    }

    @Test fun rg07_hiddenTrackRemainsPlayableAndOldControlsStillWork() {
        RegressionFixture.open().use { f ->
            val study = f.createPlaylist("Study", "A", "B")
            val running = f.createPlaylist("Running", "B", "C")
            val b = f.tracks.getValue("B")
            // B's unique album isolates this existing album/artist/folder filtering feature.
            f.onMain { f.preferences.filters = setOf(b.album!!) }
            repeat(3) { refreshThroughPlaylistUi(f) }
            f.onMain {
                assertEquals(3, f.viewModel.playlistLibrary.size)
                assertEquals(setOf(f.tracks.getValue("A").id, f.tracks.getValue("C").id),
                    f.viewModel.deviceMusicFiltered.orEmpty().map { it.id }.toSet())
            }
            assertTrue(f.repository.resolve(success(f.repository.getPlaylist(study.id)), f.onMain { f.viewModel.playlistLibrary }).all { it.isAvailable })
            openPlaylists(f)
            selectPlaylist(f, running.name)
            assertTrackStatus(f, null, expectedRows = 2)
            clickSheet(f, R.id.playlist_add)
            f.await("add-song picker") { f.onMain { sheet(f).childFragmentManager.findFragmentByTag("playlist_add") is PlaylistSongPickerDialogFragment } }
            f.await("picker excludes hidden B") {
                f.onMain { picker(f).requireDialog().findViewById<RecyclerView>(R.id.playlist_picker_rows)?.adapter?.itemCount == 2 }
            }
            f.onMain {
                val rows = picker(f).requireDialog().findViewById<RecyclerView>(R.id.playlist_picker_rows)!!
                assertEquals(2, rows.adapter!!.itemCount)
                assertFalse(visibleTexts(rows).contains(b.title.orEmpty()))
            }
            f.record("RG07-hidden-B-excluded-from-picker")
            f.onMain { (picker(f).requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_NEGATIVE).performClick() }
            f.await("picker closed") { f.onMain { sheet(f).childFragmentManager.findFragmentByTag("playlist_add") == null } }
            clickSheet(f, R.id.playlist_primary)
            f.expectPlaying("B")
            assertEquals("Saved playlist preserves hidden membership", running.tracks, success(f.repository.getPlaylist(running.id)).tracks)
            f.record("RG07-hidden-B-plays-from-saved-playlist")

            // Use the ordinary Songs tab row to leave playlist playback.
            f.onActivity { activity ->
                activity.findViewById<ViewPager2>(R.id.view_pager2).setCurrentItem(
                    f.preferences.activeTabs.toList().indexOf(GoConstants.SONGS_TAB), false)
            }
            f.await("ordinary Songs tab has two rows without refresh duplicates") {
                f.onMain { f.scenarioActivityView<RecyclerView>(R.id.all_music_rv)?.let { it.isShown && it.adapter?.itemCount == 2 } == true }
            }
            clickActivityRecyclerRow(f, R.id.all_music_rv, 0)
            f.expectPlaying("A")
            assertFalse(f.onMain { f.holder.isPlaylistPlayback })
            openNowPlaying(f)
            clickNowPlaying(f, R.id.np_play)
            f.await("ordinary pause control") { f.onMain { !f.holder.isPlaying && !f.player.isPlaying } }
            val pausedPosition = f.onMain { f.player.currentPosition }
            SystemClock.sleep(250)
            assertTrue(kotlin.math.abs(pausedPosition - f.onMain { f.player.currentPosition }) < 150)
            clickNowPlaying(f, R.id.np_play)
            f.expectPlaying("A")
            clickNowPlaying(f, R.id.np_love)
            f.await("favorite added by Now Playing control") { f.preferences.favorites.orEmpty().any { it.id == f.tracks.getValue("A").id } }
            closeNowPlaying(f)
            clickActivity(f, R.id.favorites_button)
            f.await("Favorites sheet") { f.onMain { recyclerSheet(f)?.sheetType == RecyclerSheet.FAV_TYPE } }
            clickModalRow(f, 0)
            f.expectPlaying("A")
            dismissRecyclerSheet(f)
            // Remove the favorite through its original toggle, then test a fresh ordinary queue.
            openNowPlaying(f)
            clickNowPlaying(f, R.id.np_love)
            f.await("favorite removed by Now Playing control") { f.preferences.favorites.orEmpty().none { it.id == f.tracks.getValue("A").id } }
            closeNowPlaying(f)
            f.play("A") // Existing playlist action clears the temporary queue before this subcase.
            clickActivityRecyclerRow(f, R.id.all_music_rv, 0)
            f.expectPlaying("A")
            f.onActivity { it.onAddToQueue(f.tracks.getValue("C")) }
            clickActivity(f, R.id.queue_button)
            f.await("ordinary Queue sheet") { f.onMain { recyclerSheet(f)?.sheetType == RecyclerSheet.QUEUE_TYPE } }
            f.onMain { assertEquals(1, recyclerSheet(f)!!.requireView().findViewById<RecyclerView>(R.id.modal_rv).adapter!!.itemCount) }
            clickModalRow(f, 0)
            f.expectPlaying("C")
            dismissRecyclerSheet(f)
            assertNotNull(f.onMain { f.holder.isQueue })
            openNowPlaying(f)
            clickNowPlaying(f, R.id.np_skip_next)
            f.expectPlaying("A") // End of the temporary queue returns to its ordinary source.
            closeNowPlaying(f)
            assertEquals(3, f.onMain { f.viewModel.playlistLibrary.size })
            assertEquals(2, f.onMain { f.viewModel.deviceMusicFiltered!!.size })
            f.record("RG07-library-favorites-queue-and-controls")
        }
    }

    private fun detail(f: RegressionFixture) = f.activityFragment(GoConstants.DETAILS_FRAGMENT_TAG) as? DetailsFragment
    private fun sheet(f: RegressionFixture) = f.activityFragment("custom_playlists") as PlaylistsDialogFragment
    private fun picker(f: RegressionFixture) = sheet(f).childFragmentManager.findFragmentByTag("playlist_add") as PlaylistSongPickerDialogFragment
    private fun recyclerSheet(f: RegressionFixture) = f.activityFragment(RecyclerSheet.TAG_MODAL_RV) as? RecyclerSheet

    private fun openPlaylists(f: RegressionFixture) {
        f.await("reachable playlist entry") {
            f.onMain {
                f.activityFragment(GoConstants.ERROR_FRAGMENT_TAG)?.view?.isShown == true ||
                    f.scenarioActivityView<View>(R.id.playlists_button)?.isShown == true
            }
        }
        f.onActivity { activity ->
            val error = activity.supportFragmentManager.findFragmentByTag(GoConstants.ERROR_FRAGMENT_TAG)
            if (error?.view?.isShown == true) {
                val toolbar = error.requireView().findViewById<Toolbar>(R.id.error_toolbar)
                assertTrue(toolbar.menu.size() > 0)
                assertTrue(toolbar.menu.performIdentifierAction(toolbar.menu.getItem(0).itemId, 0))
            } else {
                val button = activity.findViewById<View>(R.id.playlists_button)
                assertTrue("Playlist entry must be reachable", button.isShown)
                button.performClick()
            }
        }
        f.await("playlists sheet ready") {
            f.onMain { (f.activityFragment("custom_playlists") as? PlaylistsDialogFragment)?.view?.findViewById<View>(R.id.playlist_refresh)?.isEnabled == true }
        }
    }

    private fun closePlaylists(f: RegressionFixture) {
        clickSheet(f, R.id.playlist_close)
        f.await("playlists sheet closed") { f.onMain { f.activityFragment("custom_playlists") == null } }
    }

    private fun refreshThroughPlaylistUi(f: RegressionFixture) {
        openPlaylists(f)
        val before = f.onMain { f.viewModel.playlistLibraryState.value }
        clickSheet(f, R.id.playlist_refresh)
        f.await("successful playlist scan") {
            f.onMain {
                val state = f.viewModel.playlistLibraryState.value
                state != null && state !== before && state.hasLoaded && !state.isLoading && !state.refreshFailed &&
                    sheet(f).requireView().findViewById<View>(R.id.playlist_refresh).isEnabled
            }
        }
        closePlaylists(f)
    }

    private fun selectPlaylist(f: RegressionFixture, name: String) {
        val list = success(f.repository.getPlaylists())
        val index = list.indexOfFirst { it.name == name }
        assertTrue("Saved playlist $name exists", index >= 0)
        f.await("playlist rows loaded") {
            f.onMain { sheet(f).requireView().findViewById<RecyclerView>(R.id.playlist_rows).adapter?.itemCount == list.size }
        }
        clickRecyclerRow(f, { sheet(f).requireView().findViewById(R.id.playlist_rows) }, index)
        f.await("selected playlist rendered") {
            f.onMain { sheet(f).requireView().findViewById<TextView>(R.id.playlist_heading).text.toString() == name }
        }
    }

    private fun renameSelected(f: RegressionFixture, name: String) {
        clickSheet(f, R.id.playlist_rename)
        fillNameDialog(f, name)
        f.await("renamed playlist rendered") {
            f.onMain { sheet(f).requireView().findViewById<TextView>(R.id.playlist_heading).text.toString() == name }
        }
    }

    private fun fillNameDialog(f: RegressionFixture, name: String) {
        f.await("playlist name dialog") { f.onMain { sheet(f).childFragmentManager.findFragmentByTag("playlist_name") is PlaylistNameDialogFragment } }
        f.onMain {
            val fragment = sheet(f).childFragmentManager.findFragmentByTag("playlist_name") as PlaylistNameDialogFragment
            val dialog = fragment.requireDialog() as AlertDialog
            dialog.findViewById<EditText>(R.id.playlist_name_input)!!.setText(name)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        }
        f.await("playlist name dialog closed after save") { f.onMain { sheet(f).childFragmentManager.findFragmentByTag("playlist_name") == null } }
    }

    private fun assertTrackStatus(f: RegressionFixture, expected: String?, expectedRows: Int) {
        f.await("resolved track rows") {
            f.onMain { sheet(f).requireView().findViewById<RecyclerView>(R.id.playlist_rows).adapter?.itemCount == expectedRows }
        }
        repeat(expectedRows) { index ->
            val row = { sheet(f).requireView().findViewById<RecyclerView>(R.id.playlist_rows) }
            f.onMain { row().scrollToPosition(index) }
            f.await("resolved track row $index laid out") { f.onMain { row().findViewHolderForAdapterPosition(index) != null } }
            f.onMain {
                val status = row().findViewHolderForAdapterPosition(index)!!.itemView.findViewById<TextView>(R.id.playlist_track_status)
                if (expected == null) assertEquals(View.GONE, status.visibility)
                else { assertEquals(View.VISIBLE, status.visibility); assertEquals(expected, status.text.toString()) }
            }
        }
    }

    private fun clickSheet(f: RegressionFixture, id: Int) = f.onMain {
        val view = sheet(f).requireView().findViewById<View>(id)
        assertTrue("Enabled playlist action $id", view.isEnabled)
        assertTrue(view.performClick())
    }

    private fun clickActivity(f: RegressionFixture, id: Int) = f.onActivity {
        val view = it.findViewById<View>(id)
        assertTrue("Visible activity action $id", view.isShown)
        assertTrue(view.performClick())
    }

    private fun clickActivityRecyclerRow(f: RegressionFixture, id: Int, index: Int) =
        clickRecyclerRow(f, { f.scenarioActivityView<RecyclerView>(id)!! }, index)

    private fun clickModalRow(f: RegressionFixture, index: Int) =
        clickRecyclerRow(f, { recyclerSheet(f)!!.requireView().findViewById(R.id.modal_rv) }, index)

    private fun clickRecyclerRow(f: RegressionFixture, recycler: () -> RecyclerView, index: Int) {
        f.onMain { recycler().scrollToPosition(index) }
        f.await("row $index laid out") { f.onMain { recycler().findViewHolderForAdapterPosition(index) != null } }
        f.onMain { assertTrue(recycler().findViewHolderForAdapterPosition(index)!!.itemView.performClick()) }
    }

    private fun openNowPlaying(f: RegressionFixture) {
        // Activity controls share a 500 ms debounce. A fast emulator can finish the
        // previous Favorites/Queue flow before it expires; send a usable UI click.
        SystemClock.sleep(550)
        clickActivity(f, R.id.playing_song_container)
        f.await("Now Playing sheet") { f.onMain { f.activityFragment(NowPlaying.TAG_MODAL) is NowPlaying } }
    }

    private fun clickNowPlaying(f: RegressionFixture, id: Int) = f.onMain {
        val np = f.activityFragment(NowPlaying.TAG_MODAL) as NowPlaying
        assertTrue(np.requireView().findViewById<View>(id).performClick())
    }

    private fun closeNowPlaying(f: RegressionFixture) {
        f.onMain { (f.activityFragment(NowPlaying.TAG_MODAL) as NowPlaying).dismiss() }
        f.await("Now Playing closed") { f.onMain { f.activityFragment(NowPlaying.TAG_MODAL) == null } }
    }

    private fun dismissRecyclerSheet(f: RegressionFixture) {
        f.onMain { recyclerSheet(f)!!.dismiss() }
        f.await("old list dialog closed") { f.onMain { recyclerSheet(f) == null } }
    }

    private fun visibleTexts(root: View): List<String> = when (root) {
        is TextView -> listOf(root.text.toString())
        is android.view.ViewGroup -> (0 until root.childCount).flatMap { visibleTexts(root.getChildAt(it)) }
        else -> emptyList()
    }

    private fun RegressionFixture.activityFragment(tag: String): androidx.fragment.app.Fragment? {
        var fragment: androidx.fragment.app.Fragment? = null
        onActivity { fragment = it.supportFragmentManager.findFragmentByTag(tag) }
        return fragment
    }

    private fun <T : View> RegressionFixture.scenarioActivityView(id: Int): T? {
        var result: T? = null
        onActivity { result = it.findViewById(id) }
        return result
    }

    private fun <T> success(result: PlaylistResult<T>): T {
        assertTrue("Expected repository success, got $result", result is PlaylistResult.Success)
        return (result as PlaylistResult.Success).value
    }

    /** Uses the production scanner and real successful MediaStore reads around one access error. */
    private class SwitchableQueryApplication(private val delegate: Application) : Application() {
        @Volatile var rejectQuery = false
        @Volatile var denyMediaPermission = false
        @Volatile var resolverAccessesWhileDenied = 0
        override fun getContentResolver(): ContentResolver {
            if (denyMediaPermission) resolverAccessesWhileDenied++
            if (rejectQuery) throw SecurityException("RG06 controlled provider access failure")
            return delegate.contentResolver
        }
        override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
            if (denyMediaPermission && (permission == Manifest.permission.READ_MEDIA_AUDIO ||
                        permission == Manifest.permission.READ_EXTERNAL_STORAGE)) {
                PackageManager.PERMISSION_DENIED
            } else delegate.checkPermission(permission, pid, uid)
        override fun getResources(): Resources = delegate.resources
        override fun getApplicationContext(): Context = delegate.applicationContext
    }
}

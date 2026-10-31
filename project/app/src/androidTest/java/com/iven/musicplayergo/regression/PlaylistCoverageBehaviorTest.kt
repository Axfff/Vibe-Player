package com.iven.musicplayergo.regression

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Resources
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.textfield.TextInputLayout
import com.iven.musicplayergo.R
import com.iven.musicplayergo.library.LibraryScanViewModel
import com.iven.musicplayergo.library.MediaScanOutcome
import com.iven.musicplayergo.playlists.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** Behavior gaps identified by the first full report (1005/1375 team lines). */
@RunWith(AndroidJUnit4::class)
class PlaylistCoverageBehaviorTest {
    @Test fun scanButtonIndexesRealAudioAndRefreshesWithoutChangingSavedMembership() {
        RegressionFixture.open().use { f ->
            val saved = f.createPlaylist("Scan preserves", "B", "A")
            lateinit var scanner: LibraryScanViewModel
            f.onActivity { activity ->
                scanner = ViewModelProvider(activity)[LibraryScanViewModel::class.java]
                assertTrue(activity.findViewById<View>(R.id.library_scan_music).isEnabled)
                assertTrue(activity.findViewById<View>(R.id.library_scan_music).performClick())
                assertFalse(activity.findViewById<View>(R.id.library_scan_music).isEnabled)
            }
            f.await("real indexing and post-index library refresh", 40_000) {
                f.onMain { scanner.state.value?.let { it.report != null && !it.running && !it.refreshPending } == true &&
                    f.viewModel.playlistLibraryState.value?.let { !it.isLoading && !it.refreshFailed } == true }
            }
            f.onMain {
                val report = scanner.state.value!!.report!!
                assertEquals(MediaScanOutcome.COMPLETE, report.outcome)
                assertEquals(3, report.attempted)
                assertEquals(3, report.indexed)
                assertFalse("Refresh request is consumed once", scanner.consumeRefreshRequest())
            }
            assertEquals(saved, success(f.repository.getPlaylist(saved.id)))
            assertEquals(f.songs.map { it.id }.toSet(), f.onMain { f.viewModel.playlistLibrary.map { it.id }.toSet() })
            f.onActivity { assertTrue(it.findViewById<View>(R.id.library_scan_music).isEnabled) }
        }
    }

    @Test fun emptyScanReportsNoCandidatesAndKeepsUnavailableMembership() {
        RegressionFixture.open().use { f ->
            val saved = f.createPlaylist("Empty scan", "A")
            f.tracks.keys.toList().forEach(f::deleteAudio)
            f.refreshLibrary()
            lateinit var scanner: LibraryScanViewModel
            f.onActivity {
                scanner = ViewModelProvider(it)[LibraryScanViewModel::class.java]
                it.findViewById<View>(R.id.library_scan_music).performClick()
            }
            f.await("empty reindex finishes") { f.onMain { scanner.state.value?.report != null } }
            f.onMain {
                assertEquals(MediaScanOutcome.NO_CANDIDATES, scanner.state.value!!.report!!.outcome)
                assertEquals(0, scanner.state.value!!.report!!.attempted)
            }
            assertEquals(saved, success(f.repository.getPlaylist(saved.id)))
        }
    }

    @Test fun scanPermissionAndProviderFailuresNeverBecomeSuccessfulEmptyReports() {
        RegressionFixture.open().use { f ->
            val application = f.context.applicationContext as Application
            for (mode in listOf("denied", "security", "io")) {
                val app = object : Application() {
                    override fun getApplicationContext(): Context = application
                    override fun getResources(): Resources = application.resources
                    override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
                        if (mode == "denied") PackageManager.PERMISSION_DENIED else PackageManager.PERMISSION_GRANTED
                    override fun getContentResolver(): ContentResolver =
                        if (mode == "security") throw SecurityException("Controlled provider denial")
                        else throw IOException("Controlled provider unavailable")
                }
                val scanner = f.onMain { LibraryScanViewModel(app).also { it.startScan() } }
                f.await("$mode scan classified") { f.onMain { scanner.state.value?.running == false } }
                f.onMain {
                    val state = scanner.state.value!!
                    assertNull(state.report)
                    assertFalse(state.refreshPending)
                    assertEquals(mode != "io", state.permissionDenied)
                    assertEquals(mode == "io", state.failed)
                    assertFalse(scanner.consumeRefreshRequest())
                }
            }
        }
    }

    @Test fun pickerAddsInClickOrderRejectsDuplicateAndRemovalAllowsReaddingAtEnd() {
        RegressionFixture.open().use { f ->
            val saved = f.createPlaylist("Picker actions")
            val other = f.createPlaylist("Isolated", "B")
            open(f)
            select(f, saved.name)
            click(f, R.id.playlist_add)
            awaitChild(f, "playlist_add")
            add(f, saved.id, "B", listOf("B"))
            add(f, saved.id, "A", listOf("B", "A"))
            add(f, saved.id, "B", listOf("B", "A"))
            // Refresh clears persistent duplicate feedback before a new no-match search.
            f.onMain { (child(f, "playlist_add").requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_NEUTRAL).performClick() }
            f.onMain {
                val dialog = child(f, "playlist_add").requireDialog() as AlertDialog
                dialog.findViewById<EditText>(R.id.playlist_song_search)!!.setText("no-such-fixture")
            }
            f.await("no-match search message") { f.onMain {
                val dialog = child(f, "playlist_add").requireDialog() as AlertDialog
                dialog.findViewById<TextView>(R.id.playlist_picker_status)!!.text.toString() == f.context.getString(R.string.playlist_no_search_results)
            } }
            f.onMain { (child(f, "playlist_add").requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_NEGATIVE).performClick() }
            f.await("picker closes") { f.onMain { sheet(f).childFragmentManager.findFragmentByTag("playlist_add") == null } }
            val rows = { sheet(f).requireView().findViewById<RecyclerView>(R.id.playlist_rows) }
            f.await("two saved rows") { f.onMain { rows().adapter?.itemCount == 2 && rows().findViewHolderForAdapterPosition(0) != null } }
            f.onMain { rows().findViewHolderForAdapterPosition(0)!!.itemView.findViewById<View>(R.id.playlist_track_action).performClick() }
            f.await("remove persisted") { success(f.repository.getPlaylist(saved.id)).tracks.map { it.mediaId } == listOf(f.tracks.getValue("A").id) }
            f.await("add action available after remove") { f.onMain { sheet(f).requireView().findViewById<View>(R.id.playlist_add).isEnabled } }
            click(f, R.id.playlist_add)
            awaitChild(f, "playlist_add")
            add(f, saved.id, "B", listOf("A", "B"))
            assertEquals(other, success(f.repository.getPlaylist(other.id)))
        }
    }

    @Test fun nameValidationRecoversAndImeDoneSavesTrimmedName() {
        RegressionFixture.open().use { f ->
            f.createPlaylist("Existing")
            open(f)
            click(f, R.id.playlist_primary)
            awaitChild(f, "playlist_name")
            for ((input, message) in listOf("  " to R.string.playlist_error_empty_name,
                    "existing" to R.string.playlist_error_duplicate_name)) {
                f.onMain {
                    val dialog = child(f, "playlist_name").requireDialog() as AlertDialog
                    dialog.findViewById<EditText>(R.id.playlist_name_input)!!.setText(input)
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                }
                f.await("inline name validation") { f.onMain {
                    val dialog = child(f, "playlist_name").requireDialog() as AlertDialog
                    dialog.findViewById<TextInputLayout>(R.id.playlist_name_layout)!!.error?.toString() == f.context.getString(message) &&
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled && dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled
                } }
            }
            f.onMain {
                val dialog = child(f, "playlist_name").requireDialog() as AlertDialog
                val input = dialog.findViewById<EditText>(R.id.playlist_name_input)!!
                input.setText("  Recovered  ")
                assertNull(dialog.findViewById<TextInputLayout>(R.id.playlist_name_layout)!!.error)
                input.onEditorAction(EditorInfo.IME_ACTION_DONE)
            }
            f.await("IME saved") { success(f.repository.getPlaylists()).any { it.name == "Recovered" } }
            assertEquals(2, success(f.repository.getPlaylists()).size)
        }
    }

    @Test fun corruptStorageDisablesMutationAndRefreshRecoversWithoutOverwritingBytes() {
        RegressionFixture.open().use { f ->
            val preferences = PreferenceManager.getDefaultSharedPreferences(f.context)
            val damaged = "{broken document"
            assertTrue(preferences.edit().putString(SharedPreferencesPlaylistStorage.KEY, damaged).commit())
            open(f, ready = false)
            f.await("read failure rendered") { f.onMain {
                sheet(f).requireView().findViewById<TextView>(R.id.playlist_status).text.toString() == f.context.getString(R.string.playlist_error_read)
            } }
            f.onMain { assertFalse(sheet(f).requireView().findViewById<View>(R.id.playlist_primary).isEnabled) }
            assertEquals(damaged, preferences.getString(SharedPreferencesPlaylistStorage.KEY, null))
            assertTrue(preferences.edit().remove(SharedPreferencesPlaylistStorage.KEY).commit())
            click(f, R.id.playlist_refresh)
            f.await("storage repaired externally and refreshed") { f.onMain { sheet(f).requireView().findViewById<View>(R.id.playlist_primary).isEnabled } }
            assertTrue(success(f.repository.getPlaylists()).isEmpty())
        }
    }

    @Test fun failedPreferencesCommitRestoresPreviousValueAndKeepsUnrelatedSettings() {
        RegressionFixture.open().use { f ->
            val real = f.context.getSharedPreferences("coverage-write-failure", Context.MODE_PRIVATE)
            for (previous in listOf(null, "previous-json")) for (throws in listOf(false, true)) {
                assertTrue(real.edit().clear().putString("unrelated", "keep").apply {
                    if (previous != null) putString(SharedPreferencesPlaylistStorage.KEY, previous)
                }.commit())
                var first = true
                val boundary = object : SharedPreferences by real {
                    override fun edit(): SharedPreferences.Editor {
                        val editor = real.edit()
                        return object : SharedPreferences.Editor by editor {
                            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                                editor.putString(key, value)
                                return this
                            }
                            override fun commit(): Boolean {
                                val committed = editor.commit()
                                if (first) {
                                    first = false
                                    if (throws) throw IOException("Controlled disk write failure")
                                    return false
                                }
                                return committed
                            }
                        }
                    }
                }
                val storage = SharedPreferencesPlaylistStorage(boundary)
                assertFalse(storage.write("replacement-json"))
                assertEquals(previous, storage.read())
                assertEquals("keep", real.getString("unrelated", null))
            }
        }
    }

    private fun open(f: RegressionFixture, ready: Boolean = true) {
        f.onActivity { it.openPlaylists() }
        f.await("playlist sheet") { f.onMain { sheetOrNull(f)?.view != null } }
        if (ready) f.await("playlist storage ready") { f.onMain { sheet(f).requireView().findViewById<View>(R.id.playlist_primary).isEnabled } }
    }

    private fun select(f: RegressionFixture, name: String) {
        val playlists = success(f.repository.getPlaylists())
        val position = playlists.indexOfFirst { it.name == name }
        f.await("playlist row") { f.onMain { sheet(f).requireView().findViewById<RecyclerView>(R.id.playlist_rows).findViewHolderForAdapterPosition(position) != null } }
        f.onMain { sheet(f).requireView().findViewById<RecyclerView>(R.id.playlist_rows).findViewHolderForAdapterPosition(position)!!.itemView.performClick() }
        f.await("playlist detail") { f.onMain { sheet(f).requireView().findViewById<TextView>(R.id.playlist_heading).text.toString() == name } }
    }

    private fun add(f: RegressionFixture, playlistId: String, label: String, order: List<String>) {
        f.onMain { (child(f, "playlist_add").requireDialog() as AlertDialog).findViewById<EditText>(R.id.playlist_song_search)!!.setText(f.tracks.getValue(label).displayName) }
        // RecyclerView may rebind after the save/refresh callback. Lookup and click
        // in one main-thread action so a holder cannot disappear between them.
        f.await("one filtered song ready to click") { f.onMain {
            val list = (child(f, "playlist_add").requireDialog() as AlertDialog).findViewById<RecyclerView>(R.id.playlist_picker_rows)!!
            if (list.adapter?.itemCount != 1 || list.hasPendingAdapterUpdates()) return@onMain false
            val row = list.findViewHolderForAdapterPosition(0) ?: return@onMain false
            val action = row.itemView.findViewById<View>(R.id.playlist_track_action)
            if (action.isEnabled) assertTrue(action.performClick())
            // A membership-disabled button is equally safe; persistence is asserted below.
            true
        } }
        f.await("saved click order $order") { success(f.repository.getPlaylist(playlistId)).tracks.map { it.mediaId } == order.map { f.tracks.getValue(it).id } }
        f.await("picker save finished") { f.onMain { (child(f, "playlist_add").requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled } }
    }

    private fun click(f: RegressionFixture, id: Int) = f.onMain {
        val action = sheet(f).requireView().findViewById<View>(id)
        assertTrue(action.isEnabled)
        assertTrue(action.performClick())
    }
    private fun awaitChild(f: RegressionFixture, tag: String) = f.await(tag) {
        f.onMain { (sheet(f).childFragmentManager.findFragmentByTag(tag) as? DialogFragment)?.dialog?.isShowing == true }
    }
    private fun child(f: RegressionFixture, tag: String) = sheet(f).childFragmentManager.findFragmentByTag(tag) as DialogFragment
    private fun sheet(f: RegressionFixture) = sheetOrNull(f)!!
    private fun sheetOrNull(f: RegressionFixture): PlaylistsDialogFragment? {
        var result: PlaylistsDialogFragment? = null
        f.onActivity { result = it.supportFragmentManager.fragments.filterIsInstance<PlaylistsDialogFragment>().firstOrNull() }
        return result
    }
    private fun <T> success(result: PlaylistResult<T>): T {
        assertTrue("Expected success, got $result", result is PlaylistResult.Success)
        return (result as PlaylistResult.Success).value
    }
}

package com.iven.musicplayergo.regression

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.iven.musicplayergo.GoConstants
import com.iven.musicplayergo.GoPreferences
import com.iven.musicplayergo.R
import com.iven.musicplayergo.covers.*
import com.iven.musicplayergo.photos.*
import com.iven.musicplayergo.playlists.*
import com.iven.musicplayergo.preferences.ActiveTabsAdapter
import com.iven.musicplayergo.regression.RegressionFixture.Companion.success
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Reset fixtures are confined by RegressionFixture to disposable emulators. */
@RunWith(AndroidJUnit4::class)
class PlaylistNavigationRepairTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun upgradeThenSettingsHideSurvivesFreshPreferencesWithoutRewritingPlaylists() {
        RegressionFixture.open().use { f ->
            val saved = f.createPlaylist("Kept through migration", "B", "A")
            val prefs = PreferenceManager.getDefaultSharedPreferences(f.context)
            val original = prefs.getString("custom_playlists_v1", null)
            val order = listOf(GoConstants.FOLDERS_TAB, GoConstants.SONGS_TAB, GoConstants.SETTINGS_TAB)
            f.preferences.activeTabsDef = order
            f.preferences.activeTabs = listOf(GoConstants.SONGS_TAB, GoConstants.SETTINGS_TAB)
            prefs.edit().remove("playlist_tab_migrated_v1").putString("unrelated-fixture", "kept").commit()
            val upgraded = GoPreferences(f.context)
            assertEquals(listOf(GoConstants.FOLDERS_TAB, GoConstants.SONGS_TAB, GoConstants.PLAYLIST_TAB, GoConstants.SETTINGS_TAB), upgraded.activeTabsDef)
            assertEquals(listOf(GoConstants.SONGS_TAB, GoConstants.PLAYLIST_TAB, GoConstants.SETTINGS_TAB), upgraded.activeTabs)
            // Drive the actual Settings adapter's row and save path, not just a hand-written hidden list.
            f.onActivity { activity ->
                val adapter = ActiveTabsAdapter()
                val rows = RecyclerView(activity).apply {
                    layoutManager = LinearLayoutManager(activity); this.adapter = adapter
                    measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
                    layout(0, 0, 800, 1000)
                }
                val position = adapter.availableItems.indexOf(GoConstants.PLAYLIST_TAB)
                val holder = rows.findViewHolderForAdapterPosition(position)!!
                assertTrue(holder.itemView.performClick())
                f.preferences.activeTabs = adapter.getUpdatedItems()
            }
            val hidden = f.preferences.activeTabs
            assertFalse(hidden.contains(GoConstants.PLAYLIST_TAB))
            repeat(2) { assertEquals(hidden, GoPreferences(f.context).activeTabs) }
            assertTrue(prefs.getBoolean("playlist_tab_migrated_v1", false))
            assertEquals("kept", prefs.getString("unrelated-fixture", null))
            assertEquals(original, prefs.getString("custom_playlists_v1", null))
            assertEquals(saved, success(f.repository.getPlaylist(saved.id)))
        }
    }

    @Test fun anEarlierNavigationInstallKeepsItsAlreadyHiddenTabWithoutAMarker() {
        RegressionFixture.open().use { f ->
            val available = listOf(GoConstants.PLAYLIST_TAB, GoConstants.SONGS_TAB, GoConstants.SETTINGS_TAB)
            val visible = listOf(GoConstants.SONGS_TAB, GoConstants.SETTINGS_TAB)
            f.preferences.activeTabsDef = available; f.preferences.activeTabs = visible
            PreferenceManager.getDefaultSharedPreferences(f.context).edit().remove("playlist_tab_migrated_v1").commit()
            val restored = GoPreferences(f.context)
            assertEquals(available, restored.activeTabsDef)
            assertEquals(visible, restored.activeTabs)
        }
    }

    @Test fun defaultAndMissingSettingsOrdersMigrateOnlyOnce() {
        RegressionFixture.open().use { f ->
            val prefs = PreferenceManager.getDefaultSharedPreferences(f.context)
            prefs.edit().remove("active_tabs_def_pref").remove("active_tabs_pref").remove("playlist_tab_migrated_v1").commit()
            assertEquals(GoConstants.DEFAULT_ACTIVE_FRAGMENTS, GoPreferences(f.context).activeTabs)
            f.preferences.activeTabsDef = listOf(GoConstants.SONGS_TAB)
            f.preferences.activeTabs = listOf(GoConstants.SONGS_TAB)
            prefs.edit().remove("playlist_tab_migrated_v1").commit()
            val migrated = GoPreferences(f.context)
            assertEquals(listOf(GoConstants.SONGS_TAB, GoConstants.PLAYLIST_TAB), migrated.activeTabs)
            migrated.activeTabs = listOf(GoConstants.SONGS_TAB)
            assertEquals(listOf(GoConstants.SONGS_TAB), GoPreferences(f.context).activeTabs)
        }
    }

    @Test fun actualTabDetailRotationAndSystemBackStayInThePlaylistTab() = restoredBack(first = false)
    @Test fun firstTabDetailRecreationAndSystemBackDoNotExitTheActivity() = restoredBack(first = true)

    private fun restoredBack(first: Boolean) {
        // Settings applies changed tab orders with a fresh Activity, not a saved old pager state.
        val initialTabs = if (first) listOf(GoConstants.PLAYLIST_TAB, GoConstants.SONGS_TAB, GoConstants.SETTINGS_TAB) else null
        try { RegressionFixture.open(initialTabs = initialTabs).use { f ->
            val saved = f.createPlaylist("Restored detail", "A", "B")
            selectActualTab(f, GoConstants.PLAYLIST_TAB)
            selectPlaylist(f, page(f), saved.id)
            val old = page(f)
            if (first) f.scenario.recreate()
            else assertTrue(instrumentation.uiAutomation.setRotation(android.app.UiAutomation.ROTATION_FREEZE_90))
            f.await("restored visible playlist detail") {
                f.onMain { pageOrNull(f)?.let { it !== old && it.isResumed && it.view?.findViewById<TextView>(R.id.playlist_heading)?.text == saved.name } == true }
            }
            if (!first) f.await("device remains landscape after recreation") { landscape(f) }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            f.await("system Back returns to list in the same tab") {
                f.onMain { pageOrNull(f)?.view?.findViewById<View>(R.id.playlist_back)?.visibility == View.GONE }
            }
            f.onActivity {
                assertFalse(it.isFinishing)
                assertEquals(f.preferences.activeTabs.indexOf(GoConstants.PLAYLIST_TAB), it.findViewById<ViewPager2>(R.id.view_pager2).currentItem)
            }
            f.await("list creation action visible after Back") { f.onMain { fullyVisible(page(f).requireView().findViewById(R.id.playlist_primary)) } }
            capture(f, if (first) "pr70-restored-first-tab-list" else "pr70-rotated-back-list")
            assertEquals(saved, success(f.repository.getPlaylist(saved.id)))
        } } finally { instrumentation.uiAutomation.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE) }
    }

    @Test fun pagePhotoSourcesAndDeletePreserveAnotherPlaylistsSharedSourceAndCover() = photoActions(fallback = false)
    @Test fun emptyLibraryDialogHasTheSamePhotoSourcesAndDeleteCleanup() = photoActions(fallback = true)

    private fun photoActions(fallback: Boolean) {
        RegressionFixture.open().use { f ->
            val selected = f.createPlaylist("Delete target", "A")
            val retained = f.createPlaylist("Keep target", "B")
            val target = CoverTarget.Playlist(selected.id); val other = CoverTarget.Playlist(retained.id)
            val photos = PhotoRepositories.get(f.context)
            val shared = PhotoSource("navigation-${selected.id}", "Shared fixture", PhotoSourceKind.SELECTED, listOf("content://navigation-fixture/photo"))
            photos.attach(target, photos.request(target), shared)
            photos.attach(other, photos.request(other), shared)
            val originalOther = photos.snapshot(other)
            val staleRequest = photos.request(target)
            val covers = CoverRepositories.get(f.context)
            val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
            try {
                for (t in listOf(target, other)) assertTrue(covers.setCover(t, CoverImageInput.CroppedBitmap(bitmap), CoverSource.USER_SELECTED) is CoverResult.Success)
                val otherCover = (covers.getCover(other) as CoverResult.Success).value!!
                val originalBytes = covers.coverFile(otherCover).readBytes()
                val host: Fragment
                if (fallback) {
                    f.tracks.keys.toList().forEach(f::deleteAudio); f.refreshLibrary()
                    f.await("library error shown") { f.onMain { fragment(f, GoConstants.ERROR_FRAGMENT_TAG)?.view?.isShown == true } }
                    f.onActivity {
                        val error = it.supportFragmentManager.findFragmentByTag(GoConstants.ERROR_FRAGMENT_TAG)!!
                        val toolbar = error.requireView().findViewById<Toolbar>(R.id.error_toolbar)
                        assertTrue(toolbar.menu.performIdentifierAction(toolbar.menu.getItem(0).itemId, 0))
                    }
                    f.await("fallback is a dialog") { f.onMain { (fragment(f, "custom_playlists") as? PlaylistsDialogFragment)?.dialog?.isShowing == true } }
                    host = f.onMain { fragment(f, "custom_playlists")!! }
                } else { selectActualTab(f, GoConstants.PLAYLIST_TAB); host = page(f) }
                selectPlaylist(f, host, selected.id)
                f.onMain { assertTrue(host.requireView().findViewById<View>(R.id.playlist_photo_sources).performClick()) }
                f.await("source dialog bound to selected target") {
                    f.onMain { (host.childFragmentManager.findFragmentByTag("playlist-photo-sources") as? PhotoSourcesDialogFragment)?.dialog?.isShowing == true }
                }
                capture(f, if (fallback) "pr70-fallback-photo-sources" else "pr70-page-photo-sources")
                f.onMain {
                    val dialog = host.childFragmentManager.findFragmentByTag("playlist-photo-sources") as PhotoSourcesDialogFragment
                    assertEquals(target, dialog.requireArguments().photoTarget())
                    dialog.dismiss()
                    host.requireView().findViewById<View>(R.id.playlist_delete).performClick()
                }
                f.await("delete confirmation") { f.onMain { host.childFragmentManager.findFragmentByTag("playlist_delete") is PlaylistDeleteDialogFragment } }
                f.onMain {
                    val dialog = host.childFragmentManager.findFragmentByTag("playlist_delete") as PlaylistDeleteDialogFragment
                    (dialog.requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                }
                f.await("deleted target scope and cover reclaimed") {
                    success(f.repository.getPlaylists()).none { it.id == selected.id } &&
                        photos.snapshot(target).first.sources.isEmpty() && !covers.hasCover(target)
                }
                assertEquals(originalOther, photos.snapshot(other))
                assertEquals(retained, success(f.repository.getPlaylist(retained.id)))
                assertArrayEquals(originalBytes, covers.coverFile(otherCover).readBytes())
                assertTrue(photos.shareableSources().contains(shared))
                try { photos.validate(target, staleRequest); fail("Deleted target revived an old photo request") } catch (_: PhotoScopeException) { }
            } finally {
                bitmap.recycle()
                photos.deleteTarget(target); photos.deleteTarget(other)
                covers.removeCover(target); covers.removeCover(other)
            }
        }
    }

    @Test fun landscapeScrollReachesATrackAndItsRemoveAction() = compactTracks(largeText = false)
    @Test fun landscapeLargeTextScrollReachesATrackAndItsRemoveAction() = compactTracks(largeText = true)

    private fun compactTracks(largeText: Boolean) {
        require(android.os.Build.HARDWARE in setOf("ranchu", "goldfish")) { "Font fixtures require a disposable emulator" }
        val previousFont = instrumentation.uiAutomation.executeShellCommand("settings get system font_scale").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
        }
        try {
            if (largeText) shell("settings put system font_scale 1.3")
            RegressionFixture.open().use { f ->
                val saved = f.createPlaylist("Compact saved playlist", "A", "B", "C")
                assertTrue(instrumentation.uiAutomation.setRotation(android.app.UiAutomation.ROTATION_FREEZE_90))
                f.await("landscape window after recreation") { landscape(f) }
                selectActualTab(f, GoConstants.PLAYLIST_TAB)
                selectPlaylist(f, page(f), saved.id)
                val host = page(f)
                if (largeText) f.onMain { assertEquals(1.3f, host.resources.configuration.fontScale, 0.02f) }
                // Actual vertical gestures collapse the controls; rows remain a recycled list.
                repeat(4) { swipeUp(f, host.requireView()) }
                f.onMain { host.requireView().findViewById<RecyclerView>(R.id.playlist_rows).scrollToPosition(0) }
                f.await("a complete track and Remove fit in the viewport") {
                    f.onMain {
                        val rows = host.requireView().findViewById<RecyclerView>(R.id.playlist_rows)
                        val row = rows.findViewHolderForAdapterPosition(0)?.itemView
                        row != null && fullyVisible(row) && fullyVisible(row.findViewById(R.id.playlist_track_action))
                    }
                }
                assertTrue("The actual window must still be landscape at the track assertion", landscape(f))
                capture(f, if (largeText) "pr70-landscape-large-tracks" else "pr70-landscape-tracks")
                f.onMain {
                    val rows = host.requireView().findViewById<RecyclerView>(R.id.playlist_rows)
                    assertEquals(3, rows.adapter!!.itemCount)
                    assertTrue(rows.findViewHolderForAdapterPosition(0)!!.itemView.findViewById<View>(R.id.playlist_track_action).performClick())
                }
                f.await("only chosen membership removed") { success(f.repository.getPlaylist(saved.id)).tracks.size == 2 }
                assertEquals(saved.tracks.drop(1), success(f.repository.getPlaylist(saved.id)).tracks)
            }
        } finally {
            instrumentation.uiAutomation.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE)
            shell("settings put system font_scale ${previousFont.takeIf { it.toFloatOrNull() != null } ?: "1.0"}")
        }
    }

    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }; Unit
    }

    private fun capture(f: RegressionFixture, label: String) {
        if (InstrumentationRegistry.getArguments().getString("pr70Screenshots") != "true") return
        instrumentation.uiAutomation.waitForIdle(300, 5_000)
        val frames = java.util.concurrent.CountDownLatch(1)
        instrumentation.runOnMainSync {
            android.view.Choreographer.getInstance().postFrameCallback {
                android.view.Choreographer.getInstance().postFrameCallback { frames.countDown() }
            }
        }
        assertTrue(frames.await(5, java.util.concurrent.TimeUnit.SECONDS))
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            if (label.contains("landscape") || label.contains("rotated")) assertTrue("Capture must be landscape", bitmap.width > bitmap.height)
            val directory = java.io.File(f.context.getExternalFilesDir(null), "regression-evidence").apply { mkdirs() }
            java.io.File(directory, "VibeRegression_${System.nanoTime()}-$label.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    private fun landscape(f: RegressionFixture): Boolean {
        var result = false
        f.onActivity { result = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && it.window.decorView.width > it.window.decorView.height }
        return result
    }

    private fun swipeUp(f: RegressionFixture, view: View) {
        val rect = f.onMain { Rect().also { assertTrue(view.getGlobalVisibleRect(it)) } }
        val x = rect.centerX().toFloat(); val from = rect.bottom - 16f; val to = rect.top + 16f
        val down = SystemClock.uptimeMillis()
        fun event(action: Int, y: Float) {
            val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(e, true)) } finally { e.recycle() }
        }
        event(MotionEvent.ACTION_DOWN, from)
        for (step in 1..12) { SystemClock.sleep(20); event(MotionEvent.ACTION_MOVE, from + (to - from) * step / 12) }
        event(MotionEvent.ACTION_UP, to)
        instrumentation.waitForIdleSync()
    }

    private fun fullyVisible(view: View): Boolean = Rect().let { view.getGlobalVisibleRect(it) && it.height() == view.height && it.width() == view.width && view.height > 0 }
    private fun fragment(f: RegressionFixture, tag: String): Fragment? {
        var result: Fragment? = null
        f.onActivity { result = it.supportFragmentManager.findFragmentByTag(tag) }
        return result
    }
    private fun pageOrNull(f: RegressionFixture): PlaylistsFragment? = f.onMain {
        var page: PlaylistsFragment? = null
        f.onActivity { activity -> page = activity.supportFragmentManager.fragments.filterIsInstance<PlaylistsFragment>().firstOrNull { it.isResumed } }
        page
    }
    private fun page(f: RegressionFixture) = pageOrNull(f) ?: error("Playlist tab is not resumed")
    private fun selectActualTab(f: RegressionFixture, tab: String) {
        f.onActivity { activity ->
            val index = f.preferences.activeTabs.indexOf(tab)
            val tabs = activity.findViewById<TabLayout>(R.id.tab_layout)
            assertTrue(index >= 0)
            assertTrue((tabs.getChildAt(0) as ViewGroup).getChildAt(index).performClick())
        }
        f.await("actual Playlists tab selected and loaded") { f.onMain { pageOrNull(f)?.view?.findViewById<View>(R.id.playlist_refresh)?.isEnabled == true } }
    }
    private fun selectPlaylist(f: RegressionFixture, host: Fragment, id: String) {
        val saved = success(f.repository.getPlaylists()); val index = saved.indexOfFirst { it.id == id }
        assertTrue(index >= 0)
        // A first-position page may already be loaded before fixture repository writes finish.
        f.await("playlist refresh available") { f.onMain { host.view?.findViewById<View>(R.id.playlist_refresh)?.isEnabled == true } }
        f.onMain { assertTrue(host.requireView().findViewById<View>(R.id.playlist_refresh).performClick()) }
        f.await("playlist rows loaded") { f.onMain { host.view?.findViewById<RecyclerView>(R.id.playlist_rows)?.adapter?.itemCount == saved.size } }
        f.onMain { host.requireView().findViewById<RecyclerView>(R.id.playlist_rows).scrollToPosition(index) }
        f.await("playlist row laid out") { f.onMain { host.requireView().findViewById<RecyclerView>(R.id.playlist_rows).findViewHolderForAdapterPosition(index) != null } }
        f.onMain { host.requireView().findViewById<RecyclerView>(R.id.playlist_rows).findViewHolderForAdapterPosition(index)!!.itemView.performClick() }
        f.await("playlist detail ready") { f.onMain { host.requireView().findViewById<TextView>(R.id.playlist_heading).text == saved[index].name } }
    }
}

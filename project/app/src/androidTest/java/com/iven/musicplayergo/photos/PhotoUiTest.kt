package com.iven.musicplayergo.photos

import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iven.musicplayergo.covers.CoverTarget
import com.iven.musicplayergo.regression.RegressionFixture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** App windows only; no synthetic picker result is counted as a real grant. */
@RunWith(AndroidJUnit4::class)
class PhotoUiTest {
    private fun all(view: View): List<View> = listOf(view) + if(view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun views()=WindowInspector.getGlobalWindowViews().flatMap(::all)
    private fun texts()=views().filterIsInstance<TextView>().map { it.text.toString() }
    private fun click(label: String) {
        val view = views().filterIsInstance<TextView>().lastOrNull { it.text.toString() == label } ?: error("Missing action $label")
        if (view is Button) assertTrue(view.performClick()) else {
            val list = view.parent as android.widget.ListView
            assertTrue(list.performItemClick(view, list.getPositionForView(view), 0))
        }
    }
    private fun back(f: RegressionFixture) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // DocumentsUI may restore a nested directory; Back first traverses parents.
        for (attempt in 0 until 8) {
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            instrumentation.waitForIdleSync()
            if (runCatching { f.await("picker transition", 2_000) {
                PhotoSystemUi.hasPackage(f.context.packageName)
            } }.isSuccess) break
        }
        f.await("picker has returned to the app") {
            PhotoSystemUi.hasPackage(f.context.packageName)
        }
        instrumentation.waitForIdleSync()
    }
    @Test fun sourceUiShowsUncertainCountKeepsBindingsOnCancellationAndRecreation() {
        RegressionFixture.open().use { f ->
            val playlist=f.createPlaylist("Photo target A","A");val target=CoverTarget.Playlist(playlist.id)
            val repo=PhotoRepositories.get(f.context)
            val source=PhotoSource("ui-source","Unreachable source",PhotoSourceKind.SELECTED,listOf("content://unavailable/photo"))
            repo.attach(target,repo.request(target),source)
            f.onActivity { PhotoSourcesDialogFragment.newInstance(target,playlist.name).showNow(it.supportFragmentManager,"source-ui") }
            f.await("invalid grant is explained") { f.onMain { texts().any { it.contains("Photo count is unavailable") } } }
            f.onMain {
                assertTrue(texts().containsAll(listOf("Recommend a cover", "Choose a photo directly", "Add photos", "More")))
                assertFalse(texts().contains("Recommendation settings"))
                assertFalse(texts().contains("Find photos by keyword"))
                click("More") }
            f.await("menu item How photos are used is visible") { f.onMain { texts().contains("How photos are used") } }
            f.onMain { click("How photos are used"); click("Got it")
            }
            f.onMain { click("Add photos") }
            f.await("menu item Find photos in Xiaomi Gallery is visible") { f.onMain { texts().contains("Find photos in Xiaomi Gallery") } }
            f.onMain { click("Find photos in Xiaomi Gallery"); click("Back") }
            assertEquals(listOf(source),repo.snapshot(target).second)
            f.onMain { click("Add photos") }
            f.await("menu item Find photos in Xiaomi Gallery is visible") { f.onMain { texts().contains("Find photos in Xiaomi Gallery") } }
            f.onMain { click("Find photos in Xiaomi Gallery"); click("Choose a photo folder") }
            f.await("album guide folder options displayed") { f.onMain { texts().contains("Choose a photo folder") } }
            f.onMain { click("Cancel") }
            assertEquals(listOf(source),repo.snapshot(target).second)
            f.onMain { click("Add photos") }
            f.await("menu item Find photos in Xiaomi Gallery is visible") { f.onMain { texts().contains("Find photos in Xiaomi Gallery") } }
            f.onMain { click("Find photos in Xiaomi Gallery"); click("Choose the prepared photos") }
            f.await("album guide opens ordinary picker") { PhotoSystemUi.hasPackage("providers.media") }
            back(f)
            assertEquals(listOf(source),repo.snapshot(target).second)
            f.onMain { click("Add photos") }
            f.await("menu item Choose a photo folder is visible") { f.onMain { texts().contains("Choose a photo folder") } }
            f.onMain { click("Choose a photo folder") }
            f.onMain { click("Cancel") }
            assertEquals(listOf(source),repo.snapshot(target).second)
            f.scenario.recreate()
            f.await("source dialog restored") { f.onMain { texts().any { it.contains("Unreachable source") } } }
            f.onMain { click("More") }
            f.await("menu item Refresh photos is visible") { f.onMain { texts().contains("Refresh photos") } }
            f.onMain { click("Refresh photos") }
            f.await("refreshed invalid grant") { f.onMain { texts().any { it.contains("Choose these photos again") } } }
            f.onMain { click("More") }
            f.await("menu item Restore removed photos is visible") { f.onMain { texts().contains("Restore removed photos") } }
            f.onMain { click("Restore removed photos") }
            f.onMain { click("Add photos") }
            f.await("menu item Find photos by keyword is visible") { f.onMain { texts().contains("Find photos by keyword") } }
            f.onMain { click("Find photos by keyword") }
            f.onMain { views().filterIsInstance<EditText>().last().setText("private query");click("Cancel") }
            assertEquals(listOf(source),repo.snapshot(target).second)
            f.onMain { click("Add photos") }
            f.await("menu item Find photos by keyword is visible") { f.onMain { texts().contains("Find photos by keyword") } }
            f.onMain { click("Find photos by keyword") }
            assertTrue(f.onMain { views().filterIsInstance<EditText>().last().text.toString()=="private query" })
            f.onMain { click("Suggest keywords from an atmosphere") }
            f.await("manual search atmosphere choices") { f.onMain { texts().contains("calm") } }
            f.onMain {
                val text=views().filterIsInstance<TextView>().last { it.text.toString()=="calm" }
                val list=text.parent as android.widget.ListView
                list.performItemClick(text,list.getPositionForView(text),0)
            }
            f.await("editable suggestions") { f.onMain { views().filterIsInstance<EditText>().last().text.contains("lakeside") } }
            f.onMain { views().filterIsInstance<EditText>().last().setText(" ");click("Find photos") }
            f.await("empty query stays in app") { f.onMain { texts().any { it.contains("Enter keywords") } } }
            f.await("app window after empty search dialog closes") { PhotoSystemUi.hasPackage(f.context.packageName) }
            f.onMain { click("Add photos") }
            f.await("menu item Find photos by keyword is visible") { f.onMain { texts().contains("Find photos by keyword") } }
            f.onMain { click("Find photos by keyword");views().filterIsInstance<EditText>().last().setText("edited lake") }
            f.onMain { click("Find photos") }
            f.await("ordinary picker launched") { PhotoSystemUi.hasPackage("providers.media") }
            back(f)
            assertEquals(listOf(source),repo.snapshot(target).second)
            f.onMain { click("Add photos") }
            f.await("menu item Choose photo files is visible") { f.onMain { texts().contains("Choose photo files") } }
            f.onMain { click("Choose photo files") }
            f.await("document picker launched") { PhotoSystemUi.hasPackage("documentsui") }
            back(f)
            assertEquals(listOf(source),repo.snapshot(target).second)
            f.onMain { click("Choose a photo directly") }
            f.await("manual picker launched") { PhotoSystemUi.hasPackage("providers.media") }
            back(f)
            f.await("cancelled manual crop dismissed") { f.onMain {
                texts().contains("Close") && !texts().contains(f.context.getString(com.iven.musicplayergo.R.string.cover_crop_title))
            } }
            f.onMain { click("Close") }
            repo.deleteTarget(target)
        }
    }
    @Test fun manualAtmosphereWithEmptyScopeDoesNotInventRecommendation() {
        RegressionFixture.open().use { f ->
            val playlist=f.createPlaylist("Empty photos","A");val target=CoverTarget.Playlist(playlist.id)
            f.onActivity { PhotoRecommendationsDialogFragment.newInstance(target).showNow(it.supportFragmentManager,"recommend-ui") }
            f.onMain { click("Choose an atmosphere yourself") }
            f.onMain {
                val text=views().filterIsInstance<TextView>().last { it.text.toString()=="calm" }
                // List row click through its ListView parent, as Android dispatches it.
                val parent=text.parent as android.widget.ListView
                parent.performItemClick(text,parent.getPositionForView(text),0)
            }
            f.await("empty scope explains no readable photos") { f.onMain { texts().any { it.contains("No photos are available") } } }
            f.onMain { click("Choose a song") }
            f.await("representative chooser shows actual title") { f.onMain { texts().any { it == "Choose one song" } } }
            f.onMain { click("Cancel") }
            f.onMain { click("Cancel analysis") }
            assertTrue(f.onMain { texts().any { it.contains("cover is unchanged") } })
            f.onMain { click("Close") }
        }
    }
    @Test fun changedAlbumOffersExplicitEmptyRebindAndCancellationPreservesOldScope() {
        RegressionFixture.open().use { f ->
            val song=f.songs.first()
            val current=CoverTarget.Album(song.albumId!!,song.artist,song.album)
            val old=current.copy(artistSnapshot="Previous album artist")
            val repo=PhotoRepositories.get(f.context)
            val source=PhotoSource("old-album-source","Old album",PhotoSourceKind.SELECTED,listOf("content://unavailable/photo"))
            repo.attach(old,repo.request(old),source)
            val oldRequest=repo.request(old)
            f.onActivity { PhotoSourcesDialogFragment.newInstance(current,"Current album").showNow(it.supportFragmentManager,"rebind-ui") }
            f.await("changed album offers recovery") { f.onMain { texts().contains("Choose photos again") } }
            f.onMain { click("Choose photos again"); click("Cancel") }
            assertEquals(listOf(source),repo.snapshot(old).second)
            f.onMain { click("Choose photos again"); click("Continue") }
            f.await("confirmed current album is empty") { runCatching { repo.snapshot(current).second.isEmpty() }.getOrDefault(false) }
            assertThrows(PhotoScopeException::class.java) { repo.validate(current,oldRequest) }
            f.onMain { click("Close") }; repo.deleteTarget(current)
        }
    }
}

package com.iven.musicplayergo.covers

import android.graphics.Bitmap
import android.graphics.Color
import android.app.Activity
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.media.MediaMetadataCompat
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.toBitmap
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iven.musicplayergo.GoConstants
import com.iven.musicplayergo.R
import com.iven.musicplayergo.dialogs.NowPlaying
import com.iven.musicplayergo.fragments.DetailsFragment
import com.iven.musicplayergo.regression.RegressionFixture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoverUiRegressionTest {
    @Test fun actualPickerDialogUsesSquareMeasuredViewportAndSavesUndistortedCrop() {
        RegressionFixture.open().use { f ->
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val uri = requireNotNull(f.context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "square-crop-${System.nanoTime()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                }))
            val image = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
            Canvas(image).apply {
                drawColor(Color.WHITE)
                drawRect(110f, 60f, 190f, 140f, Paint().apply { color = Color.RED })
            }
            f.context.contentResolver.openOutputStream(uri)!!.use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
            // Replace only the external picker result; the production dialog decodes, measures,
            // previews, crops and saves the actual image through its normal activity-result path.
            val monitor = instrumentation.addMonitor(IntentFilter(MediaStore.ACTION_PICK_IMAGES).apply { addDataType("image/*") },
                Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)), true)
            val target = CoverTarget.Playlist(java.util.UUID.randomUUID().toString())
            val repo = CoverRepositories.get(f.context)
            val fragment = CoverCropDialogFragment.forPlaylist(target.playlistId)
            try {
                f.onActivity { fragment.show(it.supportFragmentManager, "crop-regression") }
                f.await("picker result decoded in actual crop dialog") {
                    f.onMain { monitor.hits > 0 && (fragment.dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled == true }
                }
                assertTrue("The system picker boundary was exercised", monitor.hits > 0)
                f.onMain { fragment.dialog!!.window!!.setLayout((280 * f.context.resources.displayMetrics.density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT) }
                fun crop(view: View): CropImageView? {
                    if (view is CropImageView) return view
                    if (view is ViewGroup) for (i in 0 until view.childCount) crop(view.getChildAt(i))?.let { return it }
                    return null
                }
                f.await("constrained real dialog finishes square measurement") {
                    f.onMain {
                        val view = crop(fragment.dialog!!.window!!.decorView)
                        view != null && view.width > 0 && view.width == view.height
                    }
                }
                f.onMain { (fragment.dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick() }
                f.await("actual crop saved") { repo.hasCover(target) }
                val saved = (repo.getCover(target) as CoverResult.Success).value!!
                val decoded = requireNotNull(BitmapFactory.decodeFile(repo.coverFile(saved).path))
                try {
                    assertEquals(1024, decoded.width)
                    assertEquals(1024, decoded.height)
                    val horizontal = (0 until 1024).count { red(decoded.getPixel(it, 512)) }
                    val vertical = (0 until 1024).count { red(decoded.getPixel(512, it)) }
                    assertTrue("Saved shape remains square", kotlin.math.abs(horizontal - vertical) <= 2)
                    assertTrue(horizontal in 405..415)
                } finally { decoded.recycle() }
            } finally {
                instrumentation.removeMonitor(monitor)
                f.onMain { fragment.dismissAllowingStateLoss() }
                repo.removeCover(target)
                f.context.contentResolver.delete(uri, null, null)
            }
        }
    }

    @Test fun changingAndRemovingCoverKeepsAlbumSearchWorkingAndRefreshesMediaMetadata() {
        RegressionFixture.open(taggedAudio = true).use { f ->
            f.preferences.isCovers = false
            val song = f.tracks.getValue("A")
            val target = CoverTarget.Album(song.albumId!!, song.artist, song.album)
            val repo = CoverRepositories.get(f.context)
            try {
                f.play("A")
                f.expectPlaying("A")
                f.onActivity { it.onArtistOrFolderSelected(song.album!!, GoConstants.ALBUM_VIEW) }
                var details: DetailsFragment? = null
                f.await("album details visible") {
                    f.onActivity { details = it.supportFragmentManager.findFragmentByTag(GoConstants.DETAILS_FRAGMENT_TAG) as? DetailsFragment }
                    f.onMain { details?.view?.findViewById<RecyclerView>(R.id.songs_rv)?.adapter?.itemCount == 1 }
                }
                val fragment = details!!
                val search = f.onMain {
                    val item = fragment.requireView().findViewById<Toolbar>(R.id.details_toolbar).menu.findItem(R.id.action_search)
                    item.expandActionView()
                    (item.actionView as SearchView).apply { setQuery("", false) }
                }
                saveColor(repo, target, Color.RED)
                f.onMain { fragment.childFragmentManager.setFragmentResult(CoverCropDialogFragment.RESULT_KEY, Bundle()) }
                f.await("new cover reaches active media session without a track switch") {
                    f.onMain {
                        val bitmap = f.service.getMediaSession()?.controller?.metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
                        bitmap != null && red(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
                    }
                }
                f.onMain {
                    val toolbar = fragment.requireView().findViewById<Toolbar>(R.id.details_toolbar)
                    assertSame(search, toolbar.menu.findItem(R.id.action_search).actionView)
                    search.setQuery("no-song-matches-this-cover-regression", false)
                    assertEquals(0, fragment.requireView().findViewById<RecyclerView>(R.id.songs_rv).adapter!!.itemCount)
                    search.setQuery("", false)
                    assertEquals(1, fragment.requireView().findViewById<RecyclerView>(R.id.songs_rv).adapter!!.itemCount)
                    assertTrue(toolbar.menu.findItem(R.id.action_remove_cover).isVisible)
                    assertTrue(toolbar.menu.performIdentifierAction(R.id.action_remove_cover, 0))
                }
                f.await("restore-default refreshes metadata and menu") {
                    f.onMain {
                        !repo.hasCover(target) &&
                            !fragment.requireView().findViewById<Toolbar>(R.id.details_toolbar).menu.findItem(R.id.action_remove_cover).isVisible &&
                            f.service.getMediaSession()?.controller?.metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART) == null
                    }
                }
                f.onMain {
                    assertSame(search, fragment.requireView().findViewById<Toolbar>(R.id.details_toolbar).menu.findItem(R.id.action_search).actionView)
                    search.setQuery("still-no-match", false)
                    assertEquals(0, fragment.requireView().findViewById<RecyclerView>(R.id.songs_rv).adapter!!.itemCount)
                    search.setQuery("", false)
                    assertEquals(1, fragment.requireView().findViewById<RecyclerView>(R.id.songs_rv).adapter!!.itemCount)
                }
            } finally { repo.removeCover(target) }
        }
    }

    @Test fun nowPlayingSwitchesCustomArtworkWithEmbeddedCoversOffAndClearsMissingAlbum() {
        RegressionFixture.open(taggedAudio = true).use { f ->
            f.preferences.isCovers = false
            val targets = listOf("A", "B").map { f.tracks.getValue(it).let { song -> CoverTarget.Album(song.albumId!!, song.artist, song.album) } }
            val repo = CoverRepositories.get(f.context)
            try {
                saveColor(repo, targets[0], Color.RED)
                saveColor(repo, targets[1], Color.BLUE)
                f.play("A", "B", "C")
                f.expectPlaying("A")
                f.onActivity { assertTrue(it.findViewById<View>(R.id.playing_song_container).performClick()) }
                var sheet: NowPlaying? = null
                f.await("Now Playing shows album A") {
                    f.onActivity { sheet = it.supportFragmentManager.findFragmentByTag(NowPlaying.TAG_MODAL) as? NowPlaying }
                    f.onMain { color(sheet?.view?.findViewById(R.id.np_cover))?.let(::red) == true }
                }
                f.onMain { assertTrue(sheet!!.requireView().findViewById<View>(R.id.np_skip_next).performClick()) }
                f.expectPlaying("B")
                f.await("same Now Playing sheet shows album B") {
                    f.onMain { color(sheet?.view?.findViewById(R.id.np_cover))?.let(::blue) == true }
                }
                f.onActivity { it.onPlaylistSelected(listOf(f.tracks.getValue("C").copy(albumId = null))) }
                f.expectPlaying("C")
                f.await("unknown album clears B's artwork") {
                    f.onMain { color(sheet?.view?.findViewById(R.id.np_cover))?.let { !blue(it) && !red(it) } == true }
                }
                f.onMain { sheet?.dismiss() }
            } finally { targets.forEach { repo.removeCover(it) } }
        }
    }

    private fun saveColor(repo: CustomCoverRepository, target: CoverTarget, color: Int) {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        try { assertTrue(repo.setCover(target, CoverImageInput.CroppedBitmap(bitmap), CoverSource.USER_SELECTED) is CoverResult.Success) }
        finally { bitmap.recycle() }
    }
    private fun color(view: ImageView?): Int? = view?.drawable?.toBitmap(16, 16)?.getPixel(8, 8)
    private fun red(color: Int) = Color.red(color) > 200 && Color.blue(color) < 40
    private fun blue(color: Int) = Color.blue(color) > 200 && Color.red(color) < 40
}

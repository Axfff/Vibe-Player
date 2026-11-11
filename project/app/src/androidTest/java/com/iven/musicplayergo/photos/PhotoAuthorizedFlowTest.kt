package com.iven.musicplayergo.photos

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iven.musicplayergo.R
import com.iven.musicplayergo.covers.*
import com.iven.musicplayergo.regression.RegressionFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/** Real DocumentsUI consent and provider I/O. Synthetic colored images test mechanics,
 * not model quality; the optional licensed four-case inference test is separate. */
@RunWith(AndroidJUnit4::class)
class PhotoAuthorizedFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun views() = WindowInspector.getGlobalWindowViews().flatMap(::descendants)
    private fun texts() = views().filterIsInstance<TextView>().map { it.text.toString() }
    private fun click(label: String) = clickMatching { it == label }
    private fun clickMatching(match: (String) -> Boolean) {
        val view = views().filterIsInstance<TextView>().last { match(it.text.toString()) }
        if (view is Button) assertTrue(view.performClick()) else {
            val parent = view.parent as ListView
            assertTrue(parent.performItemClick(view, parent.getPositionForView(view), 0))
        }
    }
    private fun systemClick(f: RegressionFixture, label: String) {
        val bounds = android.graphics.Rect()
        instrumentation.uiAutomation.waitForIdle(300, 5_000)
        try { f.await("system control $label", 20_000) {
            val node = PhotoSystemUi.nodes().firstOrNull { it.text?.toString().equals(label, ignoreCase = true) }
                ?: return@await false
            node.getBoundsInScreen(bounds)
            !bounds.isEmpty
        } } catch (e: AssertionError) { PhotoSystemUi.diagnose(); throw e }
        // Tap bounds read from the actual accessibility node, including breadcrumb links
        // whose text node does not implement ACTION_CLICK itself.
        instrumentation.uiAutomation.executeShellCommand("input tap ${bounds.centerX()} ${bounds.centerY()}").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
        }
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(300, 5_000)
    }
    @Test fun realTreeGrantBoundsScopeAndCropRechecksRevocation() {
        RegressionFixture.open().use { f ->
            val folder = "PhotoGrant_${System.nanoTime()}"
            val owned = mutableListOf<Uri>()
            val resolver = f.context.contentResolver
            val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
            val a = CoverTarget.Playlist(f.createPlaylist("Source A", "A").id)
            val b = CoverTarget.Playlist(f.createPlaylist("Source B", "B").id)
            val repo = PhotoRepositories.get(f.context)
            val covers = CoverRepositories.get(f.context)
            fun show(target: CoverTarget) = f.onActivity {
                PhotoSourcesDialogFragment.newInstance(target, if(target == a) "Source A" else "Source B")
                    .showNow(it.supportFragmentManager, "authorized-source")
            }
            try {
                // Two direct images and one nested image establish the recursion boundary.
                for (index in 0..2) {
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "sample_$index.png")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$folder/" + if(index == 2) "nested/" else "")
                    })!!
                    owned += uri
                    resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                show(a)
                f.onMain { click("添加照片") }
            f.await("menu item 选择照片文件夹 is visible") { f.onMain { texts().contains("选择照片文件夹") } }
            f.onMain { click("选择照片文件夹"); click("选择目录") }
                systemClick(f, "Pictures")
                systemClick(f, folder)
                f.await("DocumentsUI entered owned directory") {
                    PhotoSystemUi.nodes().any { it.text?.toString() == "Files in $folder" }
                }
                systemClick(f, "USE THIS FOLDER")
                systemClick(f, "ALLOW")
                f.await("target-specific confirmation") { f.onMain { texts().any { it.contains("目标：Source A") } } }
                f.onMain { click("确认加入") }
                f.await("two direct photos") { f.onMain { texts().any { it.contains("当前可用 2 张") } } }
                val source = repo.snapshot(a).second.single()
                assertTrue(PhotoAccessManager(f.context).hasGrant(Uri.parse(source.uris.single())))
                assertTrue(repo.snapshot(b).second.isEmpty())
                val provider = PhotoCandidateProvider(f.context)
                val direct = runBlocking { provider.enumerate(source) }
                assertEquals(2, direct.photos.size)
                assertEquals(3, runBlocking { provider.enumerate(source.copy(recursive = true)) }.photos.size)
                assertEquals(PhotoReadState.BUDGET_REACHED, runBlocking { provider.enumerate(source, limit = 1) }.state)
                assertEquals(PhotoReadState.BUDGET_REACHED, runBlocking { provider.enumerate(source, budgetMs = 0) }.state)
                assertEquals(1, runBlocking { provider.enumerate(source, setOf(direct.photos.first().id)) }.photos.size)
                f.scenario.recreate()
                f.await("real grant survives recreation") { f.onMain { texts().any { it.contains("当前可用 2 张") } } }
                f.onMain { clickMatching { it.startsWith("照片文件夹 ·") } }
                f.await("candidate action menu") { f.onMain { texts().any { it == "查看照片" } } }
                f.onMain { clickMatching { it == "查看照片" } }
                f.await("readable thumbnails") { f.onMain { views().filterIsInstance<ImageView>().count { it.contentDescription?.startsWith("候选 ") == true } == 2 } }
                f.onMain { views().filterIsInstance<ImageView>().first { it.contentDescription?.startsWith("候选 ") == true }.performClick(); click("移除") }
                f.await("one excluded candidate") { f.onMain { texts().any { it.contains("当前可用 1 张") } } }
                f.onMain { click("更多") }
            f.await("menu item 恢复已移除候选 is visible") { f.onMain { texts().contains("恢复已移除候选") } }
            f.onMain { click("恢复已移除候选") }
                f.await("exclusion restored") { f.onMain { texts().any { it.contains("当前可用 2 张") } } }
                f.onMain { click("关闭") }
                show(b)
                f.onMain { click("添加照片") }
            f.await("menu item 共用已有来源 is visible") { f.onMain { texts().contains("共用已有来源") } }
            f.onMain { click("共用已有来源") }
                f.await("share source list") { f.onMain { texts().any { it.endsWith(source.id.take(6)) } } }
                f.onMain { clickMatching { it.endsWith(source.id.take(6)) } }
                f.await("sharing consent") { f.onMain { texts().contains("共用") } }
                f.onMain { click("共用") }
                f.await("shared source bound") { repo.snapshot(b).second == listOf(source) }
                f.onMain { click("关闭") }
                show(a)
                f.await("source row") { f.onMain { texts().any { it.startsWith("照片文件夹 ·") } } }
                f.onMain { clickMatching { it.startsWith("照片文件夹 ·") } }
                f.await("removal action menu") { f.onMain { texts().contains("从当前目标移除来源") } }
                f.onMain { click("从当前目标移除来源") }
                f.await("A detached only") { repo.snapshot(a).second.isEmpty() }
                assertEquals(listOf(source), repo.snapshot(b).second)
                assertEquals(2, runBlocking { provider.enumerate(source) }.photos.size)
                f.onMain { click("关闭") }
                val photo = direct.photos.first()
                val bytes = SafePhotoDecoder(f.context).bytes(Uri.parse(photo.uri))
                val fingerprint = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
                // A provider can change between verification and preview decoding.
                // Decode the returned verified snapshot, then reject the new content.
                val requestBeforeChange = repo.request(b)
                val verified = runBlocking { verifyCandidate(f.context,b,requestBeforeChange,photo.uri,fingerprint) }
                assertArrayEquals(bytes,verified)
                bitmap.eraseColor(Color.MAGENTA)
                owned.forEach { uri -> resolver.openOutputStream(uri,"wt")!!.use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
                val preview = SafePhotoDecoder(f.context).decode(verified,2048)
                try { assertEquals(Color.CYAN,preview.getPixel(0,0)) } finally { preview.recycle() }
                assertThrows(IllegalStateException::class.java) {
                    runBlocking { verifyCandidate(f.context,b,requestBeforeChange,photo.uri,fingerprint) }
                }
                bitmap.eraseColor(Color.CYAN)
                owned.forEach { uri -> resolver.openOutputStream(uri,"wt")!!.use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
                fun crop() {
                    val request = repo.request(b)
                    f.onActivity { CoverCropDialogFragment.forCandidate(b, photo.uri, request, fingerprint).showNow(it.supportFragmentManager, "guarded-crop") }
                    f.await("candidate preview ready") { f.onMain { texts().contains(f.context.getString(R.string.cover_crop_help)) } }
                }
                crop()
                f.scenario.recreate()
                f.await("crop restored with fixed target") { f.onMain { texts().contains(f.context.getString(R.string.cover_crop_help)) } }
                f.onMain { click(f.context.getString(android.R.string.cancel)) }
                assertFalse(covers.hasCover(b))
                crop()
                f.onMain { click(f.context.getString(R.string.cover_save)) }
                f.await("confirmed recommended cover saved") { covers.hasCover(b) }
                val saved = (covers.getCover(b) as CoverResult.Success).value!!
                assertEquals(CoverSource.RECOMMENDED, saved.source)
                assertFalse(covers.hasCover(a))
                crop()
                repo.remove(b, source.id)
                f.onMain { click(f.context.getString(R.string.cover_save)) }
                f.await("revoked preview cannot save") { f.onMain { texts().any { it.contains("SOURCE_NOT_READABLE") } } }
                assertEquals(saved, (covers.getCover(b) as CoverResult.Success).value)
                f.onMain { click(f.context.getString(android.R.string.cancel)) }
                show(b)
                f.onMain { click("更多") }
            f.await("menu item 恢复默认封面 is visible") { f.onMain { texts().contains("恢复默认封面") } }
            f.onMain { click("恢复默认封面"); click("恢复") }
                f.await("default cover restored") { !covers.hasCover(b) }
                f.onMain { click("关闭") }
                assertTrue(PhotoAccessManager(f.context).hasGrant(Uri.parse(source.uris.single())))
            } finally {
                repo.deleteTarget(a); repo.deleteTarget(b)
                covers.removeCover(a); covers.removeCover(b)
                owned.forEach { resolver.delete(it, null, null) }
                bitmap.recycle()
            }
        }
    }
}

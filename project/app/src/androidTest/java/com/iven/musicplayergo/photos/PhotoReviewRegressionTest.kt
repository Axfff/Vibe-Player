package com.iven.musicplayergo.photos

import android.content.ContentValues
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.*
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iven.musicplayergo.R
import com.iven.musicplayergo.covers.*
import com.iven.musicplayergo.regression.RegressionFixture
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** Disposable-emulator UI/permission mechanics. Colored photos and controlled ranking
 * outputs are test fixtures, not real model inference or relevance-quality evidence. */
@RunWith(AndroidJUnit4::class)
class PhotoReviewRegressionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun all(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun views() = WindowInspector.getGlobalWindowViews().flatMap(::all)
    private fun texts() = views().filterIsInstance<TextView>().map { it.text.toString() }
    private fun click(label: String) {
        val view = views().filterIsInstance<TextView>().last { it.text.toString() == label }
        if (view is Button) assertTrue(view.performClick()) else {
            val list = view.parent as ListView
            assertTrue(list.performItemClick(view, list.getPositionForView(view), 0))
        }
    }
    private fun systemClick(f: RegressionFixture, label: String) {
        val bounds = Rect()
        instrumentation.uiAutomation.waitForIdle(300, 5_000)
        f.await("system control $label", 20_000) {
            val node = PhotoSystemUi.nodes().firstOrNull { it.text?.toString().equals(label, true) }
                ?: return@await false
            node.getBoundsInScreen(bounds)
            !bounds.isEmpty
        }
        instrumentation.uiAutomation.executeShellCommand("input tap ${bounds.centerX()} ${bounds.centerY()}").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes() }
        }
        instrumentation.waitForIdleSync()
    }

    @Test fun coverControlsUseEnglishFallbackAndPreserveChineseLocale() {
        val context = instrumentation.targetContext
        val ids = listOf(R.string.cover_crop_title, R.string.cover_crop_preview, R.string.cover_crop_loading,
            R.string.cover_crop_help, R.string.cover_save, R.string.cover_saving, R.string.cover_error_decode,
            R.string.cover_error_save, R.string.cover_error_source, R.string.cover_error_target,
            R.string.cover_set, R.string.cover_change, R.string.cover_remove, R.string.cover_removed, R.string.photo_sources)
        fun localized(tag: String) = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(tag))
        })
        val english = localized("en")
        for (tag in listOf("en", "fr", "zu")) for (id in ids) {
            val value = localized(tag).getString(id)
            assertEquals("English fallback for ${context.resources.getResourceEntryName(id)} / $tag", english.getString(id), value)
            assertFalse(value.any { it in '\u3400'..'\u9fff' })
        }
        assertEquals("Adjust cover", english.getString(R.string.cover_crop_title))
        assertEquals("Set as cover", english.getString(R.string.cover_save))
        assertEquals("调整封面", localized("zh-CN").getString(R.string.cover_crop_title))
        assertEquals("设为封面", localized("zh-CN").getString(R.string.cover_save))
    }

    @Test fun authorizedPhotosRecoverModelSetupAndKeepSixthResultExplanation() {
        RegressionFixture.open().use { f ->
            val playlist = f.createPlaylist("Photo review regression", "A")
            val target = CoverTarget.Playlist(playlist.id)
            val repo = PhotoRepositories.get(f.context)
            val covers = CoverRepositories.get(f.context)
            val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
            val owned = mutableListOf<Uri>()
            val folder = "PhotoReview_${System.nanoTime()}"
            val model = PhotoModelStore(f.context).file(PhotoModel.PHOTO)
            val backup = File(model.parentFile, "review-original-${System.nanoTime()}.bak")
            val hadModel = model.exists()
            if (hadModel) assertTrue(model.renameTo(backup))
            try {
                val cover = (covers.setCover(target, CoverImageInput.CroppedBitmap(bitmap), CoverSource.USER_SELECTED) as CoverResult.Success).value
                val originalCover = covers.coverFile(cover).readBytes()
                repeat(6) { index ->
                    val uri = f.context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "review_$index.png")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$folder/")
                    })!!
                    owned += uri
                    f.context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                f.onActivity { PhotoSourcesDialogFragment.newInstance(target, playlist.name).showNow(it.supportFragmentManager, "review-sources") }
                f.onMain { click("添加照片") }
                f.await("folder action") { f.onMain { texts().contains("选择照片文件夹") } }
                f.onMain { click("选择照片文件夹"); click("选择目录") }
                systemClick(f, "Pictures"); systemClick(f, folder)
                systemClick(f, "USE THIS FOLDER"); systemClick(f, "ALLOW")
                f.await("target consent") { f.onMain { texts().any { it.contains("用于：${playlist.name}") } } }
                f.onMain { click("添加") }
                f.await("six readable authorized photos") { f.onMain { texts().any { it.contains("当前可用 6 张") } } }
                val originalScope = repo.snapshot(target)
                val source = originalScope.second.single()
                val photos = runBlocking { PhotoCandidateProvider(f.context).enumerate(source).photos }
                assertEquals(6, photos.size)
                assertTrue(PhotoAccessManager(f.context).hasGrant(Uri.parse(source.uris.single())))
                f.onMain { click("关闭") }

                // Missing and corrupt ONNX are distinct setup states, never an instruction
                // to re-authorize a perfectly readable source. Neither case starts inference.
                for (invalid in listOf(false, true)) {
                    if (invalid) { model.parentFile!!.mkdirs(); model.writeBytes(byteArrayOf(1, 2, 3)) }
                    assertEquals(if (invalid) PhotoModelStatus.INVALID else PhotoModelStatus.MISSING,
                        runBlocking { PhotoModelStore(f.context).inspect()[PhotoModel.PHOTO] })
                    f.onActivity { PhotoRecommendationsDialogFragment.newInstance(target).showNow(it.supportFragmentManager, "review-recommend") }
                    f.onMain { click("自己选氛围") }
                    f.await("manual atmosphere options") { f.onMain { texts().contains("舒缓") } }
                    f.onMain { click("舒缓") }
                    f.await("model setup guidance") { f.onMain { texts().contains(f.context.getString(R.string.photo_model_setup_required)) } }
                    f.onMain {
                        assertFalse(texts().contains("照片暂时打不开，请检查照片或重新添加。"))
                        assertEquals(0, views().filterIsInstance<ImageView>().count { it.contentDescription == "推荐照片" })
                        click(f.context.getString(R.string.photo_model_setup_action))
                    }
                    f.await("actual model settings opened") { f.onMain {
                        texts().contains("推荐功能设置") && texts().any { it.startsWith(PhotoModel.PHOTO.title + " ·") }
                    } }
                    f.onMain { click("返回") }
                    f.await("model settings closed") { f.onMain { !texts().contains("推荐功能设置") } }
                    f.onMain { click("关闭") }
                    f.await("recommendations closed") { f.onMain { !texts().contains("自己选氛围") } }
                    assertEquals(originalScope, repo.snapshot(target))
                    assertArrayEquals(originalCover, covers.coverFile(cover).readBytes())
                }

                lateinit var dialog: PhotoRecommendationsDialogFragment
                f.onActivity { activity ->
                    dialog = PhotoRecommendationsDialogFragment.newInstance(target)
                    dialog.showNow(activity.supportFragmentManager, "review-pages")
                }
                val ranked = photos.mapIndexed { index, photo ->
                    val bytes = SafePhotoDecoder(f.context).bytes(Uri.parse(photo.uri))
                    val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
                    RankedPhoto(PhotoFeatures(photo, hash, "ui-test-only", emptyList()), (index + 1) / 10f, listOf("Evidence ${index + 1}"))
                }
                val request = repo.request(target)
                lateinit var render: Job
                f.onMain { render = dialog.lifecycleScope.launch { dialog.showRecommendations(ranked, request, "Controlled UI result") } }
                runBlocking { render.join() }
                fun imageCount() = views().filterIsInstance<ImageView>().count { it.contentDescription == "推荐照片" }
                assertEquals(5, f.onMain { imageCount() })
                f.onMain { click("继续查看") }
                f.await("sixth result is displayed") { f.onMain { imageCount() == 6 } }
                f.onMain { click("更多") }
                f.await("analysis details action") { f.onMain { texts().contains("分析详情") } }
                f.onMain { click("分析详情") }
                f.await("sixth result has both reason and score") { f.onMain {
                    texts().any { it.contains("Evidence 6") && it.contains("0.60") }
                } }
                f.onMain {
                    val details = texts().single { it.contains("Controlled UI result") }
                    for (index in 1..6) assertTrue(details.contains("$index. Evidence $index"))
                    click("知道了"); click("关闭")
                }
                assertEquals(originalScope, repo.snapshot(target))
                assertArrayEquals(originalCover, covers.coverFile(cover).readBytes())
            } finally {
                model.delete()
                if (hadModel) assertTrue(backup.renameTo(model))
                owned.forEach { f.context.contentResolver.delete(it, null, null) }
                covers.removeCover(target); repo.deleteTarget(target); bitmap.recycle()
            }
        }
    }
}

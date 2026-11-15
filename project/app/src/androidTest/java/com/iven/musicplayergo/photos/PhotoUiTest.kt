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
    private fun click(label: String) { val button=views().filterIsInstance<Button>().lastOrNull { it.text.toString()==label } ?: error("Missing button $label");assertTrue(button.performClick()) }
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
            f.await("invalid grant is explained") { f.onMain { texts().any { it.contains("数量未知") } } }
            f.onMain { click("小米相册搜索后添加"); click("返回") }
            assertEquals(listOf(source),repo.snapshot(target).second)
            f.onMain { click("小米相册搜索后添加"); click("授权照片文件夹") }
            f.await("album guide folder options displayed") { f.onMain { texts().contains("选择照片文件夹") } }
            f.onMain { click("取消") }
            assertEquals(listOf(source),repo.snapshot(target).second)
            f.onMain { click("小米相册搜索后添加"); click("选择整理好的照片") }
            f.await("album guide opens ordinary picker") { PhotoSystemUi.hasPackage("providers.media") }
            back(f)
            assertEquals(listOf(source),repo.snapshot(target).second)
            f.onMain { click("添加照片文件夹") }
            f.onMain { click("取消") }
            assertEquals(listOf(source),repo.snapshot(target).second)
            f.scenario.recreate()
            f.await("source dialog restored") { f.onMain { texts().any { it.contains("Unreachable source") } } }
            f.onMain { click("刷新目录与权限") }
            f.await("refreshed invalid grant") { f.onMain { texts().any { it.contains("需要重新授权") } } }
            f.onMain { click("恢复已移除候选");click("取消加载／分析") }
            f.onMain { click("从系统搜索添加照片") }
            f.onMain { views().filterIsInstance<EditText>().last().setText("private query");click("取消") }
            assertEquals(listOf(source),repo.snapshot(target).second)
            f.onMain { click("从系统搜索添加照片") }
            assertTrue(f.onMain { views().filterIsInstance<EditText>().last().text.toString()=="private query" })
            f.onMain { click("从我选择的氛围生成建议词") }
            f.await("manual search atmosphere choices") { f.onMain { texts().contains("舒缓") } }
            f.onMain {
                val text=views().filterIsInstance<TextView>().last { it.text.toString()=="舒缓" }
                val list=text.parent as android.widget.ListView
                list.performItemClick(text,list.getPositionForView(text),0)
            }
            f.await("editable suggestions") { f.onMain { views().filterIsInstance<EditText>().last().text.contains("湖面") } }
            f.onMain { views().filterIsInstance<EditText>().last().setText(" ");click("请求系统搜索") }
            f.await("empty query stays in app") { f.onMain { texts().any { it.contains("请输入关键词") } } }
            f.await("app window after empty search dialog closes") { PhotoSystemUi.hasPackage(f.context.packageName) }
            f.onMain { click("从系统搜索添加照片");views().filterIsInstance<EditText>().last().setText("edited lake") }
            f.onMain { click("请求系统搜索") }
            f.await("ordinary picker launched") { PhotoSystemUi.hasPackage("providers.media") }
            back(f)
            assertEquals(listOf(source),repo.snapshot(target).second)
            f.onMain { click("找不到相册？从文件选择照片") }
            f.await("document picker launched") { PhotoSystemUi.hasPackage("documentsui") }
            back(f)
            assertEquals(listOf(source),repo.snapshot(target).second)
            f.onMain { click("手动选择单张封面") }
            f.await("manual picker launched") { PhotoSystemUi.hasPackage("providers.media") }
            back(f)
            f.await("cancelled manual crop dismissed") { f.onMain {
                texts().contains("关闭") && !texts().contains(f.context.getString(com.iven.musicplayergo.R.string.cover_crop_title))
            } }
            f.onMain { click("关闭") }
            repo.deleteTarget(target)
        }
    }
    @Test fun manualAtmosphereWithEmptyScopeDoesNotInventRecommendation() {
        RegressionFixture.open().use { f ->
            val playlist=f.createPlaylist("Empty photos","A");val target=CoverTarget.Playlist(playlist.id)
            f.onActivity { PhotoRecommendationsDialogFragment.newInstance(target).showNow(it.supportFragmentManager,"recommend-ui") }
            f.onMain { click("使用我选择的氛围") }
            f.onMain {
                val text=views().filterIsInstance<TextView>().last { it.text.toString()=="舒缓" }
                // List row click through its ListView parent, as Android dispatches it.
                val parent=text.parent as android.widget.ListView
                parent.performItemClick(text,parent.getPositionForView(text),0)
            }
            f.await("empty scope explains no readable photos") { f.onMain { texts().any { it.contains("没有可读取照片") } } }
            f.onMain { click("选择／更换代表曲并分析") }
            f.await("representative chooser shows actual title") { f.onMain { texts().any { it.contains("代表曲（默认") } } }
            f.onMain { click("取消") }
            f.onMain { click("取消分析") }
            assertTrue(f.onMain { texts().any { it.contains("封面未改变") } })
            f.onMain { click("关闭") }
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
            f.await("changed album offers recovery") { f.onMain { texts().contains("为当前专辑重新授权") } }
            f.onMain { click("为当前专辑重新授权"); click("取消") }
            assertEquals(listOf(source),repo.snapshot(old).second)
            f.onMain { click("为当前专辑重新授权"); click("重新授权") }
            f.await("confirmed current album is empty") { runCatching { repo.snapshot(current).second.isEmpty() }.getOrDefault(false) }
            assertThrows(PhotoScopeException::class.java) { repo.validate(current,oldRequest) }
            f.onMain { click("关闭") }; repo.deleteTarget(current)
        }
    }
}

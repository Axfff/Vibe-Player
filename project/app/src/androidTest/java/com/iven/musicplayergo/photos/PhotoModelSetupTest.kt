package com.iven.musicplayergo.photos

import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iven.musicplayergo.covers.CoverTarget
import com.iven.musicplayergo.regression.RegressionFixture
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class PhotoModelSetupTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

    @Test fun verifiedInstallerIsAtomicAndPreservesOldFileOnWrongTruncatedOversizeAndIoFailure() = runBlocking {
        val root = File(context.cacheDir, "model-installer-${System.nanoTime()}").apply { mkdirs() }
        val store = PhotoModelStore(root)
        val original = "previous complete version".toByteArray()
        val expected = ByteArray(130000) { (it % 127).toByte() }
        val output = File(root, "model.onnx").apply { writeBytes(original) }
        suspend fun rejected(input: () -> InputStream) {
            try { store.install(output.name, expected.size.toLong(), hash(expected), input); fail("Rejected bytes must not be installed") }
            catch (_: IOException) {} catch (_: IllegalStateException) {}
            assertArrayEquals(original, output.readBytes())
            assertEquals(listOf(output.name), root.list()!!.toList())
        }
        try {
            rejected { ByteArray(expected.size).inputStream() }
            rejected { expected.copyOf(10).inputStream() }
            rejected { (expected + byteArrayOf(1)).inputStream() }
            rejected { object : InputStream() { override fun read(): Int = throw IOException("provider disconnected") } }
            val percentages = mutableListOf<Int>()
            store.install(output.name, expected.size.toLong(), hash(expected), { expected.inputStream() }) { percentages += it }
            assertArrayEquals(expected, output.readBytes())
            assertEquals(100, percentages.last())
            assertEquals(percentages.distinct().sorted(), percentages)
            assertEquals(listOf(output.name), root.list()!!.toList())
        } finally { root.deleteRecursively() }
    }

    @Test fun cancellationAndInterruptedImportRecoveryKeepActiveModels() = runBlocking {
        val root = File(context.cacheDir, "model-cancel-${System.nanoTime()}").apply { mkdirs() }
        val store = PhotoModelStore(root)
        val output = File(root, "test.onnx").apply { writeText("existing") }
        val bytes = ByteArray(200000)
        try {
            val operation = launch {
                store.install(output.name, bytes.size.toLong(), hash(bytes), { bytes.inputStream() }) { cancel() }
            }
            operation.join()
            assertTrue(operation.isCancelled)
            assertEquals("existing", output.readText())
            assertFalse(root.list()!!.any { it.endsWith(".part") })
            File(root, "import-interrupted.part").writeText("incomplete")
            File(root, PhotoModel.MOOD.filename).writeText("wrong model")
            assertEquals(PhotoModelStatus.INVALID, store.inspect()[PhotoModel.MOOD])
            assertEquals(PhotoModelStatus.MISSING, store.inspect()[PhotoModel.PHOTO])
            File(root,"mobilenetv2-7.onnx").writeText("old photo model")
            assertEquals(PhotoModelStatus.UPDATE_REQUIRED, store.inspect()[PhotoModel.PHOTO])
            assertEquals("old photo model",File(root,"mobilenetv2-7.onnx").readText())
            File(root,"mobilenetv2-7.onnx").delete()
            File(root,"tinyclip-atmospheres-v3.onnx").writeText("previous atmosphere model")
            assertEquals(PhotoModelStatus.UPDATE_REQUIRED, store.inspect()[PhotoModel.PHOTO])
            assertEquals("previous atmosphere model",File(root,"tinyclip-atmospheres-v3.onnx").readText())
            assertFalse(File(root, "import-interrupted.part").exists())
            try { store.import(PhotoModel.MOOD, { "bad".byteInputStream() }); fail("Official identity must be verified") }
            catch (_: IllegalStateException) {}
            assertEquals("wrong model", store.file(PhotoModel.MOOD).readText())
        } finally { root.deleteRecursively() }
    }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun views() = WindowInspector.getGlobalWindowViews().flatMap(::descendants)
    private fun texts() = views().filterIsInstance<TextView>().map { it.text.toString() }
    private fun click(label: String) { assertTrue(views().filterIsInstance<Button>().last { it.text.toString() == label }.performClick()) }

    @Test fun setupCanBeOpenedFromSourcesAndRecommendationsAndSurvivesRotationAndPickerCancellation() {
        RegressionFixture.open().use { f ->
            val target = CoverTarget.Playlist(f.createPlaylist("Model setup", "A").id)
            f.onActivity { PhotoSourcesDialogFragment.newInstance(target, "Model setup").showNow(it.supportFragmentManager, "source-model-test") }
            f.onMain { click("配置本地分析模型") }
            f.await("model check completed") { f.onMain { texts().contains("模型状态已更新。") } }
            f.onMain { click("音乐氛围：文件与来源说明") }
            assertTrue(f.onMain { texts().any { it.contains(PhotoModel.MOOD.filename) && it.contains("CC BY-NC-SA") && it.contains(PhotoModel.MOOD.sha256) } })
            f.onMain { click("知道了"); click("重新检查模型") }
            f.await("recheck completed") { f.onMain { texts().contains("模型状态已更新。") } }
            f.scenario.recreate()
            f.await("setup restored") { f.onMain { texts().contains("本地分析模型") && texts().contains("模型状态已更新。") } }
            f.onMain { click("导入音乐氛围模型") }
            try { f.await("system documents picker") { PhotoSystemUi.hasPackage("documentsui") } }
            catch (e: AssertionError) { PhotoSystemUi.diagnose(); throw e }
            // DocumentsUI restores its last folder; Back may first traverse parent folders.
            instrumentation.uiAutomation.waitForIdle(300, 5_000)
            for (attempt in 0 until 8) {
                if (PhotoSystemUi.hasPackage(f.context.packageName)) break
                instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                instrumentation.waitForIdleSync()
                instrumentation.uiAutomation.waitForIdle(300, 5_000)
                if (runCatching { f.await("picker transition", 2_000) {
                    PhotoSystemUi.hasPackage(f.context.packageName)
                } }.isSuccess) break
            }
            f.await("returned with no import") { PhotoSystemUi.hasPackage(f.context.packageName) }
            f.await("ready after picker cancellation") { f.onMain { texts().contains("模型状态已更新。") } }
            f.onMain { click("返回"); click("推荐当前照片范围") }
            f.await("recommendation model status") { f.onMain { texts().any { it.contains("本地分析模型已就绪") || it.contains("模型尚未就绪") } } }
            f.onMain { click("配置本地分析模型") }
            f.await("setup from recommendation") { f.onMain { texts().contains("模型状态已更新。") } }
            f.onMain { click("返回"); click("关闭"); click("关闭") }
            assertTrue(PhotoRepositories.get(f.context).snapshot(target).second.isEmpty())
        }
    }

    @Test fun failedProviderAndWrongFileAreActionableAndDoNotAddPhotoSources() {
        RegressionFixture.open().use { f ->
            lateinit var vm: PhotoModelSetupViewModel
            f.onActivity {
                val fragment = PhotoModelsDialogFragment()
                fragment.showNow(it.supportFragmentManager, "model-error")
                vm = ViewModelProvider(fragment)[PhotoModelSetupViewModel::class.java]
            }
            f.await("model check completed") { f.onMain { vm.state.value?.busy == false } }
            f.onMain { vm.import(PhotoModel.MOOD, Uri.parse("content://unavailable/model")) }
            f.await("provider failure explained") { f.onMain { vm.state.value?.busy == false && texts().any { it.contains("读取权限和可用空间") } } }
            val file = File(f.context.cacheDir, "wrong-model.onnx").apply { writeText("not a model") }
            try {
                f.onMain { vm.import(PhotoModel.MOOD, Uri.fromFile(file)); vm.cancel() }
                f.await("cancellation is reported") { f.onMain { vm.state.value?.busy == false && texts().any { it.contains("操作已取消") } } }
                f.onMain { vm.import(PhotoModel.MOOD, Uri.fromFile(file)) }
                f.scenario.recreate()
                f.await("wrong model explained across recreation") { f.onMain { texts().any { it.contains("版本不匹配") } } }
                f.onMain { click("返回") }
            } finally { file.delete() }
        }
    }
}

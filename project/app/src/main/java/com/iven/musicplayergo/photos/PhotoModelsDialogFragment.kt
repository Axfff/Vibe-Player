package com.iven.musicplayergo.photos

import android.app.Application
import android.app.Dialog
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.text.util.Linkify
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.*
import kotlinx.coroutines.*
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference

data class PhotoModelSetupState(
    val models: Map<PhotoModel, PhotoModelStatus> = emptyMap(),
    val busy: Boolean = false,
    val message: String = "正在检查模型…"
)

/** Survives rotation; does not hold an Activity, View, or retained source grant. */
class PhotoModelSetupViewModel(application: Application) : AndroidViewModel(application) {
    private val store = PhotoModelStore(application)
    private val mutableState = MutableLiveData(PhotoModelSetupState())
    val state: LiveData<PhotoModelSetupState> = mutableState
    private var job: Job? = null
    private val input = AtomicReference<InputStream?>()

    fun refresh() { if (job?.isActive != true) runOperation("正在校验模型…") {} }
    fun import(model: PhotoModel, uri: Uri) {
        if (job?.isActive == true) return
        runOperation("正在导入${model.title}…") {
            store.import(model, {
                getApplication<Application>().contentResolver.openInputStream(uri)?.also { input.set(it) }
                    ?: error("无法读取所选文件，请重新选择。")
            }) { percent -> withContext(Dispatchers.Main) {
                mutableState.value = PhotoModelSetupState(state.value?.models.orEmpty(), true, "正在导入${model.title}：$percent%")
            } }
            "${model.title}已导入并通过校验。"
        }
    }
    private fun runOperation(message: String, action: suspend () -> Any?) {
        mutableState.value = PhotoModelSetupState(state.value?.models.orEmpty(), true, message)
        job = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var result = "模型状态已更新。"
            try { (action() as? String)?.let { result = it } }
            catch (e: CancellationException) { result = "操作已取消；已完成的导入以校验状态为准，原有模型保留。" }
            catch (e: Exception) { result = if (e is IllegalStateException) e.message ?: "导入失败。" else "无法导入，请检查文件读取权限和可用空间；原模型保留。" }
            finally {
                input.getAndSet(null)?.let { runCatching { it.close() } }
                // Publish the terminal state before returning to the cancelled dispatcher.
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    mutableState.value = PhotoModelSetupState(store.inspect(), false, result)
                }
            }
        }
    }
    fun cancel() {
        job?.cancel()
        val stream = input.getAndSet(null)
        if (stream != null) viewModelScope.launch(Dispatchers.IO) { runCatching { stream.close() } }
    }
    override fun onCleared() { input.getAndSet(null)?.let { runCatching { it.close() } }; super.onCleared() }
}

class PhotoModelsDialogFragment : DialogFragment() {
    private val model by lazy { ViewModelProvider(this)[PhotoModelSetupViewModel::class.java] }
    private var pending: PhotoModel? = null
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val selected = pending
        pending = null
        if (uri != null && selected != null) model.import(selected, uri)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pending = savedInstanceState?.getString("model")?.let { name -> PhotoModel.values().firstOrNull { it.name == name } }
    }
    override fun onSaveInstanceState(outState: Bundle) { pending?.let { outState.putString("model", it.name) }; super.onSaveInstanceState(outState) }
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val padding = (16 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(padding, padding / 2, padding, padding / 2) }
        fun text(value: String) = TextView(requireContext()).apply { text = value; layout.addView(this) }
        text("导入后在本机分析音乐和已授权照片，不上传媒体。模型供所有歌单／音乐专辑共用；不会扩大照片授权范围。")
        val summary = text("")
        val status = text("")
        val rows = PhotoModel.values().associateWith { item ->
            val state = text(item.title)
            val button = Button(requireContext()).apply {
                text = "导入${item.title}模型"
                setOnClickListener { pending = item; picker.launch(arrayOf("*/*")) }
            }
            layout.addView(button)
            layout.addView(Button(requireContext()).apply {
                text = "${item.title}：文件与来源说明"
                setOnClickListener {
                    val info = TextView(requireContext()).apply {
                        setPadding(padding, padding, padding, padding)
                        text = "所需文件：${item.filename}\n大小：${item.bytes} 字节\n许可：${item.license}\n\n官方来源（在浏览器中打开）：\n${item.source}\n\n下载完成后回到此页导入。文件会复制到应用私有存储；卸载或清除应用数据后需重新导入。\n\nSHA-256：${item.sha256}"
                        Linkify.addLinks(this, Linkify.WEB_URLS)
                    }
                    AlertDialog.Builder(requireContext()).setTitle(item.title).setView(ScrollView(requireContext()).apply { addView(info) }).setPositiveButton("知道了", null).show()
                }
            })
            state to button
        }
        val cancel = Button(requireContext()).apply { text = "取消导入／检查"; setOnClickListener { model.cancel() } }
        layout.addView(cancel)
        val refresh = Button(requireContext()).apply { text = "重新检查模型"; setOnClickListener { model.refresh() } }
        layout.addView(refresh)
        model.state.observe(this) { value ->
            if (!value.busy && value.models.isNotEmpty()) parentFragmentManager.setFragmentResult("photo-models-updated", Bundle())
            summary.text = if (value.models.size == 3 && value.models.values.all { it == PhotoModelStatus.READY })
                "模型已就绪，可返回推荐页分析。实际运行仍取决于音频、照片与设备支持。"
            else "已就绪 ${value.models.values.count { it == PhotoModelStatus.READY }}/3。音乐分析需要两个音乐模型；手选氛围推荐仍需要照片模型。"
            status.text = value.message
            rows.forEach { (item, row) ->
                row.first.text = "${item.title} · ${String.format(java.util.Locale.ROOT, "%.1f", item.bytes / 1_000_000.0)} MB\n${value.models[item]?.label ?: "待检查"}"
                row.second.isEnabled = !value.busy
            }
            cancel.isEnabled = value.busy
            refresh.isEnabled = !value.busy
        }
        return AlertDialog.Builder(requireContext()).setTitle("本地分析模型")
            .setView(ScrollView(requireContext()).apply { addView(layout) }).setNegativeButton("返回") { _, _ -> model.cancel() }.create()
    }
    override fun onStart() { super.onStart(); if (model.state.value?.models.isNullOrEmpty()) model.refresh() }
    override fun onCancel(dialog: DialogInterface) { model.cancel(); super.onCancel(dialog) }
}

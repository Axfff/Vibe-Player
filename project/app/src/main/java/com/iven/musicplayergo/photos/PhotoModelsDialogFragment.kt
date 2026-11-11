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
    val message: String = "正在检查所需文件…"
)

/** Survives rotation; does not hold an Activity, View, or retained source grant. */
class PhotoModelSetupViewModel(application: Application) : AndroidViewModel(application) {
    private val store = PhotoModelStore(application)
    private val mutableState = MutableLiveData(PhotoModelSetupState())
    val state: LiveData<PhotoModelSetupState> = mutableState
    private var job: Job? = null
    private val input = AtomicReference<InputStream?>()

    fun refresh() { if (job?.isActive != true) runOperation("正在检查所需文件…") {} }
    fun import(model: PhotoModel, uri: Uri) {
        if (job?.isActive == true) return
        runOperation("正在导入${model.title}…") {
            store.import(model, {
                getApplication<Application>().contentResolver.openInputStream(uri)?.also { input.set(it) }
                    ?: error("无法读取所选文件，请重新选择。")
            }) { percent -> withContext(Dispatchers.Main) {
                mutableState.value = PhotoModelSetupState(state.value?.models.orEmpty(), true, "正在导入${model.title}：$percent%")
            } }
            "${model.title}已准备好。"
        }
    }
    private fun runOperation(message: String, action: suspend () -> Any?) {
        mutableState.value = PhotoModelSetupState(state.value?.models.orEmpty(), true, message)
        job = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var result = "已检查所需文件。"
            try { (action() as? String)?.let { result = it } }
            catch (e: CancellationException) { result = "已取消。已完成的文件会保留。" }
            catch (e: Exception) { result = if (e is IllegalStateException) e.message ?: "导入失败。" else "导入失败，请检查文件是否能打开、手机空间是否充足。原有文件保留。" }
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
        text("首次使用需导入下面 3 个文件。准备好后，所有歌单和专辑都能使用；音乐和照片不会上传。")
        val summary = text("")
        val status = text("")
        val rows = PhotoModel.values().associateWith { item ->
            val state = text(item.title)
            val button = Button(requireContext()).apply {
                text = "导入${item.title}文件"
                setOnClickListener { pending = item; picker.launch(arrayOf("*/*")) }
            }
            layout.addView(button)
            layout.addView(Button(requireContext()).apply {
                text = "${item.title}：文件说明"
                setOnClickListener {
                    val info = TextView(requireContext()).apply {
                        setPadding(padding, padding, padding, padding)
                        text = "所需文件：${item.filename}\n大小：${item.bytes} 字节\n许可：${item.license}\n\n来源与准备说明（在浏览器中打开）：\n${item.source}\n\n${if (item == PhotoModel.PHOTO) "照片模型需按说明从官方 TinyCLIP 权重转换，或使用与下列校验值一致的已转换文件。" else "下载完成后回到此页导入。"}文件会复制到应用私有存储；卸载或清除应用数据后需重新导入。\n\nSHA-256：${item.sha256}"
                        Linkify.addLinks(this, Linkify.WEB_URLS)
                    }
                    AlertDialog.Builder(requireContext()).setTitle(item.title).setView(ScrollView(requireContext()).apply { addView(info) }).setPositiveButton("知道了", null).show()
                }
            })
            state to button
        }
        val cancel = Button(requireContext()).apply { text = "取消操作"; setOnClickListener { model.cancel() } }
        layout.addView(cancel)
        val refresh = Button(requireContext()).apply { text = "重新检查"; setOnClickListener { model.refresh() } }
        layout.addView(refresh)
        model.state.observe(this) { value ->
            if (!value.busy && value.models.isNotEmpty()) parentFragmentManager.setFragmentResult("photo-models-updated", Bundle())
            summary.text = if (value.models.size == 3 && value.models.values.all { it == PhotoModelStatus.READY })
                "准备好了，可以返回推荐封面。"
            else "已准备 ${value.models.values.count { it == PhotoModelStatus.READY }}/3，请导入缺少的文件。"
            status.text = value.message
            rows.forEach { (item, row) ->
                row.first.text = "${item.title} · ${String.format(java.util.Locale.ROOT, "%.1f", item.bytes / 1_000_000.0)} MB\n${value.models[item]?.label ?: "待检查"}"
                row.second.isEnabled = !value.busy
            }
            cancel.visibility = if (value.busy) android.view.View.VISIBLE else android.view.View.GONE
            cancel.isEnabled = value.busy
            refresh.isEnabled = !value.busy
        }
        return AlertDialog.Builder(requireContext()).setTitle("推荐功能设置")
            .setView(ScrollView(requireContext()).apply { addView(layout) }).setNegativeButton("返回") { _, _ -> model.cancel() }.create()
    }
    override fun onStart() { super.onStart(); if (model.state.value?.models.isNullOrEmpty()) model.refresh() }
    override fun onCancel(dialog: DialogInterface) { model.cancel(); super.onCancel(dialog) }
}

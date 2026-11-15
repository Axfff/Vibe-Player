package com.iven.musicplayergo.photos

import com.iven.musicplayergo.preferences.UiText
import com.iven.musicplayergo.preferences.UiTextException
import com.iven.musicplayergo.R
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
    val message: UiText = UiText.of(R.string.photo_checking)
)

/** Survives rotation; does not hold an Activity, View, or retained source grant. */
class PhotoModelSetupViewModel(application: Application) : AndroidViewModel(application) {
    private val store = PhotoModelStore(application)
    private val mutableState = MutableLiveData(PhotoModelSetupState())
    val state: LiveData<PhotoModelSetupState> = mutableState
    private var job: Job? = null
    private val input = AtomicReference<InputStream?>()

    fun refresh() { if (job?.isActive != true) runOperation(UiText.of(R.string.photo_checking)) {} }
    fun import(model: PhotoModel, uri: Uri) {
        if (job?.isActive == true) return
        runOperation(UiText.of(R.string.photo_model_importing, model.title)) {
            store.import(model, {
                getApplication<Application>().contentResolver.openInputStream(uri)?.also { input.set(it) }
                    ?: throw UiTextException(UiText.of(R.string.photo_model_read_failed))
            }) { percent -> withContext(Dispatchers.Main) {
                mutableState.value = PhotoModelSetupState(state.value?.models.orEmpty(), true, UiText.of(R.string.photo_model_progress, model.title, percent))
            } }
            UiText.of(R.string.photo_model_imported, model.title)
        }
    }
    private fun runOperation(message: UiText, action: suspend () -> Any?) {
        mutableState.value = PhotoModelSetupState(state.value?.models.orEmpty(), true, message)
        job = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var result: UiText = UiText.of(R.string.photo_checked)
            try { (action() as? UiText)?.let { result = it } }
            catch (e: CancellationException) { result = UiText.of(R.string.photo_import_cancelled) }
            catch (e: Exception) { result = (e as? UiTextException)?.text ?: UiText.of(R.string.photo_import_failed_help) }
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
    private val photoOnly get() = arguments?.getBoolean("photo-only") == true
    private val requiredModels get() = if (photoOnly) listOf(PhotoModel.PHOTO) else PhotoModel.values().toList()
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
        text(if (photoOnly) getString(R.string.discovery_photo_model_intro) else getString(R.string.photo_model_intro))
        val summary = text("")
        val status = text("")
        val rows = requiredModels.associateWith { item ->
            val state = text(item.title.resolve(requireContext()))
            val button = Button(requireContext()).apply {
                text = getString(R.string.photo_import_file , item.title.resolve(requireContext()))
                setOnClickListener { pending = item; picker.launch(arrayOf("*/*")) }
            }
            layout.addView(button)
            layout.addView(Button(requireContext()).apply {
                text = getString(R.string.photo_file_instructions , item.title.resolve(requireContext()))
                setOnClickListener {
                    val info = TextView(requireContext()).apply {
                        setPadding(padding, padding, padding, padding)
                        text = getString(R.string.photo_model_file_info , item.filename, item.bytes, item.license.resolve(requireContext()), item.source, if (item == PhotoModel.PHOTO) getString(R.string.photo_model_conversion) else getString(R.string.photo_model_download_help), item.sha256)
                        Linkify.addLinks(this, Linkify.WEB_URLS)
                    }
                    AlertDialog.Builder(requireContext()).setTitle(item.title.resolve(requireContext())).setView(ScrollView(requireContext()).apply { addView(info) }).setPositiveButton(getString(R.string.photo_ok), null).show()
                }
            })
            state to button
        }
        val cancel = Button(requireContext()).apply { text = getString(R.string.photo_cancel_operation); setOnClickListener { model.cancel() } }
        layout.addView(cancel)
        val refresh = Button(requireContext()).apply { text = getString(R.string.photo_recheck); setOnClickListener { model.refresh() } }
        layout.addView(refresh)
        model.state.observe(this) { value ->
            if (!value.busy && value.models.isNotEmpty()) parentFragmentManager.setFragmentResult("photo-models-updated", Bundle())
            summary.text = if (requiredModels.all { value.models[it] == PhotoModelStatus.READY })
                getString(R.string.photo_setup_ready)
            else getString(R.string.photo_setup_count , requiredModels.count { value.models[it] == PhotoModelStatus.READY }, requiredModels.size)
            status.text = value.message.resolve(requireContext())
            rows.forEach { (item, row) ->
                row.first.text = getString(R.string.photo_setup_row , item.title.resolve(requireContext()), item.bytes / 1_000_000.0, value.models[item]?.label?.resolve(requireContext()) ?: getString(R.string.photo_pending))
                row.second.isEnabled = !value.busy
            }
            cancel.visibility = if (value.busy) android.view.View.VISIBLE else android.view.View.GONE
            cancel.isEnabled = value.busy
            refresh.isEnabled = !value.busy
        }
        return AlertDialog.Builder(requireContext()).setTitle(getString(R.string.photo_setup_title))
            .setView(ScrollView(requireContext()).apply { addView(layout) }).setNegativeButton(getString(R.string.photo_back)) { _, _ -> model.cancel() }.create()
    }
    companion object { fun forDiscovery() = PhotoModelsDialogFragment().apply { arguments = Bundle().apply { putBoolean("photo-only", true) } } }
    override fun onStart() { super.onStart(); if (model.state.value?.models.isNullOrEmpty()) model.refresh() }
    override fun onCancel(dialog: DialogInterface) { model.cancel(); super.onCancel(dialog) }
}

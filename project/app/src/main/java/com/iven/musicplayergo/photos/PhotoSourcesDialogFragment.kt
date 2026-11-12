package com.iven.musicplayergo.photos

import com.iven.musicplayergo.R
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.iven.musicplayergo.covers.*
import kotlinx.coroutines.*
import java.util.UUID

/** The fragment arguments fix the target for its entire lifetime, including picker callbacks. */
class PhotoSourcesDialogFragment : DialogFragment() {
    private val target get() = requireArguments().photoTarget()
    private val repository get() = PhotoRepositories.get(requireContext())
    private var body: LinearLayout? = null
    private var status: TextView? = null
    private var cancelLoading: Button? = null
    private var job: Job? = null
    private var pending: PhotoRequest? = null
    private var recursive = false
    private var searchQuery = ""
    private val systemSearch = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uris = mutableListOf<Uri>()
            result.data?.data?.let { uris += it }
            result.data?.clipData?.let { clip -> for (index in 0 until minOf(clip.itemCount, 100)) uris += clip.getItemAt(index).uri }
            if (uris.isNotEmpty()) confirm(uris.distinct(), PhotoSourceKind.SELECTED)
            else status?.text = getString(R.string.photo_none_selected)
        } else status?.text = getString(R.string.photo_selection_cancelled)
    }
    private val photos = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isNotEmpty()) confirm(uris.distinct(), PhotoSourceKind.SELECTED)
    }
    // Some OEM galleries omit newly created folders even after MediaStore indexing.
    // SAF lets the user explicitly choose files without granting a whole directory.
    private val files = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            if (uris.distinct().size > 100) status?.text = getString(R.string.photo_selection_limit)
            else confirm(uris.distinct(), PhotoSourceKind.SELECTED)
        } else status?.text = getString(R.string.photo_selection_cancelled)
    }
    private val tree = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.takeIf { result.resultCode == android.app.Activity.RESULT_OK }?.let { confirm(listOf(it), PhotoSourceKind.DIRECTORY) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString("request")?.let { id -> pending = PhotoRequest(target.storageKey,
            savedInstanceState.getString("identity") ?: "", savedInstanceState.getLong("revision"), id) }
        recursive = savedInstanceState?.getBoolean("recursive") ?: false
        searchQuery = savedInstanceState?.getString("search_query") ?: ""
        parentFragmentManager.setFragmentResultListener("photo-search-suggestions:${target.storageKey}", this) { _, bundle ->
            searchQuery = bundle.getString("query") ?: searchQuery
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        pending?.let { outState.putString("request", it.requestId); outState.putString("identity", it.identity); outState.putLong("revision", it.revision) }
        outState.putBoolean("recursive", recursive)
        outState.putString("search_query", searchQuery)
        super.onSaveInstanceState(outState)
    }
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), dp(12)) }
        layout.addView(TextView(requireContext()).apply {
            text = requireArguments().getString("display") ?: getString(R.string.photo_current_collection)
            textSize = 16f
        })
        fun button(label: String, action: () -> Unit) { layout.addView(Button(requireContext()).apply { text = label; setOnClickListener { action() } }) }
        button(getString(R.string.photo_recommend_cover)) { recommend() }
        button(getString(R.string.photo_choose_direct)) { manualCover() }
        layout.addView(TextView(requireContext()).apply {
            text = getString(R.string.photo_recommendation_sources); textSize = 16f; setPadding(0, dp(16), 0, dp(4))
        })
        status = TextView(requireContext()).also { layout.addView(it) }
        body = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }.also { layout.addView(it) }
        val actions = LinearLayout(requireContext())
        fun action(label: String, run: () -> Unit) { actions.addView(Button(requireContext()).apply {
            text = label; layoutParams = LinearLayout.LayoutParams(0, -2, 1f); setOnClickListener { run() }
        }) }
        action(getString(R.string.photo_add)) { addPhotos() }
        action(getString(R.string.photo_more)) { more() }
        layout.addView(actions)
        cancelLoading = Button(requireContext()).apply {
            text = getString(R.string.photo_cancel_loading); visibility = View.GONE
            setOnClickListener { job?.cancel(); visibility = View.GONE; status?.text = getString(R.string.photo_loading_cancelled) }
        }.also { layout.addView(it) }
        return AlertDialog.Builder(requireContext()).setTitle(getString(R.string.photo_choose_cover))
            .setView(ScrollView(requireContext()).apply { addView(layout) }).setNegativeButton(getString(R.string.photo_close), null).create()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun addPhotos() {
        AlertDialog.Builder(requireContext()).setTitle(getString(R.string.photo_add_sources))
            .setItems(arrayOf(getString(R.string.photo_choose), getString(R.string.photo_choose_folder), getString(R.string.photo_choose_files), getString(R.string.photo_search), getString(R.string.photo_xiaomi_search), getString(R.string.photo_reuse))) { _, index ->
                when (index) {
                    0 -> begin { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    1 -> chooseTree()
                    2 -> begin { files.launch(arrayOf("image/*")) }
                    3 -> search()
                    4 -> albumHelp()
                    5 -> share()
                }
            }.setNegativeButton(getString(R.string.photo_cancel), null).show()
    }
    private fun more() {
        AlertDialog.Builder(requireContext()).setTitle(getString(R.string.photo_manage))
            .setItems(arrayOf(getString(R.string.photo_refresh), getString(R.string.photo_restore_excluded), getString(R.string.photo_settings), getString(R.string.photo_restore_cover), getString(R.string.photo_usage_title))) { _, index ->
                when (index) {
                    0 -> work { repository.refresh(target); reload() }
                    1 -> work { repository.refresh(target, true); reload() }
                    2 -> PhotoModelsDialogFragment().show(parentFragmentManager, "photo-models")
                    3 -> AlertDialog.Builder(requireContext()).setTitle(getString(R.string.photo_restore_question))
                        .setMessage(getString(R.string.photo_restore_help))
                        .setNegativeButton(getString(R.string.photo_cancel), null).setPositiveButton(getString(R.string.photo_restore)) { _, _ -> work {
                            check(targetExists(requireContext(), target)) { getString(R.string.photo_target_unavailable) }
                            val result = CoverRepositories.get(requireContext()).removeCover(target)
                            if (result is CoverResult.Failure) error(result.error.name)
                            parentFragmentManager.setFragmentResult(CoverCropDialogFragment.RESULT_KEY, Bundle())
                        } }.show()
                    4 -> AlertDialog.Builder(requireContext()).setTitle(getString(R.string.photo_usage_title))
                        .setMessage(getString(R.string.photo_usage))
                        .setPositiveButton(getString(R.string.photo_ok), null).show()
                }
            }.setNegativeButton(getString(R.string.photo_cancel), null).show()
    }
    private fun manualCover() {
        val crop = when (val t = target) {
            is CoverTarget.Playlist -> CoverCropDialogFragment.forPlaylist(t.playlistId)
            is CoverTarget.Album -> CoverCropDialogFragment.forAlbum(t.albumId, t.artistSnapshot, t.albumTitleSnapshot)
        }
        crop.show(parentFragmentManager, "manual-cover")
    }
    override fun onStart() { super.onStart(); reload() }
    private fun begin(action: () -> Unit) = work {
        check(targetExists(requireContext(), target)) { getString(R.string.photo_target_unavailable) }
        pending = repository.request(target)
        withContext(Dispatchers.Main) { action() }
    }
    private fun chooseTree() {
        val option = CheckBox(requireContext()).apply { text = getString(R.string.photo_recursive) }
        AlertDialog.Builder(requireContext()).setTitle(getString(R.string.photo_choose_folder))
            .setMessage(getString(R.string.photo_folder_help))
            .setView(option).setNegativeButton(getString(R.string.photo_cancel), null).setPositiveButton(getString(R.string.photo_select_folder)) { _, _ ->
                recursive = option.isChecked
                begin { tree.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)) }
            }.show()
    }
    private fun albumHelp() {
        AlertDialog.Builder(requireContext()).setTitle(getString(R.string.photo_xiaomi_help_title))
            .setMessage(getString(R.string.photo_xiaomi_step1) +
                getString(R.string.photo_xiaomi_step2) +
                getString(R.string.photo_xiaomi_step3) +
                getString(R.string.photo_xiaomi_fallback) +
                getString(R.string.photo_xiaomi_cleanup))
            .setPositiveButton(getString(R.string.photo_choose_prepared)) { _, _ ->
                begin { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            }
            .setNeutralButton(getString(R.string.photo_authorize_folder)) { _, _ -> chooseTree() }
            .setNegativeButton(getString(R.string.photo_back), null).show()
    }
    private fun confirm(uris: List<Uri>, kind: PhotoSourceKind) {
        val request = pending ?: return
        AlertDialog.Builder(requireContext()).setTitle(getString(R.string.photo_add_question))
            .setMessage(getString(R.string.photo_add_summary , requireArguments().getString("display") ?: getString(R.string.photo_current_target), uris.size, if (kind == PhotoSourceKind.DIRECTORY) getString(R.string.photo_folders_unit) else getString(R.string.photo_photos_unit)))
            .setNegativeButton(getString(R.string.photo_cancel), null).setPositiveButton(getString(R.string.photo_add_confirm)) { _, _ -> work {
                check(targetExists(requireContext(), target)) { getString(R.string.photo_target_unavailable) }
                repository.validate(target, request)
                val access = PhotoAccessManager(requireContext())
                check(uris.all { access.persist(it) }) { getString(R.string.photo_persist_failed) }
                repository.attach(target, request, PhotoSource(UUID.randomUUID().toString(),
                    if (kind == PhotoSourceKind.DIRECTORY) getString(R.string.photo_folder_name) else getString(R.string.photo_group_name), kind, uris.map { it.toString() }, recursive && kind == PhotoSourceKind.DIRECTORY))
                reload()
            } }.show()
    }
    private fun share() = work {
        val sources = repository.shareableSources()
        withContext(Dispatchers.Main) {
            AlertDialog.Builder(requireContext()).setTitle(getString(R.string.photo_choose_existing))
                .setItems(sources.map { "${it.displayName().resolve(requireContext())} · ${it.id.take(6)}" }.toTypedArray()) { _, which -> begin {
                    AlertDialog.Builder(requireContext()).setMessage(getString(R.string.photo_reuse_question))
                        .setNegativeButton(getString(R.string.photo_cancel), null).setPositiveButton(getString(R.string.photo_use)) { _, _ -> work {
                            check(targetExists(requireContext(), target)); repository.attach(target, pending!!, sources[which]); reload()
                        } }.show()
                } }.setNegativeButton(getString(R.string.photo_cancel), null).show()
        }
    }
    private fun reload() {
        job?.cancel()
        status?.text = getString(R.string.photo_reading)
        cancelLoading?.visibility = View.VISIBLE
        body?.removeAllViews()
        job = lifecycleScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) { check(targetExists(requireContext(), target)); repository.snapshot(target) }
                for (source in snapshot.second) {
                    val result = PhotoCandidateProvider(requireContext()).enumerate(source, snapshot.first.excluded.toSet())
                    val row = Button(requireContext()).apply {
                        val label = when(result.state) {
                            PhotoReadState.AVAILABLE -> getString(R.string.photo_available)
                            PhotoReadState.REAUTHORIZE -> getString(R.string.photo_reauthorize)
                            PhotoReadState.MISSING_OR_OFFLINE -> getString(R.string.photo_unavailable)
                            PhotoReadState.UNSUPPORTED -> getString(R.string.photo_unsupported)
                            PhotoReadState.PARTIAL -> getString(R.string.photo_partial)
                            PhotoReadState.BUDGET_REACHED -> getString(R.string.photo_budget)
                        }
                        val count = if (result.photos.isEmpty() && result.state in setOf(PhotoReadState.REAUTHORIZE, PhotoReadState.MISSING_OR_OFFLINE)) getString(R.string.photo_count_unknown) else getString(R.string.photo_available_count , result.photos.size)
                        text = "${source.displayName().resolve(requireContext())}\n$count · $label"
                        setOnClickListener { sourceActions(source, result) }
                    }; body?.addView(row)
                }
                status?.text = if (snapshot.second.isEmpty()) getString(R.string.photo_no_sources) else getString(R.string.photo_source_count , snapshot.second.size)
            } catch (e: PhotoScopeIdentityChanged) {
                status?.text = getString(R.string.photo_album_changed)
                body?.addView(Button(requireContext()).apply {
                    text = getString(R.string.photo_reselect)
                    setOnClickListener {
                        AlertDialog.Builder(requireContext()).setTitle(getString(R.string.photo_rebind_title))
                            .setMessage(getString(R.string.photo_rebind_help))
                            .setNegativeButton(getString(R.string.photo_cancel), null).setPositiveButton(getString(R.string.photo_continue)) { _, _ -> work {
                                withContext(Dispatchers.IO) {
                                    check(targetExists(requireContext(), target)) { getString(R.string.photo_target_unavailable) }
                                    repository.rebindAlbum(target as CoverTarget.Album, e.previous)
                                }
                                pending = null; reload()
                            } }.show()
                    }
                })
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { status?.text = getString(R.string.photo_read_failed) }
            finally { if (job === currentCoroutineContext()[Job]) cancelLoading?.visibility = View.GONE }
        }
    }
    private fun sourceActions(source: PhotoSource, result: SourceCandidates) {
        AlertDialog.Builder(requireContext()).setTitle(source.displayName().resolve(requireContext()))
            .setItems(arrayOf(getString(R.string.photo_view), getString(R.string.photo_remove_here), getString(R.string.photo_source_info))) { _, index ->
                if (index == 1) work { repository.remove(target, source.id); reload() }
                else if (index == 0) showCandidates(result.photos)
                else AlertDialog.Builder(requireContext()).setTitle(source.displayName().resolve(requireContext()))
                    .setMessage(getString(R.string.photo_source_help , if (source.kind == PhotoSourceKind.DIRECTORY) getString(R.string.photo_folder_refresh_help) else getString(R.string.photo_selected_refresh_help)))
                    .setPositiveButton(getString(R.string.photo_ok), null).show()
            }.show()
    }
    private fun showCandidates(photos: List<ScopedPhoto>) {
        val rows = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        val preview = AlertDialog.Builder(requireContext()).setTitle(getString(R.string.photo_added_count , photos.size))
            .setView(ScrollView(requireContext()).apply { addView(rows) }).setNegativeButton(getString(R.string.photo_close), null).create()
        val previewJob = lifecycleScope.launch {
            for (photo in photos.take(30)) {
                ensureActive()
                val bitmap = withContext(Dispatchers.IO) { runCatching { SafePhotoDecoder(requireContext()).let { it.decode(it.bytes(Uri.parse(photo.uri)), 160) } }.getOrNull() }
                val image = ImageView(requireContext()).apply { layoutParams = LinearLayout.LayoutParams(-1, 220); setImageBitmap(bitmap); contentDescription = getString(R.string.photo_remove_accessibility , photo.id.take(8)) }
                rows.addView(image)
                image.setOnClickListener { AlertDialog.Builder(requireContext()).setMessage(getString(R.string.photo_remove_question))
                    .setNegativeButton(getString(R.string.photo_cancel), null).setPositiveButton(getString(R.string.photo_remove)) { _, _ -> work { repository.exclude(target, photo.id); preview.dismiss(); reload() } }.show() }
            }
            if (photos.size > 30) rows.addView(Button(requireContext()).apply { text = getString(R.string.photo_remaining_count , photos.size - 30); setOnClickListener { preview.dismiss(); showCandidates(photos.drop(30)) } })
        }
        preview.setOnDismissListener { previewJob.cancel(); rows.removeAllViews() }; preview.show()
    }
    private fun work(action: suspend () -> Unit) { lifecycleScope.launch {
        try { action() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { status?.text = if (e.message == getString(R.string.photo_persist_failed)) e.message else getString(R.string.photo_operation_failed) }
    } }
    private fun recommend() { PhotoRecommendationsDialogFragment.newInstance(target).show(parentFragmentManager, "photo-recommendations") }
    private fun search() {
        val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 8) }
        val edit = EditText(requireContext()).apply { setText(searchQuery); hint = getString(R.string.photo_keyword_hint) }
        layout.addView(edit)
        layout.addView(Button(requireContext()).apply {
            text = getString(R.string.photo_mood_keywords)
            setOnClickListener {
                val moods = PhotoRanker.mapping.keys.toList()
                AlertDialog.Builder(requireContext()).setTitle(getString(R.string.photo_choose_mood)).setItems(moods.map { PhotoRanker.moodName(it).resolve(requireContext()) }.toTypedArray()) { _, index ->
                    edit.setText(PhotoRanker.suggestions(listOf(com.iven.musicplayergo.analysis.MoodScore(moods[index], 1f))).joinToString(" ") { it.resolve(requireContext()) })
                }.show()
            }
        })
        val launcher = SystemPhotoSearchLauncher(requireContext())
        val support = if (launcher.capability() == SystemPhotoSearchCapability.MAY_REQUEST) getString(R.string.photo_system_search_hint) else getString(R.string.photo_system_search_fallback)
        AlertDialog.Builder(requireContext()).setTitle(getString(R.string.photo_search))
            .setMessage(getString(R.string.photo_search_consent , support))
            .setView(layout).setNegativeButton(getString(R.string.photo_cancel)) { _, _ -> searchQuery = edit.text.toString() }
            .setNeutralButton(getString(R.string.photo_choose_direct_plural)) { _, _ -> searchQuery = edit.text.toString(); begin { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
            .setPositiveButton(getString(R.string.photo_find)) { _, _ ->
                searchQuery = PhotoSearchPolicy.query(edit.text.toString())
                if (searchQuery.isEmpty()) { status?.text = getString(R.string.photo_enter_keywords) }
                else begin {
                    launcher.launch(searchQuery, { systemSearch.launch(it); status?.text = getString(R.string.photo_gallery_opened) }, {
                        status?.text = getString(R.string.photo_picker_opened)
                        photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                }
            }.show()
    }
    override fun onDestroyView() { job?.cancel(); body = null; status = null; cancelLoading = null; super.onDestroyView() }
    companion object { fun newInstance(target: CoverTarget, display: String) = PhotoSourcesDialogFragment().apply {
        arguments = target.toPhotoBundle().apply { putString("display", display) }
    } }
}

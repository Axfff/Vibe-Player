package com.iven.musicplayergo.photos

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
            else status?.text = "没有选择照片，已保留你的搜索词。"
        } else status?.text = "已取消，原有照片不变。"
    }
    private val photos = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isNotEmpty()) confirm(uris.distinct(), PhotoSourceKind.SELECTED)
    }
    // Some OEM galleries omit newly created folders even after MediaStore indexing.
    // SAF lets the user explicitly choose files without granting a whole directory.
    private val files = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            if (uris.distinct().size > 100) status?.text = "一次最多选择 100 张；本次未加入，请减少数量后重试。"
            else confirm(uris.distinct(), PhotoSourceKind.SELECTED)
        } else status?.text = "已取消，原有照片不变。"
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
            text = requireArguments().getString("display") ?: "当前音乐集合"
            textSize = 16f
        })
        fun button(label: String, action: () -> Unit) { layout.addView(Button(requireContext()).apply { text = label; setOnClickListener { action() } }) }
        button("推荐封面") { recommend() }
        button("直接选一张") { manualCover() }
        layout.addView(TextView(requireContext()).apply {
            text = "用于推荐的照片"; textSize = 16f; setPadding(0, dp(16), 0, dp(4))
        })
        status = TextView(requireContext()).also { layout.addView(it) }
        body = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }.also { layout.addView(it) }
        val actions = LinearLayout(requireContext())
        fun action(label: String, run: () -> Unit) { actions.addView(Button(requireContext()).apply {
            text = label; layoutParams = LinearLayout.LayoutParams(0, -2, 1f); setOnClickListener { run() }
        }) }
        action("添加照片") { addPhotos() }
        action("更多") { more() }
        layout.addView(actions)
        cancelLoading = Button(requireContext()).apply {
            text = "取消加载"; visibility = View.GONE
            setOnClickListener { job?.cancel(); visibility = View.GONE; status?.text = "已停止加载，原有照片不变。" }
        }.also { layout.addView(it) }
        return AlertDialog.Builder(requireContext()).setTitle("选择封面")
            .setView(ScrollView(requireContext()).apply { addView(layout) }).setNegativeButton("关闭", null).create()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun addPhotos() {
        AlertDialog.Builder(requireContext()).setTitle("添加用于推荐的照片")
            .setItems(arrayOf("选择照片", "选择照片文件夹", "从文件选择照片", "按关键词找照片", "小米相册搜索后添加", "使用已添加的照片")) { _, index ->
                when (index) {
                    0 -> begin { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    1 -> chooseTree()
                    2 -> begin { files.launch(arrayOf("image/*")) }
                    3 -> search()
                    4 -> albumHelp()
                    5 -> share()
                }
            }.setNegativeButton("取消", null).show()
    }
    private fun more() {
        AlertDialog.Builder(requireContext()).setTitle("封面与照片管理")
            .setItems(arrayOf("刷新照片", "恢复移除的照片", "推荐设置", "恢复默认封面", "照片使用说明")) { _, index ->
                when (index) {
                    0 -> work { repository.refresh(target); reload() }
                    1 -> work { repository.refresh(target, true); reload() }
                    2 -> PhotoModelsDialogFragment().show(parentFragmentManager, "photo-models")
                    3 -> AlertDialog.Builder(requireContext()).setTitle("换回默认封面？")
                        .setMessage("只更换封面，已添加的照片和原图不受影响。")
                        .setNegativeButton("取消", null).setPositiveButton("恢复") { _, _ -> work {
                            check(targetExists(requireContext(), target)) { "目标已删除或暂不可用" }
                            val result = CoverRepositories.get(requireContext()).removeCover(target)
                            if (result is CoverResult.Failure) error(result.error.name)
                            parentFragmentManager.setFragmentResult(CoverCropDialogFragment.RESULT_KEY, Bundle())
                        } }.show()
                    4 -> AlertDialog.Builder(requireContext()).setTitle("照片使用说明")
                        .setMessage("只使用你为这个歌单或专辑添加的照片，推荐在手机上完成。云端照片可能需要联网读取。\n\n从这里移除不会删除原图或更换封面。如需取消 App 的照片访问权限，请在手机设置中修改。\n\n新照片需要另行添加，或刷新已添加的文件夹。")
                        .setPositiveButton("知道了", null).show()
                }
            }.setNegativeButton("取消", null).show()
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
        check(targetExists(requireContext(), target)) { "目标已删除或暂不可用" }
        pending = repository.request(target)
        withContext(Dispatchers.Main) { action() }
    }
    private fun chooseTree() {
        val option = CheckBox(requireContext()).apply { text = "同时包含子文件夹（最多 3 层）" }
        AlertDialog.Builder(requireContext()).setTitle("选择照片文件夹")
            .setMessage("只读取你选择的文件夹。刷新后，新加入的照片也会用于推荐。照片较多时，可能只显示部分结果。")
            .setView(option).setNegativeButton("取消", null).setPositiveButton("选择目录") { _, _ ->
                recursive = option.isChecked
                begin { tree.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)) }
            }.show()
    }
    private fun albumHelp() {
        AlertDialog.Builder(requireContext()).setTitle("用小米相册先筛选照片")
            .setMessage("1. 在小米相册搜索并选中照片。\n" +
                "2. 添加到新相册，选择“复制”以保留原图。\n" +
                "3. 回到这里，选择并添加整理好的照片。\n\n" +
                "找不到新相册时，可从文件选择照片或添加文件夹。每次最多选 100 张，新照片需要另行添加。\n\n" +
                "清理临时相册前，请确认只删除副本；相册删除可能同步到云端。")
            .setPositiveButton("选择整理好的照片") { _, _ ->
                begin { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            }
            .setNeutralButton("授权照片文件夹") { _, _ -> chooseTree() }
            .setNegativeButton("返回", null).show()
    }
    private fun confirm(uris: List<Uri>, kind: PhotoSourceKind) {
        val request = pending ?: return
        AlertDialog.Builder(requireContext()).setTitle("添加这些照片？")
            .setMessage("用于：${requireArguments().getString("display") ?: "当前歌单或专辑"}\n添加 ${uris.size} ${if (kind == PhotoSourceKind.DIRECTORY) "个文件夹" else "张照片"}，在手机上推荐封面。部分云端照片可能需要联网读取。")
            .setNegativeButton("取消", null).setPositiveButton("添加") { _, _ -> work {
                check(targetExists(requireContext(), target)) { "目标已删除或暂不可用" }
                repository.validate(target, request)
                val access = PhotoAccessManager(requireContext())
                check(uris.all { access.persist(it) }) { "有些照片暂时无法长期使用，本次未添加。请重新选择照片或文件夹。" }
                repository.attach(target, request, PhotoSource(UUID.randomUUID().toString(),
                    if (kind == PhotoSourceKind.DIRECTORY) "照片文件夹" else "已选照片组", kind, uris.map { it.toString() }, recursive && kind == PhotoSourceKind.DIRECTORY))
                reload()
            } }.show()
    }
    private fun share() = work {
        val sources = repository.shareableSources()
        withContext(Dispatchers.Main) {
            AlertDialog.Builder(requireContext()).setTitle("选择已添加的照片")
                .setItems(sources.map { "${it.name} · ${it.id.take(6)}" }.toTypedArray()) { _, which -> begin {
                    AlertDialog.Builder(requireContext()).setMessage("也用这些照片为这个歌单或专辑推荐封面？")
                        .setNegativeButton("取消", null).setPositiveButton("使用") { _, _ -> work {
                            check(targetExists(requireContext(), target)); repository.attach(target, pending!!, sources[which]); reload()
                        } }.show()
                } }.setNegativeButton("取消", null).show()
        }
    }
    private fun reload() {
        job?.cancel()
        status?.text = "正在读取照片…"
        cancelLoading?.visibility = View.VISIBLE
        body?.removeAllViews()
        job = lifecycleScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) { check(targetExists(requireContext(), target)); repository.snapshot(target) }
                for (source in snapshot.second) {
                    val result = PhotoCandidateProvider(requireContext()).enumerate(source, snapshot.first.excluded.toSet())
                    val row = Button(requireContext()).apply {
                        val label = when(result.state) {
                            PhotoReadState.AVAILABLE -> "可用"
                            PhotoReadState.REAUTHORIZE -> "请重新选择照片"
                            PhotoReadState.MISSING_OR_OFFLINE -> "暂时打不开，请检查照片是否还在或稍后重试"
                            PhotoReadState.UNSUPPORTED -> "暂不支持这种图片"
                            PhotoReadState.PARTIAL -> "部分照片暂时打不开"
                            PhotoReadState.BUDGET_REACHED -> "照片较多，只读取了部分"
                        }
                        val count = if (result.photos.isEmpty() && result.state in setOf(PhotoReadState.REAUTHORIZE, PhotoReadState.MISSING_OR_OFFLINE)) "暂时无法统计张数" else "当前可用 ${result.photos.size} 张"
                        text = "${source.name}\n$count · $label"
                        setOnClickListener { sourceActions(source, result) }
                    }; body?.addView(row)
                }
                status?.text = if (snapshot.second.isEmpty()) "添加照片后即可推荐，也可以直接选一张封面。" else "已添加 ${snapshot.second.size} 组照片，点按可查看或移除。"
            } catch (e: PhotoScopeIdentityChanged) {
                status?.text = "专辑信息已变化，请重新选择照片。"
                body?.addView(Button(requireContext()).apply {
                    text = "重新选择照片"
                    setOnClickListener {
                        AlertDialog.Builder(requireContext()).setTitle("为这个专辑重新选择照片？")
                            .setMessage("专辑信息已变化。继续后会移除它原先添加的照片，再由你重新选择。原图和其他歌单、专辑不受影响。")
                            .setNegativeButton("取消", null).setPositiveButton("继续") { _, _ -> work {
                                withContext(Dispatchers.IO) {
                                    check(targetExists(requireContext(), target)) { "目标已删除或暂不可用" }
                                    repository.rebindAlbum(target as CoverTarget.Album, e.previous)
                                }
                                pending = null; reload()
                            } }.show()
                    }
                })
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { status?.text = "照片暂时打不开，请稍后重试或重新添加。" }
            finally { if (job === currentCoroutineContext()[Job]) cancelLoading?.visibility = View.GONE }
        }
    }
    private fun sourceActions(source: PhotoSource, result: SourceCandidates) {
        AlertDialog.Builder(requireContext()).setTitle(source.name)
            .setItems(arrayOf("查看照片", "从这里移除", "照片说明")) { _, index ->
                if (index == 1) work { repository.remove(target, source.id); reload() }
                else if (index == 0) showCandidates(result.photos)
                else AlertDialog.Builder(requireContext()).setTitle(source.name)
                    .setMessage("${if (source.kind == PhotoSourceKind.DIRECTORY) "刷新后会加入文件夹里的新照片。" else "新照片需要你另行添加。"}\n\n照片打不开时，可从“添加照片”重新选择。移除不会删除原图或更换封面。")
                    .setPositiveButton("知道了", null).show()
            }.show()
    }
    private fun showCandidates(photos: List<ScopedPhoto>) {
        val rows = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        val preview = AlertDialog.Builder(requireContext()).setTitle("已添加的照片 · ${photos.size} 张")
            .setView(ScrollView(requireContext()).apply { addView(rows) }).setNegativeButton("关闭", null).create()
        val previewJob = lifecycleScope.launch {
            for (photo in photos.take(30)) {
                ensureActive()
                val bitmap = withContext(Dispatchers.IO) { runCatching { SafePhotoDecoder(requireContext()).let { it.decode(it.bytes(Uri.parse(photo.uri)), 160) } }.getOrNull() }
                val image = ImageView(requireContext()).apply { layoutParams = LinearLayout.LayoutParams(-1, 220); setImageBitmap(bitmap); contentDescription = "照片 ${photo.id.take(8)}；点击从范围移除" }
                rows.addView(image)
                image.setOnClickListener { AlertDialog.Builder(requireContext()).setMessage("从这里移除这张照片？原图和封面不受影响。")
                    .setNegativeButton("取消", null).setPositiveButton("移除") { _, _ -> work { repository.exclude(target, photo.id); preview.dismiss(); reload() } }.show() }
            }
            if (photos.size > 30) rows.addView(Button(requireContext()).apply { text = "查看后续 ${photos.size - 30} 张"; setOnClickListener { preview.dismiss(); showCandidates(photos.drop(30)) } })
        }
        preview.setOnDismissListener { previewJob.cancel(); rows.removeAllViews() }; preview.show()
    }
    private fun work(action: suspend () -> Unit) { lifecycleScope.launch {
        try { action() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { status?.text = if (e.message?.startsWith("有些照片") == true) e.message else "操作未完成，原有照片不变。请重新打开后再试。" }
    } }
    private fun recommend() { PhotoRecommendationsDialogFragment.newInstance(target).show(parentFragmentManager, "photo-recommendations") }
    private fun search() {
        val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 8) }
        val edit = EditText(requireContext()).apply { setText(searchQuery); hint = "想找什么？例如：湖边、雨天" }
        layout.addView(edit)
        layout.addView(Button(requireContext()).apply {
            text = "按氛围填写关键词"
            setOnClickListener {
                val moods = PhotoRanker.mapping.keys.toList()
                AlertDialog.Builder(requireContext()).setTitle("选择氛围").setItems(moods.map { PhotoRanker.moodName(it) }.toTypedArray()) { _, index ->
                    edit.setText(PhotoRanker.suggestions(listOf(com.iven.musicplayergo.analysis.MoodScore(moods[index], 1f))).joinToString(" "))
                }.show()
            }
        })
        val launcher = SystemPhotoSearchLauncher(requireContext())
        val support = if (launcher.capability() == SystemPhotoSearchCapability.MAY_REQUEST) "打开手机相册查找照片；有些手机需要你手动输入关键词。" else "这台手机暂不支持自动搜索，你仍可手动查找或选择照片。"
        AlertDialog.Builder(requireContext()).setTitle("按关键词找照片")
            .setMessage("$support\n\n关键词会交给手机相册服务。只有你选中并确认的照片会添加到这里。")
            .setView(layout).setNegativeButton("取消") { _, _ -> searchQuery = edit.text.toString() }
            .setNeutralButton("直接选照片") { _, _ -> searchQuery = edit.text.toString(); begin { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
            .setPositiveButton("查找照片") { _, _ ->
                searchQuery = PhotoSearchPolicy.query(edit.text.toString())
                if (searchQuery.isEmpty()) { status?.text = "请输入关键词，也可以直接选照片。" }
                else begin {
                    launcher.launch(searchQuery, { systemSearch.launch(it); status?.text = "已打开相册。如未自动搜索，请手动输入关键词。" }, {
                        status?.text = "已打开选图页面，请选择照片。搜索词已保留。"
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

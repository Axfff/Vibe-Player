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
            else status?.text = "未选择照片。关键词已保留；可使用普通选图或文件夹。"
        } else status?.text = "已取消；范围保持不变。若系统忽略关键词或无结果，可重新打开并选择普通选图。"
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
        } else status?.text = "已取消文件选择，照片范围保持不变。"
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
            setOnClickListener { job?.cancel(); visibility = View.GONE; status?.text = "已取消加载，照片范围保持不变。" }
        }.also { layout.addView(it) }
        return AlertDialog.Builder(requireContext()).setTitle("选择封面")
            .setView(ScrollView(requireContext()).apply { addView(layout) }).setNegativeButton("关闭", null).create()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun addPhotos() {
        AlertDialog.Builder(requireContext()).setTitle("添加用于推荐的照片")
            .setItems(arrayOf("选择照片", "选择照片文件夹", "从文件选择照片", "从系统搜索添加照片", "小米相册搜索后添加", "共用已有来源")) { _, index ->
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
            .setItems(arrayOf("刷新照片来源", "恢复已移除候选", "配置本地分析模型", "恢复默认封面", "照片使用说明")) { _, index ->
                when (index) {
                    0 -> work { repository.refresh(target); reload() }
                    1 -> work { repository.refresh(target, true); reload() }
                    2 -> PhotoModelsDialogFragment().show(parentFragmentManager, "photo-models")
                    3 -> AlertDialog.Builder(requireContext()).setTitle("恢复默认封面？")
                        .setMessage("只移除当前自定义封面，照片来源和原图保留。")
                        .setNegativeButton("取消", null).setPositiveButton("恢复") { _, _ -> work {
                            check(targetExists(requireContext(), target)) { "目标已删除或暂不可用" }
                            val result = CoverRepositories.get(requireContext()).removeCover(target)
                            if (result is CoverResult.Failure) error(result.error.name)
                            parentFragmentManager.setFragmentResult(CoverCropDialogFragment.RESULT_KEY, Bundle())
                        } }.show()
                    4 -> AlertDialog.Builder(requireContext()).setTitle("照片使用说明")
                        .setMessage("推荐只分析你为当前目标选择的照片，在本机完成。照片提供方可能需要联网下载。\n\n移除来源或候选不会删除原图或已保存封面，也不等于撤销系统授权。新增照片需要重新选择，或刷新已授权文件夹。")
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
        val option = CheckBox(requireContext()).apply { text = "包括子目录（最多 3 层）；默认仅直接图片" }
        AlertDialog.Builder(requireContext()).setTitle("选择照片文件夹")
            .setMessage("刷新可能纳入新增文件。每次最多检查 500 项，10 秒预算；不扫描存储根目录。")
            .setView(option).setNegativeButton("取消", null).setPositiveButton("选择目录") { _, _ ->
                recursive = option.isChecked
                begin { tree.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)) }
            }.show()
    }
    private fun albumHelp() {
        AlertDialog.Builder(requireContext()).setTitle("用小米相册先筛选照片")
            .setMessage("1. 在小米相册搜索关键词，多选想要的结果。\n" +
                "2. 选择“添加到 → 新建相册”；出现移动／复制选项时选“复制”，保留原图。\n" +
                "3. 回到这里，选择整理好的照片（每批最多 100 张），确认加入当前目标。\n" +
                "4. 点击“推荐封面”。\n\n" +
                "建相册本身不等于授权。已选照片组不会自动纳入新增照片；需要随目录刷新时可授权照片文件夹。\n\n" +
                "找不到相册时，可返回使用“从文件选择照片”。移除 App 内来源不删除图片或已保存封面。小米相册的删除可能同步到云端和其他设备；清理时请只确认临时副本。")
            .setPositiveButton("选择整理好的照片") { _, _ ->
                begin { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            }
            .setNeutralButton("授权照片文件夹") { _, _ -> chooseTree() }
            .setNegativeButton("返回", null).show()
    }
    private fun confirm(uris: List<Uri>, kind: PhotoSourceKind) {
        val request = pending ?: return
        AlertDialog.Builder(requireContext()).setTitle("加入发起操作的目标？")
            .setMessage("目标：${requireArguments().getString("display") ?: target.storageKey}\n${uris.size} 个选择，仅用于本地封面分析。照片提供方可能需要联网下载。取消保留旧来源。")
            .setNegativeButton("取消", null).setPositiveButton("确认加入") { _, _ -> work {
                check(targetExists(requireContext(), target)) { "目标已删除或暂不可用" }
                repository.validate(target, request)
                val access = PhotoAccessManager(requireContext())
                check(uris.all { access.persist(it) }) { "部分照片无法保留读取权限；未加入来源。请重新选择或使用照片文件夹。" }
                repository.attach(target, request, PhotoSource(UUID.randomUUID().toString(),
                    if (kind == PhotoSourceKind.DIRECTORY) "照片文件夹" else "已选照片组", kind, uris.map { it.toString() }, recursive && kind == PhotoSourceKind.DIRECTORY))
                reload()
            } }.show()
    }
    private fun share() = work {
        val sources = repository.shareableSources()
        withContext(Dispatchers.Main) {
            AlertDialog.Builder(requireContext()).setTitle("选择要共用的来源")
                .setItems(sources.map { "${it.name} · ${it.id.take(6)}" }.toTypedArray()) { _, which -> begin {
                    AlertDialog.Builder(requireContext()).setMessage("将此来源用于当前目标的本地分析？")
                        .setNegativeButton("取消", null).setPositiveButton("共用") { _, _ -> work {
                            check(targetExists(requireContext(), target)); repository.attach(target, pending!!, sources[which]); reload()
                        } }.show()
                } }.setNegativeButton("取消", null).show()
        }
    }
    private fun reload() {
        job?.cancel()
        status?.text = "正在读取照片来源…"
        cancelLoading?.visibility = View.VISIBLE
        body?.removeAllViews()
        job = lifecycleScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) { check(targetExists(requireContext(), target)); repository.snapshot(target) }
                for (source in snapshot.second) {
                    val result = PhotoCandidateProvider(requireContext()).enumerate(source, snapshot.first.excluded.toSet())
                    val row = Button(requireContext()).apply {
                        val label = when(result.state) {
                            PhotoReadState.AVAILABLE -> "可读取"
                            PhotoReadState.REAUTHORIZE -> "需要重新授权"
                            PhotoReadState.MISSING_OR_OFFLINE -> "已移动、删除或提供方暂时离线；无法确认原因"
                            PhotoReadState.UNSUPPORTED -> "不支持的格式"
                            PhotoReadState.PARTIAL -> "部分图片不可读取"
                            PhotoReadState.BUDGET_REACHED -> "达到扫描预算，未扫描全部"
                        }
                        val count = if (result.photos.isEmpty() && result.state in setOf(PhotoReadState.REAUTHORIZE, PhotoReadState.MISSING_OR_OFFLINE)) "数量未知" else "当前可用 ${result.photos.size} 张"
                        text = "${source.name} · ${if(source.kind == PhotoSourceKind.DIRECTORY) "目录" else "固定照片组"}\n$label · $count"
                        setOnClickListener { sourceActions(source, result) }
                    }; body?.addView(row)
                }
                status?.text = if (snapshot.second.isEmpty()) "还没有照片。添加后即可推荐，或直接选一张封面。" else "已关联 ${snapshot.second.size} 个来源，点按可查看照片或移除。"
            } catch (e: PhotoScopeIdentityChanged) {
                status?.text = e.message
                body?.addView(Button(requireContext()).apply {
                    text = "为当前专辑重新授权"
                    setOnClickListener {
                        AlertDialog.Builder(requireContext()).setTitle("清空旧绑定并重新授权？")
                            .setMessage("此音乐专辑的身份已变化。确认后会移除旧专辑的照片来源绑定，当前专辑从空范围开始；原图和其他目标的来源保留。然后可重新选择照片。")
                            .setNegativeButton("取消", null).setPositiveButton("重新授权") { _, _ -> work {
                                withContext(Dispatchers.IO) {
                                    check(targetExists(requireContext(), target)) { "目标已删除或暂不可用" }
                                    repository.rebindAlbum(target as CoverTarget.Album, e.previous)
                                }
                                pending = null; reload()
                            } }.show()
                    }
                })
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { status?.text = e.message ?: "来源暂不可用" }
            finally { if (job === currentCoroutineContext()[Job]) cancelLoading?.visibility = View.GONE }
        }
    }
    private fun sourceActions(source: PhotoSource, result: SourceCandidates) {
        AlertDialog.Builder(requireContext()).setTitle(source.name)
            .setItems(arrayOf("查看照片", "从当前目标移除来源", "来源详情与授权说明")) { _, index ->
                if (index == 1) work { repository.remove(target, source.id); reload() }
                else if (index == 0) showCandidates(result.photos)
                else AlertDialog.Builder(requireContext()).setTitle(source.name)
                    .setMessage("本次已检查 ${result.examined} 项。${if (source.kind == PhotoSourceKind.DIRECTORY) "文件夹可刷新纳入新增照片。" else "已选照片组不会自动增加照片。"}\n\n需要重新授权时，请从“添加照片”重新选择该来源。移除只影响当前目标，不删除原图或已保存封面。")
                    .setPositiveButton("知道了", null).show()
            }.show()
    }
    private fun showCandidates(photos: List<ScopedPhoto>) {
        val rows = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        val preview = AlertDialog.Builder(requireContext()).setTitle("当前候选 · ${photos.size} 张")
            .setView(ScrollView(requireContext()).apply { addView(rows) }).setNegativeButton("关闭", null).create()
        val previewJob = lifecycleScope.launch {
            for (photo in photos.take(30)) {
                ensureActive()
                val bitmap = withContext(Dispatchers.IO) { runCatching { SafePhotoDecoder(requireContext()).let { it.decode(it.bytes(Uri.parse(photo.uri)), 160) } }.getOrNull() }
                val image = ImageView(requireContext()).apply { layoutParams = LinearLayout.LayoutParams(-1, 220); setImageBitmap(bitmap); contentDescription = "候选 ${photo.id.take(8)}；点击从范围移除" }
                rows.addView(image)
                image.setOnClickListener { AlertDialog.Builder(requireContext()).setMessage("只从此目标移除候选？")
                    .setNegativeButton("取消", null).setPositiveButton("移除") { _, _ -> work { repository.exclude(target, photo.id); preview.dismiss(); reload() } }.show() }
            }
            if (photos.size > 30) rows.addView(Button(requireContext()).apply { text = "查看后续 ${photos.size - 30} 张"; setOnClickListener { preview.dismiss(); showCandidates(photos.drop(30)) } })
        }
        preview.setOnDismissListener { previewJob.cancel(); rows.removeAllViews() }; preview.show()
    }
    private fun work(action: suspend () -> Unit) { lifecycleScope.launch {
        try { action() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { status?.text = e.message ?: "操作失败，原范围保留" }
    } }
    private fun recommend() { PhotoRecommendationsDialogFragment.newInstance(target).show(parentFragmentManager, "photo-recommendations") }
    private fun search() {
        val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 8) }
        val edit = EditText(requireContext()).apply { setText(searchQuery); hint = "输入／修改关键词（最多 200 字）" }
        layout.addView(edit)
        layout.addView(Button(requireContext()).apply {
            text = "从我选择的氛围生成建议词"
            setOnClickListener {
                val moods = PhotoRanker.mapping.keys.toList()
                AlertDialog.Builder(requireContext()).setTitle("用户选择的氛围").setItems(moods.map { PhotoRanker.moodName(it) }.toTypedArray()) { _, index ->
                    edit.setText(PhotoRanker.suggestions(listOf(com.iven.musicplayergo.analysis.MoodScore(moods[index], 1f))).joinToString(" "))
                }.show()
            }
        })
        val launcher = SystemPhotoSearchLauncher(requireContext())
        val support = launcher.explanation()
        AlertDialog.Builder(requireContext()).setTitle("从系统搜索添加照片")
            .setMessage("$support\n关键词将交给系统选图器及其媒体提供方处理；可能显示范围外照片。仅你选中并确认的图片会加入本目标。系统可能忽略关键词；可随时使用普通选图／文件夹。")
            .setView(layout).setNegativeButton("取消") { _, _ -> searchQuery = edit.text.toString() }
            .setNeutralButton("普通选图") { _, _ -> searchQuery = edit.text.toString(); begin { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
            .setPositiveButton("请求系统搜索") { _, _ ->
                searchQuery = PhotoSearchPolicy.query(edit.text.toString())
                if (searchQuery.isEmpty()) { status?.text = "请输入关键词，或使用普通选图。" }
                else begin {
                    launcher.launch(searchQuery, { systemSearch.launch(it); status?.text = "已请求搜索高亮；实际效果由系统提供方决定。" }, {
                        status?.text = "当前系统未提供可用的搜索增强，你仍可手动选择照片。关键词已保留。"
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

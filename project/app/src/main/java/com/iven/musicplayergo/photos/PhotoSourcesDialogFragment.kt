package com.iven.musicplayergo.photos

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
    private var job: Job? = null
    private var pending: PhotoRequest? = null
    private var recursive = false
    private val photos = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isNotEmpty()) confirm(uris.distinct(), PhotoSourceKind.SELECTED)
    }
    private val tree = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.takeIf { result.resultCode == android.app.Activity.RESULT_OK }?.let { confirm(listOf(it), PhotoSourceKind.DIRECTORY) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString("request")?.let { id -> pending = PhotoRequest(target.storageKey,
            savedInstanceState.getString("identity") ?: "", savedInstanceState.getLong("revision"), id) }
        recursive = savedInstanceState?.getBoolean("recursive") ?: false
    }
    override fun onSaveInstanceState(outState: Bundle) {
        pending?.let { outState.putString("request", it.requestId); outState.putString("identity", it.identity); outState.putLong("revision", it.revision) }
        outState.putBoolean("recursive", recursive)
        super.onSaveInstanceState(outState)
    }
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 12, 24, 12) }
        status = TextView(requireContext()).also { layout.addView(it) }
        body = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        fun button(label: String, action: () -> Unit) { layout.addView(Button(requireContext()).apply { text = label; setOnClickListener { action() } }) }
        button("添加一组照片") { begin { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
        button("添加照片文件夹") { chooseTree() }
        button("明确共用已有来源") { share() }
        button("刷新目录与权限") { work { repository.refresh(target); reload() } }
        button("恢复已移除候选") { work { repository.refresh(target, true); reload() } }
        button("推荐当前照片范围") { recommend() }
        button("从系统搜索添加照片") { search() }
        button("手动选择单张封面") {
            val crop = when(val t = target) { is CoverTarget.Playlist -> CoverCropDialogFragment.forPlaylist(t.playlistId)
                is CoverTarget.Album -> CoverCropDialogFragment.forAlbum(t.albumId, t.artistSnapshot, t.albumTitleSnapshot) }
            crop.show(parentFragmentManager, "manual-cover")
        }
        button("恢复默认封面") { work {
            if (!targetExists(requireContext(), target)) error("目标已删除或暂不可用")
            val result = CoverRepositories.get(requireContext()).removeCover(target)
            if (result is CoverResult.Failure) error(result.error.name)
            parentFragmentManager.setFragmentResult(CoverCropDialogFragment.RESULT_KEY, Bundle())
        } }
        button("取消加载／分析") { job?.cancel(); status?.text = "已取消；原范围和封面保持不变。" }
        layout.addView(body)
        return AlertDialog.Builder(requireContext()).setTitle("封面照片来源")
            .setView(ScrollView(requireContext()).apply { addView(layout) }).setNegativeButton("关闭", null).create()
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
        status?.text = "正在校验授权与候选…"
        body?.removeAllViews()
        job = lifecycleScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) { check(targetExists(requireContext(), target)); repository.snapshot(target) }
                for (source in snapshot.second) {
                    val result = PhotoCandidateProvider(requireContext()).enumerate(source, snapshot.first.excluded.toSet())
                    val row = Button(requireContext()).apply {
                        text = "${source.name} · ${source.kind}\n${result.state} · 可用 ${result.photos.size} 张 · 已检查 ${result.examined} 项"
                        setOnClickListener { sourceActions(source, result) }
                    }; body?.addView(row)
                }
                status?.text = "仅当前目标范围；移除不会删除原图或已保存封面。应用内移除不等于撤销系统授权。"
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { status?.text = e.message ?: "来源暂不可用" }
        }
    }
    private fun sourceActions(source: PhotoSource, result: SourceCandidates) {
        AlertDialog.Builder(requireContext()).setTitle(source.name)
            .setItems(arrayOf("查看候选（点击移除；重新添加来源可重新授权）", "从当前目标移除来源")) { _, index ->
                if (index == 1) work { repository.remove(target, source.id); reload() }
                else showCandidates(result.photos)
            }.show()
    }
    private fun showCandidates(photos: List<ScopedPhoto>) {
        val rows = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        val preview = AlertDialog.Builder(requireContext()).setTitle("当前候选 · ${photos.size} 张")
            .setView(ScrollView(requireContext()).apply { addView(rows) }).setNegativeButton("关闭", null).create()
        val previewJob = lifecycleScope.launch {
            for (photo in photos) {
                ensureActive()
                val bitmap = withContext(Dispatchers.IO) { runCatching { SafePhotoDecoder(requireContext()).let { it.decode(it.bytes(Uri.parse(photo.uri)), 160) } }.getOrNull() }
                val image = ImageView(requireContext()).apply { layoutParams = LinearLayout.LayoutParams(-1, 220); setImageBitmap(bitmap); contentDescription = "候选 ${photo.id.take(8)}；点击从范围移除" }
                rows.addView(image)
                image.setOnClickListener { AlertDialog.Builder(requireContext()).setMessage("只从此目标移除候选？")
                    .setNegativeButton("取消", null).setPositiveButton("移除") { _, _ -> work { repository.exclude(target, photo.id); preview.dismiss(); reload() } }.show() }
            }
        }
        preview.setOnDismissListener { previewJob.cancel(); rows.removeAllViews() }; preview.show()
    }
    private fun work(action: suspend () -> Unit) { lifecycleScope.launch {
        try { action() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { status?.text = e.message ?: "操作失败，原范围保留" }
    } }
    private fun recommend() { status?.text = "推荐模块正在准备；仍可手动设置封面。" }
    private fun search() { begin { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
    override fun onDestroyView() { job?.cancel(); body = null; status = null; super.onDestroyView() }
    companion object { fun newInstance(target: CoverTarget, display: String) = PhotoSourcesDialogFragment().apply {
        arguments = target.toPhotoBundle().apply { putString("display", display) }
    } }
}

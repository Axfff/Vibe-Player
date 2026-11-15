package com.iven.musicplayergo.photos

import android.app.Dialog
import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.iven.musicplayergo.MusicViewModel
import com.iven.musicplayergo.R
import com.iven.musicplayergo.analysis.*
import com.iven.musicplayergo.covers.*
import com.iven.musicplayergo.models.Music
import com.iven.musicplayergo.playlists.*
import kotlinx.coroutines.*
import java.io.File

class PhotoRecommendationsDialogFragment : DialogFragment() {
    private val target get() = requireArguments().photoTarget()
    private var status: TextView? = null
    private var modelStatus: TextView? = null
    private var modelCheck: Job? = null
    private var rows: LinearLayout? = null
    private var analysisDetails = ""
    private var cancelButton: Button? = null
    private var modelSetupButton: Button? = null
    private var job: Job? = null
    private var representative: Long? = null
    private val viewModel by lazy { ViewModelProvider(requireActivity())[MusicViewModel::class.java] }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); representative = savedInstanceState?.getLong("representative")?.takeIf { it > 0 }
        parentFragmentManager.setFragmentResultListener("photo-models-updated", this) { _, _ -> checkModels() }
    }
    override fun onSaveInstanceState(outState: Bundle) { representative?.let { outState.putLong("representative", it) }; super.onSaveInstanceState(outState) }
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(24,16,24,16) }
        status = TextView(requireContext()).also { it.text = "选一首歌，为它搭配封面。也可以自己选择喜欢的氛围。"; layout.addView(it) }
        modelStatus = TextView(requireContext()).also { layout.addView(it) }
        fun button(label: String, action: () -> Unit) { layout.addView(Button(requireContext()).apply { text = label; setOnClickListener { action() } }) }
        button("选择歌曲") { chooseSong() }
        button("自己选氛围") {
            val moods = PhotoRanker.mapping.keys.filter { it != "nature" }
            AlertDialog.Builder(requireContext()).setTitle("想要什么感觉？").setItems(moods.map { PhotoRanker.moodName(it) }.toTypedArray()) { _, index ->
                rank(listOf(MoodScore(moods[index], 1f)), "你选择了：${PhotoRanker.moodName(moods[index])}")
            }.show()
        }
        button("更多") {
            AlertDialog.Builder(requireContext()).setTitle("推荐选项")
                .setItems(arrayOf("分析详情", "推荐设置")) { _, index ->
                    if (index == 0) AlertDialog.Builder(requireContext()).setTitle("分析详情")
                        .setMessage(analysisDetails.ifEmpty { "只分析代表曲开头最多 30 秒，在当前授权照片中推荐。匹配分用于本批排序，不是准确率。" })
                        .setPositiveButton("知道了", null).show()
                    else { job?.cancel(); rows?.removeAllViews(); PhotoModelsDialogFragment().show(parentFragmentManager, "photo-models") }
                }.setNegativeButton("取消", null).show()
        }
        cancelButton = Button(requireContext()).apply {
            text = "取消分析"; visibility = View.GONE
            setOnClickListener { job?.cancel(); visibility = View.GONE; rows?.removeAllViews(); analysisDetails = ""; status?.text = "已取消，封面未改变。" }
        }.also { layout.addView(it) }
        modelSetupButton = Button(requireContext()).apply {
            text = getString(R.string.photo_model_setup_action)
            visibility = View.GONE
            setOnClickListener { PhotoModelsDialogFragment().show(parentFragmentManager, "photo-models") }
        }.also { layout.addView(it) }
        rows = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }.also { layout.addView(it) }
        return AlertDialog.Builder(requireContext()).setTitle("推荐封面")
            .setView(ScrollView(requireContext()).apply { addView(layout) }).setNegativeButton("关闭", null).create()
    }
    override fun onStart() { super.onStart(); checkModels() }
    private fun checkModels() {
        modelCheck?.cancel()
        modelCheck = lifecycleScope.launch {
            val models = PhotoModelStore(requireContext()).inspect()
            modelStatus?.text = if (models.values.all { it == PhotoModelStatus.READY }) "推荐功能已准备好。"
                else "还差一步准备。请在“更多 → 推荐设置”中导入所需文件。"
        }
    }
    private fun chooseSong() {
        job?.cancel()
        job = lifecycleScope.launch {
            try {
                val songs = withContext(Dispatchers.IO) {
                    val library = viewModel.playlistLibrary.ifEmpty { viewModel.deviceMusic.value.orEmpty() }
                    when(val t = target) {
                        is CoverTarget.Album -> library.filter { it.albumId == t.albumId && it.album == t.albumTitleSnapshot && it.artist == t.artistSnapshot }
                        is CoverTarget.Playlist -> {
                            val repo = PlaylistRepositories.get(requireContext())
                            val playlist = (repo.getPlaylist(t.playlistId) as? PlaylistResult.Success)?.value ?: error("歌单不可用")
                            repo.resolve(playlist, library).mapNotNull { it.music }
                        }
                    }
                }
                if (songs.isEmpty()) { viewModel.refreshPlaylistLibrary(); error("还没有可用歌曲。请刷新音乐库，或自己选择氛围。") }
                var selected = songs.indexOfFirst { it.id == representative }.coerceAtLeast(0)
                AlertDialog.Builder(requireContext()).setTitle("选择一首歌")
                    .setSingleChoiceItems(songs.map { it.title ?: it.displayName ?: "歌曲" }.toTypedArray(), selected) { _, index -> selected = index }
                    .setNegativeButton("取消", null).setPositiveButton("开始推荐") { _, _ -> analyze(songs[selected]) }.show()
            } catch (e: CancellationException) { throw e } catch (e: Exception) { status?.text = "暂时无法读取歌曲，请刷新音乐库后重试。" }
        }
    }
    private fun analyze(song: Music) {
        analysisDetails = ""
        modelSetupButton?.visibility = View.GONE
        cancelButton?.visibility = View.VISIBLE
        representative = song.id
        job?.cancel(); rows?.removeAllViews()
        status?.text = "正在为 ${song.title ?: song.displayName} 寻找合适照片…"
        job = lifecycleScope.launch {
            try {
                val models = PhotoModelStore(requireContext()).inspect()
                if (models.values.any { it != PhotoModelStatus.READY }) {
                    status?.text = "请先在“更多 → 推荐设置”中完成准备，也可以直接选一张封面。"
                    return@launch
                }
                val repo = PhotoRepositories.get(requireContext()); val request = withContext(Dispatchers.IO) { repo.request(target) }
                val root = File(requireContext().noBackupFilesDir, "photo-models")
                val result = MoodAnalyzer(requireContext(), MoodModelFiles(File(root, "discogs-effnet-bsdynamic-1.onnx"),
                    File(root, "mtg_jamendo_moodtheme-discogs-effnet-1.onnx"))).analyze(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id!!))
                repo.validate(target, request)
                when(result) {
                    is MoodResult.Unavailable -> status?.text = "暂时无法分析这首歌，请换一首或自己选择氛围。"
                    is MoodResult.Available -> {
                        val description = "歌曲：${song.title ?: song.displayName}；开头解码 ${result.decodedSeconds} 秒，实际 ${result.patchCount} 个有效片段 / ${result.analyzedSeconds} 秒。\n${PhotoRanker.musicDescription(result.scores)}"
                        performRank(result.scores, description, request)
                    }
                }
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { status?.text = "暂时无法推荐，请重试或换一首歌。" }
              catch (_: LinkageError) { status?.text = "这台手机暂时无法自动推荐，请直接选一张封面。" }
            finally { if (job === currentCoroutineContext()[Job]) cancelButton?.visibility = View.GONE }
        }
    }
    private fun rank(signal: List<MoodScore>, description: String) {
        analysisDetails = ""
        modelSetupButton?.visibility = View.GONE
        cancelButton?.visibility = View.VISIBLE
        job?.cancel(); rows?.removeAllViews()
        job = lifecycleScope.launch {
            try { performRank(signal, description, withContext(Dispatchers.IO) { PhotoRepositories.get(requireContext()).request(target) }) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { status?.text = "照片暂时打不开，请检查照片或重新添加。" }
            catch (_: LinkageError) { status?.text = "这台手机暂时无法自动推荐，请直接选一张封面。" }
            finally { if (job === currentCoroutineContext()[Job]) cancelButton?.visibility = View.GONE }
        }
    }
    private suspend fun performRank(signal: List<MoodScore>, description: String, request: PhotoRequest) {
        val context = requireContext().applicationContext
        val repo = PhotoRepositories.get(context)
        val sources = withContext(Dispatchers.IO) {
            check(targetExists(context, target)) { "目标已删除或不可用" }; repo.validate(target, request); repo.snapshot(target)
        }
        val candidates = linkedMapOf<String, ScopedPhoto>(); var partial = false
        for (source in sources.second) {
            ensureActiveRequest(request)
            val result = PhotoCandidateProvider(context).enumerate(source, sources.first.excluded.toSet())
            partial = partial || result.state != PhotoReadState.AVAILABLE
            result.photos.forEach { if (candidates.size < 500) candidates[it.id] = it else partial = true }
        }
        if (candidates.isEmpty()) { status?.text = "还没有可用照片，请返回添加照片，或重新选择打不开的照片。"; return }
        // A manual mood still needs the photo model, but does not need either audio model.
        // Keep source recovery separate: importing a model must not replace valid grants.
        if (PhotoModelStore(context).inspect()[PhotoModel.PHOTO] != PhotoModelStatus.READY) {
            ensureActiveRequest(request)
            status?.text = getString(R.string.photo_model_setup_required)
            modelSetupButton?.visibility = View.VISIBLE
            return
        }
        var failures = 0; val features = mutableListOf<PhotoFeatures>()
        withPhotoExtractor({ OnnxPhotoFeatureExtractor(context, PhotoModelStore(context).file(PhotoModel.PHOTO)) }) { extractor ->
            for ((index, photo) in candidates.values.withIndex()) {
                ensureActiveRequest(request)
                withContext(Dispatchers.Main) { status?.text = "正在查看照片 ${index + 1}/${candidates.size}…" }
                try { features += extractor.extract(photo) } catch(e: CancellationException) { throw e } catch (_: Exception) { failures++ }
            }
        }
        ensureActiveRequest(request)
        val ranked = PhotoRanker.rank(signal, features, features.size.coerceAtLeast(1))
        parentFragmentManager.setFragmentResult("photo-search-suggestions:${target.storageKey}", Bundle().apply {
            putString("query", PhotoRanker.suggestions(signal).joinToString(" "))
        })
        val uncertain = features.count { it.atmospheres.isEmpty() }
        val summary = "$description\n${if (ranked.isEmpty()) "暂未找到明确匹配，可手动选封面或更换氛围" else "找到 ${ranked.size} 张候选，点击图片预览、裁剪后再确认"}。\n$uncertain 张画面氛围不确定或不适用，已跳过；读取／分析失败 $failures 张${if(partial) "；部分来源未完整扫描" else ""}。\n按整体画面氛围搭配，不要求认出具体物体。描述为模型推测，不代表照片中人物的情绪；可自行改选。"
        status?.text = "${description.substringBefore('；').substringBefore('\n')}\n" +
            (if (ranked.isEmpty()) "暂时没找到合适照片，试试换个氛围。" else "找到 ${ranked.size} 张照片，点选预览。") +
            (if (failures > 0 || partial) "\n部分照片暂时打不开，详见分析详情。" else "")
        showRecommendations(ranked, request, summary)
    }

    /** Render the complete ranked result, while decoding thumbnails one page at a time. */
    internal suspend fun showRecommendations(ranked: List<RankedPhoto>, request: PhotoRequest, summary: String) {
        analysisDetails = summary + getString(R.string.photo_all_recommendation_reasons) + ranked.mapIndexed { index, candidate ->
            getString(R.string.photo_recommendation_reason_score, index + 1,
                candidate.reasons.joinToString("; "), candidate.score)
        }.joinToString("\n\n")
        if (ranked.isEmpty()) return
        val context = requireContext().applicationContext
        val pages = PhotoPageCursor(ranked.size)
        suspend fun showNext() {
            val reservation = pages.begin() ?: return
            var success = false
            try {
            val page = reservation.map { ranked[it] }
            for (candidate in page) {
                ensureActiveRequest(request)
                val bitmap = withContext(Dispatchers.IO) { runCatching { SafePhotoDecoder(context).let { it.decode(it.bytes(Uri.parse(candidate.features.photo.uri)), 200) } }.getOrNull() }
                val image = ImageView(requireContext()).apply { layoutParams = LinearLayout.LayoutParams(-1, 240); setImageBitmap(bitmap); contentDescription = "推荐照片" }
                rows?.addView(image)
                rows?.addView(TextView(requireContext()).apply { text = PhotoRanker.caption(candidate) })
                image.setOnClickListener { CoverCropDialogFragment.forCandidate(target, candidate.features.photo.uri, request,
                    candidate.features.fingerprint).show(parentFragmentManager, "recommended-crop") }
            }
            success = true
            } finally { pages.finish(success) }
        }
        showNext()
        if (pages.shown < ranked.size) rows?.addView(Button(requireContext()).apply { text = "继续查看"; setOnClickListener {
            if (job?.isActive == true) return@setOnClickListener
            isEnabled = false
            job = lifecycleScope.launch {
                try { showNext(); isEnabled = pages.shown < ranked.size }
                catch (e: CancellationException) { throw e }
                catch(e: Exception) { status?.text = "照片已更改，请重新推荐。"; rows?.removeAllViews() }
            }
        } })
    }
    private suspend fun ensureActiveRequest(request: PhotoRequest) {
        currentCoroutineContext().ensureActive()
        withContext(Dispatchers.IO) { check(targetExists(requireContext(), target)) { "目标已删除或不可用" }; PhotoRepositories.get(requireContext()).validate(target, request) }
    }
    override fun onStop() { job?.cancel(); modelCheck?.cancel(); rows?.removeAllViews(); super.onStop() }
    override fun onDestroyView() { job?.cancel(); modelCheck?.cancel(); rows = null; status = null; modelStatus = null; cancelButton = null; modelSetupButton = null; super.onDestroyView() }
    companion object { fun newInstance(target: CoverTarget) = PhotoRecommendationsDialogFragment().apply { arguments = target.toPhotoBundle() } }
}

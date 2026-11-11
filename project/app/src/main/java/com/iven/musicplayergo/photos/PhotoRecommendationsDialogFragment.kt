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
        status = TextView(requireContext()).also { it.text = "选择当前目标的代表曲；只分析开头最多 30 秒，不分析歌词或整份歌单。"; layout.addView(it) }
        modelStatus = TextView(requireContext()).also { layout.addView(it) }
        fun button(label: String, action: () -> Unit) { layout.addView(Button(requireContext()).apply { text = label; setOnClickListener { action() } }) }
        button("选择／更换代表曲并分析") { chooseSong() }
        button("使用我选择的氛围") {
            val moods = PhotoRanker.mapping.keys.filter { it != "nature" }
            AlertDialog.Builder(requireContext()).setTitle("用户选择（不是音频模型分析）").setItems(moods.map { PhotoRanker.moodName(it) }.toTypedArray()) { _, index ->
                rank(listOf(MoodScore(moods[index], 1f)), "用户选择：${PhotoRanker.moodName(moods[index])}")
            }.show()
        }
        button("更多") {
            AlertDialog.Builder(requireContext()).setTitle("推荐选项")
                .setItems(arrayOf("分析详情", "配置本地分析模型")) { _, index ->
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
        rows = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }.also { layout.addView(it) }
        return AlertDialog.Builder(requireContext()).setTitle("推荐当前照片范围")
            .setView(ScrollView(requireContext()).apply { addView(layout) }).setNegativeButton("关闭", null).create()
    }
    override fun onStart() { super.onStart(); checkModels() }
    private fun checkModels() {
        modelCheck?.cancel()
        modelCheck = lifecycleScope.launch {
            val models = PhotoModelStore(requireContext()).inspect()
            modelStatus?.text = if (models.values.all { it == PhotoModelStatus.READY }) "本地分析模型已就绪。"
                else "模型尚未就绪：${models.filterValues { it != PhotoModelStatus.READY }.keys.joinToString("、") { it.title }}。请在“更多”中配置模型。"
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
                if (songs.isEmpty()) { viewModel.refreshPlaylistLibrary(); error("当前目标没有已加载的可用歌曲。刷新音乐库后重试，也可手选氛围。") }
                var selected = songs.indexOfFirst { it.id == representative }.coerceAtLeast(0)
                AlertDialog.Builder(requireContext()).setTitle("代表曲（默认第一首可用歌曲）")
                    .setSingleChoiceItems(songs.map { it.title ?: it.displayName ?: "歌曲" }.toTypedArray(), selected) { _, index -> selected = index }
                    .setNegativeButton("取消", null).setPositiveButton("分析开头片段") { _, _ -> analyze(songs[selected]) }.show()
            } catch (e: CancellationException) { throw e } catch (e: Exception) { status?.text = e.message }
        }
    }
    private fun analyze(song: Music) {
        analysisDetails = ""
        cancelButton?.visibility = View.VISIBLE
        representative = song.id
        job?.cancel(); rows?.removeAllViews()
        status?.text = "正在分析代表曲：${song.title ?: song.displayName}，开头最多 30 秒…"
        job = lifecycleScope.launch {
            try {
                val models = PhotoModelStore(requireContext()).inspect()
                if (models.values.any { it != PhotoModelStatus.READY }) {
                    status?.text = "请先配置本地分析模型；导入并校验三个模型后再分析音乐。也可手动选择封面。"
                    return@launch
                }
                val repo = PhotoRepositories.get(requireContext()); val request = withContext(Dispatchers.IO) { repo.request(target) }
                val root = File(requireContext().noBackupFilesDir, "photo-models")
                val result = MoodAnalyzer(requireContext(), MoodModelFiles(File(root, "discogs-effnet-bsdynamic-1.onnx"),
                    File(root, "mtg_jamendo_moodtheme-discogs-effnet-1.onnx"))).analyze(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id!!))
                repo.validate(target, request)
                when(result) {
                    is MoodResult.Unavailable -> status?.text = "音乐信号不可用：${result.reason}。可以手选氛围或手动封面。"
                    is MoodResult.Available -> {
                        val description = "依据音乐：${song.title ?: song.displayName}；开头解码 ${result.decodedSeconds} 秒，实际 ${result.patchCount} 个有效片段 / ${result.analyzedSeconds} 秒。\n${PhotoRanker.musicDescription(result.scores)}"
                        performRank(result.scores, description, request)
                    }
                }
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { status?.text = e.message ?: "分析不可用" }
              catch (_: LinkageError) { status?.text = "此设备的模型运行库不可用；请手动选择封面。" }
            finally { if (job === currentCoroutineContext()[Job]) cancelButton?.visibility = View.GONE }
        }
    }
    private fun rank(signal: List<MoodScore>, description: String) {
        analysisDetails = ""
        cancelButton?.visibility = View.VISIBLE
        job?.cancel(); rows?.removeAllViews()
        job = lifecycleScope.launch {
            try { performRank(signal, description, withContext(Dispatchers.IO) { PhotoRepositories.get(requireContext()).request(target) }) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { status?.text = e.message ?: "图片分析不可用" }
            catch (_: LinkageError) { status?.text = "此设备的模型运行库不可用；请手动选择封面。" }
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
        if (candidates.isEmpty()) { status?.text = "当前范围没有可读取照片；请检查来源与授权。"; return }
        var failures = 0; val features = mutableListOf<PhotoFeatures>()
        withPhotoExtractor({ OnnxPhotoFeatureExtractor(context, PhotoModelStore(context).file(PhotoModel.PHOTO)) }) { extractor ->
            for ((index, photo) in candidates.values.withIndex()) {
                ensureActiveRequest(request)
                withContext(Dispatchers.Main) { status?.text = "$description\n本地图片分析 ${index + 1}/${candidates.size}；可取消。" }
                try { features += extractor.extract(photo) } catch(e: CancellationException) { throw e } catch (_: Exception) { failures++ }
            }
        }
        ensureActiveRequest(request)
        val ranked = PhotoRanker.rank(signal, features, features.size.coerceAtLeast(1))
        parentFragmentManager.setFragmentResult("photo-search-suggestions:${target.storageKey}", Bundle().apply {
            putString("query", PhotoRanker.suggestions(signal).joinToString(" "))
        })
        val uncertain = features.count { it.atmospheres.isEmpty() }
        analysisDetails = "$description\n${if (ranked.isEmpty()) "暂未找到明确匹配，可手动选封面或更换氛围" else "找到 ${ranked.size} 张候选，点击图片预览、裁剪后再确认"}。\n$uncertain 张画面氛围不确定或不适用，已跳过；读取／分析失败 $failures 张${if(partial) "；部分来源未完整扫描" else ""}。\n按整体画面氛围搭配，不要求认出具体物体。描述为模型推测，不代表照片中人物的情绪；可自行改选。"
        status?.text = "${description.substringBefore('；').substringBefore('\n')}\n" +
            (if (ranked.isEmpty()) "暂未找到明确匹配，可更换氛围或手动选图。" else "找到 ${ranked.size} 张，点按图片预览。") +
            "\n优先匹配主要氛围，不足 5 张时不补入其他氛围。" +
            (if (failures > 0 || partial) "\n部分照片未能分析，详见分析详情。" else "")
        if (ranked.isEmpty()) return
        val pages = PhotoPageCursor(ranked.size)
        suspend fun showNext() {
            val reservation = pages.begin() ?: return
            var success = false
            try {
            val page = reservation.map { ranked[it] }
            for (candidate in page) {
                ensureActiveRequest(request)
                val bitmap = withContext(Dispatchers.IO) { runCatching { SafePhotoDecoder(context).let { it.decode(it.bytes(Uri.parse(candidate.features.photo.uri)), 200) } }.getOrNull() }
                val image = ImageView(requireContext()).apply { layoutParams = LinearLayout.LayoutParams(-1, 240); setImageBitmap(bitmap); contentDescription = "推荐候选" }
                rows?.addView(image)
                rows?.addView(TextView(requireContext()).apply { text = "推荐理由：${candidate.reasons.joinToString("\n")}\n参考匹配分 ${String.format(java.util.Locale.ROOT, "%.2f", candidate.score)}，仅用于本批排序，不是准确率。" })
                if (candidate.features.labels.any { it.label in PhotoScenePolicy.broadNames }) rows?.addView(TextView(requireContext()).apply {
                    text = "仅支持宽泛画面描述，具体内容仍需你确认。"
                })
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
                catch(e: Exception) { status?.text = "范围已变化；请重新推荐。"; rows?.removeAllViews() }
            }
        } })
    }
    private suspend fun ensureActiveRequest(request: PhotoRequest) {
        currentCoroutineContext().ensureActive()
        withContext(Dispatchers.IO) { check(targetExists(requireContext(), target)) { "目标已删除或不可用" }; PhotoRepositories.get(requireContext()).validate(target, request) }
    }
    override fun onStop() { job?.cancel(); modelCheck?.cancel(); rows?.removeAllViews(); super.onStop() }
    override fun onDestroyView() { job?.cancel(); modelCheck?.cancel(); rows = null; status = null; modelStatus = null; cancelButton = null; super.onDestroyView() }
    companion object { fun newInstance(target: CoverTarget) = PhotoRecommendationsDialogFragment().apply { arguments = target.toPhotoBundle() } }
}

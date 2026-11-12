package com.iven.musicplayergo.photos

import com.iven.musicplayergo.R
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
        status = TextView(requireContext()).also { it.text = getString(R.string.photo_recommend_intro); layout.addView(it) }
        modelStatus = TextView(requireContext()).also { layout.addView(it) }
        fun button(label: String, action: () -> Unit) { layout.addView(Button(requireContext()).apply { text = label; setOnClickListener { action() } }) }
        button(getString(R.string.photo_choose_song)) { chooseSong() }
        button(getString(R.string.photo_choose_atmosphere)) {
            val moods = PhotoRanker.mapping.keys.filter { it != "nature" }
            AlertDialog.Builder(requireContext()).setTitle(getString(R.string.photo_feeling_question)).setItems(moods.map { PhotoRanker.moodName(it).resolve(requireContext()) }.toTypedArray()) { _, index ->
                rank(listOf(MoodScore(moods[index], 1f)), getString(R.string.photo_chosen_mood , PhotoRanker.moodName(moods[index]).resolve(requireContext())))
            }.show()
        }
        button(getString(R.string.photo_more)) {
            AlertDialog.Builder(requireContext()).setTitle(getString(R.string.photo_options))
                .setItems(arrayOf(getString(R.string.photo_analysis_details), getString(R.string.photo_settings))) { _, index ->
                    if (index == 0) AlertDialog.Builder(requireContext()).setTitle(getString(R.string.photo_analysis_details))
                        .setMessage(analysisDetails.ifEmpty { getString(R.string.photo_analysis_boundary) })
                        .setPositiveButton(getString(R.string.photo_ok), null).show()
                    else { job?.cancel(); rows?.removeAllViews(); PhotoModelsDialogFragment().show(parentFragmentManager, "photo-models") }
                }.setNegativeButton(getString(R.string.photo_cancel), null).show()
        }
        cancelButton = Button(requireContext()).apply {
            text = getString(R.string.photo_cancel_analysis); visibility = View.GONE
            setOnClickListener { job?.cancel(); visibility = View.GONE; rows?.removeAllViews(); analysisDetails = ""; status?.text = getString(R.string.photo_analysis_cancelled) }
        }.also { layout.addView(it) }
        rows = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }.also { layout.addView(it) }
        return AlertDialog.Builder(requireContext()).setTitle(getString(R.string.photo_recommend_cover))
            .setView(ScrollView(requireContext()).apply { addView(layout) }).setNegativeButton(getString(R.string.photo_close), null).create()
    }
    override fun onStart() { super.onStart(); checkModels() }
    private fun checkModels() {
        modelCheck?.cancel()
        modelCheck = lifecycleScope.launch {
            val models = PhotoModelStore(requireContext()).inspect()
            modelStatus?.text = if (models.values.all { it == PhotoModelStatus.READY }) getString(R.string.photo_models_ready)
                else getString(R.string.photo_models_needed)
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
                            val playlist = (repo.getPlaylist(t.playlistId) as? PlaylistResult.Success)?.value ?: error(getString(R.string.photo_playlist_unavailable))
                            repo.resolve(playlist, library).mapNotNull { it.music }
                        }
                    }
                }
                if (songs.isEmpty()) { viewModel.refreshPlaylistLibrary(); error(getString(R.string.photo_no_songs)) }
                var selected = songs.indexOfFirst { it.id == representative }.coerceAtLeast(0)
                AlertDialog.Builder(requireContext()).setTitle(getString(R.string.photo_pick_song_title))
                    .setSingleChoiceItems(songs.map { it.title ?: it.displayName ?: getString(R.string.photo_song_fallback) }.toTypedArray(), selected) { _, index -> selected = index }
                    .setNegativeButton(getString(R.string.photo_cancel), null).setPositiveButton(getString(R.string.photo_start_recommend)) { _, _ -> analyze(songs[selected]) }.show()
            } catch (e: CancellationException) { throw e } catch (e: Exception) { status?.text = getString(R.string.photo_song_read_failed) }
        }
    }
    private fun analyze(song: Music) {
        analysisDetails = ""
        cancelButton?.visibility = View.VISIBLE
        representative = song.id
        job?.cancel(); rows?.removeAllViews()
        status?.text = getString(R.string.photo_finding_for_song , song.title ?: song.displayName ?: getString(R.string.photo_song_fallback))
        job = lifecycleScope.launch {
            try {
                val models = PhotoModelStore(requireContext()).inspect()
                if (models.values.any { it != PhotoModelStatus.READY }) {
                    status?.text = getString(R.string.photo_setup_first)
                    return@launch
                }
                val repo = PhotoRepositories.get(requireContext()); val request = withContext(Dispatchers.IO) { repo.request(target) }
                val root = File(requireContext().noBackupFilesDir, "photo-models")
                val result = MoodAnalyzer(requireContext(), MoodModelFiles(File(root, "discogs-effnet-bsdynamic-1.onnx"),
                    File(root, "mtg_jamendo_moodtheme-discogs-effnet-1.onnx"))).analyze(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id!!))
                repo.validate(target, request)
                when(result) {
                    is MoodResult.Unavailable -> status?.text = getString(R.string.photo_song_analysis_failed)
                    is MoodResult.Available -> {
                        val description = getString(R.string.photo_song_analysis , song.title ?: song.displayName ?: getString(R.string.photo_song_fallback), result.decodedSeconds, result.patchCount, result.analyzedSeconds, PhotoRanker.musicDescription(result.scores).resolve(requireContext()))
                        performRank(result.scores, description, request)
                    }
                }
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { status?.text = getString(R.string.photo_recommend_failed) }
              catch (_: LinkageError) { status?.text = getString(R.string.photo_device_unsupported) }
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
            catch (e: Exception) { status?.text = getString(R.string.photo_access_failed) }
            catch (_: LinkageError) { status?.text = getString(R.string.photo_device_unsupported) }
            finally { if (job === currentCoroutineContext()[Job]) cancelButton?.visibility = View.GONE }
        }
    }
    private suspend fun performRank(signal: List<MoodScore>, description: String, request: PhotoRequest) {
        val context = requireContext().applicationContext
        val repo = PhotoRepositories.get(context)
        val sources = withContext(Dispatchers.IO) {
            check(targetExists(context, target)) { getString(R.string.photo_target_missing) }; repo.validate(target, request); repo.snapshot(target)
        }
        val candidates = linkedMapOf<String, ScopedPhoto>(); var partial = false
        for (source in sources.second) {
            ensureActiveRequest(request)
            val result = PhotoCandidateProvider(context).enumerate(source, sources.first.excluded.toSet())
            partial = partial || result.state != PhotoReadState.AVAILABLE
            result.photos.forEach { if (candidates.size < 500) candidates[it.id] = it else partial = true }
        }
        if (candidates.isEmpty()) { status?.text = getString(R.string.photo_no_available); return }
        var failures = 0; val features = mutableListOf<PhotoFeatures>()
        withPhotoExtractor({ OnnxPhotoFeatureExtractor(context, PhotoModelStore(context).file(PhotoModel.PHOTO)) }) { extractor ->
            for ((index, photo) in candidates.values.withIndex()) {
                ensureActiveRequest(request)
                withContext(Dispatchers.Main) { status?.text = getString(R.string.photo_analyzing_count , index + 1, candidates.size) }
                try { features += extractor.extract(photo) } catch(e: CancellationException) { throw e } catch (_: Exception) { failures++ }
            }
        }
        ensureActiveRequest(request)
        val ranked = PhotoRanker.rank(signal, features, features.size.coerceAtLeast(1))
        parentFragmentManager.setFragmentResult("photo-search-suggestions:${target.storageKey}", Bundle().apply {
            putString("query", PhotoRanker.suggestions(signal).joinToString(" ") { it.resolve(requireContext()) })
        })
        val uncertain = features.count { it.atmospheres.isEmpty() }
        analysisDetails = getString(R.string.photo_analysis_summary , description, if (ranked.isEmpty()) getString(R.string.photo_no_match_detail) else getString(R.string.photo_candidates_detail, ranked.size), uncertain, failures, if (partial) getString(R.string.photo_partial_scan) else "")
        analysisDetails += getString(R.string.photo_top_reasons) + ranked.take(5).mapIndexed { index, candidate ->
            getString(R.string.photo_reason_score , index + 1, candidate.reasons.joinToString("; ") { it.resolve(requireContext()) }, candidate.score)
        }.joinToString("\n\n")
        status?.text = "${description.substringBefore('\n')}\n" +
            (if (ranked.isEmpty()) getString(R.string.photo_no_match) else getString(R.string.photo_candidates_count , ranked.size)) +
            (if (failures > 0 || partial) getString(R.string.photo_partial_details) else "")
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
                val image = ImageView(requireContext()).apply { layoutParams = LinearLayout.LayoutParams(-1, 240); setImageBitmap(bitmap); contentDescription = getString(R.string.photo_recommended_image) }
                rows?.addView(image)
                rows?.addView(TextView(requireContext()).apply { text = PhotoRanker.caption(candidate).resolve(requireContext()) })
                image.setOnClickListener { CoverCropDialogFragment.forCandidate(target, candidate.features.photo.uri, request,
                    candidate.features.fingerprint).show(parentFragmentManager, "recommended-crop") }
            }
            success = true
            } finally { pages.finish(success) }
        }
        showNext()
        if (pages.shown < ranked.size) rows?.addView(Button(requireContext()).apply { text = getString(R.string.photo_see_more); setOnClickListener {
            if (job?.isActive == true) return@setOnClickListener
            isEnabled = false
            job = lifecycleScope.launch {
                try { showNext(); isEnabled = pages.shown < ranked.size }
                catch (e: CancellationException) { throw e }
                catch(e: Exception) { status?.text = getString(R.string.photo_changed_retry); rows?.removeAllViews() }
            }
        } })
    }
    private suspend fun ensureActiveRequest(request: PhotoRequest) {
        currentCoroutineContext().ensureActive()
        withContext(Dispatchers.IO) { check(targetExists(requireContext(), target)) { getString(R.string.photo_target_missing) }; PhotoRepositories.get(requireContext()).validate(target, request) }
    }
    override fun onStop() { job?.cancel(); modelCheck?.cancel(); rows?.removeAllViews(); super.onStop() }
    override fun onDestroyView() { job?.cancel(); modelCheck?.cancel(); rows = null; status = null; modelStatus = null; cancelButton = null; super.onDestroyView() }
    companion object { fun newInstance(target: CoverTarget) = PhotoRecommendationsDialogFragment().apply { arguments = target.toPhotoBundle() } }
}

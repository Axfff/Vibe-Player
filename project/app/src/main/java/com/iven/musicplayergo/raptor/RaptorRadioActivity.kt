package com.iven.musicplayergo.raptor

import android.animation.ValueAnimator
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.iven.musicplayergo.R
import com.iven.musicplayergo.analysis.RaptorTrackAnalyzer
import com.iven.musicplayergo.databinding.RaptorRadioActivityBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RaptorRadioActivity : AppCompatActivity() {
    private data class Selection(val uri: Uri, val title: String?, val buttonId: Int = View.NO_ID)
    private data class PreparedTrack(
        val selection: Selection,
        val title: String,
        val bpm: Int?,
        val timeline: RaptorTimeline,
        val playback: RaptorPlayback
    )

    private lateinit var binding: RaptorRadioActivityBinding
    private val analyzer by lazy { RaptorTrackAnalyzer(applicationContext) }
    private val preferences by lazy { getSharedPreferences("raptor_radio", MODE_PRIVATE) }
    private val run = RaptorRun()
    private var track: PreparedTrack? = null
    private var pendingSelection: Selection? = null
    private var loadJob: Job? = null
    private var loadRevision = 0
    private var muted = false
    private var best = 0
    private var lastFrameNanos = 0L
    private var displayedScore = -1
    private var displayedTheme: String? = null

    private val documentPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selectTrack(Selection(it, null)) }
    }

    private val frame = object : Runnable {
        override fun run() {
            if (run.phase != RaptorPhase.RUNNING) return
            if (track?.playback?.yieldToLibrary() == true) return
            val now = System.nanoTime()
            if (lastFrameNanos != 0L) run.step(((now - lastFrameNanos) / 1_000_000_000.0).toFloat())
            lastFrameNanos = now
            if (run.phase == RaptorPhase.OVER) {
                track?.playback?.pause()
                saveBest()
                updateControls()
            }
            renderFrame()
            if (run.phase == RaptorPhase.RUNNING) binding.raptorGameStage.postOnAnimation(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = RaptorRadioActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        best = preferences.getInt(KEY_BEST, 0)
        muted = savedInstanceState?.getBoolean(KEY_MUTED) ?: false
        renderBest()
        updateMute()
        renderFrame()

        binding.raptorGameBack.setOnClickListener { finish() }
        binding.raptorGameStage.setOnClickListener { jumpOrStart() }
        binding.raptorGamePrimary.setOnClickListener { startRun() }
        binding.raptorGameJump.setOnClickListener { run.jump() }
        binding.raptorGamePause.setOnClickListener { pauseRun() }
        binding.raptorGameMute.setOnClickListener {
            muted = !muted
            track?.playback?.muted = muted
            updateMute()
        }
        binding.raptorGameOpenAudio.setOnClickListener {
            pauseRun()
            documentPicker.launch(arrayOf("audio/*"))
        }
        val afterglow = bundled(R.raw.raptor_afterglow, R.string.raptor_game_afterglow, R.id.raptor_game_afterglow)
        val signalHop = bundled(R.raw.raptor_signal_hop, R.string.raptor_game_signal_hop, R.id.raptor_game_signal_hop)
        val meteor = bundled(R.raw.raptor_meteor_fm, R.string.raptor_game_meteor_fm, R.id.raptor_game_meteor_fm)
        binding.raptorGameAfterglow.setOnClickListener { selectTrack(afterglow) }
        binding.raptorGameSignalHop.setOnClickListener { selectTrack(signalHop) }
        binding.raptorGameMeteorFm.setOnClickListener { selectTrack(meteor) }

        val restored = savedInstanceState?.getString(KEY_URI)?.let { uri ->
            Selection(Uri.parse(uri), savedInstanceState.getString(KEY_TITLE), savedInstanceState.getInt(KEY_BUTTON, View.NO_ID))
        }
        val librarySong = intent.data?.let { uri -> Selection(uri, intent.getStringExtra(KEY_TITLE)) }
        selectTrack(restored ?: librarySong ?: afterglow)
    }

    override fun onPause() {
        pauseRun()
        saveBest()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        (pendingSelection ?: track?.selection)?.let { selection ->
            outState.putString(KEY_URI, selection.uri.toString())
            outState.putString(KEY_TITLE, selection.title)
            outState.putInt(KEY_BUTTON, selection.buttonId)
        }
        outState.putBoolean(KEY_MUTED, muted)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        loadJob?.cancel()
        binding.raptorGameStage.removeCallbacks(frame)
        track?.playback?.close()
        track = null
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount == 0 && keyCode in JUMP_KEYS) {
            jumpOrStart()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun bundled(resource: Int, title: Int, button: Int) =
        Selection(Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://$packageName/$resource"), getString(title), button)

    private fun selectTrack(selection: Selection) {
        if (pendingSelection == selection || (pendingSelection == null && track?.selection == selection)) return
        pauseRun()
        val revision = ++loadRevision
        loadJob?.cancel()
        if (track?.selection == selection) {
            pendingSelection = null
            updateTrackLabel()
            updateControls()
            showStatus(null)
            return
        }

        pendingSelection = selection
        updateTrackLabel()
        updateControls()
        showStatus(R.string.raptor_game_loading)
        loadJob = lifecycleScope.launch {
            lateinit var playback: RaptorPlayback
            playback = RaptorPlayback(this@RaptorRadioActivity,
                onInterruption = { if (track?.playback === playback) pauseRun() },
                onError = { if (track?.playback === playback) handlePlaybackError() })
            try {
                val title = selection.title ?: displayName(selection.uri)
                val analysis = analyzer.analyze(selection.uri)
                playback.prepare(selection.uri, analysis.durationSeconds)
                ensureActive()
                playback.muted = muted
                track?.playback?.close()
                track = PreparedTrack(selection, title, analysis.bpm, RaptorTimeline(analysis), playback)
                saveBest()
                run.reset()
                showStatus(null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (loadRevision == revision) showStatus(R.string.raptor_game_load_error)
            } finally {
                if (track?.playback !== playback) playback.close()
                if (loadRevision == revision) {
                    pendingSelection = null
                    updateTrackLabel()
                    updateControls()
                    renderFrame()
                }
            }
        }
    }

    private suspend fun displayName(uri: Uri): String {
        val name = withContext(Dispatchers.IO) {
            runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            }.getOrNull()
        }
        return name?.takeIf(String::isNotBlank) ?: getString(R.string.raptor_game_local_audio)
    }

    private fun startRun() {
        val current = track ?: return
        if (pendingSelection != null || run.phase == RaptorPhase.RUNNING ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if (run.phase != RaptorPhase.PAUSED) current.playback.rewind()
        if (track !== current) return
        if (!current.playback.play()) {
            showStatus(R.string.raptor_game_audio_unavailable)
            return
        }
        if (track !== current) return
        if (run.phase != RaptorPhase.PAUSED) run.reset()
        run.phase = RaptorPhase.RUNNING
        lastFrameNanos = 0L
        showStatus(null)
        updateControls()
        renderFrame()
        binding.raptorGameStage.postOnAnimation(frame)
    }

    private fun jumpOrStart() {
        if (run.phase == RaptorPhase.RUNNING) run.jump() else startRun()
    }

    private fun pauseRun() {
        if (run.phase != RaptorPhase.RUNNING) return
        run.phase = RaptorPhase.PAUSED
        binding.raptorGameStage.removeCallbacks(frame)
        track?.playback?.pause()
        updateControls()
        renderFrame()
    }

    private fun handlePlaybackError() {
        pauseRun()
        track?.playback?.close()
        track = null
        run.reset()
        showStatus(R.string.raptor_game_load_error)
        updateTrackLabel()
        updateControls()
        renderFrame()
    }

    private fun updateTrackLabel() {
        val current = track
        binding.raptorGameTrack.text = current?.title
            ?: pendingSelection?.title ?: getString(R.string.raptor_game_local_audio)
        binding.raptorGameTempo.text = when {
            current == null -> ""
            current.bpm == null -> getString(R.string.raptor_game_no_beat)
            else -> getString(R.string.raptor_game_tempo, current.bpm)
        }
        val button = (pendingSelection ?: current?.selection)?.buttonId ?: View.NO_ID
        if (button == View.NO_ID) binding.raptorGameSoundtrackChoices.clearCheck()
        else binding.raptorGameSoundtrackChoices.check(button)
    }

    private fun updateControls() {
        val running = run.phase == RaptorPhase.RUNNING
        binding.raptorGameOverlay.isVisible = !running
        binding.raptorGamePause.isEnabled = running
        binding.raptorGameJump.isEnabled = running
        binding.raptorGamePrimary.isEnabled = !running && pendingSelection == null && track != null
        val (message, action) = when (run.phase) {
            RaptorPhase.PAUSED -> R.string.raptor_game_paused to R.string.raptor_game_resume
            RaptorPhase.OVER -> R.string.raptor_game_over to R.string.raptor_game_retry
            RaptorPhase.READY, RaptorPhase.RUNNING -> R.string.raptor_game_ready to R.string.raptor_game_start
        }
        binding.raptorGameMessage.setText(message)
        binding.raptorGamePrimary.setText(action)
    }

    private fun renderFrame() {
        val seconds = if (run.phase == RaptorPhase.READY) 0.0 else track?.playback?.positionSeconds ?: 0.0
        val signal = track?.timeline?.signalAt(seconds) ?: RaptorSignal()
        val motionEnabled = Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()
        binding.raptorGameStage.present(run,
            if (run.phase == RaptorPhase.RUNNING && motionEnabled) signal else signal.copy(pulse = 0f))
        if (displayedScore != run.score) {
            displayedScore = run.score
            binding.raptorGameScore.text = getString(R.string.raptor_game_score, run.score)
        }
        if (displayedTheme != signal.theme) {
            displayedTheme = signal.theme
            val name = when (signal.theme) {
                "drift" -> R.string.raptor_analysis_drift
                "groove" -> R.string.raptor_analysis_groove
                "surge" -> R.string.raptor_analysis_surge
                else -> R.string.raptor_analysis_neutral
            }
            binding.raptorGameScene.text = getString(R.string.raptor_game_scene, getString(name))
        }
    }

    private fun showStatus(message: Int?) {
        binding.raptorGameStatus.isVisible = message != null
        binding.raptorGameStatus.text = message?.let(::getString).orEmpty()
    }

    private fun saveBest() {
        if (run.score <= best) return
        best = run.score
        preferences.edit().putInt(KEY_BEST, best).apply()
        renderBest()
    }

    private fun renderBest() {
        binding.raptorGameBest.text = getString(R.string.raptor_game_best, best)
    }

    private fun updateMute() {
        binding.raptorGameMute.setText(if (muted) R.string.raptor_game_unmute else R.string.raptor_game_mute)
    }

    companion object {
        private const val KEY_URI = "track_uri"
        private const val KEY_TITLE = "track_title"
        private const val KEY_BUTTON = "track_button"
        private const val KEY_BEST = "best_score"
        private const val KEY_MUTED = "muted"
        private val JUMP_KEYS = setOf(KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_BUTTON_A)

        fun intent(context: Context, uri: Uri? = null, title: String? = null): Intent =
            Intent(context, RaptorRadioActivity::class.java).apply {
                data = uri
                putExtra(KEY_TITLE, title)
            }
    }
}

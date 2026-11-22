package com.iven.musicplayergo.raptor

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.iven.musicplayergo.player.MediaPlayerHolder
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/** The runner's foreground audio clock. Control and read it on the main thread. */
internal class RaptorPlayback(
    context: Context,
    private val onInterruption: () -> Unit,
    private val onError: () -> Unit
) {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var player: MediaPlayer? = null
    private var loadId = 0L
    private var pendingPrepare: CancellableContinuation<Unit>? = null
    private var prepared = false
    private var playbackRequested = false
    private var seekingToStart = false
    private var nativeLoop = false
    private var excerptMs = 0
    private var hasFocus = false

    private val focusRequest = AudioFocusRequestCompat.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(
            AudioAttributesCompat.Builder()
                .setUsage(AudioAttributesCompat.USAGE_GAME)
                .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener({ change ->
            if (change < 0 && hasFocus) {
                pause()
                onInterruption()
            }
        }, handler)
        .build()

    private val loopCheck = Runnable {
        val current = readyPlayer ?: return@Runnable
        if (playbackRequested && !seekingToStart) checkLoopEnd(current)
    }

    private val readyPlayer: MediaPlayer?
        get() = if (prepared) player else null

    val positionSeconds: Double
        get() {
            val current = readyPlayer ?: return 0.0
            if (seekingToStart) return 0.0
            return try {
                current.currentPosition.coerceIn(0, excerptMs - 1) / 1000.0
            } catch (error: IllegalStateException) {
                failed(current, error)
                0.0
            }
        }

    var muted = false
        set(value) {
            field = value
            readyPlayer?.let(::applyVolume)
        }

    suspend fun prepare(uri: Uri, analyzedSeconds: Double) {
        require(uri.scheme in setOf("content", "file", "android.resource")) { "Choose local audio" }
        require(analyzedSeconds.isFinite() && analyzedSeconds > 0.0) { "Audio excerpt must have a duration" }
        withContext(Dispatchers.Main.immediate) {
            close()
            val request = loadId
            // A document provider may block while opening its file descriptor.
            withContext(Dispatchers.IO) {
                val source = app.contentResolver.openAssetFileDescriptor(uri, "r")
                    ?: throw IOException("Cannot open game audio")
                source.use {
                    withContext(Dispatchers.Main.immediate) {
                        if (request != loadId) throw CancellationException("Audio source was replaced")
                        preparePlayer(source, (analyzedSeconds * 1000).roundToInt().coerceAtLeast(1))
                    }
                }
            }
        }
    }

    private suspend fun preparePlayer(source: AssetFileDescriptor, requestedMs: Int) =
        suspendCancellableCoroutine<Unit> { continuation ->
            val candidate = MediaPlayer()
            player = candidate
            pendingPrepare = continuation
            continuation.invokeOnCancellation {
                handler.post { if (player === candidate) close() }
            }
            try {
                candidate.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                if (source.declaredLength >= 0) {
                    candidate.setDataSource(source.fileDescriptor, source.startOffset, source.declaredLength)
                } else {
                    candidate.setDataSource(source.fileDescriptor)
                }
                candidate.setOnPreparedListener { onPrepared(it, requestedMs) }
                candidate.setOnSeekCompleteListener { onRewound(it) }
                candidate.setOnCompletionListener {
                    if (player === it && playbackRequested) seekToStart(it)
                }
                candidate.setOnErrorListener { sourcePlayer, what, extra ->
                    failed(sourcePlayer, IOException("Game audio playback failed ($what, $extra)"))
                    true
                }
                candidate.prepareAsync()
            } catch (error: Exception) {
                failed(candidate, error)
            }
        }

    private fun onPrepared(current: MediaPlayer, requestedMs: Int) {
        if (player !== current) return
        try {
            val duration = current.duration
            if (duration <= 0) throw IOException("Game audio has no playable duration")
            excerptMs = minOf(duration, requestedMs)
            nativeLoop = duration <= requestedMs
            current.isLooping = nativeLoop
            applyVolume(current)
            prepared = true
            val continuation = pendingPrepare
            pendingPrepare = null
            continuation?.resume(Unit)
        } catch (error: Exception) {
            failed(current, error)
        }
    }

    fun play(): Boolean {
        val current = readyPlayer ?: return false
        if (playbackRequested) return true
        if (!hasFocus) {
            val granted = AudioManagerCompat.requestAudioFocus(audioManager, focusRequest)
            if (granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
            hasFocus = true
        }
        // The library player's audio focus behavior is optional in its settings.
        val library = MediaPlayerHolder.getInstance()
        if (library.isPlaying) library.pauseMediaPlayer()
        playbackRequested = true
        return seekingToStart || startPlayer(current)
    }

    /** Yield if library playback restarts without requesting audio focus. */
    fun yieldToLibrary(): Boolean {
        if (!playbackRequested || !MediaPlayerHolder.getInstance().isPlaying) return false
        pause()
        onInterruption()
        return true
    }

    private fun startPlayer(current: MediaPlayer): Boolean = try {
        current.start()
        if (!nativeLoop) checkLoopEnd(current)
        player === current
    } catch (error: IllegalStateException) {
        failed(current, error)
        false
    }

    fun pause() {
        playbackRequested = false
        handler.removeCallbacks(loopCheck)
        val current = readyPlayer
        try {
            if (current?.isPlaying == true) current.pause()
        } catch (error: IllegalStateException) {
            current?.let { failed(it, error) }
        } finally {
            abandonFocus()
        }
    }

    fun rewind() {
        readyPlayer?.let(::seekToStart)
    }

    @Suppress("DEPRECATION") // The int overload works on all supported Android versions.
    private fun seekToStart(current: MediaPlayer) {
        if (seekingToStart) return
        handler.removeCallbacks(loopCheck)
        try {
            seekingToStart = true
            if (current.isPlaying) current.pause()
            current.seekTo(0)
        } catch (error: IllegalStateException) {
            failed(current, error)
        }
    }

    private fun onRewound(current: MediaPlayer) {
        if (player !== current || !seekingToStart) return
        seekingToStart = false
        if (playbackRequested) startPlayer(current)
    }

    private fun checkLoopEnd(current: MediaPlayer) {
        try {
            val remainingMs = excerptMs - current.currentPosition
            if (remainingMs <= 0) {
                seekToStart(current)
            } else {
                handler.postDelayed(loopCheck, remainingMs.coerceAtMost(100).toLong())
            }
        } catch (error: IllegalStateException) {
            failed(current, error)
        }
    }

    private fun applyVolume(current: MediaPlayer) {
        val volume = if (muted) 0f else 0.78f
        current.setVolume(volume, volume)
    }

    private fun failed(current: MediaPlayer, error: Exception) {
        if (player !== current) return
        val continuation = pendingPrepare
        pendingPrepare = null
        close()
        if (continuation != null) continuation.resumeWithException(error) else onError()
    }

    private fun abandonFocus() {
        if (hasFocus) {
            hasFocus = false
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, focusRequest)
        }
    }

    /** Releases the current source, including an unfinished preparation. */
    fun close() {
        loadId++
        handler.removeCallbacks(loopCheck)
        playbackRequested = false
        seekingToStart = false
        prepared = false
        val current = player
        player = null
        val continuation = pendingPrepare
        pendingPrepare = null
        current?.release()
        abandonFocus()
        continuation?.cancel()
    }
}

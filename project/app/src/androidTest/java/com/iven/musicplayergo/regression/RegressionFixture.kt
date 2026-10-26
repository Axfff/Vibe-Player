package com.iven.musicplayergo.regression

import android.Manifest
import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.iven.musicplayergo.GoConstants
import com.iven.musicplayergo.GoPreferences
import com.iven.musicplayergo.MusicViewModel
import com.iven.musicplayergo.models.Music
import com.iven.musicplayergo.player.MediaPlayerHolder
import com.iven.musicplayergo.player.PlayerService
import com.iven.musicplayergo.playlists.Playlist
import com.iven.musicplayergo.playlists.PlaylistRepositories
import com.iven.musicplayergo.playlists.PlaylistRepository
import com.iven.musicplayergo.playlists.PlaylistResult
import com.iven.musicplayergo.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.sin

/**
 * Real Android fixtures for the documented F0/F1 regression scenarios.
 *
 * Run only on a disposable emulator with the debug application: preferences are reset per
 * test. Audio is inserted through MediaStore, decoded by the production MediaPlayer and
 * removed by URI afterwards. No production lifecycle or playback callback is substituted.
 */
class RegressionFixture private constructor() : AutoCloseable {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context: Context = instrumentation.targetContext
    val preferences: GoPreferences get() = GoPreferences.getPrefsInstance()
    val repository: PlaylistRepository get() = PlaylistRepositories.get(context)
    lateinit var scenario: ActivityScenario<MainActivity>
        private set

    private val session = "VibeRegression_${System.nanoTime()}"
    private val audioUris = linkedMapOf<String, Uri>()
    private val fixtureTracks = linkedMapOf<String, Music>()
    val tracks: Map<String, Music> get() = fixtureTracks.toMap()
    val songs: List<Music> get() = fixtureTracks.values.toList()
    val holder: MediaPlayerHolder get() = MediaPlayerHolder.getInstance()

    /** Reflection is limited to observing the actual Android player and bound service. */
    val player: MediaPlayer
        get() = MediaPlayerHolder::class.java.getDeclaredField("mediaPlayer").run {
            isAccessible = true
            get(holder) as MediaPlayer
        }

    val service: PlayerService
        get() {
            lateinit var value: PlayerService
            onActivity { activity ->
                value = MainActivity::class.java.getDeclaredField("mPlayerService").run {
                    isAccessible = true
                    get(activity) as PlayerService
                }
            }
            return value
        }

    val viewModel: MusicViewModel
        get() {
            lateinit var value: MusicViewModel
            onActivity { value = ViewModelProvider(it)[MusicViewModel::class.java] }
            return value
        }

    private fun initialize() {
        require(context.packageName.endsWith(".debug")) { "Use the isolated debug app" }
        require(Build.VERSION.SDK_INT >= 29) { "Fixtures require MediaStore scoped storage" }
        grantMediaPermission()
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName, Manifest.permission.POST_NOTIFICATIONS
            )
        }
        check(PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit())
        preferences.isAnimations = false
        preferences.lockRotation = false
        preferences.continueOnEnd = true
        preferences.onListEnded = "1" // Stop when the saved snapshot ends.
        listOf("A", "B", "C").forEach { insertAudio(it) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        await("initial successful MediaStore scan", 20_000) {
            onMain {
                val state = viewModel.playlistLibraryState.value
                state?.hasLoaded == true && !state.isLoading && !state.refreshFailed &&
                    state.songs.count { it.displayName?.startsWith(session) == true } == 3
            }
        }
        fixtureTracks.putAll(onMain {
            listOf("A", "B", "C").associateWith { label ->
                viewModel.playlistLibrary.single { it.displayName == fileName(label) }
            }
        })
        fixtureTracks.forEach { (label, song) ->
            assertTrue("$label has a real decoded duration", song.duration >= 29_000)
        }
        record("fixture-ready")
    }

    fun grantMediaPermission() {
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    fun onActivity(block: (MainActivity) -> Unit) {
        scenario.onActivity(block)
    }

    fun <T> onMain(block: () -> T): T {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return block()
        val result = AtomicReference<Result<T>>()
        instrumentation.runOnMainSync { result.set(runCatching(block)) }
        return result.get().getOrThrow()
    }

    /** Condition runs on the instrumentation thread; wrap UI/player reads in [onMain]. */
    fun await(label: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        do {
            if (condition()) return
            SystemClock.sleep(50)
        } while (SystemClock.elapsedRealtime() < end)
        record("timeout-$label")
        throw AssertionError("Timed out waiting for $label")
    }

    fun createPlaylist(name: String, vararg labels: String): Playlist {
        var playlist = success(repository.create(name))
        labels.forEach { playlist = success(repository.addTrack(playlist.id, tracks.getValue(it))) }
        return playlist
    }

    fun playPlaylist(id: String) {
        val playlist = success(repository.getPlaylist(id))
        val available = repository.resolve(playlist, onMain { viewModel.playlistLibrary })
            .mapNotNull { it.music }
        onActivity { it.onPlaylistSelected(available) }
    }

    fun play(vararg labels: String) {
        onActivity { activity -> activity.onPlaylistSelected(labels.map { tracks.getValue(it) }) }
    }

    fun expectPlaying(label: String) {
        val expected = tracks.getValue(label).id
        await("$label really playing") {
            onMain { holder.currentSong?.id == expected && holder.isPlaying && player.isPlaying }
        }
        val before = onMain { player.currentPosition }
        await("$label playback position advancing") {
            onMain {
                holder.currentSong?.id == expected && player.isPlaying &&
                    player.currentPosition >= before + 100
            }
        }
    }

    /** Delete owned bytes and row, deliberately retaining the app's last successful scan. */
    fun deleteAudio(label: String) {
        val uri = audioUris.remove(label) ?: return
        assertEquals("delete fixture $label", 1, context.contentResolver.delete(uri, null, null))
    }

    /** A replacement gets a new MediaStore ID; it must not silently repair a saved old ID. */
    fun restoreAudio(label: String): Music {
        check(!audioUris.containsKey(label))
        insertAudio(label)
        val model = MusicViewModel(context.applicationContext as Application)
        val restored = model.queryForMusic(context.applicationContext as Application)
            .orEmpty().single { it.id == ContentUris.parseId(audioUris.getValue(label)) }
        fixtureTracks[label] = restored
        return restored
    }

    fun refreshLibrary() {
        instrumentation.waitForIdleSync()
        onMain { viewModel.refreshPlaylistLibrary() }
        await("library refresh finished", 20_000) {
            onMain { viewModel.playlistLibraryState.value?.isLoading == false }
        }
    }

    /** Store screenshots in the app's external files for adb-pullable evidence. */
    fun record(label: String) {
        val description = onMain {
            "song=${holder.currentSong?.displayName}, state=${holder.state}, " +
                "queue=${holder.queueSongs.map { it.displayName }}, playlist=${holder.isPlaylistPlayback}"
        }
        Log.i(TAG, "$label: $description")
        val directory = File(context.getExternalFilesDir(null), "regression-evidence").apply { mkdirs() }
        val safeName = label.replace(Regex("[^A-Za-z0-9._-]"), "-")
        instrumentation.uiAutomation.takeScreenshot()?.let { screenshot ->
            File(directory, "$session-$safeName.png").outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()
        }
    }

    override fun close() {
        try {
            if (::scenario.isInitialized) {
                val boundService = runCatching { service }.getOrNull()
                onMain {
                    if (holder.isMediaPlayer && holder.currentSong != null) holder.pauseMediaPlayer()
                }
                scenario.close()
                context.stopService(Intent(context, PlayerService::class.java))
                instrumentation.waitForIdleSync()
                if (boundService != null) {
                    await("service released after fixture", 5_000) {
                        onMain { boundService.getMediaSession() == null }
                    }
                }
            }
        } finally {
            audioUris.values.toList().forEach { context.contentResolver.delete(it, null, null) }
            audioUris.clear()
        }
    }

    private fun fileName(label: String) = "${session}_$label.wav"

    private fun insertAudio(label: String) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName(label))
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.TITLE, "Regression $label")
            put(MediaStore.Audio.Media.ARTIST, "Vibe regression fixtures")
            put(MediaStore.Audio.Media.ALBUM, "$session album $label")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/$session/$label/")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = checkNotNull(context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values))
        audioUris[label] = uri
        context.contentResolver.openOutputStream(uri, "w")!!.use { it.write(waveBytes(label)) }
        values.clear()
        values.put(MediaStore.Audio.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
        // Preserve explicit album/folder distinctions even on scanners without WAV INFO support.
        values.clear()
        values.put(MediaStore.Audio.Media.TITLE, "Regression $label")
        values.put(MediaStore.Audio.Media.ARTIST, "Vibe regression fixtures")
        values.put(MediaStore.Audio.Media.ALBUM, "$session album $label")
        values.put(MediaStore.Audio.Media.IS_MUSIC, 1)
        context.contentResolver.update(uri, values, null, null)
    }

    private fun waveBytes(label: String): ByteArray {
        val rate = 8_000
        val samples = rate * 30
        val bytes = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(bytes.capacity() - 8).put("WAVE".toByteArray())
        bytes.put("fmt ".toByteArray()).putInt(16).putShort(1.toShort()).putShort(1.toShort())
        bytes.putInt(rate).putInt(rate * 2).putShort(2.toShort()).putShort(16.toShort())
        bytes.put("data".toByteArray()).putInt(samples * 2)
        val frequency = when (label) { "A" -> 440.0; "B" -> 554.37; else -> 659.25 }
        repeat(samples) { bytes.putShort((sin(2 * PI * frequency * it / rate) * 1_200).toInt().toShort()) }
        return bytes.array()
    }

    companion object {
        const val TAG = "PlaylistRegression"

        fun open(): RegressionFixture = RegressionFixture().also {
            try {
                it.initialize()
            } catch (failure: Throwable) {
                it.close()
                throw failure
            }
        }

        fun <T> success(result: PlaylistResult<T>): T = when (result) {
            is PlaylistResult.Success -> result.value
            is PlaylistResult.Failure -> throw AssertionError("Playlist operation failed: ${result.error}")
        }
    }
}

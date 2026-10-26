package com.iven.musicplayergo.library

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Explicit, bounded re-indexing of audio rows the app can already see in MediaStore.
 * No storage walk, writes, permission escalation, URI-to-path guessing or IS_MUSIC edits.
 * A callback URI means the system indexed the path, NOT that it is a new playable song.
 */
internal class LocalMediaIndexer(private val context: Context) {
    private data class Candidates(val paths: List<String>, val skipped: Int, val limited: Boolean)

    suspend fun scan(): MediaScanReport = withContext(Dispatchers.IO) {
        val candidates = findCandidates()
        if (candidates.paths.isEmpty()) return@withContext MediaScanReport(
            MediaScanOutcome.NO_CANDIDATES, skipped = candidates.skipped, limited = candidates.limited
        )
        val indexed = AtomicInteger(0)
        val completed = withTimeoutOrNull(SCAN_TIMEOUT_MS) {
            awaitScanner(candidates.paths, indexed)
            true
        } ?: false
        MediaScanReport(
            outcome = when {
                !completed -> MediaScanOutcome.TIMED_OUT
                indexed.get() != candidates.paths.size || candidates.skipped > 0 || candidates.limited -> MediaScanOutcome.PARTIAL
                else -> MediaScanOutcome.COMPLETE
            },
            attempted = candidates.paths.size,
            indexed = indexed.get(),
            skipped = candidates.skipped,
            limited = candidates.limited
        )
    }

    @Suppress("DEPRECATION") // DATA is read only for the system path-based scanning API.
    private suspend fun findCandidates(): Candidates {
        val paths = linkedSetOf<String>()
        var skipped = 0
        var examined = 0
        var limited = false
        val names = MediaScanPolicy.audioExtensions.map { "LOWER(${MediaStore.MediaColumns.DISPLAY_NAME}) LIKE ?" }
        val audio = "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.MediaColumns.MIME_TYPE} LIKE ? OR ${names.joinToString(" OR ")})"
        val conditions = mutableListOf(audio)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) conditions += "${MediaStore.MediaColumns.IS_PENDING} = 0"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) conditions += "${MediaStore.MediaColumns.IS_TRASHED} = 0"
        val arguments = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString(), "audio/%",
            *MediaScanPolicy.audioExtensions.map { "%.$it" }.toTypedArray())
        val projection = arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE)
        val cursor = context.contentResolver.query(
            MediaStore.Files.getContentUri("external"), projection,
            conditions.joinToString(" AND "), arguments,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        ) ?: throw IOException("The accessible media query did not complete")
        cursor.use {
            val pathColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            while (it.moveToNext()) {
                coroutineContext.ensureActive()
                if (examined++ >= MAX_ROWS) { limited = true; break }
                val path = it.getString(pathColumn)
                if (!MediaScanPolicy.isAudioCandidate(path, it.getString(nameColumn), it.getString(mimeColumn))) {
                    skipped++
                    continue
                }
                // Some scoped-storage rows expose no readable filesystem path. Do not bypass that.
                val file = File(path!!)
                val readable = try { file.isFile && file.canRead() && file.length() > 0 } catch (_: SecurityException) { false }
                if (readable) paths.add(path) else skipped++
            }
        }
        return Candidates(paths.toList(), skipped, limited)
    }

    private suspend fun awaitScanner(paths: List<String>, indexed: AtomicInteger) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val remaining = Collections.synchronizedSet(paths.toMutableSet())
            val pending = AtomicInteger(paths.size)
            try {
                MediaScannerConnection.scanFile(context.applicationContext, paths.toTypedArray(), null) { path, uri ->
                    // Ignore late / duplicate callbacks; cancellation never publishes stale UI state.
                    if (continuation.isActive && remaining.remove(path)) {
                        if (uri != null) indexed.incrementAndGet()
                        if (pending.decrementAndGet() == 0) continuation.resume(Unit)
                    }
                }
            } catch (exception: Exception) {
                if (continuation.isActive) continuation.resumeWithException(exception)
            }
        }
    }

    companion object {
        private const val MAX_ROWS = 1000
        private const val SCAN_TIMEOUT_MS = 30_000L
    }
}

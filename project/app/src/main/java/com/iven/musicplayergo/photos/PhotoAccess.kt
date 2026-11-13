package com.iven.musicplayergo.photos

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CancellationSignal
import android.os.SystemClock
import android.provider.DocumentsContract
import android.util.AtomicFile
import kotlinx.coroutines.*
import java.io.File
import java.security.MessageDigest

object PhotoRepositories {
    @Volatile private var instance: PhotoScopeRepository? = null
    fun get(context: Context): PhotoScopeRepository = instance ?: synchronized(this) {
        instance ?: PhotoScopeRepository(object : PhotoScopeStorage {
            // Android excludes noBackupFilesDir from both cloud backup and device transfer.
            private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, "photo-scopes-v1.json"))
            override fun read(): String? = if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists())
                file.openRead().bufferedReader().use { it.readText() } else null
            override fun write(value: String) {
                val stream = file.startWrite()
                try { stream.write(value.toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
                catch (e: Exception) { file.failWrite(stream); throw e }
            }
        }, { OnnxPhotoFeatureExtractor.clearCache() }).also { instance = it }
    }
}

enum class PhotoReadState { AVAILABLE, REAUTHORIZE, MISSING_OR_OFFLINE, UNSUPPORTED, PARTIAL, BUDGET_REACHED }
data class SourceCandidates(val source: PhotoSource, val photos: List<ScopedPhoto>, val state: PhotoReadState,
    val examined: Int, val unavailable: Int)

class PhotoAccessManager(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    fun persist(uri: Uri): Boolean = try {
        require(uri.scheme == "content")
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        hasGrant(uri)
    } catch (_: Exception) { false }
    fun hasGrant(uri: Uri) = resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
    fun check(uri: Uri, signal: CancellationSignal? = null): PhotoReadState = try {
        if (uri.scheme != "content" || resolver.getType(uri) !in MIME_TYPES) PhotoReadState.UNSUPPORTED
        else resolver.openAssetFileDescriptor(uri, "r", signal)?.use { PhotoReadState.AVAILABLE } ?: PhotoReadState.MISSING_OR_OFFLINE
    } catch (_: SecurityException) { PhotoReadState.REAUTHORIZE }
      catch (_: Exception) { PhotoReadState.MISSING_OR_OFFLINE }
    // No releasePersistableUriPermission: other app modules may own the same grant.
    companion object { val MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp") }
}

/** Bounded, cancellable traversal. Never queries MediaStore's entire image collection. */
class PhotoCandidateProvider(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val access = PhotoAccessManager(context)
    suspend fun enumerate(source: PhotoSource, excluded: Set<String> = emptySet(), limit: Int = 500,
        budgetMs: Long = 10_000): SourceCandidates = withContext(Dispatchers.IO) {
        val found = linkedMapOf<String, ScopedPhoto>(); var examined = 0; var unavailable = 0
        var state = PhotoReadState.AVAILABLE
        val start = SystemClock.elapsedRealtime()
        val coroutine = currentCoroutineContext()
        val signal = CancellationSignal()
        val cancellation = launch(Dispatchers.Default) {
            try { delay(budgetMs); signal.cancel() } finally { signal.cancel() }
        }
        fun withinBudget(): Boolean {
            coroutine.ensureActive()
            val valid = examined < limit && SystemClock.elapsedRealtime() - start < budgetMs
            if (!valid) state = PhotoReadState.BUDGET_REACHED
            return valid
        }
        fun add(uri: Uri) {
            val id = photoId(uri.toString())
            if (id in excluded || id in found) return
            when (access.check(uri, signal)) {
                PhotoReadState.AVAILABLE -> found[id] = ScopedPhoto(id, uri.toString())
                PhotoReadState.REAUTHORIZE -> { unavailable++; state = PhotoReadState.REAUTHORIZE }
                else -> { unavailable++; if (state == PhotoReadState.AVAILABLE) state = PhotoReadState.PARTIAL }
            }
        }
        try {
            if (source.kind == PhotoSourceKind.DIRECTORY && !access.hasGrant(Uri.parse(source.uris.single()))) return@withContext SourceCandidates(source,
                emptyList(), PhotoReadState.REAUTHORIZE, 0, 0)
            if (source.kind == PhotoSourceKind.SELECTED) {
                for (value in source.uris.distinct()) {
                    if (!withinBudget()) break
                    examined++
                    if (!access.hasGrant(Uri.parse(value))) { unavailable++; state = PhotoReadState.REAUTHORIZE }
                    else add(Uri.parse(value))
                }
            } else {
                val tree = Uri.parse(source.uris.single())
                val queue = java.util.ArrayDeque<Pair<String, Int>>()
                queue.add(DocumentsContract.getTreeDocumentId(tree) to 0)
                val visited = mutableSetOf<String>()
                while (queue.isNotEmpty() && withinBudget()) {
                    val (documentId, depth) = queue.removeFirst()
                    if (!visited.add(documentId)) continue
                    val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
                    resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null, signal)?.use { cursor ->
                        while (withinBudget() && cursor.moveToNext()) {
                            examined++
                            val id = cursor.getString(0); val mime = cursor.getString(1)
                            if (mime == DocumentsContract.Document.MIME_TYPE_DIR && source.recursive) {
                                if (depth < 3) queue.add(id to depth + 1) else state = PhotoReadState.BUDGET_REACHED
                            } else if (mime in PhotoAccessManager.MIME_TYPES) add(DocumentsContract.buildDocumentUriUsingTree(tree, id))
                        }
                    } ?: run { state = PhotoReadState.MISSING_OR_OFFLINE }
                }
            }
        } catch (e: CancellationException) { throw e }
          catch (_: SecurityException) { state = PhotoReadState.REAUTHORIZE }
          catch (_: Exception) { state = if (signal.isCanceled) PhotoReadState.BUDGET_REACHED else PhotoReadState.MISSING_OR_OFFLINE }
        finally { cancellation.cancel() }
        SourceCandidates(source, found.values.toList(), state, examined, unavailable)
    }
}

fun photoId(uri: String): String = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray())
    .joinToString("") { "%02x".format(it.toInt() and 255) }

package com.iven.musicplayergo.photos

import com.iven.musicplayergo.covers.CoverTarget
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.util.UUID

enum class PhotoSourceKind { SELECTED, DIRECTORY }
@JsonClass(generateAdapter = true)
data class ScopedPhoto(val id: String, val uri: String)
@JsonClass(generateAdapter = true)
data class PhotoSource(val id: String, val name: String, val kind: PhotoSourceKind,
    val uris: List<String>, val recursive: Boolean = false)
@JsonClass(generateAdapter = true)
data class PhotoScope(val key: String, val identity: String, val revision: Long = 0,
    val sources: List<String> = emptyList(), val excluded: List<String> = emptyList())
@JsonClass(generateAdapter = true)
data class PhotoScopeCollection(val schemaVersion: Int = 1,
    val sources: List<PhotoSource> = emptyList(), val scopes: List<PhotoScope> = emptyList())
data class PhotoRequest(val key: String, val identity: String, val revision: Long, val requestId: String = UUID.randomUUID().toString())
interface PhotoScopeStorage { fun read(): String?; fun write(value: String) }
class PhotoScopeException(message: String) : IllegalStateException(message)

/** No global candidate query exists. Each operation starts with one stable target. */
class PhotoScopeRepository(private val storage: PhotoScopeStorage, private val invalidateFeatures: () -> Unit = {}) {
    private val adapter = Moshi.Builder().build().adapter(PhotoScopeCollection::class.java)
    private fun read(): PhotoScopeCollection = try {
        val state = storage.read()?.let { adapter.fromJson(it) ?: error("invalid") } ?: PhotoScopeCollection()
        require(state.schemaVersion == 1 && state.scopes.map { it.key }.distinct().size == state.scopes.size)
        require(state.sources.map { it.id }.distinct().size == state.sources.size)
        require(state.scopes.all { scope -> scope.revision >= 0 && scope.sources.all { id -> state.sources.any { it.id == id } } })
        state
    } catch (_: Exception) { throw PhotoScopeException("来源记录无法读取；保留原记录，请重试。") }
    private fun identity(target: CoverTarget): String = when (target) {
        is CoverTarget.Playlist -> target.storageKey
        is CoverTarget.Album -> adapterIdentity(target.artistSnapshot, target.albumTitleSnapshot)
    }
    private fun adapterIdentity(artist: String?, title: String?) = "${artist?.length ?: -1}:$artist:${title?.length ?: -1}:$title"
    private fun scope(state: PhotoScopeCollection, target: CoverTarget): PhotoScope {
        require(target.isValid())
        val existing = state.scopes.firstOrNull { it.key == target.storageKey }
        if (existing != null && existing.identity != identity(target)) throw PhotoScopeException("音乐专辑身份已变化，请重新绑定来源。")
        return existing ?: PhotoScope(target.storageKey, identity(target))
    }
    @Synchronized fun snapshot(target: CoverTarget): Pair<PhotoScope, List<PhotoSource>> {
        val state = read(); val scope = scope(state, target)
        return scope to state.sources.filter { it.id in scope.sources }
    }
    @Synchronized fun request(target: CoverTarget): PhotoRequest = snapshot(target).first.let {
        PhotoRequest(it.key, it.identity, it.revision)
    }
    @Synchronized fun validate(target: CoverTarget, request: PhotoRequest) {
        val current = snapshot(target).first
        if (request.key != current.key || request.identity != current.identity || request.revision != current.revision)
            throw PhotoScopeException("照片范围已变化；请重新操作。")
    }
    private fun write(state: PhotoScopeCollection, updated: PhotoScope) {
        val scopes = state.scopes.filterNot { it.key == updated.key } + updated
        val referenced = scopes.flatMap { it.sources }.toSet()
        try { storage.write(adapter.toJson(state.copy(scopes = scopes, sources = state.sources.filter { it.id in referenced }))); invalidateFeatures() }
        catch (_: Exception) { throw PhotoScopeException("保存来源失败；原范围保持不变。") }
    }
    @Synchronized fun attach(target: CoverTarget, request: PhotoRequest, source: PhotoSource) {
        validate(target, request)
        require(source.uris.isNotEmpty() && source.uris.size <= 500 && source.uris.all { it.startsWith("content://") })
        require(source.kind != PhotoSourceKind.DIRECTORY || source.uris.size == 1)
        val state = read(); val scope = scope(state, target)
        require(source.id in scope.sources || scope.sources.size < 20) { "每个目标最多 20 个来源" }
        val existing = state.sources.firstOrNull { it.id == source.id }
        require(existing == null || existing == source)
        write(state.copy(sources = state.sources.filterNot { it.id == source.id } + source.copy(uris = source.uris.distinct())),
            scope.copy(revision = scope.revision + 1, sources = (scope.sources + source.id).distinct()))
    }
    @Synchronized fun remove(target: CoverTarget, sourceId: String) {
        val state = read(); val scope = scope(state, target)
        write(state, scope.copy(revision = scope.revision + 1, sources = scope.sources - sourceId))
    }
    @Synchronized fun exclude(target: CoverTarget, photoId: String) {
        val state = read(); val scope = scope(state, target)
        write(state, scope.copy(revision = scope.revision + 1, excluded = (scope.excluded + photoId).distinct()))
    }
    @Synchronized fun refresh(target: CoverTarget, restoreExcluded: Boolean = false) {
        val state = read(); val scope = scope(state, target)
        write(state, scope.copy(revision = scope.revision + 1, excluded = if (restoreExcluded) emptyList() else scope.excluded))
    }
    @Synchronized fun deleteTarget(target: CoverTarget) {
        val state = read(); val scope = scope(state, target)
        // Keep the revision tombstone: a delayed callback must never revive an old scope.
        write(state, scope.copy(revision = scope.revision + 1, sources = emptyList(), excluded = emptyList()))
    }
    @Synchronized fun shareableSources(): List<PhotoSource> = read().sources
    @Synchronized fun <T> guarded(target: CoverTarget, request: PhotoRequest, action: () -> T): T {
        validate(target, request)
        return action()
    }
}

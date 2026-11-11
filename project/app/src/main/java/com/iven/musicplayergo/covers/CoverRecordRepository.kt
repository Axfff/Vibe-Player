package com.iven.musicplayergo.covers

import com.squareup.moshi.Moshi

interface CoverRecordStorage {
    fun read(): String?
    fun write(json: String): Boolean
}

/** Pure Kotlin metadata repository. Image files are committed separately by the Android importer. */
class CoverRecordRepository(
    private val storage: CoverRecordStorage,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val adapter = Moshi.Builder().build().adapter(CustomCoverCollection::class.java)

    @Synchronized
    fun getCover(target: CoverTarget): CoverResult<CustomCover?> {
        if (!target.isValid()) return failure(CoverError.INVALID_TARGET)
        return withCollection { collection ->
            CoverResult.Success(collection.covers.find { it.key() == target.storageKey })
        }
    }

    @Synchronized
    fun hasCover(target: CoverTarget): Boolean =
        (getCover(target) as? CoverResult.Success)?.value != null

    @Synchronized
    fun putCover(
        target: CoverTarget,
        localFileName: String,
        source: CoverSource
    ): CoverResult<CustomCover> {
        if (!target.isValid()) return failure(CoverError.INVALID_TARGET)
        if (!isSafeFileName(localFileName)) return failure(CoverError.STORAGE_WRITE_FAILED)
        return withCollection { collection ->
            val previous = collection.covers.find { it.key() == target.storageKey }
            val now = clock()
            if (now < 0) return@withCollection failure(CoverError.STORAGE_WRITE_FAILED)
            val cover = target.toRecord(
                localFileName = localFileName,
                source = source,
                createdAt = previous?.createdAtEpochMs ?: now,
                updatedAt = maxOf(now, previous?.updatedAtEpochMs ?: now)
            )
            val updated = collection.copy(
                covers = collection.covers.filterNot { it.key() == target.storageKey } + cover
            )
            persist(updated, cover)
        }
    }

    @Synchronized
    fun removeCover(target: CoverTarget): CoverResult<CustomCover> {
        if (!target.isValid()) return failure(CoverError.INVALID_TARGET)
        return withCollection { collection ->
            val existing = collection.covers.find { it.key() == target.storageKey }
                ?: return@withCollection failure(CoverError.COVER_NOT_FOUND)
            persist(collection.copy(covers = collection.covers - existing), existing)
        }
    }

    private fun <T> withCollection(action: (CustomCoverCollection) -> CoverResult<T>): CoverResult<T> {
        val collection = try {
            val json = storage.read()
            val decoded = if (json == null) CustomCoverCollection() else adapter.fromJson(json)
            if (decoded == null || !isValid(decoded)) return failure(CoverError.STORAGE_READ_FAILED)
            decoded
        } catch (_: Exception) {
            return failure(CoverError.STORAGE_READ_FAILED)
        }
        return action(collection)
    }

    private fun <T> persist(collection: CustomCoverCollection, value: T): CoverResult<T> = try {
        if (storage.write(adapter.toJson(collection))) CoverResult.Success(value)
        else failure(CoverError.STORAGE_WRITE_FAILED)
    } catch (_: Exception) {
        failure(CoverError.STORAGE_WRITE_FAILED)
    }

    private fun isValid(collection: CustomCoverCollection): Boolean {
        if (collection.schemaVersion != 1) return false
        if (collection.covers.map { it.key() }.distinct().size != collection.covers.size) return false
        return collection.covers.all { record ->
            record.schemaVersion == 1 && record.targetId.isNotBlank() &&
                isSafeFileName(record.localFileName) && record.createdAtEpochMs >= 0 &&
                record.updatedAtEpochMs >= record.createdAtEpochMs && when (record.targetType) {
                    CoverTargetType.ALBUM -> record.targetId.toLongOrNull()?.let { it > 0 } == true
                    CoverTargetType.PLAYLIST -> CoverTarget.Playlist(record.targetId).isValid()
                }
        }
    }

    private fun isSafeFileName(value: String): Boolean =
        value.isNotBlank() && value == java.io.File(value).name &&
            !value.contains('/') && !value.contains('\\') && value.length <= 128

    private fun CustomCover.key() = when (targetType) {
        CoverTargetType.ALBUM -> "album:$targetId"
        CoverTargetType.PLAYLIST -> "playlist:$targetId"
    }

    private fun CoverTarget.toRecord(
        localFileName: String,
        source: CoverSource,
        createdAt: Long,
        updatedAt: Long
    ) = when (this) {
        is CoverTarget.Album -> CustomCover(
            targetType = CoverTargetType.ALBUM,
            targetId = albumId.toString(),
            localFileName = localFileName,
            source = source,
            createdAtEpochMs = createdAt,
            updatedAtEpochMs = updatedAt,
            artistSnapshot = artistSnapshot,
            albumTitleSnapshot = albumTitleSnapshot
        )
        is CoverTarget.Playlist -> CustomCover(
            targetType = CoverTargetType.PLAYLIST,
            targetId = playlistId,
            localFileName = localFileName,
            source = source,
            createdAtEpochMs = createdAt,
            updatedAtEpochMs = updatedAt
        )
    }

    private fun <T> failure(error: CoverError): CoverResult<T> = CoverResult.Failure(error)
}


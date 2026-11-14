package com.iven.musicplayergo.covers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class CoverRecordRepositoryTest {
    private lateinit var storage: MemoryStorage
    private lateinit var repository: CoverRecordRepository
    private var now = 100L

    @Before fun setUp() {
        storage = MemoryStorage()
        repository = CoverRecordRepository(storage) { now }
    }

    @Test fun targetKeysValidateAndKeepTypesIsolated() {
        val id = UUID.randomUUID().toString()
        assertEquals("album:7", CoverTarget.Album(7, "Artist", "Album").storageKey)
        assertEquals("playlist:$id", CoverTarget.Playlist(id).storageKey)
        assertTrue(CoverTarget.Album(7, null, null).isValid())
        assertTrue(CoverTarget.Playlist(id).isValid())
        assertFalse(CoverTarget.Album(0, null, null).isValid())
        assertFalse(CoverTarget.Playlist("7").isValid())
    }

    @Test fun freshStorageHasNoCoverAndDoesNotWrite() {
        val target = CoverTarget.Album(7, null, null)
        assertNull(success(repository.getCover(target)))
        assertFalse(repository.hasCover(target))
        assertEquals(0, storage.writes)
    }

    @Test fun recordsAlbumSnapshotsAndSurvivesRestart() {
        val target = CoverTarget.Album(7, "Artist", "Album")
        val saved = success(repository.putCover(target, "7.webp", CoverSource.USER_SELECTED))
        assertEquals(CoverTargetType.ALBUM, saved.targetType)
        assertEquals("Artist", saved.artistSnapshot)
        assertEquals("Album", saved.albumTitleSnapshot)
        assertEquals(saved, success(CoverRecordRepository(storage).getCover(target)))
    }

    @Test fun replacementPreservesCreationAndUpdatesSourceAndFile() {
        val target = CoverTarget.Album(7, "Artist", "Album")
        success(repository.putCover(target, "old.webp", CoverSource.USER_SELECTED))
        now = 200
        val changed = success(repository.putCover(target, "new.webp", CoverSource.RECOMMENDED))
        assertEquals(100, changed.createdAtEpochMs)
        assertEquals(200, changed.updatedAtEpochMs)
        assertEquals("new.webp", changed.localFileName)
        assertEquals(CoverSource.RECOMMENDED, changed.source)
    }

    @Test fun albumAndPlaylistRecordsNeverCollide() {
        val playlistId = UUID.randomUUID().toString()
        val album = CoverTarget.Album(7, null, null)
        val playlist = CoverTarget.Playlist(playlistId)
        success(repository.putCover(album, "album.webp", CoverSource.USER_SELECTED))
        success(repository.putCover(playlist, "playlist.webp", CoverSource.USER_SELECTED))
        assertEquals("album.webp", success(repository.getCover(album))!!.localFileName)
        assertEquals("playlist.webp", success(repository.getCover(playlist))!!.localFileName)
    }

    @Test fun clockRollbackDuringReplacementKeepsEveryRecordReadableAfterRestart() {
        val a = CoverTarget.Album(7, null, null)
        val b = CoverTarget.Album(8, null, null)
        now = 200
        success(repository.putCover(a, "a.webp", CoverSource.USER_SELECTED))
        success(repository.putCover(b, "b.webp", CoverSource.USER_SELECTED))
        now = 100
        val changed = success(repository.putCover(a, "replacement.webp", CoverSource.USER_SELECTED))
        val reopened = CoverRecordRepository(storage)
        assertEquals("replacement.webp", success(reopened.getCover(a))!!.localFileName)
        assertEquals("b.webp", success(reopened.getCover(b))!!.localFileName)
        assertEquals(200L, changed.createdAtEpochMs)
        assertEquals(200L, changed.updatedAtEpochMs)
    }

    @Test fun removalIsExplicitAndPersists() {
        val target = CoverTarget.Album(7, null, null)
        success(repository.putCover(target, "7.webp", CoverSource.USER_SELECTED))
        assertEquals("7.webp", success(repository.removeCover(target)).localFileName)
        assertNull(success(CoverRecordRepository(storage).getCover(target)))
        error(CoverError.COVER_NOT_FOUND, repository.removeCover(target))
    }

    @Test fun invalidTargetsNamesAndClockNeverWrite() {
        val before = storage.json
        error(CoverError.INVALID_TARGET, repository.putCover(CoverTarget.Album(0, null, null), "x.webp", CoverSource.USER_SELECTED))
        error(CoverError.STORAGE_WRITE_FAILED, repository.putCover(CoverTarget.Album(1, null, null), "../x.webp", CoverSource.USER_SELECTED))
        now = -1
        error(CoverError.STORAGE_WRITE_FAILED, repository.putCover(CoverTarget.Album(1, null, null), "x.webp", CoverSource.USER_SELECTED))
        assertEquals(before, storage.json)
        assertEquals(0, storage.writes)
    }

    @Test fun failedWritePreservesPreviousDocument() {
        val target = CoverTarget.Album(7, null, null)
        success(repository.putCover(target, "old.webp", CoverSource.USER_SELECTED))
        val before = storage.json
        storage.failWrites = true
        error(CoverError.STORAGE_WRITE_FAILED, repository.putCover(target, "new.webp", CoverSource.USER_SELECTED))
        assertEquals(before, storage.json)
        storage.failWrites = false
        assertEquals("old.webp", success(repository.getCover(target))!!.localFileName)
    }

    @Test fun corruptUnsupportedOrUnsafeDocumentsAreReadFailures() {
        val invalid = listOf(
            "", "null", "{broken", "{\"schemaVersion\":2,\"covers\":[]}",
            "{\"schemaVersion\":1,\"covers\":[{\"schemaVersion\":1,\"targetType\":\"ALBUM\",\"targetId\":\"0\",\"localFileName\":\"x.webp\",\"source\":\"USER_SELECTED\",\"createdAtEpochMs\":1,\"updatedAtEpochMs\":1}]}"
        )
        invalid.forEach {
            storage.json = it
            error(CoverError.STORAGE_READ_FAILED, repository.getCover(CoverTarget.Album(1, null, null)))
        }
    }

    @Test fun storageExceptionsAreTypedFailures() {
        storage.throwRead = true
        error(CoverError.STORAGE_READ_FAILED, repository.getCover(CoverTarget.Album(1, null, null)))
        storage.throwRead = false
        storage.throwWrite = true
        error(CoverError.STORAGE_WRITE_FAILED, repository.putCover(CoverTarget.Album(1, null, null), "x.webp", CoverSource.USER_SELECTED))
    }

    private fun <T> success(result: CoverResult<T>): T = when (result) {
        is CoverResult.Success -> result.value
        is CoverResult.Failure -> throw AssertionError("Expected success, got ${result.error}")
    }

    private fun error(expected: CoverError, result: CoverResult<*>) = assertEquals(CoverResult.Failure(expected), result)

    private class MemoryStorage : CoverRecordStorage {
        var json: String? = null
        var writes = 0
        var failWrites = false
        var throwRead = false
        var throwWrite = false
        override fun read(): String? {
            if (throwRead) error("read")
            return json
        }
        override fun write(json: String): Boolean {
            if (throwWrite) error("write")
            if (failWrites) return false
            this.json = json
            writes++
            return true
        }
    }
}

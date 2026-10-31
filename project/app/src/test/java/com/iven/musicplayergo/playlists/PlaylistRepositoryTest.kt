package com.iven.musicplayergo.playlists

import com.iven.musicplayergo.models.Music
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Domain and JSON persistence tests: no Context, mocked Android methods or device required. */
class PlaylistRepositoryTest {
    private lateinit var storage: MemoryStorage
    private lateinit var repository: PlaylistRepository

    @Before
    fun setUp() {
        storage = MemoryStorage()
        repository = PlaylistRepository(storage)
    }

    @Test
    fun freshStorageHasNoPlaylists() {
        assertTrue(success(repository.getPlaylists()).isEmpty())
        assertNull(storage.json)
        assertEquals(0, storage.writes)
    }

    @Test
    fun invalidThrowingOrRepeatedIdFactoryCannotOverwriteACollection() {
        val original = success(repository.create("Original"))
        val saved = storage.json
        for (factory in listOf<() -> String>({ "not-a-uuid" }, { original.id }, { throw IllegalStateException("ID source unavailable") })) {
            val candidate = PlaylistRepository(storage, factory)
            error(PlaylistError.STORAGE_WRITE_FAILED, candidate.create("New"))
            assertEquals(saved, storage.json)
            assertEquals(listOf(original), success(candidate.getPlaylists()))
        }
    }

    @Test
    fun createsIndependentPlaylistsWithTrimmedNamesAndStableUuids() {
        val study = success(repository.create("  Study  "))
        val running = success(repository.create("Running"))

        assertEquals("Study", study.name)
        assertNotEquals(study.id, running.id)
        assertEquals(study.id, UUID.fromString(study.id).toString())
        assertEquals(listOf(study, running), success(repository.getPlaylists()))
        assertEquals(study, success(PlaylistRepository(storage).getPlaylist(study.id)))
    }

    @Test
    fun blankAndCaseInsensitiveDuplicateNamesDoNotWriteAnything() {
        success(repository.create("Study"))
        val before = storage.json
        val writes = storage.writes

        error(PlaylistError.EMPTY_NAME, repository.create(" \n\t "))
        error(PlaylistError.DUPLICATE_NAME, repository.create(" study "))

        assertEquals(before, storage.json)
        assertEquals(writes, storage.writes)
    }

    @Test
    fun duplicateNamesDoNotDependOnTheDevicesDefaultLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            success(repository.create("MIX"))
            error(PlaylistError.DUPLICATE_NAME, repository.create("mix"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun renameExcludesItselfAndKeepsIdentityMembershipAndOrder() {
        val playlist = success(repository.create("Study"))
        success(repository.addTrack(playlist.id, track(3)))
        success(repository.addTrack(playlist.id, track(1)))

        val sameName = success(repository.rename(playlist.id, " Study "))
        val changedCase = success(repository.rename(playlist.id, "STUDY"))
        val renamed = success(repository.rename(playlist.id, "  Focus  "))

        assertEquals(playlist.id, sameName.id)
        assertEquals(playlist.id, changedCase.id)
        assertEquals(playlist.id, renamed.id)
        assertEquals("Focus", renamed.name)
        assertEquals(listOf(3L, 1L), renamed.tracks.map { it.mediaId })
        assertEquals(renamed, success(PlaylistRepository(storage).getPlaylist(playlist.id)))
    }

    @Test
    fun invalidRenameKeepsTheSavedNameAndTracks() {
        val study = success(repository.create("Study"))
        success(repository.create("Running"))
        success(repository.addTrack(study.id, track(1)))
        val before = storage.json

        error(PlaylistError.EMPTY_NAME, repository.rename(study.id, "   "))
        error(PlaylistError.DUPLICATE_NAME, repository.rename(study.id, " running "))

        assertEquals(before, storage.json)
        assertEquals("Study", success(repository.getPlaylist(study.id)).name)
        assertEquals(listOf(1L), success(repository.getPlaylist(study.id)).tracks.map { it.mediaId })
    }

    @Test
    fun additionOrderSurvivesRestartAndDuplicatesKeepTheirPosition() {
        val playlist = success(repository.create("Order"))
        listOf(3L, 1L, 2L).forEach { success(repository.addTrack(playlist.id, track(it))) }
        val beforeDuplicate = storage.json

        error(PlaylistError.DUPLICATE_TRACK, repository.addTrack(playlist.id, track(1)))
        // Changing display metadata or a bookmark position does not create another membership.
        error(
            PlaylistError.DUPLICATE_TRACK,
            repository.addTrack(playlist.id, track(1).copy(title = "Updated title", startFrom = 42_000))
        )

        assertEquals(beforeDuplicate, storage.json)
        assertEquals(
            listOf(3L, 1L, 2L),
            success(PlaylistRepository(storage).getPlaylist(playlist.id)).tracks.map { it.mediaId }
        )
    }

    @Test
    fun differentMediaIdsWithTheSameTitleAreDistinctTracks() {
        val playlist = success(repository.create("Same title"))
        success(repository.addTrack(playlist.id, track(1).copy(title = "Same", artist = "Artist A")))
        val updated = success(repository.addTrack(playlist.id, track(2).copy(title = "Same", artist = "Artist B")))

        assertEquals(listOf(1L, 2L), updated.tracks.map { it.mediaId })
    }

    @Test
    fun invalidMediaReferencesAreRejectedWithoutChangingThePlaylist() {
        val playlist = success(repository.create("Study"))
        val before = storage.json

        error(PlaylistError.INVALID_TRACK, repository.addTrack(playlist.id, track(1).copy(id = null)))
        error(PlaylistError.INVALID_TRACK, repository.addTrack(playlist.id, track(0)))
        error(PlaylistError.INVALID_TRACK, repository.addTrack(playlist.id, track(-1)))
        error(PlaylistError.INVALID_TRACK, repository.addTrack(playlist.id, track(1).copy(duration = -1)))

        assertEquals(before, storage.json)
    }

    @Test
    fun removalAffectsOnlyOneMembershipAndPersistsAfterRestart() {
        val study = success(repository.create("Study"))
        val running = success(repository.create("Running"))
        listOf(1L, 2L).forEach { success(repository.addTrack(study.id, track(it))) }
        listOf(2L, 3L).forEach { success(repository.addTrack(running.id, track(it))) }

        success(repository.removeTrack(study.id, 2))
        val reopened = PlaylistRepository(storage)

        assertEquals(listOf(1L), success(reopened.getPlaylist(study.id)).tracks.map { it.mediaId })
        assertEquals(listOf(2L, 3L), success(reopened.getPlaylist(running.id)).tracks.map { it.mediaId })
    }

    @Test
    fun removingThenAddingAgainAppendsTheTrackToTheEnd() {
        val playlist = success(repository.create("Order"))
        listOf(1L, 2L, 3L).forEach { success(repository.addTrack(playlist.id, track(it))) }
        success(repository.removeTrack(playlist.id, 2))
        val updated = success(repository.addTrack(playlist.id, track(2)))

        assertEquals(listOf(1L, 3L, 2L), updated.tracks.map { it.mediaId })
    }

    @Test
    fun deletionPreservesOtherPlaylistsAndDoesNotReappearAfterRestart() {
        val study = success(repository.create("Study"))
        val running = success(repository.create("Running"))
        success(repository.addTrack(study.id, track(2)))
        val savedRunning = success(repository.addTrack(running.id, track(2)))

        success(repository.delete(study.id))
        val reopened = PlaylistRepository(storage)

        error(PlaylistError.PLAYLIST_NOT_FOUND, reopened.getPlaylist(study.id))
        assertEquals(listOf(savedRunning), success(reopened.getPlaylists()))
        assertEquals("Study", success(reopened.create("Study")).name)
    }

    @Test
    fun unknownPlaylistAndTrackOperationsAreExplicitAndLeaveStorageUntouched() {
        val playlist = success(repository.create("Study"))
        val before = storage.json

        error(PlaylistError.PLAYLIST_NOT_FOUND, repository.getPlaylist("missing"))
        error(PlaylistError.PLAYLIST_NOT_FOUND, repository.rename("missing", "Name"))
        error(PlaylistError.PLAYLIST_NOT_FOUND, repository.addTrack("missing", track(1)))
        error(PlaylistError.PLAYLIST_NOT_FOUND, repository.removeTrack("missing", 1))
        error(PlaylistError.PLAYLIST_NOT_FOUND, repository.delete("missing"))
        error(PlaylistError.TRACK_NOT_FOUND, repository.removeTrack(playlist.id, 1))

        assertEquals(before, storage.json)
    }

    @Test
    fun emptyPlaylistHasNoPlayableTracks() {
        val playlist = success(repository.create("Empty"))

        assertTrue(playlist.tracks.isEmpty())
        assertTrue(repository.resolve(playlist, listOf(track(1))).isEmpty())
        assertEquals(playlist, success(PlaylistRepository(storage).getPlaylist(playlist.id)))
    }

    @Test
    fun missingTrackRetainsItsSnapshotAndAvailableTracksKeepSavedOrder() {
        val playlist = success(repository.create("Order"))
        listOf(3L, 1L, 2L).forEach { success(repository.addTrack(playlist.id, track(it))) }
        val saved = success(repository.getPlaylist(playlist.id))
        val before = storage.json

        val resolved = repository.resolve(saved, listOf(track(2), track(3)))

        assertEquals(listOf(3L, 1L, 2L), resolved.map { it.saved.mediaId })
        assertEquals(listOf(3L, 2L), resolved.mapNotNull { it.music?.id })
        assertFalse(resolved[1].isAvailable)
        assertEquals("Song 1", resolved[1].saved.title)
        assertEquals(before, storage.json)
        assertEquals(saved, success(PlaylistRepository(storage).getPlaylist(playlist.id)))
    }

    @Test
    fun allUnavailableEntriesStayVisibleAndBecomeAvailableAgainWhenRestored() {
        val playlist = success(repository.create("Missing"))
        val saved = success(repository.addTrack(playlist.id, track(1)))

        val missing = repository.resolve(saved, emptyList())
        assertEquals(1, missing.size)
        assertTrue(missing.none { it.isAvailable })
        assertEquals(saved.tracks, missing.map { it.saved })

        val restored = repository.resolve(saved, listOf(track(1)))
        assertTrue(restored.single().isAvailable)
        assertEquals(saved, success(PlaylistRepository(storage).getPlaylist(playlist.id)))
    }

    @Test
    fun sameTitleUnderAnotherIdNeverRebindsAnUnavailableEntry() {
        val playlist = success(repository.create("Identity"))
        val original = track(1)
        val saved = success(repository.addTrack(playlist.id, original))

        val resolved = repository.resolve(saved, listOf(original.copy(id = 999)))

        assertFalse(resolved.single().isAvailable)
        assertEquals(1L, resolved.single().saved.mediaId)
    }

    @Test
    fun obviousIdReuseDoesNotResolveToADifferentFile() {
        val playlist = success(repository.create("Identity"))
        val original = track(1)
        val saved = success(repository.addTrack(playlist.id, original))

        assertFalse(repository.resolve(saved, listOf(original.copy(displayName = "other.mp3"))).single().isAvailable)
        assertFalse(repository.resolve(saved, listOf(original.copy(duration = original.duration + 1))).single().isAvailable)
        // Harmless descriptive-tag changes do not change the file reference.
        val updatedTags = original.copy(title = "New title", artist = "New artist")
        assertEquals(updatedTags, repository.resolve(saved, listOf(updatedTags)).single().music)
    }

    @Test
    fun nullDisplayMetadataCanBePersistedAndResolvedWithoutTitleGuessing() {
        val playlist = success(repository.create("Unknown metadata"))
        val song = track(1).copy(title = null, artist = null, album = null, displayName = null)
        val saved = success(repository.addTrack(playlist.id, song))

        assertEquals(saved, success(PlaylistRepository(storage).getPlaylist(playlist.id)))
        assertEquals(song, repository.resolve(saved, listOf(song)).single().music)
        assertFalse(repository.resolve(saved, listOf(song.copy(id = 2))).single().isAvailable)
    }

    @Test
    fun failedMutationsNeverReportSuccessOrReplaceTheSavedDocument() {
        val playlist = success(repository.create("Study"))
        success(repository.addTrack(playlist.id, track(1)))
        val before = storage.json
        storage.failWrites = true

        error(PlaylistError.STORAGE_WRITE_FAILED, repository.create("Running"))
        error(PlaylistError.STORAGE_WRITE_FAILED, repository.rename(playlist.id, "Focus"))
        error(PlaylistError.STORAGE_WRITE_FAILED, repository.addTrack(playlist.id, track(2)))
        error(PlaylistError.STORAGE_WRITE_FAILED, repository.removeTrack(playlist.id, 1))
        error(PlaylistError.STORAGE_WRITE_FAILED, repository.delete(playlist.id))

        assertEquals(before, storage.json)
        val saved = success(PlaylistRepository(storage).getPlaylist(playlist.id))
        assertEquals("Study", saved.name)
        assertEquals(listOf(1L), saved.tracks.map { it.mediaId })
    }

    @Test
    fun storageExceptionsAreReturnedAsFailures() {
        storage.throwOnRead = true
        error(PlaylistError.STORAGE_READ_FAILED, repository.getPlaylists())
        error(PlaylistError.STORAGE_READ_FAILED, repository.create("Study"))
        storage.throwOnRead = false
        storage.throwOnWrite = true
        error(PlaylistError.STORAGE_WRITE_FAILED, repository.create("Study"))
        assertNull(storage.json)
    }

    @Test
    fun invalidJsonAndUnsupportedVersionAreNeverSilentlyOverwritten() {
        listOf(
            "", "null", "{broken", "{\"schemaVersion\":2,\"playlists\":[]}",
            "{\"schemaVersion\":1,\"playlists\":[null]}"
        ).forEach { invalid ->
            storage.json = invalid
            error(PlaylistError.STORAGE_READ_FAILED, repository.getPlaylists())
            error(PlaylistError.STORAGE_READ_FAILED, repository.create("Study"))
            assertEquals(invalid, storage.json)
        }
        assertEquals(0, storage.writes)
    }

    @Test
    fun invalidStoredNamesIdentitiesAndDuplicateMembershipsAreRejected() {
        val playlist = success(repository.create("Study"))
        success(repository.addTrack(playlist.id, track(1)))
        val valid = storage.json!!
        val saved = success(repository.getPlaylist(playlist.id))
        val adapter = Moshi.Builder().build().adapter(PlaylistCollection::class.java)
        val invalidDocuments = listOf(
            valid.replace("\"name\":\"Study\"", "\"name\":\"   \""),
            valid.replace(playlist.id, "not-a-uuid"),
            valid.replace("\"mediaId\":1", "\"mediaId\":0"),
            adapter.toJson(PlaylistCollection(playlists = listOf(saved, saved))),
            adapter.toJson(PlaylistCollection(playlists = listOf(
                saved, saved.copy(id = UUID.randomUUID().toString(), name = "study")
            ))),
            adapter.toJson(PlaylistCollection(playlists = listOf(
                saved.copy(tracks = saved.tracks + saved.tracks)
            )))
        )
        invalidDocuments.forEach { invalid ->
            assertNotEquals(valid, invalid)
            storage.json = invalid
            error(PlaylistError.STORAGE_READ_FAILED, repository.getPlaylists())
            error(PlaylistError.STORAGE_READ_FAILED, repository.create("Running"))
            assertEquals(invalid, storage.json)
        }
    }

    @Test
    fun concurrentAddsOnTheSharedRepositoryDoNotLoseUpdates() {
        val playlist = success(repository.create("Concurrent"))
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(4)
        try {
            val results = (1L..12L).map { id ->
                executor.submit<PlaylistResult<Playlist>> {
                    assertTrue(start.await(5, TimeUnit.SECONDS))
                    repository.addTrack(playlist.id, track(id))
                }
            }
            start.countDown()
            results.forEach { success(it.get(5, TimeUnit.SECONDS)) }
            val reopened = success(PlaylistRepository(storage).getPlaylist(playlist.id))
            assertEquals((1L..12L).toSet(), reopened.tracks.map { it.mediaId }.toSet())
            assertEquals(12, reopened.tracks.size)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun track(id: Long) = Music(
        artist = "Artist $id",
        year = 2024,
        track = id.toInt(),
        title = "Song $id",
        displayName = "song-$id.mp3",
        duration = 210_000,
        album = "Album",
        albumId = 7,
        relativePath = "Music",
        id = id,
        launchedBy = "artists",
        startFrom = 0,
        dateAdded = 0
    )

    private fun <T> success(result: PlaylistResult<T>): T = when (result) {
        is PlaylistResult.Success -> result.value
        is PlaylistResult.Failure -> throw AssertionError("Expected success, got ${result.error}")
    }

    private fun error(expected: PlaylistError, result: PlaylistResult<*>) {
        assertEquals(PlaylistResult.Failure(expected), result)
    }

    private class MemoryStorage : PlaylistStorage {
        var json: String? = null
        var failWrites = false
        var throwOnRead = false
        var throwOnWrite = false
        var writes = 0

        override fun read(): String? {
            if (throwOnRead) throw IllegalStateException("Read failed")
            return json
        }

        override fun write(json: String): Boolean {
            if (throwOnWrite) throw IllegalStateException("Write failed")
            if (failWrites) return false
            this.json = json
            writes++
            return true
        }
    }
}

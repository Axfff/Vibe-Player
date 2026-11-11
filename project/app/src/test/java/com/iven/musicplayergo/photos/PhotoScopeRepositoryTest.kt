package com.iven.musicplayergo.photos

import com.iven.musicplayergo.covers.CoverTarget
import org.junit.Assert.*
import org.junit.Test

class PhotoScopeRepositoryTest {
    private class Memory : PhotoScopeStorage {
        var value: String? = null; var fail = false
        override fun read() = value
        override fun write(value: String) { if (fail) error("disk"); this.value = value }
    }
    private val a = CoverTarget.Playlist("00000000-0000-0000-0000-000000000001")
    private val b = CoverTarget.Playlist("00000000-0000-0000-0000-000000000002")
    private val source = PhotoSource("source", "Selected", PhotoSourceKind.SELECTED, listOf("content://test/1"))
    @Test fun targetsAreIsolatedAndNewTargetsAreEmpty() {
        val repo = PhotoScopeRepository(Memory()); repo.attach(a, repo.request(a), source)
        assertEquals(listOf(source), repo.snapshot(a).second); assertTrue(repo.snapshot(b).second.isEmpty())
    }
    @Test fun removingSharedBindingDoesNotRemoveOtherUser() {
        val repo = PhotoScopeRepository(Memory()); repo.attach(a, repo.request(a), source); repo.attach(b, repo.request(b), source)
        repo.remove(a, source.id); assertEquals(listOf(source), repo.snapshot(b).second)
        repo.remove(b, source.id); assertTrue(repo.shareableSources().isEmpty())
    }
    @Test fun failedWriteKeepsPreviousState() {
        val memory = Memory(); val repo = PhotoScopeRepository(memory); repo.attach(a, repo.request(a), source)
        memory.fail = true; assertThrows(PhotoScopeException::class.java) { repo.remove(a, source.id) }
        assertEquals(listOf(source), repo.snapshot(a).second)
    }
    @Test fun restartPreservesBindingAndRevision() {
        val memory = Memory(); val repo = PhotoScopeRepository(memory); repo.attach(a, repo.request(a), source)
        assertEquals(repo.snapshot(a), PhotoScopeRepository(memory).snapshot(a))
    }
    @Test fun revokedRequestCannotReattachOrSave() {
        val repo = PhotoScopeRepository(Memory()); val old = repo.request(a); repo.deleteTarget(a)
        assertThrows(PhotoScopeException::class.java) { repo.attach(a, old, source) }
        assertThrows(PhotoScopeException::class.java) { repo.guarded(a, old) { fail("must not save") } }
    }
    @Test fun pickerCallbackCannotSwitchTarget() {
        val repo = PhotoScopeRepository(Memory()); assertThrows(PhotoScopeException::class.java) { repo.attach(b, repo.request(a), source) }
    }
    @Test fun refreshAndExcludeInvalidateOldResults() {
        val repo = PhotoScopeRepository(Memory()); val request = repo.request(a); repo.exclude(a, "p")
        assertThrows(PhotoScopeException::class.java) { repo.validate(a, request) }
        repo.refresh(a); assertEquals(listOf("p"), repo.snapshot(a).first.excluded)
        repo.refresh(a, true); assertTrue(repo.snapshot(a).first.excluded.isEmpty())
    }
    @Test fun corruptOrFutureSchemaNeverBecomesAnEmptyStore() {
        for (bad in listOf("broken", "null", "{\"schemaVersion\":99}")) {
            val memory = Memory().apply { value = bad }; val repo = PhotoScopeRepository(memory)
            assertThrows(PhotoScopeException::class.java) { repo.snapshot(a) }; assertEquals(bad, memory.value)
        }
    }
    @Test fun albumsRequireSnapshotsAndDoNotCollideWithPlaylists() {
        val repo = PhotoScopeRepository(Memory()); val album = CoverTarget.Album(1, "artist", "title")
        repo.attach(album, repo.request(album), source); assertTrue(repo.snapshot(a).second.isEmpty())
        assertThrows(PhotoScopeException::class.java) { repo.snapshot(album.copy(artistSnapshot = "another")) }
    }
    @Test fun explicitAlbumRebindStartsEmptyAndNeverRevivesRequestsEvenWhenIdentityReturns() {
        val memory = Memory(); val repo = PhotoScopeRepository(memory)
        val album = CoverTarget.Album(1,"artist","title"); val changed = album.copy(artistSnapshot="changed")
        repo.attach(album,repo.request(album),source); repo.attach(a,repo.request(a),source)
        val old = repo.request(album)
        val previous = assertThrows(PhotoScopeIdentityChanged::class.java) { repo.request(changed) }.previous
        memory.fail = true
        assertThrows(PhotoScopeException::class.java) { repo.rebindAlbum(changed,previous) }
        assertEquals(listOf(source),repo.snapshot(album).second)
        memory.fail = false; repo.rebindAlbum(changed,previous)
        assertTrue(repo.snapshot(changed).second.isEmpty()); assertEquals(listOf(source),repo.snapshot(a).second)
        assertThrows(PhotoScopeException::class.java) { repo.validate(changed,old) }
        assertThrows(IllegalStateException::class.java) { repo.rebindAlbum(changed,previous) }
        repo.rebindAlbum(album,repo.snapshot(changed).first)
        assertThrows(PhotoScopeException::class.java) { repo.validate(album,old) }
        repo.attach(album,repo.request(album),source)
        assertEquals(listOf(source),repo.snapshot(album).second)
    }
    @Test fun deletionReportsFailedScopeReadAndWriteAndStillAttemptsCoverCleanup() {
        val memory = Memory(); val repo = PhotoScopeRepository(memory); repo.attach(a,repo.request(a),source)
        val valid = memory.value
        for (readFailure in listOf(true,false)) {
            memory.value = if(readFailure) "broken" else valid; memory.fail = !readFailure
            var coverCleaned = false
            assertEquals(listOf("照片来源"),cleanupDeletedPlaylistPhotos({repo.deleteTarget(a)},{coverCleaned=true}))
            assertTrue(coverCleaned); assertEquals(if(readFailure) "broken" else valid,memory.value)
        }
        assertEquals(listOf("封面"),cleanupDeletedPlaylistPhotos({}, {error("cover disk failure")}))
        assertEquals(emptyList<String>(),cleanupDeletedPlaylistPhotos({},{}))
    }
    @Test fun selectionDuplicatesAreFixedAndDeduplicated() {
        val repo = PhotoScopeRepository(Memory()); repo.attach(a, repo.request(a), source.copy(uris = source.uris + source.uris))
        assertEquals(1, repo.snapshot(a).second.single().uris.size)
    }
    @Test fun cancellationWithNoMutationRetainsRevision() {
        val repo = PhotoScopeRepository(Memory()); val before = repo.request(a); repo.request(a)
        repo.validate(a, before); assertTrue(repo.snapshot(a).second.isEmpty())
    }
    @Test fun invalidUriAndDirectoryShapeAreRejected() {
        val repo = PhotoScopeRepository(Memory())
        assertThrows(IllegalArgumentException::class.java) { repo.attach(a, repo.request(a), source.copy(uris = listOf("file:///private"))) }
        assertThrows(IllegalArgumentException::class.java) { repo.attach(a, repo.request(a), source.copy(kind = PhotoSourceKind.DIRECTORY, uris = source.uris + "content://test/2")) }
    }
}

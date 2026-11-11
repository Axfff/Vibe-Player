package com.iven.musicplayergo.covers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID

class CoverResolverTest {
    @Test fun readableCustomFileWinsForAlbumsAndPlaylists() {
        val root = Files.createTempDirectory("covers").toFile()
        try {
            val file = File(root, "custom.webp").apply { writeText("fixture") }
            val repository = FakeRepository(file, record(CoverTargetType.ALBUM, "7"))
            val result = RepositoryCoverResolver(repository).resolve(CoverTarget.Album(7, null, null))
            assertEquals(CoverDisplaySource.CustomFile(file), result)
        } finally { root.deleteRecursively() }
    }

    @Test fun missingOrFailedCustomRecordsUseTargetSpecificFallback() {
        val missing = File("definitely-missing-cover.webp")
        val album = CoverTarget.Album(7, null, null)
        val playlist = CoverTarget.Playlist(UUID.randomUUID().toString())
        assertEquals(
            CoverDisplaySource.MediaStoreAlbum(7),
            RepositoryCoverResolver(FakeRepository(missing, record(CoverTargetType.ALBUM, "7"))).resolve(album)
        )
        assertEquals(
            CoverDisplaySource.DefaultPlaylist,
            RepositoryCoverResolver(FakeRepository(missing, null)).resolve(playlist)
        )
        assertEquals(
            CoverDisplaySource.MediaStoreAlbum(7),
            RepositoryCoverResolver(FakeRepository(missing, null, failRead = true)).resolve(album)
        )
    }

    @Test fun invalidAlbumUsesDefaultInsteadOfMediaStore() {
        val result = RepositoryCoverResolver(FakeRepository(File("missing"), null))
            .resolve(CoverTarget.Album(0, null, null))
        assertTrue(result === CoverDisplaySource.DefaultAlbum)
    }

    private fun record(type: CoverTargetType, id: String) = CustomCover(
        targetType = type,
        targetId = id,
        localFileName = "custom.webp",
        source = CoverSource.USER_SELECTED,
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1
    )

    private class FakeRepository(
        private val file: File,
        private val record: CustomCover?,
        private val failRead: Boolean = false
    ) : CustomCoverRepository {
        override fun getCover(target: CoverTarget): CoverResult<CustomCover?> =
            if (failRead) CoverResult.Failure(CoverError.STORAGE_READ_FAILED) else CoverResult.Success(record)
        override fun setCover(target: CoverTarget, input: CoverImageInput, source: CoverSource) = error("unused")
        override fun removeCover(target: CoverTarget) = error("unused")
        override fun hasCover(target: CoverTarget) = record != null
        override fun coverFile(cover: CustomCover) = file
    }
}

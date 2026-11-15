package com.iven.musicplayergo.covers

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CoverFileRecoveryTest {
    @get:Rule val temp = TemporaryFolder()
    private val uuid = "c6b34a22-2be6-4d32-b05f-94124b03b038"
    private val second = "17d20abd-1217-4fab-b8df-622100cabd76"

    @Test fun reclaimsUnreferencedImmutableLegacyAndPartialFilesInBothNamespaces() {
        val root = temp.newFolder()
        val album = File(root, "album").apply { mkdirs() }
        val playlist = File(root, "playlist").apply { mkdirs() }
        for (suffix in listOf("", ".tmp", ".bak", ".delete")) {
            File(album, "71-$uuid.webp$suffix").writeText("interrupted")
            File(album, "71.webp$suffix").writeText("legacy")
            File(playlist, "$uuid-$second.webp$suffix").writeText("interrupted")
            File(playlist, "$uuid.webp$suffix").writeText("legacy")
        }
        CoverFileRecovery.recover(root, emptyList())
        assertTrue(album.listFiles()!!.isEmpty())
        assertTrue(playlist.listFiles()!!.isEmpty())
    }

    @Test fun protectsEveryReferencedFileAndRecoveryCopyWithoutChangingBytes() {
        val root = temp.newFolder()
        val records = listOf(cover(CoverTargetType.ALBUM, "71-$uuid.webp"),
            cover(CoverTargetType.ALBUM, "72.webp"), cover(CoverTargetType.PLAYLIST, "$uuid-$second.webp"))
        val files = records.flatMap { record ->
            val directory = File(root, record.targetType.name.lowercase()).apply { mkdirs() }
            listOf("", ".bak", ".delete").map { File(directory, record.localFileName + it).apply { writeText(name) } }
        }
        val before = files.associateWith { it.readText() }
        CoverFileRecovery.recover(root, records)
        CoverFileRecovery.recover(root, records)
        assertEquals(before, files.associateWith { it.readText() })
    }

    @Test fun restoresReferencedLegacyBackupsAndKeepsFallbackWhenRenameIsUnavailable() {
        for (suffix in listOf(".bak", ".delete")) {
            val root = temp.newFolder()
            val directory = File(root, "album").apply { mkdirs() }
            val backup = File(directory, "71.webp$suffix").apply { writeText("durable") }
            CoverFileRecovery.recover(root, listOf(cover(CoverTargetType.ALBUM, "71.webp")))
            assertEquals("durable", File(directory, "71.webp").readText())
            assertFalse(backup.exists())
            File(directory, "72.webp").mkdir()
            val blockedBackup = File(directory, "72.webp$suffix").apply { writeText("keep") }
            CoverFileRecovery.recover(root, listOf(cover(CoverTargetType.ALBUM, "72.webp")))
            assertEquals("keep", blockedBackup.readText())
        }
    }

    @Test fun leavesUnknownNamesNestedDirectoriesAndOtherNamespacesAlone() {
        val root = temp.newFolder()
        val directory = File(root, "album").apply { mkdirs() }
        val unknown = listOf("photo.webp", "0.webp", "-1.webp", "71-bad-uuid.webp", "$uuid.webp", "notes.txt")
            .map { File(directory, it).apply { writeText("keep") } }
        val nested = File(directory, "73-$uuid.webp").apply { mkdir() }
        val child = File(nested, "74.webp").apply { writeText("nested") }
        val other = File(File(root, "other").apply { mkdir() }, "71.webp").apply { writeText("other") }
        CoverFileRecovery.recover(root, emptyList())
        unknown.forEach { assertEquals("keep", it.readText()) }
        assertEquals("nested", child.readText())
        assertEquals("other", other.readText())
    }

    @Test fun toleratesMissingOrNonDirectoryRoots() {
        CoverFileRecovery.recover(File(temp.root, "missing"), emptyList())
        val file = temp.newFile()
        CoverFileRecovery.recover(file, emptyList())
        assertTrue(file.isFile)
        assertFalse(File(temp.root, "missing").exists())
    }

    private fun cover(type: CoverTargetType, name: String) = CustomCover(
        targetType = type, targetId = if (type == CoverTargetType.ALBUM) "71" else uuid,
        localFileName = name, source = CoverSource.USER_SELECTED, createdAtEpochMs = 1, updatedAtEpochMs = 1
    )
}

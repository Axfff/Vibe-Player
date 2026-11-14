package com.iven.musicplayergo.covers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iven.musicplayergo.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CoverStorageRegressionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val a = CoverTarget.Album(71, "Artist", "Album A")
    private val b = CoverTarget.Album(72, "Artist", "Album B")

    @Test fun interruptedReplacementBeforeMetadataCommitPreservesBothOldCovers() = transaction(false, false)
    @Test fun interruptedReplacementAfterMetadataCommitReopensNewCover() = transaction(false, true)
    @Test fun interruptedRemovalBeforeMetadataCommitPreservesBothCovers() = transaction(true, false)
    @Test fun interruptedRemovalAfterMetadataCommitReopensDefault() = transaction(true, true)

    private fun transaction(remove: Boolean, afterCommit: Boolean) {
        val root = File(context.cacheDir, "transaction-${UUID.randomUUID()}")
        val storage = CrashStorage()
        fun reopen() = AndroidCustomCoverRepository(context, CoverRecordRepository(storage), root, outputSize = 32)
        val source = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        try {
            val repo = reopen()
            source.eraseColor(Color.RED)
            val old = success(repo.setCover(a, CoverImageInput.CroppedBitmap(source), CoverSource.USER_SELECTED))
            success(repo.setCover(b, CoverImageInput.CroppedBitmap(source), CoverSource.USER_SELECTED))
            source.eraseColor(Color.BLUE)
            storage.crash = if (afterCommit) 2 else 1
            try {
                if (remove) repo.removeCover(a) else repo.setCover(a, CoverImageInput.CroppedBitmap(source), CoverSource.RECOMMENDED)
                fail("The simulated process interruption must escape the operation")
            } catch (_: ProcessStopped) { }
            storage.crash = 0
            val restarted = reopen()
            val saved = success(restarted.getCover(a))
            if (remove && afterCommit) assertNull(saved) else {
                assertNotNull(saved)
                val file = restarted.coverFile(saved!!)
                assertTrue(file.isFile)
                val bitmap = requireNotNull(BitmapFactory.decodeFile(file.path))
                try {
                    val pixel = bitmap.getPixel(16, 16)
                    if (!remove && afterCommit) {
                        assertNotEquals(old.localFileName, saved.localFileName)
                        assertTrue(Color.blue(pixel) > 200 && Color.red(pixel) < 30)
                    } else {
                        assertEquals(old.localFileName, saved.localFileName)
                        assertTrue(Color.red(pixel) > 200 && Color.blue(pixel) < 30)
                    }
                } finally { bitmap.recycle() }
            }
            assertTrue(restarted.coverFile(success(restarted.getCover(b))!!).isFile)
        } finally { source.recycle(); root.deleteRecursively() }
    }

    @Test fun rejectedWriteKeepsOldFileAndRemovesUncommittedReplacement() {
        val root = File(context.cacheDir, "failed-write-${UUID.randomUUID()}")
        val storage = CrashStorage()
        val repo = AndroidCustomCoverRepository(context, CoverRecordRepository(storage), root, outputSize = 16)
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        try {
            val old = success(repo.setCover(a, CoverImageInput.CroppedBitmap(bitmap), CoverSource.USER_SELECTED))
            storage.reject = true
            assertEquals(CoverResult.Failure(CoverError.STORAGE_WRITE_FAILED),
                repo.setCover(a, CoverImageInput.CroppedBitmap(bitmap), CoverSource.RECOMMENDED))
            assertEquals(old, success(repo.getCover(a)))
            assertEquals(listOf(repo.coverFile(old)), root.walkTopDown().filter { it.isFile }.toList())
            assertEquals(CoverResult.Failure(CoverError.STORAGE_WRITE_FAILED), repo.removeCover(a))
            assertTrue(repo.coverFile(old).exists())
        } finally { bitmap.recycle(); root.deleteRecursively() }
    }

    @Test fun previousVersionInterruptedBackupAndDeleteAreRecoveredOnReopen() {
        for (suffix in listOf(".bak", ".delete")) {
            val root = File(context.cacheDir, "legacy-${UUID.randomUUID()}")
            val storage = CrashStorage()
            val records = CoverRecordRepository(storage)
            val record = success(records.putCover(a, "71.webp", CoverSource.USER_SELECTED))
            val directory = File(root, "album").apply { mkdirs() }
            File(directory, "71.webp$suffix").writeText("previous durable bytes")
            try {
                val reopened = AndroidCustomCoverRepository(context, CoverRecordRepository(storage), root)
                assertEquals("previous durable bytes", reopened.coverFile(record).readText())
                assertFalse(File(directory, "71.webp$suffix").exists())
            } finally { root.deleteRecursively() }
        }
    }

    @Test fun migrationPreservesRecordAndOtherPreferencesAndDoesNotResurrectDeletedCover() {
        val name = "migration-${UUID.randomUUID()}"
        val old = context.getSharedPreferences("$name-old", Context.MODE_PRIVATE)
        val current = context.getSharedPreferences("$name-new", Context.MODE_PRIVATE)
        try {
            val oldRepo = CoverRecordRepository(SharedPreferencesCoverStorage(old))
            val record = success(oldRepo.putCover(a, "71.webp", CoverSource.USER_SELECTED))
            old.edit().putString("unrelated-playlist", "keep").commit()
            val storage = SharedPreferencesCoverStorage(current, old)
            val migrated = CoverRecordRepository(storage)
            assertEquals(record, success(migrated.getCover(a)))
            assertFalse(old.contains(SharedPreferencesCoverStorage.KEY))
            assertEquals("keep", old.getString("unrelated-playlist", null))
            success(migrated.removeCover(a))
            // An interrupted cleanup can leave an older key; the newer durable record wins.
            success(oldRepo.putCover(a, "stale.webp", CoverSource.USER_SELECTED))
            assertNull(success(CoverRecordRepository(SharedPreferencesCoverStorage(current, old)).getCover(a)))
            assertFalse(old.contains(SharedPreferencesCoverStorage.KEY))
        } finally { old.edit().clear().commit(); current.edit().clear().commit() }
    }

    @Test fun backupRulesExcludePrivateImagesAndRecordsForCloudAndDeviceTransfer() {
        fun exclusions(resource: Int): Map<String, Set<Pair<String?, String?>>> {
            val result = mutableMapOf<String, MutableSet<Pair<String?, String?>>>()
            context.resources.getXml(resource).use { xml ->
                var section = "full-backup-content"
                while (xml.eventType != XmlPullParser.END_DOCUMENT) {
                    if (xml.eventType == XmlPullParser.START_TAG) {
                        if (xml.name in setOf("cloud-backup", "device-transfer")) section = xml.name
                        if (xml.name == "exclude") result.getOrPut(section) { mutableSetOf() }
                            .add(xml.getAttributeValue(null, "domain") to xml.getAttributeValue(null, "path"))
                    }
                    xml.next()
                }
            }
            return result
        }
        val expected = setOf("file" to "custom-covers/", "sharedpref" to "${SharedPreferencesCoverStorage.FILE_NAME}.xml")
        assertEquals(mapOf("full-backup-content" to expected), exclusions(R.xml.backup_rules))
        assertEquals(mapOf("cloud-backup" to expected, "device-transfer" to expected), exclusions(R.xml.data_extraction_rules))
    }

    private class ProcessStopped : Error()
    private class CrashStorage : CoverRecordStorage {
        var json: String? = null
        var crash = 0
        var reject = false
        override fun read() = json
        override fun write(json: String): Boolean {
            if (reject) return false
            if (crash == 1) throw ProcessStopped()
            this.json = json
            if (crash == 2) throw ProcessStopped()
            return true
        }
    }
    private fun <T> success(result: CoverResult<T>): T = (result as CoverResult.Success).value
}

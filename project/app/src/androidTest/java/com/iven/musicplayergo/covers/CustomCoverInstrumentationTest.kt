package com.iven.musicplayergo.covers

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.provider.MediaStore
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CustomCoverInstrumentationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var root: File
    private lateinit var repository: AndroidCustomCoverRepository
    private lateinit var target: CoverTarget.Playlist

    @Before fun setUp() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit().remove(SharedPreferencesCoverStorage.KEY).commit()
        root = File(context.cacheDir, "cover-test-${UUID.randomUUID()}")
        repository = AndroidCustomCoverRepository(
            context,
            CoverRecordRepository(SharedPreferencesCoverStorage(preferences)) { 100 },
            root = root,
            outputSize = 64
        )
        target = CoverTarget.Playlist(UUID.randomUUID().toString())
    }

    @After fun tearDown() { root.deleteRecursively() }

    @Test fun croppedBitmapIsNormalizedPersistedResolvedAndRemoved() {
        val source = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        val saved = success(repository.setCover(target, CoverImageInput.CroppedBitmap(source), CoverSource.USER_SELECTED))
        val file = repository.coverFile(saved)
        assertTrue(file.isFile)
        BitmapFactory.decodeFile(file.path).useBitmap { decoded ->
            assertEquals(64, decoded.width)
            assertEquals(64, decoded.height)
        }
        assertEquals(CoverDisplaySource.CustomFile(file), RepositoryCoverResolver(repository).resolve(target))
        success(repository.removeCover(target))
        assertFalse(file.exists())
        assertEquals(CoverDisplaySource.DefaultPlaylist, RepositoryCoverResolver(repository).resolve(target))
        source.recycle()
    }

    @Test fun contentUriUsesTheSamePrivateStoragePath() {
        val bitmap = Bitmap.createBitmap(90, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "cover-fixture-${UUID.randomUUID()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        assertNotNull(uri)
        try {
            context.contentResolver.openOutputStream(uri!!)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val saved = success(repository.setCover(target, CoverImageInput.ContentUri(uri), CoverSource.RECOMMENDED))
            assertEquals(CoverSource.RECOMMENDED, saved.source)
            assertTrue(repository.coverFile(saved).isFile)
        } finally {
            if (uri != null) context.contentResolver.delete(uri, null, null)
            bitmap.recycle()
        }
    }

    private fun <T> success(result: CoverResult<T>): T = when (result) {
        is CoverResult.Success -> result.value
        is CoverResult.Failure -> throw AssertionError("Expected success, got ${result.error}")
    }

    private inline fun Bitmap?.useBitmap(block: (Bitmap) -> Unit) {
        val bitmap = requireNotNull(this)
        try { block(bitmap) } finally { bitmap.recycle() }
    }
}

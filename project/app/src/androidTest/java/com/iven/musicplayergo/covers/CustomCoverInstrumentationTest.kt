package com.iven.musicplayergo.covers

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

    @Test fun cropPreviewSupportsDragPinchAndSquareExport() {
        val source = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
        }
        var exported: Bitmap? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = CropImageView(context)
            view.setCropBitmap(source)
            view.measure(
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, 200, 200)

            touch(view, MotionEvent.ACTION_DOWN, 100f, 100f)
            touch(view, MotionEvent.ACTION_MOVE, 180f, 100f)
            touch(view, MotionEvent.ACTION_UP, 180f, 100f)
            pinch(view, 80f, 120f, 40f, 160f)
            touch(view, MotionEvent.ACTION_CANCEL, 100f, 100f)

            exported = view.export(64)
        }
        exported.useBitmap { bitmap ->
            assertEquals(64, bitmap.width)
            assertEquals(64, bitmap.height)
            assertEquals(Color.GREEN, bitmap.getPixel(32, 32))
        }
        source.recycle()
    }

    @Test fun cropPreviewRejectsExportBeforeItHasContentAndSize() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = CropImageView(context)
            assertTrue(runCatching { view.export(8) }.isFailure)
        }
    }

    @Test fun invalidTargetsAndUnreadableInputsAreRejected() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        assertEquals(
            CoverError.INVALID_TARGET,
            failure(repository.setCover(CoverTarget.Playlist(" "), CoverImageInput.CroppedBitmap(bitmap), CoverSource.USER_SELECTED))
        )
        assertEquals(
            CoverError.SOURCE_NOT_READABLE,
            failure(repository.setCover(target, CoverImageInput.ContentUri(Uri.parse("file:///not-content")), CoverSource.USER_SELECTED))
        )
        bitmap.recycle()
        assertEquals(
            CoverError.IMAGE_DECODE_FAILED,
            failure(repository.setCover(target, CoverImageInput.CroppedBitmap(bitmap), CoverSource.USER_SELECTED))
        )
    }

    private fun <T> success(result: CoverResult<T>): T = when (result) {
        is CoverResult.Success -> result.value
        is CoverResult.Failure -> throw AssertionError("Expected success, got ${result.error}")
    }

    private fun failure(result: CoverResult<*>): CoverError = when (result) {
        is CoverResult.Success -> throw AssertionError("Expected failure, got ${result.value}")
        is CoverResult.Failure -> result.error
    }

    private inline fun Bitmap?.useBitmap(block: (Bitmap) -> Unit) {
        val bitmap = requireNotNull(this)
        try { block(bitmap) } finally { bitmap.recycle() }
    }

    private fun touch(view: CropImageView, action: Int, x: Float, y: Float) {
        MotionEvent.obtain(0, 0, action, x, y, 0).also {
            try { view.onTouchEvent(it) } finally { it.recycle() }
        }
    }

    private fun pinch(view: CropImageView, startLeft: Float, startRight: Float, endLeft: Float, endRight: Float) {
        val properties = arrayOf(
            MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER },
            MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER }
        )
        fun event(action: Int, eventTime: Long, left: Float, right: Float, pointerCount: Int = 2): MotionEvent {
            val coordinates = arrayOf(
                MotionEvent.PointerCoords().apply { x = left; y = 100f; pressure = 1f; size = 1f },
                MotionEvent.PointerCoords().apply { x = right; y = 100f; pressure = 1f; size = 1f }
            )
            return MotionEvent.obtain(
                0, eventTime, action, pointerCount,
                properties.take(pointerCount).toTypedArray(), coordinates.take(pointerCount).toTypedArray(),
                0, 0, 1f, 1f, 0, 0, 0, 0
            )
        }
        listOf(
            event(MotionEvent.ACTION_DOWN, 0, startLeft, startRight, pointerCount = 1),
            event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 10, startLeft, startRight),
            event(MotionEvent.ACTION_MOVE, 20, (startLeft + endLeft) / 2f, (startRight + endRight) / 2f),
            event(MotionEvent.ACTION_MOVE, 30, endLeft, endRight),
            event(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 40, endLeft, endRight),
            event(MotionEvent.ACTION_UP, 50, endLeft, endRight, pointerCount = 1)
        ).forEach {
            try { view.onTouchEvent(it) } finally { it.recycle() }
        }
    }
}

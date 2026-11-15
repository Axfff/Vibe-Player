package com.iven.musicplayergo.covers

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iven.musicplayergo.ui.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CoverRenderingDeviceTest {
    @Test fun decodedCoverFillsExpandedViewAndFallbackStaysInsideAfterRebinding() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".debug") && Build.HARDWARE in setOf("ranchu", "goldfish"))
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
        val repository = CoverRepositories.get(context)
        val target = CoverTarget.Playlist(UUID.randomUUID().toString())
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        assertTrue(repository.setCover(target, CoverImageInput.CroppedBitmap(bitmap), CoverSource.USER_SELECTED) is CoverResult.Success)
        bitmap.recycle()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var view: ImageView
                scenario.onActivity { activity ->
                    view = ImageView(activity).apply {
                        setBackgroundColor(Color.MAGENTA)
                        scaleType = ImageView.ScaleType.CENTER_INSIDE // Details XML starts here.
                    }
                    activity.findViewById<FrameLayout>(android.R.id.content).addView(view,
                        FrameLayout.LayoutParams(64, 64, Gravity.CENTER))
                    view.loadCover(target)
                }
                await {
                    var ready = false
                    scenario.onActivity { ready = view.width == 64 }
                    ready
                }
                assertPixel(scenario, view, Color.CYAN, 32)
                // Reproduce expansion after a small cold decode, independent of cache timing.
                scenario.onActivity { view.layoutParams = view.layoutParams.apply { width = 256; height = 256 } }
                await { var ready = false; scenario.onActivity { ready = view.width == 256 }; ready }
                assertPixel(scenario, view, Color.CYAN)

                assertTrue(repository.removeCover(target) is CoverResult.Success)
                scenario.onActivity { view.loadCover(target) }
                assertPixel(scenario, view, Color.MAGENTA)

                // A recycled view must also leave the fallback's scale mode on a new success.
                val replacement = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
                assertTrue(repository.setCover(target, CoverImageInput.CroppedBitmap(replacement), CoverSource.USER_SELECTED) is CoverResult.Success)
                replacement.recycle()
                scenario.onActivity { view.loadCover(target) }
                assertPixel(scenario, view, Color.GREEN)
                scenario.onActivity { (view.parent as ViewGroup).removeView(view) }
            }
        } finally {
            repository.removeCover(target)
        }
    }

    private fun assertPixel(scenario: ActivityScenario<MainActivity>, view: ImageView, expected: Int, offset: Int = 8) {
        val position = IntArray(2)
        scenario.onActivity { view.getLocationOnScreen(position) }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // Screen capture checks actual ImageView rendering, including scale and crossfade.
        await {
            val screen = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            val pixel = screen.getPixel(position[0] + offset, position[1] + offset)
            screen.recycle()
            kotlin.math.abs(Color.red(pixel) - Color.red(expected)) < 12 &&
                kotlin.math.abs(Color.green(pixel) - Color.green(expected)) < 12 &&
                kotlin.math.abs(Color.blue(pixel) - Color.blue(expected)) < 12
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        do {
            if (condition()) return
            SystemClock.sleep(50)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("Cover did not reach the expected rendered state")
    }
}

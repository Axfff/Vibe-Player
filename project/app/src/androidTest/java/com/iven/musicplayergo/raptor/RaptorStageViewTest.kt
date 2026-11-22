package com.iven.musicplayergo.raptor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View.MeasureSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RaptorStageViewTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun distinctPalettesAndBeatPulseReachTheRenderedFrame() = instrumentation.runOnMainSync {
        val stage = stage()
        val run = RaptorRun()
        val scenes = listOf("neutral", "drift", "groove", "surge").map { theme ->
            stage.frame(run, RaptorSignal(theme))
        }
        assertEquals(4, scenes.map { it.getPixel(10, 16) }.distinct().size)
        assertEquals(4, scenes.map { it.getPixel(12, 176) }.distinct().size)
        assertTrue(scenes.first().sameAs(stage.frame(run, RaptorSignal("unknown", Float.NaN))))

        val resting = scenes.last()
        val beat = stage.frame(run, RaptorSignal("surge", 1f))
        assertNotEquals(resting.getPixel(12, 176), beat.getPixel(12, 176))
        assertNotEquals(resting.getPixel(421, 95), beat.getPixel(421, 95))
        assertEquals(resting.getPixel(102, 151), beat.getPixel(102, 151))
    }

    @Test
    fun bothObstaclesAndTheJumpAreReadFromTheRun() = instrumentation.runOnMainSync {
        val stage = stage()
        val run = RaptorRun().apply { phase = RaptorPhase.RUNNING }
        repeat(72) { run.step(1f / 60f) }
        val before = stage.frame(run, RaptorSignal())
        val crystal = run.obstacles.first { it.kind == "crystal" }
        val spike = run.obstacles.first { it.kind == "spike" }
        assertEquals(0xFFD6A0ED.toInt(), before.getPixel(crystal.x.roundToInt() + 9, crystal.y.roundToInt() + 4))
        assertEquals(0xFFBA5F89.toInt(), before.getPixel(spike.x.roundToInt() + 8, spike.y.roundToInt() + 20))

        assertTrue(run.jump())
        repeat(12) { run.step(1f / 60f) }
        val playerY = run.player.y.roundToInt() + 3
        val after = stage.frame(run, RaptorSignal())
        assertEquals(0xFFDCFF73.toInt(), after.getPixel(102, playerY))
        assertNotEquals(before.getPixel(102, playerY), after.getPixel(102, playerY))
    }

    @Test
    fun stageKeepsWorldAspectAtPhoneWidthAndFitsAHeightCap() = instrumentation.runOnMainSync {
        val stage = RaptorStageView(instrumentation.targetContext)
        val width = MeasureSpec.makeMeasureSpec(360, MeasureSpec.EXACTLY)
        stage.measure(width, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        assertEquals(360, stage.measuredWidth)
        assertEquals(167, stage.measuredHeight)

        stage.measure(width, MeasureSpec.makeMeasureSpec(110, MeasureSpec.AT_MOST))
        assertEquals(110, stage.measuredHeight)
        stage.layout(0, 0, 360, 110)
        val compact = stage.frame(RaptorRun(), RaptorSignal())
        assertEquals(0xFF120D22.toInt(), compact.getPixel(0, 0))
        assertNotEquals(compact.getPixel(0, 0), compact.getPixel(180, 12))
    }

    private fun stage() = RaptorStageView(instrumentation.targetContext).apply {
        measure(MeasureSpec.makeMeasureSpec(480, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        layout(0, 0, measuredWidth, measuredHeight)
    }

    private fun RaptorStageView.frame(run: RaptorRun, signal: RaptorSignal): Bitmap {
        present(run, signal)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) }
    }
}

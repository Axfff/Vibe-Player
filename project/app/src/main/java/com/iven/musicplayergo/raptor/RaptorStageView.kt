package com.iven.musicplayergo.raptor

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Draws the original Raptor Radio art in the same world coordinates as the runner physics. */
class RaptorStageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    // Each shape uses integer world coordinates for the stepped pixel-art edges.
    private val paint = Paint().apply { isAntiAlias = false }
    private val ridge = Path()
    private var run = RaptorRun()
    private var palette = neutralPalette
    private var pulse = 0f

    internal fun present(run: RaptorRun, signal: RaptorSignal) {
        this.run = run
        palette = scenePalettes[signal.theme] ?: neutralPalette
        pulse = if (signal.pulse.isFinite()) signal.pulse.coerceIn(0f, 1f) else 0f
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> (RAPTOR_WORLD_WIDTH * resources.displayMetrics.density).roundToInt()
            else -> MeasureSpec.getSize(widthMeasureSpec)
        }
        val fieldHeight = (width * RAPTOR_WORLD_HEIGHT / RAPTOR_WORLD_WIDTH).roundToInt()
        setMeasuredDimension(width, resolveSize(fieldHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val scale = minOf(width / RAPTOR_WORLD_WIDTH, height / RAPTOR_WORLD_HEIGHT)
        fill(canvas, 0f, 0f, width.toFloat(), height.toFloat(), palette.floor[1])
        val saved = canvas.save()
        canvas.translate((width - RAPTOR_WORLD_WIDTH * scale) * 0.5f, (height - RAPTOR_WORLD_HEIGHT * scale) * 0.5f)
        canvas.scale(scale, scale)
        canvas.clipRect(0f, 0f, RAPTOR_WORLD_WIDTH, RAPTOR_WORLD_HEIGHT)

        drawSky(canvas, run.travel)
        drawSun(canvas)
        drawTerrain(canvas, run.travel)
        drawGround(canvas, run.travel)
        run.obstacles.forEach { drawObstacle(canvas, it) }
        drawRaptor(canvas, run.player, run.travel, run.phase)
        canvas.restoreToCount(saved)
    }

    private fun drawSky(canvas: Canvas, travel: Float) {
        gradient(canvas, palette.skyFill, 0f, 0f, RAPTOR_WORLD_WIDTH, RAPTOR_GROUND)
        val offset = travel * 0.028f % RAPTOR_WORLD_WIDTH
        for (star in stars) {
            val x = ((star.x + RAPTOR_WORLD_WIDTH - offset) % RAPTOR_WORLD_WIDTH).roundToInt().toFloat()
            fill(canvas, x, star.y, 1f, 1f, palette.beat, if (star.dim) 56 else 138)
            if (star.bright) {
                fill(canvas, x - 2f, star.y, 5f, 1f, palette.beat, 77)
                fill(canvas, x, star.y - 2f, 1f, 5f, palette.beat, 77)
                fill(canvas, x, star.y, 1f, 1f, palette.beat, 230)
            }
        }
    }

    private fun drawSun(canvas: Canvas) {
        val scale = (HALO_RADIUS + pulse * 9f) / HALO_RADIUS
        val saved = canvas.save()
        canvas.scale(scale, scale, palette.sunX, SUN_Y)
        gradient(
            canvas, palette.haloFill,
            palette.sunX - HALO_RADIUS, SUN_Y - HALO_RADIUS, HALO_RADIUS * 2, HALO_RADIUS * 2
        )
        canvas.restoreToCount(saved)

        paint.shader = palette.sunFill
        paint.alpha = 255
        for (band in sunBands) {
            val x = palette.sunX - band.halfWidth
            val y = SUN_Y + band.offset
            canvas.drawRect(x, y, palette.sunX + band.halfWidth + 1f, y + 2f, paint)
        }
        paint.shader = null
    }

    private fun drawTerrain(canvas: Canvas, travel: Float) {
        drawRidge(canvas, palette.backProfile, 161f, travel * 0.065f, palette.ridge[0])
        drawRidge(canvas, palette.frontProfile, RAPTOR_GROUND, travel * 0.135f, palette.ridge[1])
        for (x in 0 until RAPTOR_WORLD_WIDTH.toInt() step 4) {
            val h = 11f + floor(ridgeHeight(palette.backProfile, x, travel * 0.21f + 113f) / 7f)
            fill(canvas, x.toFloat(), RAPTOR_GROUND - h, 4f, h, palette.ridge[2])
        }
        val alpha = opacity(0.12f + pulse * 0.38f)
        val offset = travel * 0.31f % RAPTOR_WORLD_WIDTH
        for (index in 0 until 28) {
            val x = ((index * 19 + RAPTOR_WORLD_WIDTH - offset) % RAPTOR_WORLD_WIDTH).roundToInt().toFloat()
            val h = (2 + (index * 11 % 7) * pulse).roundToInt().toFloat()
            fill(canvas, x, RAPTOR_GROUND - 3f - h, 1f, h, palette.beat, alpha)
            if (index % 4 == 0) fill(canvas, x + 3f, RAPTOR_GROUND - 4f, 3f, 1f, palette.beat, alpha)
        }
    }

    private fun drawRidge(canvas: Canvas, profile: IntArray, baseline: Float, offset: Float, color: Int) {
        ridge.rewind()
        ridge.moveTo(0f, RAPTOR_GROUND)
        for (x in 0 until RAPTOR_WORLD_WIDTH.toInt() step 3) {
            val y = baseline - ridgeHeight(profile, x, offset)
            ridge.lineTo(x.toFloat(), y)
            ridge.lineTo(x + 3f, y)
        }
        ridge.lineTo(RAPTOR_WORLD_WIDTH, RAPTOR_GROUND)
        ridge.close()
        paint.color = color
        canvas.drawPath(ridge, paint)
    }

    private fun drawGround(canvas: Canvas, travel: Float) {
        gradient(canvas, palette.floorFill, 0f, RAPTOR_GROUND, RAPTOR_WORLD_WIDTH, RAPTOR_WORLD_HEIGHT - RAPTOR_GROUND)
        for (row in gridRows) fill(canvas, 0f, RAPTOR_GROUND + row, RAPTOR_WORLD_WIDTH, 1f, palette.grid)

        paint.color = palette.grid
        paint.strokeWidth = 1f
        val slide = travel % 58f
        val centre = RAPTOR_WORLD_WIDTH / 2f
        for (index in -2..10) {
            val start = index * 58f - slide
            canvas.drawLine(start, RAPTOR_GROUND + 2f, centre + (start - centre) * 1.5f, RAPTOR_WORLD_HEIGHT, paint)
        }

        val offset = (travel * 1.2f % 120f).roundToInt()
        val alpha = opacity(0.28f + pulse * 0.32f)
        for (index in 0 until 9) {
            val x = (index * 31 + RAPTOR_WORLD_WIDTH - offset) % RAPTOR_WORLD_WIDTH
            val y = RAPTOR_GROUND + 5f + (index * 11) % 39
            fill(canvas, x, y, 4f + index % 4, 1f, palette.rim, alpha)
        }
        fill(canvas, 0f, RAPTOR_GROUND - 1f, RAPTOR_WORLD_WIDTH, 1f, palette.rim, opacity(0.6f + pulse * 0.3f))
        fill(canvas, 0f, RAPTOR_GROUND, RAPTOR_WORLD_WIDTH, 2f, palette.rim, opacity(0.24f + pulse * 0.21f))
    }

    private fun drawObstacle(canvas: Canvas, obstacle: RaptorObstacle) {
        val x = obstacle.x.roundToInt().toFloat()
        val y = obstacle.y.roundToInt().toFloat()
        val w = obstacle.w
        val h = obstacle.h
        fill(canvas, x - 2f, y + h - 1f, w + 5f, 3f, palette.floor[1])
        if (obstacle.kind == "crystal") {
            val (shadow, body, face, glint) = palette.crystal
            val left = (w * 0.24f).roundToInt()
            val right = (w * 0.65f).roundToInt()
            val heel = (h * 0.48f).roundToInt()
            fill(canvas, x, y + heel, w, h - heel, shadow)
            fill(canvas, x + 1f, y + heel, left.toFloat(), h - heel - 2f, body)
            fill(canvas, x + right, y + 5f, w - right - 1f, h - 6f, body)
            fill(canvas, x + left, y + 2f, right - left + 1f, h - 3f, body)
            fill(canvas, x + left + 2f, y, maxOf(1f, right - left - 1f), h - 3f, face)
            fill(canvas, x + right, y + 5f, 2f, h - 8f, face)
            fill(canvas, x + left + 2f, y + 2f, 1f, h - 9f, glint)
        } else {
            val (shadow, body, face) = palette.spike
            for (row in 0 until h.toInt()) {
                val remaining = 1f - row / h
                val left = (remaining * w * 0.46f).roundToInt()
                val right = (remaining * w * 0.35f).roundToInt()
                val width = w - left - right
                fill(canvas, x + left, y + row, width, 1f, shadow)
                fill(canvas, x + left, y + row, ceil(width * 0.68f), 1f, body)
                fill(canvas, x + left, y + row, 1f + row / 10, 1f, face)
            }
        }
    }

    private fun drawRaptor(canvas: Canvas, player: RaptorPlayer, travel: Float, phase: RaptorPhase) {
        val x = player.x.roundToInt().toFloat()
        val lift = maxOf(0f, RAPTOR_GROUND - player.y - player.h)
        val airborne = lift > 0f
        val stride = if (phase == RaptorPhase.READY) 0 else (travel / 7).toInt() % 4
        val y = player.y.roundToInt().toFloat() + if (!airborne && stride % 2 == 1) 1f else 0f

        fill(
            canvas, x + 5f, RAPTOR_GROUND + 1f, player.w - 2f, 2f,
            palette.floor[1], opacity(0.65f - minOf(lift, 55f) / 100f)
        )
        if (phase == RaptorPhase.RUNNING && !airborne) {
            fill(canvas, x + 3f - stride * 2, RAPTOR_GROUND - 3f, 2f, 1f, palette.rim, 128)
            fill(canvas, x + 1f - stride * 2, RAPTOR_GROUND - 6f, 1f, 1f, palette.rim, 128)
        }
        for (strip in raptorStrips) fill(canvas, x + strip.x, y + strip.y, strip.width, 1f, strip.color)

        // Distance drives the feet and scarf so a paused run holds the current pose.
        fill(canvas, x + 5f, y + 12f + stride % 2, 7f, 2f, RAPTOR_SCARF)
        fill(canvas, x + 2f, y + 11f + stride % 3, 5f, 1f, RAPTOR_SCARF)
        fill(canvas, x + 3f, y + 13f + stride % 2, 4f, 1f, RAPTOR_SCARF_SHADE)
        val back = if (airborne) 8f else backStep[stride]
        val front = if (airborne) 16f else frontStep[stride]
        val backHeight = if (airborne || stride == 2) 4f else 7f - stride % 2
        val frontHeight = if (airborne || stride == 0) 5f else 7f - stride % 2
        fill(canvas, x + back, y + 20f, 3f, backHeight, RAPTOR_GREEN)
        fill(canvas, x + back, y + 20f + backHeight, 5f, 2f, RAPTOR_GREEN)
        fill(canvas, x + front, y + 20f, 3f, frontHeight, RAPTOR_LIME)
        fill(canvas, x + front, y + 20f + frontHeight, 5f, 2f, RAPTOR_LIME)

        if (phase == RaptorPhase.OVER) {
            fill(canvas, x + 13f, y + 3f, 1f, 1f, RAPTOR_EYE)
            fill(canvas, x + 16f, y + 3f, 1f, 1f, RAPTOR_EYE)
            fill(canvas, x + 13f, y + 6f, 1f, 1f, RAPTOR_EYE)
            fill(canvas, x + 16f, y + 6f, 1f, 1f, RAPTOR_EYE)
        }
    }

    private fun gradient(canvas: Canvas, shader: Shader, x: Float, y: Float, w: Float, h: Float) {
        paint.shader = shader
        paint.alpha = 255
        canvas.drawRect(x, y, x + w, y + h, paint)
        paint.shader = null
    }

    private fun fill(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int, alpha: Int = 255) {
        paint.color = color
        paint.alpha = alpha
        canvas.drawRect(x, y, x + w, y + h, paint)
    }
}

private const val SUN_Y = 95f
private const val SUN_RADIUS = 42
private const val HALO_RADIUS = 74f
private const val RAPTOR_GREEN = 0xFF87AD4B.toInt()
private const val RAPTOR_LIME = 0xFFDCFF73.toInt()
private const val RAPTOR_EYE = 0xFF241837.toInt()
private const val RAPTOR_SCARF = 0xFFEE82A8.toInt()
private const val RAPTOR_SCARF_SHADE = 0xFFB34B82.toInt()

private fun opaque(rgb: Int): Int = rgb or 0xFF000000.toInt()
private fun colors(vararg rgb: Int): IntArray = IntArray(rgb.size) { opaque(rgb[it]) }
private fun opacity(fraction: Float): Int = (fraction * 255).roundToInt()

private class ScenePalette(
    sky: IntArray,
    sun: IntArray,
    glow: Int,
    val ridge: IntArray,
    val floor: IntArray,
    val grid: Int,
    val rim: Int,
    val beat: Int,
    val crystal: IntArray,
    val spike: IntArray,
    val backProfile: IntArray,
    val frontProfile: IntArray,
    val sunX: Float = 347f
) {
    val skyFill = LinearGradient(0f, 0f, 0f, RAPTOR_GROUND, sky, floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP)
    val sunFill = LinearGradient(
        0f, SUN_Y - SUN_RADIUS, 0f, SUN_Y + SUN_RADIUS, sun, floatArrayOf(0f, 0.52f, 1f), Shader.TileMode.CLAMP
    )
    val floorFill = LinearGradient(0f, RAPTOR_GROUND, 0f, RAPTOR_WORLD_HEIGHT, floor, null, Shader.TileMode.CLAMP)
    val haloFill = RadialGradient(
        sunX, SUN_Y, HALO_RADIUS, intArrayOf(glow, glow and 0xFFFFFF), floatArrayOf(0.2f, 1f), Shader.TileMode.CLAMP
    )
}

private val neutralPalette = ScenePalette(
    sky = colors(0x100E27, 0x302149, 0x9A4E78),
    sun = colors(0xFFD1A1, 0xF988AD, 0xB94E9B),
    glow = 0x45F36AA9,
    ridge = colors(0x5C3C6C, 0x3C2857, 0x271A3B),
    floor = colors(0x25152F, 0x120D22),
    grid = opaque(0x523060), rim = opaque(0xE381BD), beat = opaque(0xFFB6D7),
    crystal = colors(0x38234A, 0x825899, 0xD6A0ED, 0xF7CDF8),
    spike = colors(0x613052, 0xBA5F89, 0xF29BB4),
    backProfile = intArrayOf(0, 15, 31, 26, 43, 49, 34, 47, 24, 38, 33, 13, 0),
    frontProfile = intArrayOf(12, 24, 15, 6, 26, 31, 19, 31, 37, 20, 7, 20, 12)
)

private val scenePalettes = mapOf(
    "neutral" to neutralPalette,
    "drift" to ScenePalette(
        sky = colors(0x0A152A, 0x203650, 0x457E89),
        sun = colors(0xD8FFEA, 0x83DFEB, 0x528EAE),
        glow = 0x4272E6E8,
        ridge = colors(0x365675, 0x25445F, 0x16273F),
        floor = colors(0x162A41, 0x0D1528),
        grid = opaque(0x285070), rim = opaque(0x7AC9D7), beat = opaque(0xB3F3EB),
        crystal = colors(0x183B53, 0x427D9B, 0x74C6DB, 0xCCFDFF),
        spike = colors(0x30627A, 0x538BA2, 0xA6D7E0),
        backProfile = intArrayOf(12, 17, 22, 19, 16, 35, 56, 33, 20, 16, 22, 24, 12),
        frontProfile = intArrayOf(10, 18, 28, 24, 17, 9, 8, 15, 19, 12, 8, 6, 10),
        sunX = 135f
    ),
    "groove" to ScenePalette(
        sky = colors(0x1C1029, 0x4A2351, 0xAB5B6A),
        sun = colors(0xFFECAE, 0xFFB477, 0xF2728D),
        glow = 0x47F87E80,
        ridge = colors(0x73445E, 0x512B53, 0x2B1B3E),
        floor = colors(0x342038, 0x180F25),
        grid = opaque(0x65364D), rim = opaque(0xEE956F), beat = opaque(0xFFCF94),
        crystal = colors(0x522849, 0x9B537C, 0xE7809E, 0xFFD8B6),
        spike = colors(0x673050, 0xC36772, 0xFFAB8F),
        backProfile = intArrayOf(24, 24, 36, 36, 12, 12, 24, 42, 42, 15, 24, 24, 24),
        frontProfile = intArrayOf(8, 8, 10, 30, 30, 24, 13, 8, 27, 27, 29, 21, 8)
    ),
    "surge" to ScenePalette(
        sky = colors(0x0C0E2A, 0x342258, 0x6D438B),
        sun = colors(0xF5D6FF, 0xC69AFF, 0x8C62EA),
        glow = 0x52BD75FF,
        ridge = colors(0x533573, 0x382757, 0x211936),
        floor = colors(0x272043, 0x110F26),
        grid = opaque(0x483878), rim = opaque(0xB387FF), beat = opaque(0xE6C3FF),
        crystal = colors(0x342358, 0x7A5AB9, 0xC194F7, 0xEFDDFF),
        spike = colors(0x573579, 0x9A5FCE, 0xE0A1FF),
        backProfile = intArrayOf(10, 52, 24, 51, 12, 40, 16, 58, 25, 28, 48, 15, 10),
        frontProfile = intArrayOf(13, 30, 9, 17, 38, 20, 31, 13, 9, 22, 15, 32, 13)
    )
)

private data class Star(val x: Float, val y: Float, val bright: Boolean, val dim: Boolean)
private val stars = List(52) { index ->
    Star(
        (index * 73 + index * index * 11 + 41) % RAPTOR_WORLD_WIDTH,
        13f + (index * 29 + index * index * 3) % 115,
        index % 9 == 0,
        index % 4 == 0
    )
}

private data class SunBand(val offset: Int, val halfWidth: Int)
private val sunBands = (-SUN_RADIUS..SUN_RADIUS step 2)
    .filter { it <= 3 || it % (if (it > 23) 8 else 6) == 0 }
    .map { SunBand(it, sqrt((SUN_RADIUS * SUN_RADIUS - it * it).toDouble()).toInt()) }
private val gridRows = intArrayOf(6, 13, 25, 42)

private fun ridgeHeight(profile: IntArray, x: Int, offset: Float): Int {
    val wrapped = (x + offset) % RAPTOR_WORLD_WIDTH
    val point = wrapped * (profile.size - 1) / RAPTOR_WORLD_WIDTH
    val left = point.toInt()
    return (profile[left] + (profile[left + 1] - profile[left]) * (point - left)).roundToInt()
}

private data class SpriteStrip(val x: Int, val y: Int, val width: Float, val color: Int)
private val raptorColors = mapOf(
    'D' to opaque(0x466135),
    'G' to RAPTOR_GREEN,
    'L' to RAPTOR_LIME,
    'H' to opaque(0xF4FFB0),
    'E' to RAPTOR_EYE,
    'S' to RAPTOR_SCARF,
    'V' to RAPTOR_SCARF_SHADE
)
private val pixelRun = Regex("([A-Z])\\1*")
private val raptorStrips = listOf(
    "...............HHHHHH....",
    ".............HHLLLLLLHH..",
    "............HLLLLLLLLLLH.",
    "...........HLLLLLLLLLLLL.",
    "...........HLLHEHLLLLLLLL",
    "...........HLLHEHLLLLLLLL",
    "...........HLLLLLLLLLLLLL",
    "...........HLLLLLLLLLLLLL",
    "...........HLLLLLGGGGGGG.",
    "..........HLLLLLLGEEL....",
    "..........HLLLLLLLLLLL...",
    "..........SSSSSSVVVVV....",
    ".........SSVVSSVG........",
    "H......HHLLLLLGLG........",
    "HL...HLLLLLLGLGGD........",
    "HLLLLLLLLLLGLGGGGD.......",
    ".HLLLLLLLLLGLGGGGGD......",
    "..HLLLLLLLLLGGGGDD.......",
    "....HLLLLLLLGGGD.........",
    "......DGGGGGGGD..........",
    "........DDGGGD..........."
).flatMapIndexed { row, pixels ->
    pixelRun.findAll(pixels).map { match ->
        SpriteStrip(match.range.first, row, match.value.length.toFloat(), raptorColors.getValue(match.value.first()))
    }.toList()
}
private val backStep = floatArrayOf(9f, 11f, 13f, 10f)
private val frontStep = floatArrayOf(15f, 13f, 10f, 13f)

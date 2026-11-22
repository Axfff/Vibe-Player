package com.iven.musicplayergo.raptor

internal const val RAPTOR_WORLD_WIDTH = 480f
internal const val RAPTOR_WORLD_HEIGHT = 222f
internal const val RAPTOR_GROUND = 177f

internal enum class RaptorPhase { READY, RUNNING, PAUSED, OVER }

internal class RaptorPlayer {
    val x = 84f
    val w = 25f
    val h = 29f
    var y = RAPTOR_GROUND - h
        internal set
}

internal class RaptorObstacle(
    var x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val kind: String
)

/** Frame-stepped runner simulation in the web demo's logical coordinate space. */
internal class RaptorRun {
    val player = RaptorPlayer()
    private val activeObstacles = mutableListOf<RaptorObstacle>()
    val obstacles: List<RaptorObstacle> get() = activeObstacles

    var phase = RaptorPhase.READY
    val time: Float get() = elapsedSeconds.toFloat()
    val travel: Float get() = distanceTravelled.toFloat()
    val score: Int get() = (distanceTravelled / 8).toInt()

    private var elapsedSeconds = 0.0
    private var distanceTravelled = 0.0
    private var velocity = 0f
    private var nextObstacle = 0

    init {
        reset()
    }

    fun reset() {
        phase = RaptorPhase.READY
        player.y = RAPTOR_GROUND - player.h
        elapsedSeconds = 0.0
        distanceTravelled = 0.0
        velocity = 0f
        nextObstacle = 0
        activeObstacles.clear()
        addObstacle(370f)
    }

    fun jump(): Boolean {
        if (phase != RaptorPhase.RUNNING || velocity != 0f || player.y + player.h < RAPTOR_GROUND) {
            return false
        }
        velocity = -267f
        return true
    }

    fun step(seconds: Float) {
        if (phase != RaptorPhase.RUNNING || !seconds.isFinite() || seconds <= 0f) return

        // Bound movement across a slow frame so a narrow obstacle cannot be skipped.
        val elapsed = minOf(seconds.toDouble(), 1.0 / 30.0)
        elapsedSeconds += elapsed
        val distance = minOf(224.0, 118.0 + elapsedSeconds * 2.5) * elapsed
        distanceTravelled += distance

        val elapsedFrame = elapsed.toFloat()
        velocity += 730f * elapsedFrame
        player.y += velocity * elapsedFrame
        if (player.y + player.h >= RAPTOR_GROUND) {
            player.y = RAPTOR_GROUND - player.h
            velocity = 0f
        }

        for (obstacle in activeObstacles) obstacle.x -= distance.toFloat()
        val nextX = activeObstacles.last().x + patterns[nextObstacle].gap
        if (nextX <= RAPTOR_WORLD_WIDTH + 20f) addObstacle(nextX)
        val first = activeObstacles.first()
        if (first.x + first.w < 0f) activeObstacles.removeAt(0)
        if (activeObstacles.any(::collides)) phase = RaptorPhase.OVER
    }

    private fun addObstacle(x: Float) {
        val pattern = patterns[nextObstacle]
        activeObstacles.add(RaptorObstacle(x, RAPTOR_GROUND - pattern.h, pattern.w, pattern.h, pattern.kind))
        nextObstacle = (nextObstacle + 1) % patterns.size
    }

    private fun collides(obstacle: RaptorObstacle): Boolean =
        player.x + player.w - 4f > obstacle.x + 3f &&
            player.x + 5f < obstacle.x + obstacle.w - 3f &&
            player.y + player.h - 3f > obstacle.y + 3f &&
            player.y + 6f < obstacle.y + obstacle.h

    private data class ObstaclePattern(val w: Float, val h: Float, val kind: String, val gap: Float)

    private companion object {
        val patterns = listOf(
            ObstaclePattern(23f, 28f, "crystal", 181f),
            ObstaclePattern(29f, 23f, "spike", 205f),
            ObstaclePattern(32f, 32f, "crystal", 213f),
            ObstaclePattern(22f, 21f, "spike", 174f),
            ObstaclePattern(27f, 31f, "crystal", 198f)
        )
    }
}

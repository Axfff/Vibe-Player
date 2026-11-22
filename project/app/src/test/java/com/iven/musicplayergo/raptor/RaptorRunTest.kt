package com.iven.musicplayergo.raptor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RaptorRunTest {
    @Test
    fun runStartsGroundedAndOnlyAdvancesDuringPlay() {
        val run = RaptorRun()
        assertEquals(RaptorPhase.READY, run.phase)
        assertEquals(RAPTOR_GROUND, run.player.y + run.player.h, 0f)
        assertFalse(run.jump())
        run.step(0.02f)
        assertEquals(0f, run.travel, 0f)

        run.phase = RaptorPhase.RUNNING
        for (elapsed in listOf(0f, -0.1f, Float.NaN, Float.POSITIVE_INFINITY)) run.step(elapsed)
        assertEquals(0f, run.travel, 0f)
        run.step(0.02f)
        val distance = run.travel
        assertTrue(distance > 0f)

        run.phase = RaptorPhase.PAUSED
        run.step(1f)
        assertFalse(run.jump())
        assertEquals(distance, run.travel, 0f)
    }

    @Test
    fun collisionMatchesWebDemoAndResetRestoresStart() {
        val run = RaptorRun().apply { phase = RaptorPhase.RUNNING }
        repeat(180) { run.step(1f / 60f) }
        assertEquals(RaptorPhase.OVER, run.phase)
        assertEquals(33, run.score)
        assertEquals(2.233333f, run.time, 0.0001f)
        assertEquals(269.8146f, run.travel, 0.002f)
        assertFalse(run.jump())

        run.reset()
        assertEquals(RaptorPhase.READY, run.phase)
        assertEquals(0f, run.time, 0f)
        assertEquals(0f, run.travel, 0f)
        assertEquals(0, run.score)
        assertEquals(RAPTOR_GROUND, run.player.y + run.player.h, 0f)
        assertEquals(370f, run.obstacles.single().x, 0f)
        assertEquals("crystal", run.obstacles.single().kind)
    }

    @Test
    fun jumpClearsTheFirstObstacleAndRejectsAirborneInput() {
        val run = RaptorRun().apply { phase = RaptorPhase.RUNNING }
        repeat(120) { run.step(1f / 60f) }
        assertTrue(run.jump())
        assertFalse(run.jump())
        run.step(1f / 60f)
        assertTrue(run.player.y + run.player.h < RAPTOR_GROUND)
        assertFalse(run.jump())
        repeat(50) { run.step(1f / 60f) }
        assertEquals(RaptorPhase.RUNNING, run.phase)
        assertEquals(RAPTOR_GROUND, run.player.y + run.player.h, 0f)
        assertTrue(run.jump())
    }

    @Test
    fun aLongFrameCannotTeleportPastTheFirstObstacle() {
        val run = RaptorRun().apply { phase = RaptorPhase.RUNNING }
        repeat(133) { run.step(1f / 60f) }
        assertEquals(RaptorPhase.RUNNING, run.phase)
        val before = run.time
        run.step(10f)
        assertEquals(RaptorPhase.OVER, run.phase)
        assertEquals(1f / 30f, run.time - before, 0.0001f)
    }

    @Test
    fun successiveJumpsWorkAtTopSpeedAndOffscreenObstaclesAreRemoved() {
        val run = RaptorRun().apply { phase = RaptorPhase.RUNNING }
        val encounteredKinds = mutableSetOf<String>()
        var jumps = 0
        repeat(120 * 80) {
            val distanceToJump = 22f + minOf(224f, 118f + run.time * 2.5f) * 0.24f
            val next = run.obstacles.firstOrNull { it.x >= run.player.x }
            if (next != null && next.x - run.player.x <= distanceToJump && run.jump()) {
                encounteredKinds.add(next.kind)
                jumps++
            }
            run.step(1f / 120f)
            assertTrue(run.obstacles.size in 1..3)
        }
        assertEquals(RaptorPhase.RUNNING, run.phase)
        assertEquals(setOf("crystal", "spike"), encounteredKinds)
        assertEquals(80, jumps)
        assertEquals(1959, run.score)
    }
}

package com.iven.musicplayergo

import org.junit.Assert.assertEquals
import org.junit.Test

/** Deliberate red probe. This disposable branch must never be merged. */
class CoverageFailureProbeTest {
    @Test fun assertionFailureMustMakeCiRed() {
        assertEquals("Controlled assertion failure", 2, 1)
    }
}

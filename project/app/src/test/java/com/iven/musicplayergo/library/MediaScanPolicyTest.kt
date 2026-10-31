package com.iven.musicplayergo.library

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaScanPolicyTest {
    @Test
    fun candidateCases() {
        mediaScanCases().forEach { case ->
            assertEquals(case.name, case.expected,
                MediaScanPolicy.isAudioCandidate(case.path, case.filename, case.mime))
        }
    }
}

package com.iven.musicplayergo.raptor

import com.iven.musicplayergo.analysis.RaptorSceneWindow
import com.iven.musicplayergo.analysis.RaptorTrackAnalysis
import org.junit.Assert.assertEquals
import org.junit.Test

class RaptorTimelineTest {
    private val analysis = RaptorTrackAnalysis(
        durationSeconds = 24.0,
        reachedDurationLimit = false,
        bpm = 120,
        beats = listOf(0.5, 1.0, 1.5, 8.5, 16.5),
        scenes = listOf(
            RaptorSceneWindow(0.0, "drift", 0.84),
            RaptorSceneWindow(8.0, "surge", 0.72),
            RaptorSceneWindow(16.0, "groove", 0.44)
        )
    )

    @Test
    fun scenesSwitchOnTheNextDetectedBeatAndPulseDecays() {
        val timeline = RaptorTimeline(analysis)
        assertEquals(RaptorSignal("drift", 0f), timeline.signalAt(0.0))
        assertEquals(RaptorSignal("drift", 1f), timeline.signalAt(0.5))
        assertEquals(0.31947f, timeline.signalAt(0.6).pulse, 0.00001f)
        assertEquals(0f, timeline.signalAt(0.74).pulse, 0f)
        assertEquals("drift", timeline.signalAt(8.0).theme)
        assertEquals(RaptorSignal("surge", 1f), timeline.signalAt(8.5))
        assertEquals("surge", timeline.signalAt(16.0).theme)
        assertEquals(RaptorSignal("neutral", 1f), timeline.signalAt(16.5))
    }

    @Test
    fun beatlessAudioSwitchesOnWindowBoundariesWithoutPulsing() {
        val timeline = RaptorTimeline(analysis.copy(beats = emptyList(), bpm = null))
        assertEquals(RaptorSignal("drift", 0f), timeline.signalAt(7.99))
        assertEquals(RaptorSignal("surge", 0f), timeline.signalAt(8.0))
        assertEquals(RaptorSignal(), timeline.signalAt(16.0))
    }

    @Test
    fun aLoopRestartsTheBeatGridAndSceneSelection() {
        val timeline = RaptorTimeline(analysis)
        assertEquals(RaptorSignal(), timeline.signalAt(23.99))
        assertEquals(timeline.signalAt(0.0), timeline.signalAt(24.0))
        assertEquals(timeline.signalAt(0.5), timeline.signalAt(24.5))
        assertEquals(timeline.signalAt(8.5), timeline.signalAt(56.5))
    }

    @Test
    fun missingOrUnrecognizedAnalysisFallsBackToNeutral() {
        val unknown = RaptorTimeline(analysis.copy(scenes = listOf(RaptorSceneWindow(0.0, "other", 1.0))))
        assertEquals("neutral", unknown.signalAt(0.0).theme)
        assertEquals("neutral", RaptorTimeline(analysis.copy(scenes = emptyList())).signalAt(1.0).theme)
        assertEquals(RaptorSignal(), RaptorTimeline(analysis.copy(durationSeconds = 0.0)).signalAt(4.0))

        val timeline = RaptorTimeline(analysis)
        assertEquals(RaptorSignal(), timeline.signalAt(Double.NaN))
        assertEquals(RaptorSignal(), timeline.signalAt(Double.POSITIVE_INFINITY))
        assertEquals(RaptorSignal(), timeline.signalAt(-0.1))
    }
}

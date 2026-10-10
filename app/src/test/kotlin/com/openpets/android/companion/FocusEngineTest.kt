package com.openpets.android.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusEngineTest {
    private var now = 1_000L
    private val engine = FocusEngine { now }

    @Test fun startEntersFocus() {
        val snapshot = engine.start(10_000L, 5_000L)
        assertEquals(FocusState.FOCUS, snapshot.state)
        assertEquals(10_000L, snapshot.phaseTotalMs)
        assertEquals(0, snapshot.cyclesCompleted)
    }

    @Test fun snapshotAutoAdvancesPhasesAndCountsCycles() {
        engine.start(1_000L, 500L)
        now += 1_000L
        assertEquals(FocusState.BREAK, engine.snapshot().state)
        assertEquals(1, engine.snapshot().cyclesCompleted)
        now += 500L
        val next = engine.snapshot()
        assertEquals(FocusState.FOCUS, next.state)
        assertEquals(1, next.cyclesCompleted)
    }

    @Test fun pauseFreezesAndResumeContinues() {
        engine.start(10_000L, 2_000L)
        now += 2_000L
        val paused = engine.pause()
        assertEquals(FocusState.PAUSED, paused.state)
        assertEquals(FocusPhase.FOCUS, paused.pausedPhase)
        now += 5_000L
        assertEquals(8_000L, engine.snapshot().phaseRemainingMs)
        engine.resume()
        now += 3_000L
        assertEquals(5_000L, engine.snapshot().phaseRemainingMs)
    }

    @Test fun skipTogglesPhase() {
        engine.start(10_000L, 2_000L)
        assertEquals(FocusState.BREAK, engine.skip().state)
        assertEquals(FocusState.FOCUS, engine.skip().state)
    }

    @Test fun endResetsState() {
        engine.start(10_000L, 2_000L)
        val ended = engine.end()
        assertEquals(FocusState.IDLE, ended.state)
        assertEquals(0L, ended.phaseRemainingMs)
        assertEquals(0, ended.cyclesCompleted)
    }

    @Test fun remainingClampsAtZero() {
        engine.start(1_000L, 500L)
        assertEquals(0L, engine.remainingAt(now + 2_000L))
    }
}

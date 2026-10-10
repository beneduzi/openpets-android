package com.openpets.android.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BreathingEngineTest {
    private var now = 0L
    private val engine = BreathingEngine { now }

    @Test fun phasesProgressAcrossFullCycleAndCountCycles() {
        engine.start(BreathingPattern.BOX, 60_000L)
        assertEquals(BreathPhase.INHALE, engine.snapshot().phase)
        now = 4_000L
        assertEquals(BreathPhase.HOLD_IN, engine.snapshot().phase)
        now = 8_000L
        assertEquals(BreathPhase.EXHALE, engine.snapshot().phase)
        now = 12_000L
        assertEquals(BreathPhase.HOLD_OUT, engine.snapshot().phase)
        now = 16_000L
        assertEquals(BreathPhase.INHALE, engine.snapshot().phase)
        assertEquals(1, engine.snapshot().cyclesCompleted)
    }

    @Test fun sessionEndsAsDoneAndInactive() {
        engine.start(BreathingPattern.BOX, 5_000L)
        now = 5_000L
        val snapshot = engine.snapshot()
        assertFalse(snapshot.active)
        assertEquals(BreathPhase.DONE, snapshot.phase)
    }

    @Test fun zeroLengthHoldOutIsSkipped() {
        engine.start(BreathingPattern.RELAX_478, 60_000L)
        now = (4 + 7 + 8) * 1_000L
        val snapshot = engine.snapshot()
        assertTrue(snapshot.active)
        assertEquals(BreathPhase.INHALE, snapshot.phase)
        assertEquals(1, snapshot.cyclesCompleted)
    }
}

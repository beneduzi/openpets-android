package com.openpets.android.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerEngineTest {
    private var now = 1_000L
    private val engine = TimerEngine { now }

    @Test fun startAndRemainingCountdown() {
        engine.start(10_000L, "Tea")
        assertEquals(10_000L, engine.snapshot().remainingMs)
        now += 3_000L
        assertEquals(7_000L, engine.snapshot().remainingMs)
    }

    @Test fun pauseFreezesRemaining() {
        engine.start(10_000L)
        now += 2_000L
        engine.pause()
        now += 5_000L
        assertEquals(8_000L, engine.snapshot().remainingMs)
        assertEquals(TimerRunState.PAUSED, engine.snapshot().state)
    }

    @Test fun resumeContinuesCountdown() {
        engine.start(10_000L)
        now += 2_000L
        engine.pause()
        now += 5_000L
        engine.resume()
        now += 3_000L
        assertEquals(5_000L, engine.snapshot().remainingMs)
    }

    @Test fun addMinutesExtendsTotalAndRemaining() {
        engine.start(120_000L)
        now += 20_000L
        val snapshot = engine.addMinutes(1)
        assertEquals(180_000L, snapshot.totalMs)
        assertEquals(160_000L, snapshot.remainingMs)
    }

    @Test fun cancelResetsToIdle() {
        engine.start(10_000L, "x")
        val snapshot = engine.cancel()
        assertEquals(TimerRunState.IDLE, snapshot.state)
        assertEquals(0L, snapshot.totalMs)
        assertEquals(null, snapshot.label)
    }

    @Test fun expiryTransitionsToFinished() {
        engine.start(1_000L)
        now += 1_000L
        val snapshot = engine.snapshot()
        assertEquals(TimerRunState.FINISHED, snapshot.state)
        assertEquals(0L, snapshot.remainingMs)
    }

    @Test fun formatsRemaining() {
        assertEquals("9:05", TimerEngine.formatRemaining(545_000L))
        assertEquals("1:01:01", TimerEngine.formatRemaining(3_661_000L))
    }

    @Test fun finishedAnnouncementIsClaimedOnlyOnceAcrossRefreshes() {
        val gate = TimerFinishAnnouncementGate()
        engine.start(1_000L)
        now += 1_000L
        val finished = engine.snapshot()
        var announcements = 0
        repeat(5) {
            if (gate.shouldAnnounce(finished.state)) announcements++
        }
        assertEquals(1, announcements)
    }
}

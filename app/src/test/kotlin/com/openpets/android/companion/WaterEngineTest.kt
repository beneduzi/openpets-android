package com.openpets.android.companion

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WaterEngineTest {
    private val zone = TimeZone.getDefault()
    private val engine = WaterEngine(timeZone = zone)

    private fun at(hour: Int, minute: Int = 0, day: Int = 10): Calendar = Calendar.getInstance(zone).apply {
        set(2025, Calendar.JANUARY, day, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }

    @Test fun disabledReturnsNull() {
        assertNull(engine.nextFire(at(10).timeInMillis, WaterSettings(enabled = false)))
    }

    @Test fun advancesByWholeIntervalsWhenOverdue() {
        val lastFired = at(8).timeInMillis
        val now = at(11, 30).timeInMillis
        val result = engine.nextFire(now, WaterSettings(enabled = true, intervalMinutes = 60, fromHour = 7, toHour = 20, lastFiredAtMs = lastFired))
        assertEquals(at(12).timeInMillis, result)
    }

    @Test fun rollsOutsideWindowToNextWindowStart() {
        val lastFired = at(20).timeInMillis
        val now = at(20, 30).timeInMillis
        val expected = at(9, day = 11).timeInMillis
        val result = engine.nextFire(now, WaterSettings(enabled = true, intervalMinutes = 60, fromHour = 9, toHour = 21, lastFiredAtMs = lastFired))
        assertEquals(expected, result)
    }

    @Test fun clampValidatesRangesAndDistinctHours() {
        val clamped = engine.clamp(WaterSettings(enabled = true, intervalMinutes = 1, fromHour = 9, toHour = 9))
        assertEquals(15, clamped.intervalMinutes)
        assertEquals(10, clamped.toHour)
        val bounds = engine.clamp(WaterSettings(enabled = true, intervalMinutes = 999, fromHour = -4, toHour = 99))
        assertEquals(240, bounds.intervalMinutes)
        assertEquals(0, bounds.fromHour)
        assertEquals(23, bounds.toHour)
    }
}

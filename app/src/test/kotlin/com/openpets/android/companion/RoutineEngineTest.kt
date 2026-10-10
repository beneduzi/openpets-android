package com.openpets.android.companion

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineEngineTest {
    private val zone = TimeZone.getDefault()
    private val engine = RoutineEngine(timeZone = zone)

    private fun at(hour: Int, minute: Int = 0, day: Int = 10): Calendar = Calendar.getInstance(zone).apply {
        set(2025, Calendar.JANUARY, day, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }

    @Test fun selectsNextEnabledSlotAfterNow() {
        val settings = RoutineSettings(morningHour = 9, morningMinute = 0, eveningHour = 20, eveningMinute = 0)
        assertEquals(at(9).timeInMillis, engine.nextFire(at(8).timeInMillis, settings))
    }

    @Test fun skipsDisabledSlot() {
        val settings = RoutineSettings(morningEnabled = false, eveningEnabled = true, eveningHour = 20, eveningMinute = 0)
        assertEquals(at(20).timeInMillis, engine.nextFire(at(8).timeInMillis, settings))
    }

    @Test fun rollsToTomorrowAfterLastSlot() {
        val settings = RoutineSettings(morningHour = 9, morningMinute = 0, eveningHour = 20, eveningMinute = 0)
        assertEquals(at(9, day = 11).timeInMillis, engine.nextFire(at(21).timeInMillis, settings))
    }

    @Test fun clampsHoursAndMinutes() {
        val clamped = engine.clamp(RoutineSettings(morningHour = 25, morningMinute = -2, eveningHour = -1, eveningMinute = 90))
        assertEquals(23, clamped.morningHour)
        assertEquals(0, clamped.morningMinute)
        assertEquals(0, clamped.eveningHour)
        assertEquals(59, clamped.eveningMinute)
    }
}

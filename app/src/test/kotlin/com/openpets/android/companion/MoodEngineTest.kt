package com.openpets.android.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodEngineTest {
    private var now = 10L * DAY_MS
    private val engine = MoodEngine { now }

    @Test fun validatesValueAndNormalizesNote() {
        val entry = engine.add(4, "  steady day  ")
        assertEquals("steady day", entry.note)
        assertTrue(runCatching { engine.add(0, null) }.isFailure)
        assertTrue(runCatching { engine.add(6, "note") }.isFailure)
        assertNull(engine.add(3, "   ").note)
        val longNote = "x".repeat(150)
        assertEquals(120, engine.add(2, longNote).note?.length)
    }

    @Test fun recentIsNewestFirstAndLatestIsNewestEntry() {
        val first = engine.add(2, null)
        now += 1_000L
        val second = engine.add(5, "new")
        assertEquals(listOf(second, first), engine.recent())
        assertEquals(second, engine.latest())
    }

    @Test fun averageIncludesOnlyEntriesInRequestedDays() {
        engine.add(2, null)
        now += 2 * DAY_MS
        engine.add(4, null)
        now += 8 * DAY_MS
        engine.add(5, null)
        assertEquals(5.0, engine.average(1)!!, 0.001)
        assertEquals(11.0 / 3.0, engine.average(10)!!, 0.001)
        assertEquals(4.5, engine.average(9)!!, 0.001)
        assertNull(MoodEngine { now }.average(2))
    }

    companion object { private const val DAY_MS = 24L * 60 * 60 * 1000 }
}

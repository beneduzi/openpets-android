package com.openpets.android.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarFormatTest {
    @Test fun relativeTimeFormatsUpcomingAndStartedEvents() {
        assertEquals("in 12 min", CalendarFormat.relativeTime(12 * 60_000L, 0L))
        assertEquals("now", CalendarFormat.relativeTime(30_000L, 0L))
        assertEquals("started", CalendarFormat.relativeTime(-61_000L, 0L))
    }
}

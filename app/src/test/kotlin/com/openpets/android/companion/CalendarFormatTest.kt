package com.openpets.android.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarFormatTest {
    @Test fun relativeTimeFormatsUpcomingAndStartedEvents() {
        assertEquals(RelativeTime.InMinutes(12), CalendarFormat.relativeTime(12 * 60_000L, 0L))
        assertEquals(RelativeTime.Now, CalendarFormat.relativeTime(30_000L, 0L))
        assertEquals(RelativeTime.Started, CalendarFormat.relativeTime(-61_000L, 0L))
    }
}

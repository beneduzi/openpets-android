package com.openpets.android.companion

data class DeviceEvent(val id: Long, val title: String, val beginMs: Long, val endMs: Long, val allDay: Boolean, val calendarName: String?)

object CalendarFormat {
    fun relativeTime(beginMs: Long, now: Long): String {
        val delta = beginMs - now
        return when { delta <= -60_000L -> "started"; delta <= 60_000L -> "now"; else -> "in ${((delta + 59_999L) / 60_000L)} min" }
    }
}

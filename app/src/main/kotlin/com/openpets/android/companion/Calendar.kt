package com.openpets.android.companion

data class DeviceEvent(val id: Long, val title: String, val beginMs: Long, val endMs: Long, val allDay: Boolean, val calendarName: String?)

sealed interface RelativeTime {
    data object Started : RelativeTime
    data object Now : RelativeTime
    data class InMinutes(val minutes: Long) : RelativeTime
}

object CalendarFormat {
    /** Pure (JVM-testable): returns the structured relative time; call [render] for localized text. */
    fun relativeTime(beginMs: Long, now: Long): RelativeTime {
        val delta = beginMs - now
        return when {
            delta <= -60_000L -> RelativeTime.Started
            delta <= 60_000L -> RelativeTime.Now
            else -> RelativeTime.InMinutes((delta + 59_999L) / 60_000L)
        }
    }
    fun render(context: android.content.Context, relative: RelativeTime): String = when (relative) {
        RelativeTime.Started -> context.getString(com.openpets.android.R.string.settings_calendar_started)
        RelativeTime.Now -> context.getString(com.openpets.android.R.string.settings_calendar_now)
        is RelativeTime.InMinutes -> context.getString(com.openpets.android.R.string.settings_calendar_in_min, relative.minutes.toInt())
    }
}

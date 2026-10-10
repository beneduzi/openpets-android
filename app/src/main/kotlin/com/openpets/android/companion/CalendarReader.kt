package com.openpets.android.companion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

/** Read-only reader for events in the device's local calendars. */
class CalendarReader(private val context: Context) {
    fun upcoming(now: Long = System.currentTimeMillis()): List<DeviceEvent> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val end = now + 24 * 60 * 60 * 1000L
        val projection = arrayOf(CalendarContract.Events._ID, CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND, CalendarContract.Events.ALL_DAY, CalendarContract.Events.CALENDAR_ID)
        val events = mutableListOf<DeviceEvent>()
        try {
            context.contentResolver.query(CalendarContract.Events.CONTENT_URI, projection,
                "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ? AND ${CalendarContract.Events.DELETED} = 0",
                arrayOf(now.toString(), end.toString()), "${CalendarContract.Events.DTSTART} ASC")?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val title = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val begin = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val finish = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
                val allDay = cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
                val calendarId = cursor.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID)
                while (cursor.moveToNext()) {
                    val name = context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME), "${CalendarContract.Calendars._ID} = ?", arrayOf(cursor.getLong(calendarId).toString()), null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    events += DeviceEvent(cursor.getLong(id), cursor.getString(title).orEmpty(), cursor.getLong(begin), cursor.getLong(finish), cursor.getInt(allDay) != 0, name)
                }
            }
        } catch (_: SecurityException) { return emptyList() }
        return events.sortedBy { it.beginMs }
    }
}

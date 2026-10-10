package com.openpets.android.companion

import android.content.Context

data class StoredRoutine(val settings: RoutineSettings, val nextAlarmAtMs: Long)

class RoutineStore(context: Context) {
    private val preferences = context.getSharedPreferences("openpets_routine", Context.MODE_PRIVATE)

    fun load(): StoredRoutine = StoredRoutine(
        RoutineSettings(
            morningEnabled = preferences.getBoolean(KEY_MORNING_ENABLED, true),
            morningHour = preferences.getInt(KEY_MORNING_HOUR, 9),
            morningMinute = preferences.getInt(KEY_MORNING_MINUTE, 0),
            eveningEnabled = preferences.getBoolean(KEY_EVENING_ENABLED, true),
            eveningHour = preferences.getInt(KEY_EVENING_HOUR, 20),
            eveningMinute = preferences.getInt(KEY_EVENING_MINUTE, 0),
            lastFiredAtMs = preferences.getLong(KEY_LAST_FIRED, 0L),
        ),
        preferences.getLong(KEY_NEXT_ALARM, 0L),
    )

    fun save(settings: RoutineSettings, nextAlarmAtMs: Long) {
        preferences.edit()
            .putBoolean(KEY_MORNING_ENABLED, settings.morningEnabled)
            .putInt(KEY_MORNING_HOUR, settings.morningHour)
            .putInt(KEY_MORNING_MINUTE, settings.morningMinute)
            .putBoolean(KEY_EVENING_ENABLED, settings.eveningEnabled)
            .putInt(KEY_EVENING_HOUR, settings.eveningHour)
            .putInt(KEY_EVENING_MINUTE, settings.eveningMinute)
            .putLong(KEY_LAST_FIRED, settings.lastFiredAtMs)
            .putLong(KEY_NEXT_ALARM, nextAlarmAtMs)
            .apply()
    }

    companion object {
        private const val KEY_MORNING_ENABLED = "morningEnabled"
        private const val KEY_MORNING_HOUR = "morningHour"
        private const val KEY_MORNING_MINUTE = "morningMinute"
        private const val KEY_EVENING_ENABLED = "eveningEnabled"
        private const val KEY_EVENING_HOUR = "eveningHour"
        private const val KEY_EVENING_MINUTE = "eveningMinute"
        private const val KEY_LAST_FIRED = "lastFiredAtMs"
        private const val KEY_NEXT_ALARM = "nextAlarmAtMs"
    }
}

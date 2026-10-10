package com.openpets.android.companion

import android.content.Context

data class StoredWater(val settings: WaterSettings, val nextAlarmAtMs: Long)

class WaterStore(context: Context) {
    private val preferences = context.getSharedPreferences("openpets_water", Context.MODE_PRIVATE)

    fun load(): StoredWater = StoredWater(
        WaterSettings(
            enabled = preferences.getBoolean(KEY_ENABLED, false),
            intervalMinutes = preferences.getInt(KEY_INTERVAL, 60),
            fromHour = preferences.getInt(KEY_FROM_HOUR, 9),
            toHour = preferences.getInt(KEY_TO_HOUR, 21),
            lastFiredAtMs = preferences.getLong(KEY_LAST_FIRED, 0L),
        ),
        preferences.getLong(KEY_NEXT_ALARM, 0L),
    )

    fun save(settings: WaterSettings, nextAlarmAtMs: Long) {
        preferences.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putInt(KEY_INTERVAL, settings.intervalMinutes)
            .putInt(KEY_FROM_HOUR, settings.fromHour)
            .putInt(KEY_TO_HOUR, settings.toHour)
            .putLong(KEY_LAST_FIRED, settings.lastFiredAtMs)
            .putLong(KEY_NEXT_ALARM, nextAlarmAtMs)
            .apply()
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_INTERVAL = "intervalMinutes"
        private const val KEY_FROM_HOUR = "fromHour"
        private const val KEY_TO_HOUR = "toHour"
        private const val KEY_LAST_FIRED = "lastFiredAtMs"
        private const val KEY_NEXT_ALARM = "nextAlarmAtMs"
    }
}

package com.openpets.android.companion

import android.content.Context
data class StoredTimer(
    val snapshot: TimerSnapshot,
    val updatedAtMs: Long,
    val finishAtMs: Long,
    val alarmTriggerMs: Long,
    val finishedAnnounced: Boolean,
)

class TimerStore(context: Context) {
    private val preferences = context.getSharedPreferences("openpets_timer", Context.MODE_PRIVATE)

    fun load(): StoredTimer {
        val state = runCatching { TimerRunState.valueOf(preferences.getString(KEY_STATE, TimerRunState.IDLE.name)!!) }
            .getOrDefault(TimerRunState.IDLE)
        val finishAt = preferences.getLong(KEY_FINISH_AT, 0L)
        return StoredTimer(
            TimerSnapshot(
                state,
                preferences.getLong(KEY_TOTAL, 0L),
                preferences.getLong(KEY_REMAINING, 0L),
                preferences.getString(KEY_LABEL, null),
            ),
            preferences.getLong(KEY_UPDATED, 0L),
            finishAt,
            preferences.getLong(KEY_ALARM, finishAt),
            preferences.getBoolean(KEY_FINISHED_ANNOUNCED, false),
        )
    }

    fun save(snapshot: TimerSnapshot, updatedAtMs: Long, finishAtMs: Long, alarmTriggerMs: Long = finishAtMs, finishedAnnounced: Boolean = false) {
        preferences.edit()
            .putString(KEY_STATE, snapshot.state.name)
            .putLong(KEY_TOTAL, snapshot.totalMs)
            .putLong(KEY_REMAINING, snapshot.remainingMs)
            .putString(KEY_LABEL, snapshot.label)
            .putLong(KEY_UPDATED, updatedAtMs)
            .putLong(KEY_FINISH_AT, finishAtMs)
            .putLong(KEY_ALARM, alarmTriggerMs)
            .putBoolean(KEY_FINISHED_ANNOUNCED, finishedAnnounced)
            .apply()
    }

    companion object {
        private const val KEY_STATE = "state"
        private const val KEY_TOTAL = "totalMs"
        private const val KEY_REMAINING = "remainingMs"
        private const val KEY_LABEL = "label"
        private const val KEY_UPDATED = "updatedAtMs"
        private const val KEY_FINISH_AT = "finishAtMs"
        private const val KEY_ALARM = "alarmTriggerMs"
        private const val KEY_FINISHED_ANNOUNCED = "finishedAnnounced"
    }
}

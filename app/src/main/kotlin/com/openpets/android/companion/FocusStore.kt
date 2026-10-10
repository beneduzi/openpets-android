package com.openpets.android.companion

import android.content.Context

data class StoredFocus(
    val snapshot: FocusSnapshot,
    val focusMs: Long,
    val breakMs: Long,
    val finishAtMs: Long,
)

class FocusStore(context: Context) {
    private val preferences = context.getSharedPreferences("openpets_focus", Context.MODE_PRIVATE)

    fun load(): StoredFocus {
        val state = runCatching { FocusState.valueOf(preferences.getString(KEY_STATE, FocusState.IDLE.name)!!) }
            .getOrDefault(FocusState.IDLE)
        val pausedPhase = preferences.getString(KEY_PAUSED_PHASE, null)?.let { value ->
            runCatching { FocusPhase.valueOf(value) }.getOrNull()
        }
        return StoredFocus(
            FocusSnapshot(
                state,
                pausedPhase,
                preferences.getLong(KEY_REMAINING, 0L),
                preferences.getLong(KEY_TOTAL, 0L),
                preferences.getInt(KEY_CYCLES, 0),
            ),
            preferences.getLong(KEY_FOCUS_MS, 0L),
            preferences.getLong(KEY_BREAK_MS, 0L),
            preferences.getLong(KEY_FINISH_AT, 0L),
        )
    }

    fun save(snapshot: FocusSnapshot, focusMs: Long, breakMs: Long, finishAtMs: Long) {
        preferences.edit()
            .putString(KEY_STATE, snapshot.state.name)
            .putString(KEY_PAUSED_PHASE, snapshot.pausedPhase?.name)
            .putLong(KEY_REMAINING, snapshot.phaseRemainingMs)
            .putLong(KEY_TOTAL, snapshot.phaseTotalMs)
            .putInt(KEY_CYCLES, snapshot.cyclesCompleted)
            .putLong(KEY_FOCUS_MS, focusMs)
            .putLong(KEY_BREAK_MS, breakMs)
            .putLong(KEY_FINISH_AT, finishAtMs)
            .apply()
    }

    companion object {
        private const val KEY_STATE = "state"
        private const val KEY_PAUSED_PHASE = "pausedPhase"
        private const val KEY_REMAINING = "phaseRemainingMs"
        private const val KEY_TOTAL = "phaseTotalMs"
        private const val KEY_CYCLES = "cyclesCompleted"
        private const val KEY_FOCUS_MS = "focusMs"
        private const val KEY_BREAK_MS = "breakMs"
        private const val KEY_FINISH_AT = "finishAtMs"
    }
}

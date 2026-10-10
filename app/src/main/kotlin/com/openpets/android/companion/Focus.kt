package com.openpets.android.companion

enum class FocusPhase { FOCUS, BREAK }

enum class FocusState { IDLE, FOCUS, BREAK, PAUSED }

data class FocusSnapshot(
    val state: FocusState,
    val pausedPhase: FocusPhase?,
    val phaseRemainingMs: Long,
    val phaseTotalMs: Long,
    val cyclesCompleted: Int,
)

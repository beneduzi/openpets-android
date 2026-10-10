package com.openpets.android.companion

enum class RoutineSlot { MORNING, EVENING }

data class RoutineSettings(
    val morningEnabled: Boolean = true,
    val morningHour: Int = 9,
    val morningMinute: Int = 0,
    val eveningEnabled: Boolean = true,
    val eveningHour: Int = 20,
    val eveningMinute: Int = 0,
    val lastFiredAtMs: Long = 0L,
)

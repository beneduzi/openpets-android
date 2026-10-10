package com.openpets.android.companion

data class WaterSettings(
    val enabled: Boolean = false,
    val intervalMinutes: Int = 60,
    val fromHour: Int = 9,
    val toHour: Int = 21,
    val lastFiredAtMs: Long = 0L,
)

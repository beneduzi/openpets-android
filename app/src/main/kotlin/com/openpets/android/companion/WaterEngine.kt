package com.openpets.android.companion

import java.util.Calendar

/** Pure calculation and validation for recurring water reminders. */
class WaterEngine(
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeZone: java.util.TimeZone = java.util.TimeZone.getDefault(),
) {
    fun nextFire(now: Long, settings: WaterSettings): Long? {
        val valid = clamp(settings)
        if (!valid.enabled) return null
        val intervalMs = valid.intervalMinutes * 60_000L
        var candidate = if (valid.lastFiredAtMs <= 0L) now else valid.lastFiredAtMs + intervalMs
        while (candidate <= now) candidate += intervalMs
        if (isInWindow(candidate, valid)) return candidate

        val dayStart = Calendar.getInstance(timeZone).apply {
            timeInMillis = candidate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (hour(candidate) >= valid.toHour) dayStart.add(Calendar.DAY_OF_YEAR, 1)
        dayStart.set(Calendar.HOUR_OF_DAY, valid.fromHour)
        return dayStart.timeInMillis
    }

    fun onFired(now: Long, settings: WaterSettings): WaterSettings = clamp(settings).copy(lastFiredAtMs = now)

    fun clamp(settings: WaterSettings): WaterSettings = settings.copy(
        intervalMinutes = settings.intervalMinutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES),
        fromHour = settings.fromHour.coerceIn(0, 23),
        toHour = settings.toHour.coerceIn(0, 23).let { to ->
            if (to == settings.fromHour.coerceIn(0, 23)) (to + 1) % 24 else to
        },
    )

    private fun isInWindow(timestamp: Long, settings: WaterSettings): Boolean {
        val hour = hour(timestamp)
        return if (settings.fromHour < settings.toHour) hour >= settings.fromHour && hour < settings.toHour
        else hour >= settings.fromHour || hour < settings.toHour
    }

    private fun hour(timestamp: Long): Int = Calendar.getInstance(timeZone).apply { timeInMillis = timestamp }
        .get(Calendar.HOUR_OF_DAY)

    companion object {
        const val MIN_INTERVAL_MINUTES = 15
        const val MAX_INTERVAL_MINUTES = 240
    }
}

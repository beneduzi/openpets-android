package com.openpets.android.companion

import java.util.Calendar
import java.util.TimeZone

/** Pure daily slot calculation using local wall-clock time. */
class RoutineEngine(
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeZone: TimeZone = TimeZone.getDefault(),
) {
    fun nextFire(now: Long, settings: RoutineSettings): Long? {
        val valid = clamp(settings)
        val candidates = buildList {
            repeat(2) { dayOffset ->
                if (valid.morningEnabled) add(slotTime(now, valid.morningHour, valid.morningMinute, dayOffset))
                if (valid.eveningEnabled) add(slotTime(now, valid.eveningHour, valid.eveningMinute, dayOffset))
            }
        }
        return candidates.filter { it > now && it > valid.lastFiredAtMs }.minOrNull()
    }

    fun slotFor(timestampMs: Long, settings: RoutineSettings): RoutineSlot? {
        val valid = clamp(settings)
        val toleranceMs = SLOT_MATCH_TOLERANCE_MS
        val matches = mutableListOf<Pair<Long, RoutineSlot>>()
        if (valid.morningEnabled) {
            val scheduled = sameDaySlot(timestampMs, valid.morningHour, valid.morningMinute)
            val distance = kotlin.math.abs(timestampMs - scheduled)
            if (distance <= toleranceMs) matches += distance to RoutineSlot.MORNING
        }
        if (valid.eveningEnabled) {
            val scheduled = sameDaySlot(timestampMs, valid.eveningHour, valid.eveningMinute)
            val distance = kotlin.math.abs(timestampMs - scheduled)
            if (distance <= toleranceMs) matches += distance to RoutineSlot.EVENING
        }
        return matches.minByOrNull { it.first }?.second
    }

    fun clamp(settings: RoutineSettings): RoutineSettings = settings.copy(
        morningHour = settings.morningHour.coerceIn(0, 23),
        morningMinute = settings.morningMinute.coerceIn(0, 59),
        eveningHour = settings.eveningHour.coerceIn(0, 23),
        eveningMinute = settings.eveningMinute.coerceIn(0, 59),
    )

    private fun slotTime(reference: Long, hour: Int, minute: Int, dayOffset: Int): Long = Calendar.getInstance(timeZone).apply {
        timeInMillis = reference
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, dayOffset)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }.timeInMillis

    private fun sameDaySlot(timestamp: Long, hour: Int, minute: Int): Long = Calendar.getInstance(timeZone).apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object { private const val SLOT_MATCH_TOLERANCE_MS = 5 * 60_000L }
}

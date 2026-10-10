package com.openpets.android.companion

import kotlin.math.ceil

/** Pure elapsed-time breathing session engine. */
class BreathingEngine(private val clock: () -> Long = System::currentTimeMillis) {
    private var pattern: BreathingPattern? = null
    private var startedAtMs = 0L
    private var finishAtMs = 0L
    private var stopped = false

    fun start(pattern: BreathingPattern, durationMs: Long = DEFAULT_DURATION_MS) {
        require(durationMs > 0L)
        require(listOf(pattern.inhaleSec, pattern.holdInSec, pattern.exhaleSec, pattern.holdOutSec).all { it >= 0 })
        require(pattern.inhaleSec + pattern.holdInSec + pattern.exhaleSec + pattern.holdOutSec > 0)
        this.pattern = pattern
        startedAtMs = clock()
        finishAtMs = startedAtMs + durationMs
        stopped = false
    }

    fun stop() { stopped = true }

    fun snapshot(): BreathSnapshot {
        val current = pattern
        if (current == null || stopped) return done(current)
        val now = clock()
        val remaining = (finishAtMs - now).coerceAtLeast(0L)
        if (remaining == 0L) return done(current)
        val phases = listOf(
            BreathPhase.INHALE to current.inhaleSec,
            BreathPhase.HOLD_IN to current.holdInSec,
            BreathPhase.EXHALE to current.exhaleSec,
            BreathPhase.HOLD_OUT to current.holdOutSec,
        ).filter { it.second > 0 }
        val cycleSec = phases.sumOf { it.second }
        val elapsedMs = (now - startedAtMs).coerceAtLeast(0L)
        val elapsedSec = elapsedMs / 1000L
        val cycles = (elapsedSec / cycleSec).toInt()
        var inCycleSec = (elapsedSec % cycleSec).toInt()
        val phase = phases.first { (_, seconds) ->
            if (inCycleSec < seconds) true else { inCycleSec -= seconds; false }
        }
        // Compute remaining using the position within the selected phase.
        val positionMs = elapsedMs % (cycleSec * 1000L)
        var phaseStartMs = 0L
        for ((phaseType, seconds) in phases) {
            val phaseEndMs = phaseStartMs + seconds * 1000L
            if (phaseType == phase.first) {
                val phaseRemaining = ceil((phaseEndMs - positionMs).coerceAtLeast(0L) / 1000.0).toInt()
                return BreathSnapshot(true, current.id, phase.first, phaseRemaining, cycles, remaining)
            }
            phaseStartMs = phaseEndMs
        }
        return done(current)
    }

    private fun done(current: BreathingPattern?) = BreathSnapshot(false, current?.id, BreathPhase.DONE, 0, 0, 0L)

    companion object { const val DEFAULT_DURATION_MS = 3 * 60_000L }
}

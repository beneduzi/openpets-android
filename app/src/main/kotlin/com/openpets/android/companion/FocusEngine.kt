package com.openpets.android.companion

/** Pure phase-transition logic for focus sessions. */
class FocusEngine(private val clock: () -> Long = System::currentTimeMillis) {
    private var state = FocusState.IDLE
    private var pausedPhase: FocusPhase? = null
    private var pausedRemainingMs = 0L
    private var phaseRemainingMs = 0L
    private var phaseTotalMs = 0L
    private var focusMs = 0L
    private var breakMs = 0L
    private var finishAt = 0L
    private var cyclesCompleted = 0

    fun start(focusMs: Long, breakMs: Long): FocusSnapshot {
        require(focusMs > 0 && breakMs > 0) { "Focus and break durations must be positive." }
        this.focusMs = focusMs
        this.breakMs = breakMs
        state = FocusState.FOCUS
        pausedPhase = null
        phaseTotalMs = focusMs
        phaseRemainingMs = focusMs
        finishAt = clock() + focusMs
        cyclesCompleted = 0
        return snapshot()
    }

    fun pause(): FocusSnapshot {
        if (state == FocusState.FOCUS || state == FocusState.BREAK) {
            pausedPhase = currentPhase()
            pausedRemainingMs = remainingAt(clock())
            phaseRemainingMs = pausedRemainingMs
            state = FocusState.PAUSED
            finishAt = 0L
        }
        return snapshot()
    }

    fun resume(): FocusSnapshot {
        if (state == FocusState.PAUSED) {
            state = if (pausedPhase == FocusPhase.BREAK) FocusState.BREAK else FocusState.FOCUS
            phaseRemainingMs = pausedRemainingMs
            finishAt = clock() + pausedRemainingMs
            pausedPhase = null
        }
        return snapshot()
    }

    fun skip(): FocusSnapshot {
        when (state) {
            FocusState.FOCUS -> enterBreak(clock(), countCycle = true)
            FocusState.BREAK -> enterFocus(clock())
            FocusState.IDLE, FocusState.PAUSED -> Unit
        }
        return snapshot()
    }

    fun end(): FocusSnapshot {
        state = FocusState.IDLE
        pausedPhase = null
        pausedRemainingMs = 0L
        phaseRemainingMs = 0L
        phaseTotalMs = 0L
        focusMs = 0L
        breakMs = 0L
        finishAt = 0L
        cyclesCompleted = 0
        return snapshot()
    }

    fun snapshot(): FocusSnapshot {
        var now = clock()
        while ((state == FocusState.FOCUS || state == FocusState.BREAK) && remainingAt(now) <= 0L) {
            if (state == FocusState.FOCUS) enterBreak(finishAt, countCycle = true) else enterFocus(finishAt)
            now = clock()
        }
        phaseRemainingMs = remainingAt(now)
        return FocusSnapshot(state, pausedPhase, phaseRemainingMs, phaseTotalMs, cyclesCompleted)
    }

    fun remainingAt(now: Long): Long = when (state) {
        FocusState.FOCUS, FocusState.BREAK -> (finishAt - now).coerceAtLeast(0L)
        FocusState.PAUSED -> pausedRemainingMs.coerceAtLeast(0L)
        FocusState.IDLE -> 0L
    }

    fun phaseTotalMs(): Long = phaseTotalMs
    fun finishAtMs(): Long = finishAt

    fun restore(snapshot: FocusSnapshot, finishAtMs: Long, focusMs: Long, breakMs: Long) {
        state = snapshot.state
        pausedPhase = snapshot.pausedPhase
        phaseRemainingMs = snapshot.phaseRemainingMs
        pausedRemainingMs = snapshot.phaseRemainingMs
        phaseTotalMs = snapshot.phaseTotalMs
        cyclesCompleted = snapshot.cyclesCompleted
        this.focusMs = focusMs
        this.breakMs = breakMs
        finishAt = finishAtMs
    }

    private fun currentPhase(): FocusPhase? = when (state) {
        FocusState.FOCUS -> FocusPhase.FOCUS
        FocusState.BREAK -> FocusPhase.BREAK
        else -> null
    }

    private fun enterBreak(at: Long, countCycle: Boolean) {
        if (countCycle) cyclesCompleted++
        state = FocusState.BREAK
        pausedPhase = null
        phaseTotalMs = breakMs
        phaseRemainingMs = breakMs
        finishAt = at + breakMs
    }

    private fun enterFocus(at: Long) {
        state = FocusState.FOCUS
        pausedPhase = null
        phaseTotalMs = focusMs
        phaseRemainingMs = focusMs
        finishAt = at + focusMs
    }
}

/** Tracks phase instances, including repeated phases in later completed cycles. */
class FocusPhaseAnnouncementGate(initialSnapshot: FocusSnapshot? = null) {
    private var lastKey: String? = initialSnapshot?.let(::phaseInstanceKey)

    fun shouldAnnounce(snapshot: FocusSnapshot): Boolean {
        val nextKey = phaseInstanceKey(snapshot) ?: return false
        if (nextKey == lastKey) return false
        lastKey = nextKey
        return true
    }

    fun reset() { lastKey = null }

    companion object {
        fun phaseInstanceKey(snapshot: FocusSnapshot): String? {
            val phase = when (snapshot.state) {
                FocusState.FOCUS -> FocusPhase.FOCUS
                FocusState.BREAK -> FocusPhase.BREAK
                FocusState.PAUSED -> snapshot.pausedPhase
                FocusState.IDLE -> null
            } ?: return null
            return "$phase:${snapshot.cyclesCompleted}"
        }
    }
}

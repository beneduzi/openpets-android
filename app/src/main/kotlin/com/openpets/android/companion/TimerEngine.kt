package com.openpets.android.companion

enum class TimerRunState { IDLE, RUNNING, PAUSED, FINISHED }

data class TimerSnapshot(
    val state: TimerRunState,
    val totalMs: Long,
    val remainingMs: Long,
    val label: String?,
)

/** Pure timer state machine. Persist its snapshot and finish timestamp through [TimerStore]. */
class TimerEngine(private val clock: () -> Long = System::currentTimeMillis) {
    private var state = TimerRunState.IDLE
    private var totalMs = 0L
    private var remainingMs = 0L
    private var label: String? = null
    private var finishAtMs = 0L

    fun start(totalMs: Long, label: String? = null): TimerSnapshot {
        require(totalMs > 0) { "Timer duration must be positive." }
        this.totalMs = totalMs
        this.remainingMs = totalMs
        this.label = label
        this.finishAtMs = clock() + totalMs
        this.state = TimerRunState.RUNNING
        return snapshot()
    }

    fun pause(): TimerSnapshot {
        if (state == TimerRunState.RUNNING) {
            remainingMs = remainingAt(clock())
            state = if (remainingMs <= 0) TimerRunState.FINISHED else TimerRunState.PAUSED
            finishAtMs = 0L
        }
        return snapshot()
    }

    fun resume(): TimerSnapshot {
        if (state == TimerRunState.PAUSED) {
            state = TimerRunState.RUNNING
            finishAtMs = clock() + remainingMs
        }
        return snapshot()
    }

    fun addMinutes(minutes: Int): TimerSnapshot {
        require(minutes > 0) { "Minutes must be positive." }
        val addedMs = minutes * 60_000L
        val currentRemaining = remainingAt(clock())
        if (state == TimerRunState.IDLE || state == TimerRunState.FINISHED) {
            totalMs = addedMs
            remainingMs = addedMs
            state = TimerRunState.RUNNING
            finishAtMs = clock() + addedMs
        } else {
            totalMs += addedMs
            remainingMs = currentRemaining + addedMs
            if (state == TimerRunState.RUNNING) finishAtMs = clock() + remainingMs
        }
        return snapshot()
    }

    fun cancel(): TimerSnapshot {
        state = TimerRunState.IDLE
        totalMs = 0L
        remainingMs = 0L
        label = null
        finishAtMs = 0L
        return snapshot()
    }

    fun dismissFinished(): TimerSnapshot = if (state == TimerRunState.FINISHED) cancel() else snapshot()

    fun snapshot(): TimerSnapshot {
        if (state == TimerRunState.RUNNING && remainingAt(clock()) == 0L) {
            state = TimerRunState.FINISHED
            remainingMs = 0L
        }
        return TimerSnapshot(state, totalMs, remainingAt(clock()), label)
    }

    fun remainingAt(now: Long): Long = when (state) {
        TimerRunState.RUNNING -> (finishAtMs - now).coerceAtLeast(0L)
        TimerRunState.PAUSED -> remainingMs.coerceAtLeast(0L)
        TimerRunState.IDLE, TimerRunState.FINISHED -> 0L
    }

    fun finishAtMs(): Long = finishAtMs

    fun restore(snapshot: TimerSnapshot, finishAtMs: Long) {
        state = snapshot.state
        totalMs = snapshot.totalMs
        remainingMs = snapshot.remainingMs
        label = snapshot.label
        this.finishAtMs = finishAtMs
    }

    companion object {
        fun formatRemaining(ms: Long): String {
            val totalSeconds = (ms.coerceAtLeast(0L) + 999L) / 1000L
            val seconds = totalSeconds % 60
            val minutes = totalSeconds / 60
            return if (totalSeconds >= 3600) {
                "%d:%02d:%02d".format(minutes / 60, minutes % 60, seconds)
            } else {
                "%d:%02d".format(minutes, seconds)
            }
        }
    }
}

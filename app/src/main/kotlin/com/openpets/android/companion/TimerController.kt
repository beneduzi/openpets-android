package com.openpets.android.companion

import android.content.Context
import com.openpets.android.PetBus
import com.openpets.android.protocol.HudItem
import com.openpets.android.protocol.SayRequest
import java.util.concurrent.CopyOnWriteArrayList

/**
 * UI-facing timer API. Construct with any Context; it uses application context and persists
 * state across instances/process restarts. Call [refresh] when the UI becomes visible, and
 * register listeners to receive snapshots after controller mutations.
 *
 * Signatures: snapshot(): TimerSnapshot; refresh(); start(durationMs: Long, label: String? = null);
 * pause(); resume(); addMinutes(minutes: Int); cancel(); dismissFinished();
 * addListener(l: (TimerSnapshot) -> Unit); removeListener(l: (TimerSnapshot) -> Unit); showOnPet().
 */
class TimerController(context: Context) {
    private val appContext = context.applicationContext
    private val store = TimerStore(appContext)
    private val scheduler = TimerScheduler(appContext)
    private val engine = TimerEngine()
    private val listeners = CopyOnWriteArrayList<(TimerSnapshot) -> Unit>()
    private var alarmTriggerMs = 0L

    init {
        val stored = store.load()
        alarmTriggerMs = stored.alarmTriggerMs
        engine.restore(stored.snapshot, stored.finishAtMs)
        if (stored.snapshot.state == TimerRunState.RUNNING && stored.alarmTriggerMs > 0) scheduler.schedule(stored.alarmTriggerMs)
    }

    @Synchronized fun snapshot(): TimerSnapshot = engine.snapshot()

    @Synchronized fun refresh() {
        val current = engine.snapshot()
        if (current.state == TimerRunState.FINISHED && alarmTriggerMs != 0L) {
            scheduler.cancel()
            alarmTriggerMs = 0L
            persist(current)
        }
        notifyListeners(current)
        if (current.state == TimerRunState.RUNNING) showOnPet()
    }

    @Synchronized fun start(durationMs: Long, label: String? = null) = mutate {
        scheduler.cancel()
        engine.start(durationMs, label)
        alarmTriggerMs = engine.finishAtMs()
        scheduler.schedule(alarmTriggerMs)
    }

    @Synchronized fun pause() = mutate {
        engine.pause()
        scheduler.cancel()
        alarmTriggerMs = 0L
    }

    @Synchronized fun resume() = mutate {
        engine.resume()
        if (engine.snapshot().state == TimerRunState.RUNNING) {
            alarmTriggerMs = engine.finishAtMs()
            scheduler.schedule(alarmTriggerMs)
        }
    }

    @Synchronized fun addMinutes(minutes: Int) = mutate {
        engine.addMinutes(minutes)
        if (engine.snapshot().state == TimerRunState.RUNNING) {
            alarmTriggerMs = engine.finishAtMs()
            scheduler.schedule(alarmTriggerMs)
        }
    }

    @Synchronized fun cancel() = mutate {
        scheduler.cancel()
        engine.cancel()
        alarmTriggerMs = 0L
    }

    @Synchronized fun dismissFinished() = mutate {
        if (engine.snapshot().state == TimerRunState.FINISHED) engine.dismissFinished()
        alarmTriggerMs = 0L
    }

    fun addListener(l: (TimerSnapshot) -> Unit) { listeners.addIfAbsent(l) }
    fun removeListener(l: (TimerSnapshot) -> Unit) { listeners.remove(l) }

    @Synchronized fun showOnPet() {
        val current = engine.snapshot()
        val value = current.remainingMs.toFloat().coerceAtMost(current.totalMs.toFloat().coerceAtLeast(1f))
        PetBus.sink.onSay(
            SayRequest(
                message = current.label ?: "Timer: ${TimerEngine.formatRemaining(current.remainingMs)}",
                reaction = null,
                source = "timer",
                hud = listOf(HudItem("Timer", value, current.totalMs.toFloat().coerceAtLeast(1f), "info", "timer")),
                bubbleId = "simple-timer",
                pinned = true,
            ),
        )
    }

    private inline fun mutate(change: () -> Unit) {
        change()
        val current = engine.snapshot()
        persist(current)
        notifyListeners(current)
        if (current.state == TimerRunState.RUNNING) showOnPet()
    }

    private fun persist(snapshot: TimerSnapshot) {
        store.save(snapshot, System.currentTimeMillis(), engine.finishAtMs(), alarmTriggerMs)
    }

    private fun notifyListeners(snapshot: TimerSnapshot) { listeners.forEach { it(snapshot) } }
}

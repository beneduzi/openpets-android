package com.openpets.android.companion

import android.content.Context
import com.openpets.android.PetBus
import com.openpets.android.protocol.HudItem
import com.openpets.android.protocol.SayRequest
import java.util.concurrent.CopyOnWriteArrayList

/**
 * UI API: snapshot(): FocusSnapshot; refresh(); start(focusMinutes: Int, breakMinutes: Int);
 * pause(); resume(); skip(); end(); addListener(l: (FocusSnapshot) -> Unit);
 * removeListener(l: (FocusSnapshot) -> Unit); showOnPet(). Construct with any Context;
 * session state is persisted across controller instances and process restarts.
 */
class FocusController(context: Context) {
    private val appContext = context.applicationContext
    private val store = FocusStore(appContext)
    private val scheduler = FocusScheduler(appContext)
    private val engine = FocusEngine()
    private val listeners = CopyOnWriteArrayList<(FocusSnapshot) -> Unit>()
    private var focusMs = 0L
    private var breakMs = 0L

    init {
        val stored = store.load()
        focusMs = stored.focusMs
        breakMs = stored.breakMs
        engine.restore(stored.snapshot, stored.finishAtMs, focusMs, breakMs)
        if (stored.snapshot.state == FocusState.FOCUS || stored.snapshot.state == FocusState.BREAK) {
            scheduler.schedule(stored.finishAtMs)
        }
    }

    @Synchronized fun snapshot(): FocusSnapshot = engine.snapshot()

    @Synchronized fun refresh() {
        val current = engine.snapshot()
        persistAndSchedule(current)
        notifyListeners(current)
    }

    @Synchronized fun start(focusMinutes: Int, breakMinutes: Int) {
        require(focusMinutes > 0 && breakMinutes > 0)
        focusMs = focusMinutes * 60_000L
        breakMs = breakMinutes * 60_000L
        engine.start(focusMs, breakMs)
        persistAndSchedule(engine.snapshot())
        notifyListeners(engine.snapshot())
    }

    @Synchronized fun pause() = mutate { engine.pause() }
    @Synchronized fun resume() = mutate { engine.resume() }
    @Synchronized fun skip() = mutate { engine.skip() }

    @Synchronized fun end() {
        scheduler.cancel()
        engine.end()
        focusMs = 0L
        breakMs = 0L
        persistAndSchedule(engine.snapshot())
        notifyListeners(engine.snapshot())
    }

    fun addListener(l: (FocusSnapshot) -> Unit) { listeners.addIfAbsent(l) }
    fun removeListener(l: (FocusSnapshot) -> Unit) { listeners.remove(l) }

    @Synchronized fun showOnPet() {
        val current = engine.snapshot()
        if (current.state != FocusState.FOCUS && current.state != FocusState.BREAK) return
        val title = if (current.state == FocusState.BREAK) "Break" else "Focus"
        PetBus.sink.onSay(
            SayRequest(
                message = if (current.state == FocusState.BREAK) "Break time" else "Focus session",
                reaction = "testing",
                source = "focus",
                hud = listOf(
                    HudItem(title, current.phaseRemainingMs.toFloat(), current.phaseTotalMs.toFloat().coerceAtLeast(1f), "info", "focus"),
                ),
                bubbleId = "focus-buddy",
                pinned = true,
            ),
        )
    }

    private inline fun mutate(change: () -> FocusSnapshot) {
        change()
        val current = engine.snapshot()
        persistAndSchedule(current)
        notifyListeners(current)
    }

    private fun persistAndSchedule(snapshot: FocusSnapshot) {
        val active = snapshot.state == FocusState.FOCUS || snapshot.state == FocusState.BREAK
        store.save(snapshot, focusMs, breakMs, if (active) engine.finishAtMs() else 0L)
        scheduler.cancel()
        if (active) scheduler.schedule(engine.finishAtMs())
    }

    private fun notifyListeners(snapshot: FocusSnapshot) { listeners.forEach { it(snapshot) } }
}

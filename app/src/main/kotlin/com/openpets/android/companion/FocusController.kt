package com.openpets.android.companion

import android.content.Context
import com.openpets.android.FeatureHud
import com.openpets.android.NotificationHelper
import com.openpets.android.PetBus
import com.openpets.android.protocol.HudItem
import com.openpets.android.protocol.SayRequest
import java.util.concurrent.CopyOnWriteArrayList

/** UI API: snapshot(), refresh(), start(minutes, minutes), pause(), resume(), skip(), end(), listeners, showOnPet(). */
class FocusController private constructor(context: Context, runtimeInstance: Boolean) {
    constructor(context: Context) : this(context, false)
    private val delegate: FocusController? = if (runtimeInstance) null else CompanionRuntime.run { init(context); focus }
    private val appContext = context.applicationContext
    private val store = FocusStore(appContext)
    private val scheduler = FocusScheduler(appContext)
    private val engine = FocusEngine()
    private val listeners = CopyOnWriteArrayList<(FocusSnapshot) -> Unit>()
    private var focusMs = 0L
    private var breakMs = 0L
    private var lastObservedKey: String
    private var lastAnnouncedPhase: FocusPhase?
    private var lastHudKey: String? = null

    init {
        val stored = store.load()
        focusMs = stored.focusMs
        breakMs = stored.breakMs
        engine.restore(stored.snapshot, stored.finishAtMs, focusMs, breakMs)
        lastObservedKey = key(stored.snapshot)
        lastAnnouncedPhase = phase(stored.snapshot)
        if (stored.snapshot.state == FocusState.FOCUS || stored.snapshot.state == FocusState.BREAK) {
            scheduler.schedule(stored.finishAtMs)
        }
    }

    @Synchronized fun snapshot(): FocusSnapshot = delegate?.snapshot() ?: engine.snapshot()

    @Synchronized fun refresh() {
        delegate?.let { it.refresh(); return }
        val current = engine.snapshot()
        val currentPhase = phase(current)
        val announceTransition = currentPhase != null && currentPhase != lastAnnouncedPhase
        if (announceTransition) {
            lastAnnouncedPhase = currentPhase
        }
        persistAndSchedule(current)
        if (announceTransition) announcePhase(current)
        if (current.state == FocusState.FOCUS || current.state == FocusState.BREAK) showOnPet()
        val currentKey = key(current)
        if (currentKey != lastObservedKey) {
            lastObservedKey = currentKey
            listeners.forEach { it(current) }
        }
    }

    @Synchronized fun start(focusMinutes: Int, breakMinutes: Int) {
        delegate?.let { it.start(focusMinutes, breakMinutes); return }
        require(focusMinutes > 0 && breakMinutes > 0)
        focusMs = focusMinutes * 60_000L
        breakMs = breakMinutes * 60_000L
        lastHudKey = null
        lastAnnouncedPhase = null
        engine.start(focusMs, breakMs)
        persistAndSchedule(engine.snapshot())
        refresh()
    }

    @Synchronized fun pause() { delegate?.let { it.pause(); return }; engine.pause(); refresh() }
    @Synchronized fun resume() { delegate?.let { it.resume(); return }; engine.resume(); refresh() }
    @Synchronized fun skip() { delegate?.let { it.skip(); return }; engine.skip(); refresh() }

    @Synchronized fun end() {
        delegate?.let { it.end(); return }
        scheduler.cancel()
        engine.end()
        focusMs = 0L
        breakMs = 0L
        lastHudKey = null
        lastAnnouncedPhase = null
        persistAndSchedule(engine.snapshot())
        PetBus.sink.onDismissBubble("focus-buddy")
        refresh()
    }

    fun addListener(l: (FocusSnapshot) -> Unit) { (delegate ?: this).listeners.addIfAbsent(l) }
    fun removeListener(l: (FocusSnapshot) -> Unit) { (delegate ?: this).listeners.remove(l) }

    @Synchronized fun showOnPet() {
        delegate?.let { it.showOnPet(); return }
        val current = engine.snapshot()
        if (current.state != FocusState.FOCUS && current.state != FocusState.BREAK) return
        val key = key(current)
        val isBreak = current.state == FocusState.BREAK
        val title = if (isBreak) "Break" else "Focus"
        PetBus.hud(
            FeatureHud(
                bubbleId = "focus-buddy",
                source = "focus",
                message = title,
                subtext = TimerEngine.formatRemaining(current.phaseRemainingMs),
                hud = listOf(HudItem(title, current.phaseRemainingMs.toFloat(), current.phaseTotalMs.toFloat().coerceAtLeast(1f), "info", "focus")),
                claim = key != lastHudKey,
            ),
        )
        lastHudKey = key
    }

    private fun announcePhase(snapshot: FocusSnapshot) {
        val isBreak = snapshot.state == FocusState.BREAK
        val message = if (isBreak) "Break time" else "Focus session"
        NotificationHelper(appContext).notifyFocusFinished(message, message)
        PetBus.sink.onSay(
            SayRequest(
                message = message,
                reaction = if (isBreak) "waiting" else "testing",
                source = "focus",
                hud = listOf(HudItem(if (isBreak) "Break" else "Focus", snapshot.phaseRemainingMs.toFloat(), snapshot.phaseTotalMs.toFloat().coerceAtLeast(1f), "info", "focus")),
                bubbleId = "focus-buddy",
                pinned = true,
            ),
        )
    }

    private fun persistAndSchedule(snapshot: FocusSnapshot) {
        val active = snapshot.state == FocusState.FOCUS || snapshot.state == FocusState.BREAK
        store.save(snapshot, focusMs, breakMs, if (active) engine.finishAtMs() else 0L)
        scheduler.cancel()
        if (active) scheduler.schedule(engine.finishAtMs())
    }

    private fun phase(snapshot: FocusSnapshot): FocusPhase? = when (snapshot.state) {
        FocusState.FOCUS -> FocusPhase.FOCUS
        FocusState.BREAK -> FocusPhase.BREAK
        FocusState.PAUSED -> snapshot.pausedPhase
        FocusState.IDLE -> null
    }

    private fun key(snapshot: FocusSnapshot): String = "${snapshot.state}:${snapshot.pausedPhase}"

    companion object {
        internal fun createRuntime(context: Context) = FocusController(context, true)
    }
}

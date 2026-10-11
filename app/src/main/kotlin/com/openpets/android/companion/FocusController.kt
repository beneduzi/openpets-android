package com.openpets.android.companion

import android.content.Context
import com.openpets.android.FeatureHud
import com.openpets.android.NotificationHelper
import com.openpets.android.PetBus
import com.openpets.android.protocol.HudItem
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
    private lateinit var announcementGate: FocusPhaseAnnouncementGate
    private var lastHudKey: String? = null

    init {
        val stored = store.load()
        focusMs = stored.focusMs
        breakMs = stored.breakMs
        engine.restore(stored.snapshot, stored.finishAtMs, focusMs, breakMs)
        lastObservedKey = key(stored.snapshot)
        announcementGate = FocusPhaseAnnouncementGate(stored.snapshot)
        if (stored.snapshot.state == FocusState.FOCUS || stored.snapshot.state == FocusState.BREAK) {
            scheduler.schedule(stored.finishAtMs)
        }
    }

    @Synchronized fun snapshot(): FocusSnapshot = delegate?.snapshot() ?: engine.snapshot()

    @Synchronized fun refresh() {
        delegate?.let { it.refresh(); return }
        val current = engine.snapshot()
        val announceTransition = announcementGate.shouldAnnounce(current)
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
        announcementGate.reset()
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
        announcementGate.reset()
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
        val title = if (isBreak) appContext.getString(com.openpets.android.R.string.settings_focus_break) else appContext.getString(com.openpets.android.R.string.settings_focus_title)
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
        val message = if (isBreak) appContext.getString(com.openpets.android.R.string.settings_focus_break_time) else appContext.getString(com.openpets.android.R.string.settings_focus_session)
        lastHudKey = key(snapshot)
        NotificationHelper(appContext).notifyFocusFinished(message, message)
        PetBus.hud(
            FeatureHud(
                bubbleId = "focus-buddy",
                source = "focus",
                message = message,
                subtext = TimerEngine.formatRemaining(snapshot.phaseRemainingMs),
                hud = listOf(HudItem(if (isBreak) appContext.getString(com.openpets.android.R.string.settings_focus_break) else appContext.getString(com.openpets.android.R.string.settings_focus_title), snapshot.phaseRemainingMs.toFloat(), snapshot.phaseTotalMs.toFloat().coerceAtLeast(1f), "info", "focus")),
                claim = true,
            ),
        )
        PetBus.sink.onReact(if (isBreak) "waiting" else "testing", "focus")
    }

    private fun persistAndSchedule(snapshot: FocusSnapshot) {
        val active = snapshot.state == FocusState.FOCUS || snapshot.state == FocusState.BREAK
        store.save(snapshot, focusMs, breakMs, if (active) engine.finishAtMs() else 0L)
        scheduler.cancel()
        if (active) scheduler.schedule(engine.finishAtMs())
    }

    private fun key(snapshot: FocusSnapshot): String = "${snapshot.state}:${snapshot.pausedPhase}"

    companion object {
        internal fun createRuntime(context: Context) = FocusController(context, true)
    }
}

package com.openpets.android.companion

import android.content.Context
import com.openpets.android.NotificationHelper
import com.openpets.android.PetBus
import com.openpets.android.FeatureHud
import com.openpets.android.protocol.HudItem
import java.util.concurrent.CopyOnWriteArrayList

/** UI-facing, application-scoped timer API. */
class TimerController private constructor(context: Context, runtimeInstance: Boolean) {
    constructor(context: Context) : this(context, false)
    private val delegate: TimerController? = if (runtimeInstance) null else CompanionRuntime.run { init(context); timer }
    private val appContext = context.applicationContext
    private val store = TimerStore(appContext)
    private val scheduler = TimerScheduler(appContext)
    private val engine = TimerEngine()
    private val listeners = CopyOnWriteArrayList<(TimerSnapshot) -> Unit>()
    private var alarmTriggerMs = 0L
    private lateinit var announcementGate: TimerFinishAnnouncementGate
    private var lastObservedState: TimerRunState
    private var lastAnnouncedState: TimerRunState
    private var lastHudKey: String? = null

    init {
        val stored = store.load()
        alarmTriggerMs = stored.alarmTriggerMs
        announcementGate = TimerFinishAnnouncementGate(stored.finishedAnnounced)
        engine.restore(stored.snapshot, stored.finishAtMs)
        lastObservedState = stored.snapshot.state
        lastAnnouncedState = if (stored.finishedAnnounced) TimerRunState.FINISHED else TimerRunState.IDLE
        if (stored.snapshot.state == TimerRunState.RUNNING && stored.alarmTriggerMs > 0) scheduler.schedule(stored.alarmTriggerMs)
    }

    @Synchronized fun snapshot(): TimerSnapshot = delegate?.snapshot() ?: engine.snapshot()

    @Synchronized fun refresh() {
        delegate?.let { it.refresh(); return }
        val current = engine.snapshot()
        val stateChanged = current.state != lastObservedState
        lastObservedState = current.state
        if (current.state == TimerRunState.FINISHED) {
            scheduler.cancel()
            alarmTriggerMs = 0L
            if (announcementGate.shouldAnnounce(current.state) && current.state != lastAnnouncedState) {
                lastAnnouncedState = current.state
                persist(current)
                announceFinished(current)
            } else {
                persist(current)
            }
        } else if (current.state == TimerRunState.RUNNING) {
            showOnPet()
        } else if (current.state == TimerRunState.PAUSED) {
            showOnPet()
            persist(current)
        }
        if (stateChanged) notifyListeners(current)
    }

    @Synchronized fun start(durationMs: Long, label: String? = null) {
        delegate?.let { it.start(durationMs, label); return }
        scheduler.cancel()
        engine.start(durationMs, label)
        announcementGate.reset()
        lastAnnouncedState = TimerRunState.IDLE
        lastObservedState = TimerRunState.IDLE
        lastHudKey = null
        alarmTriggerMs = engine.finishAtMs()
        scheduler.schedule(alarmTriggerMs)
        persist(engine.snapshot())
        refresh()
    }

    @Synchronized fun pause() {
        delegate?.let { it.pause(); return }
        engine.pause()
        scheduler.cancel()
        alarmTriggerMs = 0L
        persist(engine.snapshot())
        refresh()
    }

    @Synchronized fun resume() {
        delegate?.let { it.resume(); return }
        engine.resume()
        if (engine.snapshot().state == TimerRunState.RUNNING) {
            alarmTriggerMs = engine.finishAtMs()
            scheduler.schedule(alarmTriggerMs)
        }
        persist(engine.snapshot())
        refresh()
    }

    @Synchronized fun addMinutes(minutes: Int) {
        delegate?.let { it.addMinutes(minutes); return }
        engine.addMinutes(minutes)
        announcementGate.reset()
        lastAnnouncedState = TimerRunState.IDLE
        lastHudKey = null
        alarmTriggerMs = engine.finishAtMs()
        scheduler.schedule(alarmTriggerMs)
        persist(engine.snapshot())
        refresh()
    }

    @Synchronized fun cancel() {
        delegate?.let { it.cancel(); return }
        scheduler.cancel()
        engine.cancel()
        alarmTriggerMs = 0L
        announcementGate.reset()
        lastHudKey = null
        persist(engine.snapshot())
        PetBus.sink.onDismissBubble("simple-timer")
        refresh()
    }

    @Synchronized fun dismissFinished() {
        delegate?.let { it.dismissFinished(); return }
        scheduler.cancel()
        if (engine.snapshot().state == TimerRunState.FINISHED) engine.dismissFinished()
        alarmTriggerMs = 0L
        lastHudKey = null
        persist(engine.snapshot())
        PetBus.sink.onDismissBubble("simple-timer")
        refresh()
    }

    fun addListener(l: (TimerSnapshot) -> Unit) { (delegate ?: this).listeners.addIfAbsent(l) }
    fun removeListener(l: (TimerSnapshot) -> Unit) { (delegate ?: this).listeners.remove(l) }

    @Synchronized fun showOnPet() {
        delegate?.let { it.showOnPet(); return }
        val current = engine.snapshot()
        if (current.state != TimerRunState.RUNNING && current.state != TimerRunState.PAUSED) return
        val key = current.state.name
        val claim = key != lastHudKey
        lastHudKey = key
        PetBus.hud(
            FeatureHud(
                bubbleId = "simple-timer",
                source = "timer",
                message = current.label ?: "Timer",
                subtext = TimerEngine.formatRemaining(current.remainingMs),
                hud = listOf(HudItem("Timer", current.remainingMs.toFloat(), current.totalMs.toFloat().coerceAtLeast(1f), "info", "timer")),
                claim = claim,
            ),
        )
    }

    private fun announceFinished(snapshot: TimerSnapshot) {
        NotificationHelper(appContext).notifyTimerFinished("Timer finished", snapshot.label ?: "Your timer is done.")
        PetBus.hud(
            FeatureHud(
                bubbleId = "simple-timer",
                source = "timer",
                message = "Timer finished",
                subtext = snapshot.label,
                hud = listOf(HudItem(snapshot.label ?: "Timer", 0f, 1f, "success", "timer")),
                claim = true,
            ),
        )
        PetBus.sink.onReact("success", "timer")
    }

    private fun persist(snapshot: TimerSnapshot) {
        store.save(snapshot, System.currentTimeMillis(), engine.finishAtMs(), alarmTriggerMs, announcementGate.hasAnnounced())
    }

    private fun notifyListeners(snapshot: TimerSnapshot) { listeners.forEach { it(snapshot) } }

    companion object {
        internal fun createRuntime(context: Context) = TimerController(context, true)
    }
}

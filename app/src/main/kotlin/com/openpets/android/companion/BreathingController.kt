package com.openpets.android.companion

import android.content.Context
import com.openpets.android.FeatureHud
import com.openpets.android.PetBus
import com.openpets.android.protocol.HudItem
import java.util.concurrent.CopyOnWriteArrayList

class BreathingController private constructor(context: Context, runtimeInstance: Boolean) {
    constructor(context: Context) : this(context, false)
    private val delegate: BreathingController? = if (runtimeInstance) null else CompanionRuntime.run { init(context); breathing }
    private val engine = BreathingEngine()
    private val listeners = CopyOnWriteArrayList<(BreathSnapshot) -> Unit>()
    private var lastHudKey: String? = null
    private var hasSession = false

    @Synchronized fun snapshot(): BreathSnapshot = delegate?.snapshot() ?: engine.snapshot()

    @Synchronized fun refresh() {
        delegate?.let { it.refresh(); return }
        val current = engine.snapshot()
        if (current.active) showOnPet()
        else if (hasSession) {
            hasSession = false
            PetBus.sink.onDismissBubble(BUBBLE_ID)
        }
        listeners.forEach { it(current) }
    }

    @Synchronized fun start(patternId: String, durationMinutes: Int) {
        delegate?.let { it.start(patternId, durationMinutes); return }
        require(durationMinutes > 0)
        engine.start(BreathingPattern.byId(patternId), durationMinutes * 60_000L)
        hasSession = true
        lastHudKey = null
        PetBus.sink.onReact("waiting", SOURCE)
        refresh()
    }

    @Synchronized fun stop() {
        delegate?.let { it.stop(); return }
        engine.stop()
        hasSession = false
        lastHudKey = null
        PetBus.sink.onDismissBubble(BUBBLE_ID)
        val current = engine.snapshot()
        listeners.forEach { it(current) }
    }

    fun addListener(l: (BreathSnapshot) -> Unit) { (delegate ?: this).listeners.addIfAbsent(l) }
    fun removeListener(l: (BreathSnapshot) -> Unit) { (delegate ?: this).listeners.remove(l) }

    private val appContext = context.applicationContext

    @Synchronized fun showOnPet() {
        delegate?.let { it.showOnPet(); return }
        val current = engine.snapshot()
        if (!current.active) return
        val key = "${current.patternId}:${current.phase}"
        val message = when (current.phase) {
            BreathPhase.INHALE -> "In"
            BreathPhase.HOLD_IN, BreathPhase.HOLD_OUT -> "Hold"
            BreathPhase.EXHALE -> "Out"
            BreathPhase.DONE -> return
        }
        val pattern = BreathingPattern.byId(current.patternId.orEmpty())
        val phaseSeconds = when (current.phase) {
            BreathPhase.INHALE -> pattern.inhaleSec
            BreathPhase.HOLD_IN -> pattern.holdInSec
            BreathPhase.EXHALE -> pattern.exhaleSec
            BreathPhase.HOLD_OUT -> pattern.holdOutSec
            BreathPhase.DONE -> 1
        }.coerceAtLeast(1)
        PetBus.hud(
            FeatureHud(
                bubbleId = BUBBLE_ID,
                source = SOURCE,
                message = message,
                subtext = "${current.phaseRemainingSec}s",
                hud = listOf(HudItem(appContext.getString(com.openpets.android.R.string.settings_breathe_hud_breath), current.phaseRemainingSec.toFloat(), phaseSeconds.toFloat(), "blue", null)),
                claim = key != lastHudKey,
            ),
        )
        lastHudKey = key
    }

    companion object {
        private const val SOURCE = "breathing"
        private const val BUBBLE_ID = "breathing"
        internal fun createRuntime(context: Context) = BreathingController(context, true)
    }
}

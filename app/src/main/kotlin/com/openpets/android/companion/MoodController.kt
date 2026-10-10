package com.openpets.android.companion

import android.content.Context
import com.openpets.android.FeatureHud
import com.openpets.android.PetBus
import com.openpets.android.protocol.HudItem
import com.openpets.android.protocol.SayRequest
import java.util.concurrent.CopyOnWriteArrayList

/** Application-scoped mood API: recent(), latest(), average(), record(), nudge(), showOnPet(), listeners. */
class MoodController private constructor(context: Context, runtimeInstance: Boolean) {
    constructor(context: Context) : this(context, false)

    private val delegate: MoodController? = if (runtimeInstance) null else CompanionRuntime.run { init(context); mood }
    private val store = MoodStore(context.applicationContext)
    private val engine = MoodEngine()
    private val listeners = CopyOnWriteArrayList<(List<MoodEntry>) -> Unit>()

    init { engine.replaceAll(store.load()) }

    @Synchronized fun recent(limit: Int = 10): List<MoodEntry> = delegate?.recent(limit) ?: engine.recent(limit)
    @Synchronized fun latest(): MoodEntry? = delegate?.latest() ?: engine.latest()
    @Synchronized fun average(days: Int): Double? = delegate?.average(days) ?: engine.average(days)

    @Synchronized fun record(value: Int, note: String?) {
        delegate?.let { it.record(value, note); return }
        engine.add(value, note)
        persistAndNotify()
    }

    @Synchronized fun refresh() {
        delegate?.let { it.refresh(); return }
        persistAndNotify()
    }

    fun nudge() {
        delegate?.let { it.nudge(); return }
        PetBus.sink.onSay(
            SayRequest(
                message = "How are you feeling? 1–5",
                reaction = "thinking",
                source = "mood",
                bubbleId = "mood-checkin",
                pinned = false,
            ),
        )
    }

    @Synchronized fun showOnPet() {
        delegate?.let { it.showOnPet(); return }
        val entry = engine.latest() ?: return
        val average = engine.average(7)
        val levelText = when (entry.level()) {
            MoodLevel.LOW -> "Low"
            MoodLevel.OK -> "Okay"
            MoodLevel.GOOD -> "Good"
            MoodLevel.GREAT -> "Great"
        }
        PetBus.hud(
            FeatureHud(
                bubbleId = "mood-checkin",
                source = "mood",
                message = levelText,
                subtext = average?.let { "Recent average: %.1f".format(it) },
                hud = listOf(HudItem("Mood", entry.value.toFloat(), 5f, "pink", null)),
                claim = true,
            ),
        )
    }

    fun addListener(l: (List<MoodEntry>) -> Unit) { (delegate ?: this).listeners.addIfAbsent(l) }
    fun removeListener(l: (List<MoodEntry>) -> Unit) { (delegate ?: this).listeners.remove(l) }

    private fun persistAndNotify() {
        val entries = engine.all()
        store.save(entries)
        listeners.forEach { it(engine.recent(200)) }
    }

    companion object {
        internal fun createRuntime(context: Context) = MoodController(context, true)
    }
}

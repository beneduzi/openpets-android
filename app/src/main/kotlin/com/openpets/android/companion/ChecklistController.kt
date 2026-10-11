package com.openpets.android.companion

import android.content.Context
import com.openpets.android.PetBus
import com.openpets.android.protocol.HudItem
import com.openpets.android.protocol.SayRequest
import com.openpets.android.settings.AppSettings
import java.util.Calendar
import java.util.concurrent.CopyOnWriteArrayList

class ChecklistController private constructor(context: Context, runtimeInstance: Boolean) {
    constructor(context: Context) : this(context, false)

    private val appContext = context.applicationContext
    private val delegate = if (runtimeInstance) null else CompanionRuntime.run { init(context); checklist }
    private val store = ChecklistStore(context.applicationContext)
    private val engine = ChecklistEngine(store.load())
    private val listeners = CopyOnWriteArrayList<(List<ChecklistItem>) -> Unit>()

    @Synchronized fun items(): List<ChecklistItem> = delegate?.items() ?: engine.items()

    @Synchronized fun add(label: String): ChecklistItem {
        delegate?.let { return it.add(label) }
        return engine.add(label).also { persistAndNotify() }
    }

    @Synchronized fun toggle(id: String) { delegate?.let { it.toggle(id); return }; if (engine.toggle(id)) persistAndNotify() }
    @Synchronized fun remove(id: String) { delegate?.let { it.remove(id); return }; if (engine.remove(id)) persistAndNotify() }
    @Synchronized fun clearCompleted() { delegate?.let { it.clearCompleted(); return }; if (engine.clearCompleted()) persistAndNotify() }

    @Synchronized fun refresh() {
        delegate?.let { it.refresh(); return }
        engine.items().let { current -> store.save(current); listeners.forEach { it(current) } }
    }

    fun addListener(listener: (List<ChecklistItem>) -> Unit) { (delegate ?: this).listeners.addIfAbsent(listener) }
    fun removeListener(listener: (List<ChecklistItem>) -> Unit) { (delegate ?: this).listeners.remove(listener) }

    fun launch() {
        delegate?.let { it.launch(); return }
        val current = items()
        val greeting = AppSettings(appContext).launchGreeting.trim().ifEmpty { defaultGreeting() }
        val remaining = current.filterNot { it.done }.take(4).joinToString(" · ") { it.label }
        PetBus.sink.onSay(SayRequest(
            message = remaining.ifEmpty { greeting }, reaction = "waving", source = "launch",
            hud = current.takeIf { it.isNotEmpty() }?.let { listOf(HudItem(appContext.getString(com.openpets.android.R.string.settings_checklist_hud_label), it.count { item -> item.done }.toFloat(), it.size.toFloat(), "green", null)) }.orEmpty(),
            bubbleId = "launch-checklist", pinned = false,
        ))
    }

    private fun defaultGreeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> appContext.getString(com.openpets.android.R.string.settings_checklist_morning)
        in 12..17 -> appContext.getString(com.openpets.android.R.string.settings_checklist_afternoon)
        else -> appContext.getString(com.openpets.android.R.string.settings_checklist_evening)
    }

    private fun persistAndNotify() { val current = engine.items(); store.save(current); listeners.forEach { it(current) } }

    companion object { internal fun createRuntime(context: Context) = ChecklistController(context, true) }
}

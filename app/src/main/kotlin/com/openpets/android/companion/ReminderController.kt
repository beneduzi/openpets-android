package com.openpets.android.companion

import android.content.Context
import com.openpets.android.PetBus
import com.openpets.android.protocol.HudItem
import com.openpets.android.protocol.SayRequest
import java.util.concurrent.CopyOnWriteArrayList

/**
 * UI API: snapshot(): List<Reminder>; refresh(); add(title: String, dueAtMs: Long): Reminder;
 * addInMinutes(title: String, minutes: Int): Reminder; remove(id: String); complete(id: String);
 * snooze(id: String, minutes: Int); addListener(l: (List<Reminder>) -> Unit);
 * removeListener(l: (List<Reminder>) -> Unit). Construct with any Context; persisted reminders
 * are loaded immediately and refresh reconciles missed due notifications and alarms.
 */
class ReminderController private constructor(context: Context, runtimeInstance: Boolean) {
    constructor(context: Context) : this(context, false)
    private val delegate: ReminderController? = if (runtimeInstance) null else CompanionRuntime.run { init(context); reminders }
    private val store = ReminderStore(context.applicationContext)
    private val scheduler = ReminderScheduler(context.applicationContext)
    private val engine = ReminderEngine()
    private val listeners = CopyOnWriteArrayList<(List<Reminder>) -> Unit>()

    init { engine.replaceAll(store.load()) }

    @Synchronized fun snapshot(): List<Reminder> = delegate?.snapshot() ?: engine.pending()

    @Synchronized fun refresh() {
        delegate?.let { it.refresh(); return }
        val now = System.currentTimeMillis()
        val due = engine.due(now)
        due.forEach { engine.markNotified(it.id) }
        persistAndSchedule(now)
        if (due.isNotEmpty()) {
            due.forEach { reminder ->
                com.openpets.android.NotificationHelper(contextForNotifications)
                    .notifyReminder(reminder.id, reminder.title, reminder.title)
                PetBus.sink.onSay(
                    SayRequest(
                        message = reminder.title,
                        reaction = "waiting",
                        source = "reminder",
                        hud = listOf(HudItem(reminder.title.take(36), 1f, 1f, "warning", "reminder")),
                        bubbleId = "quick-reminder-${reminder.id}",
                        pinned = true,
                    ),
                )
            }
        }
        notifyListeners()
    }

    // Kept separately so platform notification calls remain isolated from pure engine logic.
    private val contextForNotifications = context.applicationContext

    @Synchronized fun add(title: String, dueAtMs: Long): Reminder {
        delegate?.let { return it.add(title, dueAtMs) }
        val added = engine.add(title, dueAtMs)
        persistAndSchedule(System.currentTimeMillis())
        notifyListeners()
        return added
    }

    fun addInMinutes(title: String, minutes: Int): Reminder {
        delegate?.let { return it.addInMinutes(title, minutes) }
        require(minutes > 0) { "Minutes must be positive." }
        return add(title, System.currentTimeMillis() + minutes * 60_000L)
    }

    @Synchronized fun remove(id: String) { delegate?.let { it.remove(id); return }; mutate { engine.remove(id) } }
    @Synchronized fun complete(id: String) { delegate?.let { it.complete(id); return }; mutate { engine.complete(id) } }
    @Synchronized fun snooze(id: String, minutes: Int) { delegate?.let { it.snooze(id, minutes); return }; mutate { engine.snooze(id, minutes) } }

    fun addListener(l: (List<Reminder>) -> Unit) { (delegate ?: this).listeners.addIfAbsent(l) }
    fun removeListener(l: (List<Reminder>) -> Unit) { (delegate ?: this).listeners.remove(l) }

    private inline fun mutate(change: () -> Boolean) {
        if (change()) {
            persistAndSchedule(System.currentTimeMillis())
            notifyListeners()
        }
    }

    private fun persistAndSchedule(now: Long) {
        store.save(engine.all())
        scheduler.cancel()
        engine.nextTrigger(now)?.let { scheduler.schedule(it) }
    }

    private fun notifyListeners() {
        val current = engine.pending()
        listeners.forEach { it(current) }
    }

    companion object {
        internal fun createRuntime(context: Context) = ReminderController(context, true)
    }
}

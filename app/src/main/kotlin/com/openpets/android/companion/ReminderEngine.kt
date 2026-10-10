package com.openpets.android.companion

/** Pure reminder collection and scheduling logic. */
class ReminderEngine(private val clock: () -> Long = System::currentTimeMillis) {
    private val reminders = mutableListOf<Reminder>()
    private var sequence = 0L

    fun add(title: String, dueAtMs: Long): Reminder {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "Reminder title must not be empty." }
        require(cleanTitle.length <= MAX_TITLE_LENGTH) { "Reminder title is too long." }
        val reminder = Reminder("rem-${++sequence}-${clock()}", cleanTitle, dueAtMs)
        reminders.add(reminder)
        return reminder
    }

    fun remove(id: String): Boolean = reminders.removeAll { it.id == id }

    fun complete(id: String): Boolean = update(id) { it.copy(completed = true) }

    fun snooze(id: String, minutes: Int): Boolean {
        require(minutes > 0) { "Snooze duration must be positive." }
        return update(id) { it.copy(dueAtMs = maxOf(clock(), it.dueAtMs) + minutes * 60_000L, notified = false) }
    }

    fun pending(): List<Reminder> = reminders.filterNot { it.completed }.sortedBy { it.dueAtMs }

    fun due(now: Long): List<Reminder> = reminders.filter { !it.completed && !it.notified && it.dueAtMs <= now }

    fun nextTrigger(now: Long): Long? {
        val active = reminders.filterNot { it.completed }
        return active.filter { !it.notified && it.dueAtMs <= now }.minOfOrNull { it.dueAtMs }?.let { now }
            ?: active.filter { it.dueAtMs > now }.minOfOrNull { it.dueAtMs }
    }

    fun markNotified(id: String): Boolean = update(id) { it.copy(notified = true) }

    fun replaceAll(list: List<Reminder>) {
        reminders.clear()
        reminders.addAll(list.filter { it.title.trim().isNotEmpty() && it.title.length <= MAX_TITLE_LENGTH })
    }

    fun all(): List<Reminder> = reminders.toList()

    private inline fun update(id: String, transform: (Reminder) -> Reminder): Boolean {
        val index = reminders.indexOfFirst { it.id == id }
        if (index < 0) return false
        reminders[index] = transform(reminders[index])
        return true
    }

    companion object {
        const val MAX_TITLE_LENGTH = 120
    }
}

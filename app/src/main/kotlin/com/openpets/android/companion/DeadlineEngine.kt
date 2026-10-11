package com.openpets.android.companion

class DeadlineEngine(private val clock: () -> Long = System::currentTimeMillis) {
    private val deadlines = mutableListOf<Deadline>()
    private var sequence = 0L
    fun add(title: String, dueAtMs: Long): Deadline {
        val clean = title.trim(); require(clean.length in 1..120) { "Title must be 1..120 characters." }
        return Deadline("deadline-${++sequence}-${clock()}", clean, dueAtMs).also(deadlines::add)
    }
    fun rename(id: String, title: String): Boolean {
        val clean = title.trim(); require(clean.length in 1..120) { "Title must be 1..120 characters." }
        return update(id) { it.copy(title = clean) }
    }
    fun reschedule(id: String, dueAtMs: Long) = update(id) { it.copy(dueAtMs = dueAtMs) }
    fun complete(id: String) = update(id) { it.copy(completed = true) }
    fun remove(id: String) = deadlines.removeAll { it.id == id }
    fun upcoming(now: Long): List<Deadline> = deadlines.filterNot { it.completed }.sortedBy { it.dueAtMs }
    fun nearest(now: Long): Deadline? = upcoming(now).firstOrNull()
    fun nextReminderAt(now: Long): Long? = deadlines.filterNot { it.completed }.flatMap { d -> OFFSETS.map { d.dueAtMs - it } + d.dueAtMs }.filter { it > now }.minOrNull()
    fun reminderOffsetFor(dueAtMs: Long, fireAtMs: Long): Int? = when (dueAtMs - fireAtMs) { DAY -> com.openpets.android.R.string.settings_deadline_offset_day; HOUR -> com.openpets.android.R.string.settings_deadline_offset_hour; FIFTEEN_MIN -> com.openpets.android.R.string.settings_deadline_offset_15min; 0L -> com.openpets.android.R.string.settings_deadline_offset_now; else -> null }
    fun all() = deadlines.toList()
    fun replaceAll(list: List<Deadline>) { deadlines.clear(); deadlines.addAll(list.filter { it.title.trim().length in 1..120 }) }
    private inline fun update(id: String, transform: (Deadline) -> Deadline): Boolean { val i=deadlines.indexOfFirst { it.id==id }; if(i<0)return false; deadlines[i]=transform(deadlines[i]); return true }
    companion object { const val DAY=86_400_000L; const val HOUR=3_600_000L; const val FIFTEEN_MIN=900_000L; private val OFFSETS=listOf(DAY,HOUR,FIFTEEN_MIN) }
}

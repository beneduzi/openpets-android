package com.openpets.android.companion

/** Pure mood log operations. */
class MoodEngine(private val clock: () -> Long = System::currentTimeMillis) {
    private val entries = mutableListOf<MoodEntry>()

    fun add(value: Int, note: String?): MoodEntry {
        require(value in 1..5) { "Mood value must be between 1 and 5." }
        val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_NOTE_LENGTH)
        return MoodEntry(value, cleanNote, clock()).also { entries.add(it) }
    }

    fun recent(limit: Int = 10): List<MoodEntry> = entries.sortedByDescending { it.atMs }.take(limit.coerceAtLeast(0))

    fun latest(): MoodEntry? = entries.maxByOrNull { it.atMs }

    fun average(days: Int): Double? {
        require(days > 0) { "Days must be positive." }
        val now = clock()
        val cutoff = now - days * DAY_MS
        val selected = entries.filter { it.atMs in cutoff..now }
        return selected.takeIf { it.isNotEmpty() }?.let { values -> values.sumOf { it.value }.toDouble() / values.size }
    }

    fun all(): List<MoodEntry> = entries.toList()

    fun replaceAll(entries: List<MoodEntry>) {
        this.entries.clear()
        this.entries.addAll(entries.filter { it.value in 1..5 }.map { entry ->
            entry.copy(note = entry.note?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_NOTE_LENGTH))
        })
    }

    companion object {
        const val MAX_NOTE_LENGTH = 120
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

fun MoodEntry.level(): MoodLevel = when (value) {
    1, 2 -> MoodLevel.LOW
    3 -> MoodLevel.OK
    4 -> MoodLevel.GOOD
    else -> MoodLevel.GREAT
}

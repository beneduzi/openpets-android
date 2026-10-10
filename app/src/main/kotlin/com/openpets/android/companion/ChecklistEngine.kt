package com.openpets.android.companion

import java.util.UUID

/** Pure in-memory checklist model. */
class ChecklistEngine(initialItems: List<ChecklistItem> = emptyList()) {
    private val entries = initialItems.toMutableList()

    fun add(label: String): ChecklistItem {
        val clean = label.trim()
        require(clean.length in 1..80) { "Item must be between 1 and 80 characters." }
        return ChecklistItem(UUID.randomUUID().toString(), clean).also(entries::add)
    }

    fun toggle(id: String): Boolean {
        val index = entries.indexOfFirst { it.id == id }
        if (index < 0) return false
        entries[index] = entries[index].copy(done = !entries[index].done)
        return true
    }

    fun remove(id: String): Boolean = entries.removeAll { it.id == id }

    fun clearCompleted(): Boolean = entries.removeAll { it.done }

    fun items(): List<ChecklistItem> = entries.toList()

    fun remaining(): Int = entries.count { !it.done }
}

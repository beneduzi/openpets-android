package com.openpets.android.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderEngineTest {
    private var now = 1_000L
    private val engine = ReminderEngine { now }

    @Test fun addAndOrder() {
        val later = engine.add(" later ", now + 20_000)
        val earlier = engine.add("earlier", now + 10_000)
        assertEquals("later", later.title)
        assertEquals(listOf(earlier.id, later.id), engine.pending().map { it.id })
    }

    @Test fun detectsDueReminders() {
        val due = engine.add("due", now)
        engine.add("future", now + 1)
        assertEquals(listOf(due), engine.due(now))
    }

    @Test fun completingRemovesFromPending() {
        val reminder = engine.add("done", now + 1)
        assertTrue(engine.complete(reminder.id))
        assertTrue(engine.pending().isEmpty())
    }

    @Test fun snoozePushesDueAndRearms() {
        val reminder = engine.add("snooze", now)
        engine.markNotified(reminder.id)
        assertTrue(engine.snooze(reminder.id, 5))
        val updated = engine.all().single()
        assertEquals(now + 5 * 60_000L, updated.dueAtMs)
        assertFalse(updated.notified)
        assertEquals(updated.dueAtMs, engine.nextTrigger(now))
    }

    @Test fun nextTriggerPicksEarliestOverdueThenFuture() {
        val overdue = engine.add("overdue", now - 10)
        assertEquals(overdue.dueAtMs, engine.nextTrigger(now))
        engine.add("future later", now + 20)
        assertEquals(now + 20, engine.nextTrigger(now))
        engine.markNotified(overdue.id)
        assertEquals(now + 20, engine.nextTrigger(now))
    }

    @Test fun markNotifiedSuppressesRepeat() {
        val reminder = engine.add("once", now)
        assertEquals(1, engine.due(now).size)
        engine.markNotified(reminder.id)
        assertTrue(engine.due(now).isEmpty())
    }
}

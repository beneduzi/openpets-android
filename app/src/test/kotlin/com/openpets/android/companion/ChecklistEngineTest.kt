package com.openpets.android.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistEngineTest {
    @Test fun addToggleRemoveAndClearCompleted() {
        val engine = ChecklistEngine()
        val first = engine.add("  Pack bag  ")
        val second = engine.add("Keys")
        assertEquals("Pack bag", first.label)
        assertTrue(engine.toggle(first.id))
        assertTrue(engine.items().first().done)
        assertTrue(engine.remove(second.id))
        assertTrue(engine.clearCompleted())
        assertTrue(engine.items().isEmpty())
    }

    @Test fun validatesLabels() {
        val engine = ChecklistEngine()
        listOf("", "   ", "x".repeat(81)).forEach { label ->
            assertTrue(runCatching { engine.add(label) }.isFailure)
        }
        assertEquals("x", engine.add(" x ").label)
    }

    @Test fun remainingCountsIncompleteItems() {
        val engine = ChecklistEngine()
        val first = engine.add("One")
        engine.add("Two")
        assertEquals(2, engine.remaining())
        assertTrue(engine.toggle(first.id))
        assertEquals(1, engine.remaining())
        assertFalse(engine.toggle("missing"))
    }
}

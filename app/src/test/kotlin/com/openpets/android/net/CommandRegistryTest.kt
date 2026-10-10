package com.openpets.android.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRegistryTest {
    @Test fun registerUnregisterAndSort() {
        CommandRegistry.unregister("registry-test-a"); CommandRegistry.unregister("registry-test-b")
        CommandRegistry.register("registry-test-b", listOf(RemoteCommand("registry-test-b", "b", "Zed", null)))
        CommandRegistry.register("registry-test-a", listOf(RemoteCommand("registry-test-a", "a", "Alpha", "desc")))
        assertEquals(listOf("registry-test-a", "registry-test-b"), CommandRegistry.list().filter { it.source.startsWith("registry-test-") }.map { it.source })
        CommandRegistry.unregister("registry-test-a")
        assertEquals(1, CommandRegistry.list().count { it.source == "registry-test-b" })
        CommandRegistry.unregister("registry-test-b")
    }

    @Test fun enforcesTitleAndTotalBounds() {
        assertTrue(runCatching { CommandRegistry.register("registry-test", listOf(RemoteCommand("registry-test", "x", "x".repeat(41), null))) }.isFailure)
        val many = (0 until 51).map { RemoteCommand("registry-overflow", "$it", "Item $it", null) }
        assertTrue(runCatching { CommandRegistry.register("registry-overflow", many) }.isFailure)
        CommandRegistry.unregister("registry-overflow")
    }
}

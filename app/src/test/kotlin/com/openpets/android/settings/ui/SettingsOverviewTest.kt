package com.openpets.android.settings.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsOverviewTest {
    @Test fun headingsAndDestinationsAreOrderedAndComplete() {
        val rows = overviewRows("Mochi", null)
        assertEquals(listOf("Pet", "Appearance & output", "Companion features", "Productivity", "Wellbeing", "Play & learning", "Connections & security", "Advanced & diagnostics", "About"),
            rows.filterIsInstance<Row.SectionHeader>().map { it.title })
        assertEquals(SettingsDestination.entries.toSet(), rows.filterIsInstance<Row.Navigation>().map { it.destination }.toSet())
        assertTrue(rows.filterIsInstance<Row.Navigation>().any { it.destination == SettingsDestination.PETS && it.summary == "Mochi" })
        assertTrue(rows.filterIsInstance<Row.Info>().any { it.title == "Device IP" && it.value == "Unavailable" })
    }
}

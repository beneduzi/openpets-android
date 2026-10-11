package com.openpets.android.settings.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsOverviewTest {
    @Test fun headingsAndDestinationsAreOrderedAndComplete() {
        val rows = overviewRows("Mochi", null)
        assertEquals(listOf(com.openpets.android.R.string.ui_reset_scopes, com.openpets.android.R.string.ui_appearance_title, com.openpets.android.R.string.settings_overview_companion, com.openpets.android.R.string.settings_overview_productivity, com.openpets.android.R.string.settings_overview_wellbeing, com.openpets.android.R.string.settings_overview_play, com.openpets.android.R.string.ui_connections, com.openpets.android.R.string.ui_startup, com.openpets.android.R.string.settings_overview_about),
            rows.filterIsInstance<Row.SectionHeader>().map { it.titleRes })
        assertEquals(SettingsDestination.entries.toSet(), rows.filterIsInstance<Row.Navigation>().map { it.destination }.toSet())
        assertTrue(rows.filterIsInstance<Row.Navigation>().any { it.destination == SettingsDestination.PETS && it.titleRes == com.openpets.android.R.string.ui_current_pet })
        assertTrue(rows.filterIsInstance<Row.Info>().any { it.titleRes == com.openpets.android.R.string.settings_overview_device_ip && it.value.isEmpty() })
    }
}

package com.openpets.android.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemResourcesFormatterTest {
    @Test fun nullValuesAreUnavailable() {
        assertEquals("—", SystemResourcesFormatter.percent(null))
        assertEquals("—", SystemResourcesFormatter.megabytes(null))
        val summary = SystemResourcesFormatter.format(DeviceResources(null, null, null, null, null, null, null, null))
        assert(summary.contains("unavailable"))
    }

    @Test fun formatsMegabytesAndGigabytes() {
        assertEquals("512 MB", SystemResourcesFormatter.megabytes(512L))
        assertEquals("1.5 GB", SystemResourcesFormatter.megabytes(1536L))
    }

    @Test fun formatsBatteryPercent() {
        assertEquals("84%", SystemResourcesFormatter.percent(84))
    }
}

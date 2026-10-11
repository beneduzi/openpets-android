package com.openpets.android.settings.ui

import com.openpets.android.companion.TimerEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionPresetTest {
    @Test fun timerPresetsMapMinutesToMilliseconds(){
        assertEquals(300_000L,timerPresetMillis(5));assertEquals(600_000L,timerPresetMillis(10))
        assertEquals(1_500_000L,timerPresetMillis(25));assertEquals(3_600_000L,timerPresetMillis(60))
    }
    @Test fun focusPresetDurationsArePreserved(){assertEquals(25 to 5,focusPreset(25,5));assertEquals(50 to 10,focusPreset(50,10))}
    @Test fun remainingTimeUsesExistingEngineFormatter(){assertEquals("1:05",TimerEngine.formatRemaining(65_000L))}
}

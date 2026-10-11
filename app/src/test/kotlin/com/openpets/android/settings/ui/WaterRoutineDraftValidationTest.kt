package com.openpets.android.settings.ui

import com.openpets.android.settings.SettingsValidators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterRoutineDraftValidationTest {
    @Test fun waterDraftAllowsOvernightWindowAndValidInterval(){
        val draft=WaterDraft(true,"30","22","6")
        assertTrue(validateWaterDraft(draft).isEmpty())
    }
    @Test fun waterDraftMapsInvalidIntervalHoursAndEqualWindowToFieldErrors(){
        val interval=validateWaterDraft(WaterDraft(true,"14","8","20"))
        assertEquals(SettingsValidators.Error.OUT_OF_RANGE,interval["interval"])
        val hour=validateWaterDraft(WaterDraft(true,"30","24","6"))
        assertEquals(SettingsValidators.Error.OUT_OF_RANGE,hour["fromHour"])
        assertTrue(!hour.containsKey("toHour"))
        val equal=validateWaterDraft(WaterDraft(true,"30","8","8"))
        assertEquals(SettingsValidators.Error.START_EQUALS_END,equal["toHour"])
    }
    @Test fun routineDraftRejectsInvalidHourAndMinute(){
        val errors=validateRoutineDraft(RoutineDraft(true,"24","60",false,"9","30"))
        assertEquals(SettingsValidators.Error.OUT_OF_RANGE,errors["morningHour"])
        assertEquals(SettingsValidators.Error.OUT_OF_RANGE,errors["morningMinute"])
        assertTrue(errors.keys==setOf("morningHour","morningMinute"))
    }
}

package com.openpets.android.settings.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class WalkaboutSyncTest {
    @Test fun sliderAndFieldUpdateTheSameFloatDraft() {
        val d=WalkaboutDraft("roam",1f,"1")
        stageWalkaboutSliderValue(d,1.234567f)
        assertEquals(1.234567f,d.speed)
        val old=Locale.getDefault()
        try { Locale.setDefault(Locale.GERMANY); assertEquals(2.375f,stageWalkaboutFieldValue(d,"2,375")!!,0.000001f); assertEquals("2,375",d.rawSpeed) }
        finally { Locale.setDefault(old) }
    }
    @Test fun sliderTextWatcherRoundTripPreservesPreciseDraft() {
        val d=WalkaboutDraft("roam",1f,"1")
        val precise=1.234567f
        stageWalkaboutSliderValue(d,precise)
        val watcherText=d.rawSpeed
        assertEquals(precise,stageWalkaboutFieldValue(d,watcherText)!!,0f)
        assertEquals(precise,d.speed,0f)
        assertEquals("1.235",formatWalkaboutSpeed(precise))
    }
    @Test fun blankOrInvalidEditorTextMarksDraftDirty() {
        val d=WalkaboutDraft("roam",1f,"1")
        stageWalkaboutFieldValue(d,"")
        assertTrue(isWalkaboutDraftDirty(d,"roam",1f))
        stageWalkaboutFieldValue(d,"not a speed")
        assertTrue(isWalkaboutDraftDirty(d,"roam",1f))
    }
    @Test fun formatIsLocaleStableAndParsingAcceptsLocaleDecimal() {
        val previous=Locale.getDefault()
        try { Locale.setDefault(Locale.GERMANY); assertEquals("1.235",formatWalkaboutSpeed(1.23456f)); assertEquals(1.5f,parseWalkaboutSpeed("1,5")!!,0.000001f) }
        finally { Locale.setDefault(previous) }
    }
    @Test fun cachedTextRowTracksDraftForRecycledHolderAndDiscardRows() {
        val row=SettingRow.TextFieldRow("speed","Speed","1",null,null,false)
        val edited=withTextFieldValue(listOf(row),"speed","2.75")
        assertEquals("2.75",(edited.single() as SettingRow.TextFieldRow).value)
        val persisted=WalkaboutDraft("roam",1f,"1")
        val discarded=WalkaboutDraft(persisted.mode,persisted.speed,formatWalkaboutSpeed(persisted.speed))
        val rebound=withTextFieldValue(edited,"speed",discarded.rawSpeed)
        assertEquals("1",(rebound.single() as SettingRow.TextFieldRow).value)
        assertNull(stageWalkaboutFieldValue(discarded,"invalid"))
        assertTrue(discarded.speed==1f)
    }

    @Test fun initializationUsesEditorTextAndIsClean() {
        val precise=1.234567f
        val d=WalkaboutDraft("roam",precise,walkaboutEditorText(precise))
        assertEquals("1.234567",d.rawSpeed)
        assertFalse(isWalkaboutDraftDirty(d,"roam",precise))
        // Pressing the editor action re-runs the watcher; precision and cleanliness must survive.
        assertEquals(precise,stageWalkaboutFieldValue(d,d.rawSpeed)!!,0f)
        assertFalse(isWalkaboutDraftDirty(d,"roam",precise))
    }
}

package com.openpets.android.settings.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowFeatureHelpersTest {
    @Test fun reminderValidationPreservesTitleAndDelayRules(){
        assertTrue(validReminderDraft("  call home  ","1"));assertFalse(validReminderDraft("","10"));assertFalse(validReminderDraft("x".repeat(121),"10"));assertFalse(validReminderDraft("ok","0"));assertFalse(validReminderDraft("ok","bad"))
    }
    @Test fun reminderDueTextRoundsMinutesAndHoursUp(){assertEquals("overdue",reminderDueText(99,100));assertEquals("in 2 min",reminderDueText(120_000,1));assertEquals("in 2 h",reminderDueText(7_200_000,1))}
    @Test fun deadlineTitleLimitAndRelativeFormatting(){assertTrue(validDeadlineTitle("x".repeat(120)));assertFalse(validDeadlineTitle(" "));assertFalse(validDeadlineTitle("x".repeat(121)));assertEquals("overdue",deadlineRelativeText(99,100));assertEquals("1 h",deadlineRelativeText(3_600_000,0));assertEquals("1 day",deadlineRelativeText(86_400_000,0))}
    @Test fun checklistItemLengthLimitAndRowIdsAreUnique(){assertTrue(validChecklistItem("x".repeat(80)));assertFalse(validChecklistItem("x".repeat(81)));assertFalse(validChecklistItem(" "));assertEquals(3,uniqueSettingRowIds(listOf("Aa","BB","Aa")).distinct().size)}
}

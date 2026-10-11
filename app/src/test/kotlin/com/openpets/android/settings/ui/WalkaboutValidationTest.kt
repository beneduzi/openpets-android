package com.openpets.android.settings.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkaboutValidationTest {
    @Test fun validatesSpeedRangeWithoutQuantizing() {
        assertTrue(validateWalkaboutSpeed("0.5"))
        assertTrue(validateWalkaboutSpeed("2.375"))
        assertTrue(validateWalkaboutSpeed("3.0"))
        assertFalse(validateWalkaboutSpeed("0.49"))
        assertFalse(validateWalkaboutSpeed("3.01"))
        assertFalse(validateWalkaboutSpeed("invalid"))
    }
}

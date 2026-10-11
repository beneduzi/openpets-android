package com.openpets.android.settings

import org.junit.Assert.*
import org.junit.Test

class SettingsResetterTest {
    @Test fun everyScopeExcludesTokensAndWritesOnlyItsAllowListedKeys(){
        ResetScope.values().forEach{scope->
            val before=mutableMapOf<String,Any?>("ipcToken" to "keep-ipc","remoteToken" to "keep-remote","launchChecklistEnabled" to false,"unrelated" to "preserve")
            val old=before.toMap();applyPreferenceReset(before,scope)
            assertTrue(preferenceResetValues(scope).keys==preferenceKeys(scope))
            assertFalse(preferenceResetValues(scope).keys.any{it.contains("Token",true)})
            listOf("ipcToken","remoteToken","launchChecklistEnabled","unrelated").forEach{assertEquals(old[it],before[it])}
            assertTrue(preferenceKeys(scope).all{it in preferenceResetValues(scope)})
        }
    }
    @Test fun allScopeIsUnionAndUnaffectedPreferencesRemainUntouched(){
        val scopes=listOf(ResetScope.PET,ResetScope.APPEARANCE,ResetScope.CONNECTIONS,ResetScope.COMPANION,ResetScope.STARTUP)
        assertEquals(scopes.flatMap(::preferenceKeys).toSet(),preferenceKeys(ResetScope.ALL))
        val target=mutableMapOf<String,Any?>("launchChecklistEnabled" to false,"unknown" to 42,"ipcToken" to "secret")
        applyPreferenceReset(target,ResetScope.ALL)
        assertEquals(false,target["launchChecklistEnabled"]);assertEquals(42,target["unknown"]);assertEquals("secret",target["ipcToken"])
    }
}

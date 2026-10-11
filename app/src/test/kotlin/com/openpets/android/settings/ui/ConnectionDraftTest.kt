package com.openpets.android.settings.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ConnectionDraftTest {
    private fun draft() = ConnectionDraft("0.0.0.0", true, "18420", true, "18421", false, "18421", "0123456789abcdef", "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_", false)
    @Test fun permitsDuplicateDisabledPorts() { assertEquals(emptyMap<String, com.openpets.android.settings.SettingsValidators.Error>(), validateConnectionDraft(draft())) }
    @Test fun flagsInvalidEnabledPortConflictAndBind() {
        val d=draft().apply { remotePort="18420"; bind="https://bad/path"; ipcPort="18420" }
        val errors=validateConnectionDraft(d)
        assertEquals(setOf("ipcPort","remotePort","bind"), errors.keys)
    }
    @Test fun focusedFieldKeystrokeIsAuthoritativeAtSave() { val d=draft(); d.ipcPort=" 18424 "; assertEquals(" 18424 ",d.ipcPort); assertEquals(emptyMap<String, com.openpets.android.settings.SettingsValidators.Error>(),validateConnectionDraft(d)) }
    @Test fun statusAndRecyclingDoNotChangeDraft() { val d=draft(); d.bind="edited.example"; val before=d.copy(); val status=com.openpets.android.net.ServersStatus(ipc=com.openpets.android.net.ServerState.Listening(18420)); assertEquals(18420,(status.ipc as com.openpets.android.net.ServerState.Listening).port); assertEquals(before,d) }
    @Test fun discardRestoresPersistedSnapshot() { val persisted=draft(); val edited=persisted.copy(bind="changed"); val discarded=persisted.copy(); assertEquals(persisted,discarded); assertEquals("0.0.0.0",discarded.bind); assertEquals("changed",edited.bind) }
    @Test fun unchangedLegacyTokensRemainVerbatim() { val d=draft().apply { ipcToken=" legacy token "; remoteToken=" legacy remote token " }; assertTrue(validateConnectionDraft(d).isEmpty()); assertEquals(" legacy token ",tokenForSave(d.ipcToken,false,true)); assertEquals(" legacy remote token ",tokenForSave(d.remoteToken,false,false)) }
    @Test fun disabledInvalidPortDoesNotValidate() { val d=draft().copy(ipcEnabled=false,ipcPort="not a port",loadedIpcPort="not a port"); assertFalse(validateConnectionDraft(d).containsKey("ipcPort")) }
    @Test fun validatedPortParsesWhitespace() { assertEquals(18424,connectionPortForSave(" 18424 ",true,18420)) }

    @Test fun disabledPortSaveRule() {
        // Enabled: always the validated value.
        assertEquals(18424, connectionPortForSave("18424", true, 18420, loaded = "18420"))
        // Edited while enabled, then disabled: keep the valid edited value.
        assertEquals(18425, connectionPortForSave("18425", false, 18420, loaded = "18420"))
        // Disabled and unchanged: keep persisted.
        assertEquals(18420, connectionPortForSave("18420", false, 18420, loaded = "18420"))
        // Disabled and edited to an invalid value: validation blocks Save.
        val invalid=draft().apply { ipcEnabled=false; ipcPort="nope" }
        assertTrue(validateConnectionDraft(invalid).containsKey("ipcPort"))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { connectionPortForSave("nope", false, 18420, loaded = "18420") }
        // Unchanged legacy invalid value: preserved as-is.
        assertEquals(70000, connectionPortForSave("70000", false, 70000, loaded = "70000"))
    }
}

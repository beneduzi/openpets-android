package com.openpets.android.settings

import org.junit.Assert.*
import org.junit.Test

class SettingsValidatorsTest {
    private fun <T> ok(result: SettingsValidators.Result<T>, expected: T) {
        assertEquals(SettingsValidators.Result.Ok(expected), result)
    }
    private fun error(result: SettingsValidators.Result<*>, expected: SettingsValidators.Error) {
        assertEquals(SettingsValidators.Result.Invalid(expected), result)
    }

    @Test fun ports() {
        ok(SettingsValidators.port("1"), 1)
        ok(SettingsValidators.port(" 65535 "), 65535)
        error(SettingsValidators.port(""), SettingsValidators.Error.REQUIRED)
        error(SettingsValidators.port("abc"), SettingsValidators.Error.NOT_A_NUMBER)
        error(SettingsValidators.port("0"), SettingsValidators.Error.OUT_OF_RANGE)
        error(SettingsValidators.port("65536"), SettingsValidators.Error.OUT_OF_RANGE)
        ok(SettingsValidators.distinctPorts(mapOf("a" to 1, "b" to 2)), Unit)
        error(SettingsValidators.distinctPorts(mapOf("a" to 1, "b" to 1)), SettingsValidators.Error.CONFLICT)
    }

    @Test fun tokens() {
        ok(SettingsValidators.ipcToken("x".repeat(16)), "x".repeat(16))
        ok(SettingsValidators.ipcToken("x".repeat(256)), "x".repeat(256))
        error(SettingsValidators.ipcToken("x".repeat(15)), SettingsValidators.Error.TOO_SHORT)
        error(SettingsValidators.ipcToken("x".repeat(257)), SettingsValidators.Error.TOO_LONG)
        error(SettingsValidators.ipcToken("x".repeat(15) + "\n"), SettingsValidators.Error.TOO_SHORT)
        error(SettingsValidators.ipcToken("x".repeat(15) + "\u0001"), SettingsValidators.Error.INVALID_CHARACTERS)
        ok(SettingsValidators.remoteToken("a".repeat(43)), "a".repeat(43))
        ok(SettingsValidators.remoteToken("a".repeat(256)), "a".repeat(256))
        error(SettingsValidators.remoteToken("a".repeat(42)), SettingsValidators.Error.TOO_SHORT)
        error(SettingsValidators.remoteToken("a".repeat(257)), SettingsValidators.Error.TOO_LONG)
        error(SettingsValidators.remoteToken("a".repeat(42) + "!"), SettingsValidators.Error.INVALID_CHARACTERS)
    }

    @Test fun bindAddresses() {
        listOf("", "::", "::1", "2001:db8::1", "::ffff:192.168.0.1", "1:2:3:4:5:6:192.168.0.1", "fe80::1%wlan0", "0.0.0.0", "192.168.0.5", "localhost", "example.local", "example.local.").forEach {
            assertTrue("$it: ${SettingsValidators.bindAddress(it)}", SettingsValidators.bindAddress(it) is SettingsValidators.Result.Ok)
        }
        listOf("1:2:3", "::1::2", "2001:db8::1::2", ":::", "192.168.0.1::", "1:192.168.0.1::", "fe80::1%eth0:18420", "1.2.3", "999.1.1.1", "http://x", "/tmp/x", "host:18420", "a b", "-bad.example").forEach {
            error(SettingsValidators.bindAddress(it), SettingsValidators.Error.INVALID_FORMAT)
        }
    }

    @Test fun waterWindow() {
        error(SettingsValidators.waterWindow(9, 9), SettingsValidators.Error.START_EQUALS_END)
        ok(SettingsValidators.waterWindow(9, 21), Unit)
        ok(SettingsValidators.waterWindow(21, 9), Unit)
    }

    @Test fun featureRanges() {
        listOf(SettingsValidators.waitingDuration("200") to 200, SettingsValidators.waitingDuration("10000") to 10000,
            SettingsValidators.waterInterval("15") to 15, SettingsValidators.waterInterval("240") to 240,
            SettingsValidators.waterHour("0") to 0, SettingsValidators.waterHour("23") to 23,
            SettingsValidators.routineHour("0") to 0, SettingsValidators.routineHour("23") to 23,
            SettingsValidators.routineMinute("0") to 0, SettingsValidators.routineMinute("59") to 59).forEach { (r, v) -> ok(r, v) }
        error(SettingsValidators.waitingDuration("199"), SettingsValidators.Error.OUT_OF_RANGE)
        error(SettingsValidators.waitingDuration("10001"), SettingsValidators.Error.OUT_OF_RANGE)
        error(SettingsValidators.waterInterval("14"), SettingsValidators.Error.OUT_OF_RANGE)
        error(SettingsValidators.waterInterval("241"), SettingsValidators.Error.OUT_OF_RANGE)
        error(SettingsValidators.waterHour("24"), SettingsValidators.Error.OUT_OF_RANGE)
        error(SettingsValidators.routineMinute("60"), SettingsValidators.Error.OUT_OF_RANGE)
    }

    @Test fun connectionResetKeepsTokens() {
        val keys = preferenceKeys(ResetScope.CONNECTIONS)
        assertFalse("ipcToken" in keys)
        assertFalse("remoteToken" in keys)
    }

    @Test fun noResetScopeIncludesCredentialsOrChecklistToggle() {
        ResetScope.values().forEach { scope ->
            val keys = preferenceKeys(scope)
            assertFalse("ipcToken in $scope", "ipcToken" in keys)
            assertFalse("remoteToken in $scope", "remoteToken" in keys)
            assertFalse("launchChecklistEnabled in $scope", "launchChecklistEnabled" in keys)
        }
    }
}

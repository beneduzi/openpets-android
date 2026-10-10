package com.openpets.android.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class IpcProtocolTest {

    private val token = "0123456789abcdef0123456789abcdef"

    private fun req(id: String = "r1", version: Int = 1, tok: String = token, method: String = "pet.react", params: String = """{"reaction":"thinking"}"""): String =
        """{"id":"$id","version":$version,"token":"$tok","method":"$method","params":$params}"""

    @Test fun parsesValidReact() {
        val r = parseIpcRequest(req(), token)
        assertEquals("pet.react", r.method)
        assertEquals("r1", r.id)
    }

    @Test fun rejectsWrongToken() {
        assertCode("invalid_token") { parseIpcRequest(req(tok = "wrongtoken1234567890"), token) }
    }

    @Test fun rejectsWrongVersion() {
        assertCode("invalid_version") { parseIpcRequest(req(version = 2), token) }
    }

    @Test fun rejectsUnknownMethod() {
        assertCode("unknown_method") { parseIpcRequest(req(method = "pet.dance"), token) }
    }

    @Test fun rejectsMalformedJson() {
        assertCode("invalid_request") { parseIpcRequest("{not json", token) }
    }

    @Test fun rejectsNonObject() {
        assertCode("invalid_request") { parseIpcRequest("[1,2,3]", token) }
    }

    @Test fun rejectsMissingId() {
        assertCode("invalid_request") {
            parseIpcRequest("""{"version":1,"token":"$token","method":"hello"}""", token)
        }
    }

    @Test fun rejectsTooLongId() {
        assertCode("invalid_request") { parseIpcRequest(req(id = "x".repeat(121)), token) }
    }

    @Test fun errorResponseShape() {
        val resp = errorResponse("r9", IpcProtocolError("invalid_token", "Invalid IPC token."))
        assertEquals("r9", resp.get("id").asString)
        assertEquals(false, resp.get("ok").asBoolean)
        assertEquals("invalid_token", resp.getAsJsonObject("error").get("code").asString)
    }

    @Test fun okResponseShape() {
        val resp = okResponse("r9", null)
        assertEquals(true, resp.get("ok").asBoolean)
    }

    private fun assertCode(code: String, block: () -> Unit) {
        try {
            block()
            fail("Expected IpcProtocolError($code)")
        } catch (e: IpcProtocolError) {
            assertEquals(code, e.code)
        }
    }
}

class SayValidationTest {
    @Test fun acceptsNormalMessage() {
        assertEquals("Working on it", validateSayMessage("  Working on it  "))
    }

    @Test fun rejectsEmpty() {
        assertInvalid { validateSayMessage("   ") }
    }

    @Test fun rejectsNewline() {
        assertInvalid { validateSayMessage("line1\nline2") }
    }

    @Test fun rejectsTooLong() {
        assertInvalid { validateSayMessage("a".repeat(141)) }
    }

    @Test fun rejectsUrl() {
        assertInvalid { validateSayMessage("see https://example.com") }
    }

    @Test fun rejectsPath() {
        assertInvalid { validateSayMessage("open /etc/passwd please") }
    }

    @Test fun rejectsCodeLike() {
        assertInvalid { validateSayMessage("const x = 1") }
    }

    @Test fun rejectsSecretLike() {
        assertInvalid { validateSayMessage("my password is here") }
    }

    private fun assertInvalid(block: () -> Unit) {
        try {
            block()
            fail("Expected invalid_params")
        } catch (e: IpcProtocolError) {
            assertEquals("invalid_params", e.code)
        }
    }
}

class LeaseAndPetIdTest {
    @Test fun optionalLeaseIdNullWhenAbsent() {
        assertNull(validateOptionalLeaseId(null))
    }

    @Test fun optionalLeaseIdRejectsNewline() {
        try {
            validateOptionalLeaseId("abc\ndef")
            fail("expected")
        } catch (e: IpcProtocolError) {
            assertEquals("invalid_params", e.code)
        }
    }

    @Test fun requestedPetIdBlankBecomesNull() {
        assertNull(validateRequestedPetId("   "))
    }

    @Test fun requestedPetIdRejectsSlash() {
        try {
            validateRequestedPetId("a/b")
            fail("expected")
        } catch (e: IpcProtocolError) {
            assertEquals("invalid_params", e.code)
        }
    }

    @Test fun sessionNonceInvalidIsNull() {
        assertNull(validateSessionNonce("bad\nnonce"))
        assertEquals("ok-nonce", validateSessionNonce(" ok-nonce "))
    }

    @Test fun installPetIdRules() {
        assertEquals("tux", validateInstallPetId("tux"))
        listOf("builtin", "Bad Id", "", "-x").forEach { bad ->
            try {
                validateInstallPetId(bad)
                fail("expected reject for '$bad'")
            } catch (e: IpcProtocolError) {
                assertEquals("invalid_params", e.code)
            }
        }
    }
}

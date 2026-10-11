package com.openpets.android.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import com.google.gson.JsonParser

class IpcProtocolTest {

    private val token = "0123456789abcdef0123456789abcdef"

    @Test fun validatesNotificationText() {
        assertEquals("Hello", validateNotifyTitle(" Hello "))
        assertCode("invalid_params") { validateNotifyTitle("  ") }
        assertCode("invalid_params") { validateNotifyTitle("line\nbreak") }
        assertEquals("Body", validateNotifyBody(" Body "))
    }
    @Test fun validatesStatusText() {
        assertEquals("Working", validateStatusText(" Working "))
        assertCode("invalid_params") { validateStatusText("  ") }
        assertCode("invalid_params") { validateStatusText("line\nbreak") }
    }

    private fun req(id: String = "r1", version: Int = 1, tok: String = token, method: String = "pet.react", params: String = """{"reaction":"thinking"}"""): String =
        """{"id":"$id","version":$version,"token":"$tok","method":"$method","params":$params}"""

    @Test fun parsesValidReact() {
        val r = parseIpcRequest(req(), token)
        assertEquals("pet.react", r.method)
        assertEquals("r1", r.id)
    }

    @Test fun bubbleDismissIsWhitelisted() {
        val r = parseIpcRequest(req(method = "pet.bubbleDismiss", params = """{"id":"b1"}"""), token)
        assertEquals("pet.bubbleDismiss", r.method)
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

class IpcLeaseTest {
    @Test fun heartbeatRenewsExpiry() {
        var now = 1_000L
        val sink = object : PetEventSink {
            override fun onReact(reaction: String, source: String?) = Unit
            override fun onSay(request: SayRequest) = Unit
            override fun onMedia(request: MediaRequest) = Unit
            override fun onDismissBubble(id: String?) = Unit
            override fun onMove(request: MoveRequest) = Unit
            override fun onScale(scale: Float) = Unit
            override fun onVisibility(visible: Boolean) = Unit
            override fun onNotify(request: NotificationRequest) = Unit
            override fun onStatus(text: String, tone: String?) = Unit
        }
        val dispatcher = IpcDispatcher(sink, "0.1.0", StaticPetCatalogProvider(), null, false, { now })
        fun request(id: String, method: String, params: String) = IpcRequest(
            id = id,
            version = 1,
            token = "t",
            method = method,
            params = JsonParser.parseString(params),
        )

        val acquired = dispatcher.dispatch(request("1", "lease.acquire", "{}"))
        val leaseId = acquired.getAsJsonObject("result").get("leaseId").asString
        val originalExpiry = acquired.getAsJsonObject("result").get("expiresAt").asLong
        now += 5_000L
        val heartbeat = dispatcher.dispatch(request("2", "lease.heartbeat", """{"leaseId":"$leaseId"}"""))
        val renewedExpiry = heartbeat.getAsJsonObject("result").get("expiresAt").asLong
        assertTrue(renewedExpiry > originalExpiry)
        now = renewedExpiry + 1
        try {
            dispatcher.dispatch(request("3", "lease.heartbeat", """{"leaseId":"$leaseId"}"""))
            fail("Expected unknown_lease")
        } catch (e: IpcProtocolError) {
            assertEquals("unknown_lease", e.code)
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

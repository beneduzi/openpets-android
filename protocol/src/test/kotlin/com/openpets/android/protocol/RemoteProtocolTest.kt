package com.openpets.android.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RemoteProtocolTest {

    private val token = "A".repeat(43)

    private fun req(
        id: String = "req-1",
        protocol: String = REMOTE_PROTOCOL,
        version: Int = 1,
        clientId: String? = null,
        tok: String = token,
        method: String = "pet.react",
        params: String = """{"reaction":"running"}""",
    ): String {
        val cid = if (clientId == null) "" else ""","clientId":"$clientId""""
        return """{"id":"$id","protocol":"$protocol","version":$version$cid,"token":"$tok","method":"$method","params":$params}"""
    }

    @Test fun parsesValidReact() {
        val r = parseRemoteControlRequest(req())
        assertEquals("pet.react", r.method)
        assertEquals(REMOTE_PROTOCOL, r.protocol)
    }

    @Test fun parsesWithClientId() {
        val r = parseRemoteControlRequest(req(clientId = "device_01"))
        assertEquals("device_01", r.clientId)
    }

    @Test fun rejectsWrongProtocol() {
        assertCode("invalid_request") { parseRemoteControlRequest(req(protocol = "other")) }
    }

    @Test fun rejectsWrongVersion() {
        assertCode("invalid_version") { parseRemoteControlRequest(req(version = 2)) }
    }

    @Test fun rejectsUnknownMethod() {
        assertCode("unknown_method") { parseRemoteControlRequest(req(method = "pet.dance")) }
    }

    @Test fun rejectsShortToken() {
        assertCode("invalid_request") { parseRemoteControlRequest(req(tok = "A".repeat(42))) }
    }

    @Test fun rejectsMalformedClientId() {
        assertCode("invalid_request") { parseRemoteControlRequest(req(clientId = "bad id")) }
    }

    @Test fun validatesScopeList() {
        assertEquals(listOf("status", "react"), validateRemoteScopeList(listOf("status", "react")))
        assertEquals(REMOTE_SCOPES, validateRemoteScopeList(listOf("status", "react", "say")))
        assertTrue(runCatching { validateRemoteScopeList(listOf("react", "status")) }.isFailure)
        assertTrue(runCatching { validateRemoteScopeList(listOf("status")) }.isFailure)
    }

    @Test fun validatesClientName() {
        assertEquals("Phone", validateRemoteClientName("  Phone  "))
        assertTrue(runCatching { validateRemoteClientName("") }.isFailure)
        assertTrue(runCatching { validateRemoteClientName("a".repeat(81)) }.isFailure)
    }

    @Test fun validatesSayParams() {
        val ok = validateRemoteSayParams(com.google.gson.JsonParser.parseString("""{"message":"hello","reaction":"waving"}"""))
        assertEquals("hello", ok.message)
        assertEquals("waving", ok.reaction)
        assertTrue(runCatching {
            validateRemoteSayParams(com.google.gson.JsonParser.parseString("""{"message":"see https://x.com"}"""))
        }.isFailure)
        assertTrue(runCatching {
            validateRemoteSayParams(com.google.gson.JsonParser.parseString("""{"message":"hi","extra":1}"""))
        }.isFailure)
    }

    @Test fun validatesReactParams() {
        assertEquals("error", validateRemoteReactParams(com.google.gson.JsonParser.parseString("""{"reaction":"error"}""")))
        assertTrue(runCatching {
            validateRemoteReactParams(com.google.gson.JsonParser.parseString("""{"reaction":"nope"}"""))
        }.isFailure)
    }

    @Test fun bindAddressRules() {
        assertTrue(isValidRemoteBindAddress("192.168.0.14"))
        assertTrue(isValidRemoteBindAddress("10.0.0.1"))
        assertTrue(isValidRemoteBindAddress("100.64.0.1"))
        assertTrue(isValidRemoteBindAddress("127.0.0.1"))
        assertFalse(isValidRemoteBindAddress("8.8.8.8"))
        assertFalse(isValidRemoteBindAddress("0.0.0.0"))
        assertFalse(isValidRemoteBindAddress("example.com"))
        assertFalse(isValidRemoteBindAddress("192.168.0.999"))
    }

    @Test fun normalizesMappedIpv4() {
        assertEquals("192.168.1.5", normalizeRemoteIpv4Address("::ffff:192.168.1.5"))
        assertNull(normalizeRemoteIpv4Address("::ffff:not-an-ip"))
    }

    @Test fun privatePeerAddressRules() {
        assertTrue(isValidRemotePeerAddress("192.168.0.5"))
        assertTrue(isValidRemotePeerAddress("10.0.0.7"))
        assertTrue(isValidRemotePeerAddress("::ffff:192.168.1.2"))
        assertFalse(isValidRemotePeerAddress("8.8.8.8"))
        assertFalse(isValidRemotePeerAddress("0.0.0.0"))
        assertFalse(isValidRemotePeerAddress(null))
    }

    @Test fun errorResponseShape() {
        val resp = errorRemoteResponse("req-1", "invalid_request")
        assertEquals(false, resp.get("ok").asBoolean)
        assertEquals("invalid_request", resp.getAsJsonObject("error").get("code").asString)
    }

    private fun assertCode(code: String, block: () -> Unit) {
        try {
            block()
            fail("Expected RemoteProtocolError($code)")
        } catch (e: RemoteProtocolError) {
            assertEquals(code, e.code)
        }
    }
}

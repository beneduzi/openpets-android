package com.openpets.android.net

import com.google.gson.JsonParser
import com.openpets.android.PetActionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WsMessagesTest {
    @Test fun buildsActionEvent() {
        val event = JsonParser.parseString(WsMessages.eventFromAction(PetActionEvent("bubble-1", "approve"))).asJsonObject
        assertEquals("event", event.get("type").asString)
        assertEquals("action", event.get("event").asString)
        assertEquals("bubble-1", event.get("bubbleId").asString)
        assertEquals("approve", event.get("actionId").asString)
    }
    @Test fun helloStatusAndEventMessagesRoundTrip() {
        assertEquals("hello", JsonParser.parseString(WsMessages.hello("1.2")).asJsonObject.get("type").asString)
        assertEquals("1.2", JsonParser.parseString(WsMessages.hello("1.2")).asJsonObject.get("appVersion").asString)
        val value = JsonParser.parseString("{\"ok\":true,\"count\":2}")
        assertEquals(value.toString(), JsonParser.parseString(WsMessages.status(value)).asJsonObject.get("result").toString())
        val event = JsonParser.parseString(WsMessages.event("react", JsonParser.parseString("{\"reaction\":\"idle\"}"))).asJsonObject
        assertEquals("event", event.get("type").asString); assertEquals("react", event.get("kind").asString)
        assertEquals("idle", event.getAsJsonObject("payload").get("reaction").asString)
    }

    @Test fun parsesCommandsAndBuildsResultsAndErrors() {
        val command = WsMessages.parseCommand("{\"id\":\"a\",\"method\":\"pet.react\",\"params\":{\"reaction\":\"idle\"}}")
        assertEquals("a", command.id); assertEquals("pet.react", command.method)
        assertEquals("idle", command.params!!.asJsonObject.get("reaction").asString)
        val result = JsonParser.parseString(WsMessages.result("a", true, JsonParser.parseString("{\"ok\":true}"))).asJsonObject
        assertTrue(result.get("ok").asBoolean); assertEquals("a", result.get("id").asString)
        val error = JsonParser.parseString(WsMessages.error("b", "bad", "No")).asJsonObject
        assertFalse(error.get("ok").asBoolean); assertEquals("bad", error.getAsJsonObject("error").get("code").asString)
    }

    @Test fun parsesHostStatusFrames() {
        val status = WsMessages.parseHostStatus("""{"type":"status.host","params":{"cpuPct":42.5,"ramUsedMb":1024,"ramTotalMb":4096.0,"diskUsedPct":55,"netRxKbps":12.5,"netTxKbps":9,"loadAvg":1.25,"ts":123}}""")!!
        assertEquals(42, status.cpuPct); assertEquals(1024L, status.ramUsedMb); assertEquals(4096L, status.ramTotalMb)
        assertEquals(55, status.diskUsedPct); assertEquals(12L, status.netRxKbps); assertEquals(9L, status.netTxKbps)
        assertEquals(1.25, status.loadAvg!!, 0.0); assertEquals(123L, status.tsMs)
        assertEquals(null, WsMessages.parseHostStatus("{\"type\":\"hello\",\"params\":{}}"))
        assertEquals(null, WsMessages.parseHostStatus("{\"type\":\"status.host\"}"))
    }
}

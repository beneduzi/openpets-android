package com.openpets.android.net

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonObject
import com.openpets.android.PetActionEvent
import com.openpets.android.companion.HostStatus

data class WsCommand(val id: String, val method: String, val params: JsonElement?)

object WsMessages {
    fun parseHostStatus(text: String): HostStatus? {
        return try {
        val root = JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject ?: return null
        if (root.get("type")?.takeIf { it.isJsonPrimitive }?.asString != "status.host") return null
        val params = root.get("params")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        fun number(name: String): Double? = params.get(name)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
        HostStatus(number("cpuPct")?.toInt(), number("ramUsedMb")?.toLong(), number("ramTotalMb")?.toLong(), number("diskUsedPct")?.toInt(), number("netRxKbps")?.toLong(), number("netTxKbps")?.toLong(), number("loadAvg"), number("ts")?.toLong() ?: System.currentTimeMillis())
        } catch (_: Exception) { null }
    }

    fun hello(appVersion: String): String = JsonObject().apply { addProperty("type", "hello"); addProperty("appVersion", appVersion) }.toString()
    fun status(resultJson: JsonElement): String = JsonObject().apply { addProperty("type", "status"); add("result", resultJson) }.toString()
    fun event(kind: String, payloadJson: JsonElement): String = JsonObject().apply { addProperty("type", "event"); addProperty("kind", kind); add("payload", payloadJson) }.toString()
    fun eventFromSay(payloadJson: JsonElement) = event("say", payloadJson)
    fun eventFromReact(payloadJson: JsonElement) = event("react", payloadJson)
    fun eventFromMedia(payloadJson: JsonElement) = event("media", payloadJson)
    fun eventFromDismiss(payloadJson: JsonElement) = event("dismiss", payloadJson)
    fun eventFromHud(payloadJson: JsonElement) = event("hud", payloadJson)
    fun eventFromAction(actionEvent: PetActionEvent): String = JsonObject().apply {
        addProperty("type", "event"); addProperty("event", "action")
        actionEvent.bubbleId?.let { addProperty("bubbleId", it) }
        addProperty("actionId", actionEvent.actionId)
    }.toString()

    fun parseCommand(text: String): WsCommand {
        val obj = JsonParser.parseString(text).asJsonObject
        val id = obj.get("id")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("Missing id")
        val method = obj.get("method")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("Missing method")
        val params = obj.get("params")?.takeUnless { it.isJsonNull }
        return WsCommand(id, method, params)
    }

    fun result(id: String, ok: Boolean, resultJson: JsonElement?): String = JsonObject().apply {
        addProperty("id", id); addProperty("ok", ok); if (resultJson != null) add("result", resultJson)
    }.toString()

    fun error(id: String, code: String, message: String): String = JsonObject().apply {
        addProperty("id", id); addProperty("ok", false)
        add("error", JsonObject().apply { addProperty("code", code); addProperty("message", message) })
    }.toString()
}

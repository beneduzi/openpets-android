package com.openpets.android.net

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonObject
import com.openpets.android.PetActionEvent
import com.openpets.android.FormSubmit
import com.openpets.android.companion.HostStatus
import com.openpets.android.protocol.MoveRequest
import com.openpets.android.CommandInvoked

data class WsCommand(val id: String, val method: String, val params: JsonElement?)

object WsMessages {
    fun parseHostStatus(text: String): HostStatus? {
        return try {
        val root = JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject ?: return null
        if (root.get("type")?.takeIf { it.isJsonPrimitive }?.asString != "status.host") return null
        val params = root.get("params")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        fun number(name: String): Double? = params.get(name)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
        val usagePct = number("usagePct")?.toInt()?.coerceIn(0, 100)
        val usageLabel = params.get("usageLabel")?.takeUnless { it.isJsonNull }
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.trim()?.take(24)?.takeIf { it.isNotEmpty() }
        HostStatus(number("cpuPct")?.toInt(), number("ramUsedMb")?.toLong(), number("ramTotalMb")?.toLong(), number("diskUsedPct")?.toInt(), number("netRxKbps")?.toLong(), number("netTxKbps")?.toLong(), number("loadAvg"), number("ts")?.toLong() ?: System.currentTimeMillis(), usagePct, usageLabel)
        } catch (_: Exception) { null }
    }

    private fun capabilities() = JsonObject().apply {
        addProperty("actions", true); addProperty("commands", true); addProperty("hud", true); addProperty("media", true)
        addProperty("silentUpdates", true); addProperty("formSubmit", true); addProperty("pointerPhases", true); addProperty("voiceCaptureId", true)
    }
    private fun limits() = JsonObject().apply {
        addProperty("maxMessage", 140); addProperty("maxHudItems", 4); addProperty("maxBubbleActions", 4)
        addProperty("maxInputs", 4); addProperty("maxHudLabel", 24); addProperty("maxFrameBytes", 1048576)
    }
    fun hello(appVersion: String): String = JsonObject().apply {
        addProperty("type", "hello"); addProperty("appVersion", appVersion); add("capabilities", capabilities()); add("limits", limits())
    }.toString()
    fun status(resultJson: JsonElement): String = JsonObject().apply {
        addProperty("type", "status"); add("result", resultJson); add("capabilities", capabilities()); add("limits", limits())
    }.toString()
    fun event(kind: String, payloadJson: JsonElement): String = JsonObject().apply { addProperty("type", "event"); addProperty("event", kind); add("payload", payloadJson) }.toString()
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
    fun eventFromSubmit(event: FormSubmit): String = JsonObject().apply {
        addProperty("type", "event"); addProperty("event", "submit")
        event.bubbleId?.let { addProperty("bubbleId", it) }
        add("values", com.google.gson.Gson().toJsonTree(event.values))
    }.toString()
    fun eventFromInputText(text: String, captureId: String? = null): String = JsonObject().apply {
        addProperty("type", "event"); addProperty("event", "input.text"); addProperty("text", text)
        captureId?.let { addProperty("captureId", it) }
    }.toString()
    fun eventFromStatus(text: String, tone: String?): String = JsonObject().apply {
        addProperty("type", "event"); addProperty("event", "status"); addProperty("text", text); tone?.let { addProperty("tone", it) }
    }.toString()
    fun eventFromStatusClear(): String = JsonObject().apply {
        addProperty("type", "event"); addProperty("event", "status"); addProperty("clear", true)
    }.toString()
    fun eventFromPower(plugged: Boolean, level: Int): String = JsonObject().apply {
        addProperty("type", "event"); addProperty("event", "power"); addProperty("plugged", plugged); addProperty("level", level)
    }.toString()

    fun eventPointer(kind: String, phase: String, x: Float, y: Float): String = JsonObject().apply {
        addProperty("type", "event"); addProperty("event", "pointer"); addProperty("kind", kind); addProperty("phase", phase); addProperty("x", x); addProperty("y", y)
    }.toString()
    fun eventMove(request: MoveRequest): String = JsonObject().apply {
        addProperty("type", "event"); addProperty("event", "move"); addProperty("x", request.x); request.y?.let { addProperty("y", it) }; addProperty("durationMs", request.durationMs)
    }.toString()
    fun eventScale(scale: Float): String = JsonObject().apply { addProperty("type", "event"); addProperty("event", "scale"); addProperty("scale", scale) }.toString()
    fun eventVisibility(visible: Boolean): String = JsonObject().apply { addProperty("type", "event"); addProperty("event", "visibility"); addProperty("visible", visible) }.toString()
    fun eventFromCommand(event: CommandInvoked): String = JsonObject().apply {
        addProperty("type", "event"); addProperty("event", "command"); addProperty("source", event.source); addProperty("commandId", event.commandId)
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

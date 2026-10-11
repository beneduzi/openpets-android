package com.openpets.android.protocol

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Wire constants for the OpenPets local IPC protocol (`openpets-ipc`, v1). */
const val IPC_PROTOCOL = "openpets-ipc"
const val IPC_VERSION = 1
const val MAX_IPC_MESSAGE_BYTES = 16 * 1024
const val IPC_CONNECT_TIMEOUT_MS = 2_000
const val IPC_RESPONSE_TIMEOUT_MS = 3_000
const val IPC_CONNECTION_DEADLINE_MS = 120_000L
const val MAX_IPC_RATE_LIMIT = 600
const val IPC_RATE_LIMIT_WINDOW_MS = 60_000L
const val TRANSIENT_DISPLAY_MS = 6_000
const val DEFAULT_WAITING_ANIMATION_MS = 1010

const val DEFAULT_MEDIA_DURATION_MS = 8_000
const val MIN_MEDIA_DURATION_MS = 1_000
const val MAX_MEDIA_DURATION_MS = 30_000
const val MAX_MEDIA_BYTES = 10 * 1024 * 1024

/** Max size of an IPC request line (media can be pushed inline as base64). */
const val MAX_MEDIA_REQUEST_BYTES = 16 * 1024 * 1024

val REACTIONS: List<String> = listOf(
    "idle", "thinking", "working", "editing", "running", "testing",
    "waiting", "waving", "success", "error", "celebrating",
)

val IPC_METHODS: List<String> = listOf(
    "hello", "status", "pets.list", "pets.install", "pets.install-local",
    "lease.acquire", "lease.heartbeat", "lease.release",
    "pet.react", "pet.say", "pet.showMedia", "pet.bubbleDismiss", "pet.move", "pet.setScale", "pet.hide", "pet.show", "notify", "status.clear", "status.publish",
)

/** Protocol violation carrying the machine-readable error code used on the wire. */
class IpcProtocolError(val code: String, message: String) : Exception(message)

/** Parsed, validated IPC request. */
data class IpcRequest(
    val id: String,
    val version: Int,
    val token: String,
    val method: String,
    val params: JsonElement?,
)

private val CODE_LIKE = Regex("```|<script|function\\s+\\w+|=>|\\b(class|import|export|const|let|var)\\b")
private val URL_OR_PATH = Regex("https?://|www\\.|/[\\w.-]+/[\\w./-]+|[A-Za-z]:\\\\")
private val SECRET_LIKE = Regex(
    "(api[_-]?key|secret|token|password|passwd|BEGIN [A-Z ]+PRIVATE KEY)",
    RegexOption.IGNORE_CASE,
)

/**
 * Parse and validate a raw IPC request line against [expectedToken].
 * Mirrors `parseIpcRequest` from the OpenPets desktop `local-ipc-protocol.js`.
 */
fun parseIpcRequest(raw: String, expectedToken: String): IpcRequest {
    val parsed: JsonElement = try {
        JsonParser.parseString(raw)
    } catch (e: Exception) {
        throw IpcProtocolError("invalid_request", "IPC request must be valid JSON.")
    }
    if (!parsed.isJsonObject) {
        throw IpcProtocolError("invalid_request", "IPC request must be an object.")
    }
    val obj = parsed.asJsonObject
    val id = obj.stringOrNull("id")
        ?: throw IpcProtocolError("invalid_request", "IPC request id is invalid.")
    if (id.length < 1 || id.length > 120) {
        throw IpcProtocolError("invalid_request", "IPC request id is invalid.")
    }
    val version = obj.intOrNull("version")
        ?: throw IpcProtocolError("invalid_version", "Unsupported IPC protocol version.")
    if (version != IPC_VERSION) {
        throw IpcProtocolError("invalid_version", "Unsupported IPC protocol version.")
    }
    val token = obj.stringOrNull("token")
        ?: throw IpcProtocolError("invalid_token", "Invalid IPC token.")
    if (token != expectedToken) {
        throw IpcProtocolError("invalid_token", "Invalid IPC token.")
    }
    val method = obj.stringOrNull("method")
        ?: throw IpcProtocolError("unknown_method", "Unknown IPC method.")
    if (method !in IPC_METHODS) {
        throw IpcProtocolError("unknown_method", "Unknown IPC method.")
    }
    return IpcRequest(
        id = id,
        version = version,
        token = token,
        method = method,
        params = obj.get("params"),
    )
}

/** Build a success response `{ id, ok:true, result }`. */
fun okResponse(id: String, result: JsonElement?): JsonObject {
    val out = JsonObject()
    out.addProperty("id", id)
    out.addProperty("ok", true)
    if (result != null) out.add("result", result)
    return out
}

/** Build an error response `{ id, ok:false, error:{code,message} }`. */
fun errorResponse(id: String, error: Throwable): JsonObject {
    val code: String
    val message: String
    if (error is IpcProtocolError) {
        code = error.code
        message = error.message ?: "IPC request failed."
    } else {
        code = "internal_error"
        message = error.message ?: "IPC request failed."
    }
    val errObj = JsonObject()
    errObj.addProperty("code", code)
    errObj.addProperty("message", message)
    val out = JsonObject()
    out.addProperty("id", id)
    out.addProperty("ok", false)
    out.add("error", errObj)
    return out
}

// ---- Validators (throwing IpcProtocolError("invalid_params", ...)) ----

fun validateReaction(value: String?): String {
    if (value == null || value !in REACTIONS) {
        throw IpcProtocolError("invalid_params", "Invalid pet reaction.")
    }
    return value
}

fun validateAccentHex(value: String?): String? {
    if (value == null || value.isBlank()) return null
    if (!Regex("^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$").matches(value)) {
        throw IpcProtocolError("invalid_params", "Accent is invalid.")
    }
    return value
}

fun validateMoveX(value: Float?): Float = validateFraction(value, "Move x")
fun validateMoveY(value: Float?): Float? = value?.let { validateFraction(it, "Move y") }
private fun validateFraction(value: Float?, label: String): Float {
    if (value == null || !value.isFinite() || value !in 0f..1f) throw IpcProtocolError("invalid_params", "$label must be between 0 and 1.")
    return value
}
fun validateMoveDuration(value: Int?): Int {
    if (value == null) return 300
    if (value !in 0..10_000) throw IpcProtocolError("invalid_params", "Move duration is out of range.")
    return value
}
fun validateScale(value: Float?): Float {
    if (value == null || !value.isFinite() || value !in 0.25f..3f) throw IpcProtocolError("invalid_params", "Scale must be between 0.25 and 3.")
    return value
}

fun validateNotifyTitle(value: String?): String {
    val title = value?.trim() ?: throw IpcProtocolError("invalid_params", "Notification title is required.")
    if (title.isEmpty() || title.length > 80 || Regex("[\\r\\n]").containsMatchIn(title)) {
        throw IpcProtocolError("invalid_params", "Notification title is invalid.")
    }
    return title
}

fun validateNotifyBody(value: String?): String? {
    if (value == null) return null
    val body = value.trim()
    if (body.length > 200 || Regex("[\\r\\n]").containsMatchIn(body)) {
        throw IpcProtocolError("invalid_params", "Notification body is invalid.")
    }
    return body
}

fun validateStatusText(value: String?): String {
    val text = value?.trim() ?: throw IpcProtocolError("invalid_params", "Status text is required.")
    if (text.isEmpty() || text.length > 80 || Regex("[\\r\\n]").containsMatchIn(text)) {
        throw IpcProtocolError("invalid_params", "Status text is invalid.")
    }
    return text
}

fun validateStatusTone(value: String?): String? {
    if (value == null) return null
    if (value !in setOf("default", "success", "warning", "error")) {
        throw IpcProtocolError("invalid_params", "Status tone is invalid.")
    }
    return value
}

fun validateSayMessage(value: String?): String {
    if (value == null) {
        throw IpcProtocolError("invalid_params", "Message must be a string.")
    }
    val message = value.trim()
    if (message.isEmpty()) {
        throw IpcProtocolError("invalid_params", "Message cannot be empty.")
    }
    if (message.length > 140) {
        throw IpcProtocolError("invalid_params", "Message is too long.")
    }
    if (Regex("[\\r\\n]").containsMatchIn(message)) {
        throw IpcProtocolError("invalid_params", "Message must be single-line.")
    }
    if (CODE_LIKE.containsMatchIn(message)) {
        throw IpcProtocolError("invalid_params", "Message looks like code.")
    }
    if (URL_OR_PATH.containsMatchIn(message)) {
        throw IpcProtocolError("invalid_params", "Message contains a URL or path-like content.")
    }
    if (SECRET_LIKE.containsMatchIn(message)) {
        throw IpcProtocolError("invalid_params", "Message looks secret-like.")
    }
    return message
}

fun validateOptionalLeaseId(value: String?): String? {
    if (value == null) return null
    if (value.length < 1 || value.length > 120 || Regex("[\\u0000\\r\\n]").containsMatchIn(value)) {
        throw IpcProtocolError("invalid_params", "Invalid lease id.")
    }
    return value
}

fun validateRequestedPetId(value: String?): String? {
    if (value == null) return null
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    val bytes = trimmed.toByteArray(Charsets.UTF_8).size
    if (bytes > 128 || Regex("[\\x00-\\x1F\\x7F/\\\\]").containsMatchIn(trimmed)) {
        throw IpcProtocolError("invalid_params", "Requested pet id is outside CLI bounds.")
    }
    return trimmed
}

fun validateSessionNonce(value: String?): String? {
    if (value == null) return null
    val trimmed = value.trim()
    if (trimmed.isEmpty() || trimmed.length > 128 || Regex("[\\x00-\\x1F\\x7F]").containsMatchIn(trimmed)) {
        return null
    }
    return trimmed
}

fun validateInstallPetId(value: String?): String {
    if (value == null || !Regex("^[a-z0-9][a-z0-9_-]{0,63}$").matches(value) || value == "builtin") {
        throw IpcProtocolError("invalid_params", "Invalid pet id.")
    }
    return value
}

// ---- Media (pet.showMedia) ----

/**
 * Validates a media URL. Unlike the desktop (which uses a local path), the
 * Android companion fetches the media over http(s).
 */
fun validateMediaUrl(value: String?): String {
    if (value == null) throw IpcProtocolError("invalid_params", "Media url is required.")
    val trimmed = value.trim()
    if (trimmed.isEmpty() || trimmed.length > 2048 || trimmed.any { it.isISOControl() || it.isWhitespace() }) {
        throw IpcProtocolError("invalid_params", "Media url is invalid.")
    }
    val scheme = try {
        java.net.URI(trimmed).scheme?.lowercase()
    } catch (e: Exception) {
        throw IpcProtocolError("invalid_params", "Media url is invalid.")
    }
    if (scheme != "http" && scheme != "https") {
        throw IpcProtocolError("invalid_params", "Media url must be http(s).")
    }
    return trimmed
}

fun validateMediaDurationMs(value: Int?): Int {
    if (value == null) return DEFAULT_MEDIA_DURATION_MS
    if (value < MIN_MEDIA_DURATION_MS || value > MAX_MEDIA_DURATION_MS) {
        throw IpcProtocolError("invalid_params", "Media duration is out of range.")
    }
    return value
}

/** Validates the optional base64 image payload (size-capped). Returns the trimmed payload or null. */
fun validateMediaData(value: String?): String? {
    if (value == null) return null
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    val maxBase64Length = (MAX_MEDIA_BYTES.toLong() * 4 / 3 + 8).toInt()
    if (trimmed.length > maxBase64Length) {
        throw IpcProtocolError("invalid_params", "Media data is too large.")
    }
    return trimmed
}

private val BLOCKED_CLICK_SCHEMES = setOf(
    "http", "file", "javascript", "data", "vbscript", "blob", "about", "chrome",
    "ms-appx", "ms-appx-web", "shell", "search-ms", "search", "res",
)

fun validateMediaClickUrl(value: String?): String? {
    if (value == null) return null
    val trimmed = value.trim()
    if (trimmed.isEmpty() || trimmed.length > 2048 || trimmed.any { it.isISOControl() || it.isWhitespace() }) {
        throw IpcProtocolError("invalid_params", "Media click url is invalid.")
    }
    val scheme = try {
        java.net.URI(trimmed).scheme?.lowercase()
    } catch (e: Exception) {
        throw IpcProtocolError("invalid_params", "Media click url must be absolute.")
    }
    if (scheme == null || scheme in BLOCKED_CLICK_SCHEMES) {
        throw IpcProtocolError("invalid_params", "Media click url scheme is not allowed.")
    }
    return trimmed
}

// ---- Bubble actions ----

private val ACTION_ID = Regex("^[A-Za-z0-9_-]{1,64}$")
private val ACTION_STYLES = setOf("default", "primary", "danger")

/** Parses an optional `actions` array (up to 4 tappable bubble buttons). */
fun parseActions(element: com.google.gson.JsonElement?): List<PetAction> {
    if (element == null || element.isJsonNull) return emptyList()
    if (!element.isJsonArray) throw IpcProtocolError("invalid_params", "Actions must be an array.")
    val array = element.asJsonArray
    if (array.size() > 4) throw IpcProtocolError("invalid_params", "Too many actions.")
    val actions = ArrayList<PetAction>()
    for (item in array) {
        if (!item.isJsonObject) throw IpcProtocolError("invalid_params", "Action must be an object.")
        val obj = item.asJsonObject
        val id = obj.stringOrNull("id")
            ?: throw IpcProtocolError("invalid_params", "Action id is required.")
        if (!ACTION_ID.matches(id)) throw IpcProtocolError("invalid_params", "Action id is invalid.")
        val label = (obj.stringOrNull("label") ?: "").trim()
        if (label.isEmpty() || label.length > 40 || label.any { it.isISOControl() }) {
            throw IpcProtocolError("invalid_params", "Action label is invalid.")
        }
        val style = obj.stringOrNull("style") ?: "default"
        if (style !in ACTION_STYLES) throw IpcProtocolError("invalid_params", "Action style is invalid.")
        val url = obj.stringOrNull("url")?.let { validateMediaClickUrl(it) }
        val callbackUrl = obj.stringOrNull("callbackUrl")?.let { validateMediaUrl(it) }
        actions.add(PetAction(id, label, style, url, callbackUrl))
    }
    return actions
}

// ---- Bubble HUD / inputs / id ----

private val BUBBLE_ID = Regex("^[A-Za-z0-9_-]{1,64}$")
private val INPUT_TYPES = setOf("text", "number")

fun validateBubbleId(value: String?): String? {
    if (value == null) return null
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    if (!BUBBLE_ID.matches(trimmed)) throw IpcProtocolError("invalid_params", "Bubble id is invalid.")
    return trimmed
}

fun parseHud(element: com.google.gson.JsonElement?): List<HudItem> {
    if (element == null || element.isJsonNull) return emptyList()
    if (!element.isJsonArray) throw IpcProtocolError("invalid_params", "HUD must be an array.")
    val array = element.asJsonArray
    if (array.size() > 4) throw IpcProtocolError("invalid_params", "Too many HUD items.")
    val items = ArrayList<HudItem>()
    for (item in array) {
        if (!item.isJsonObject) throw IpcProtocolError("invalid_params", "HUD item must be an object.")
        val obj = item.asJsonObject
        val label = (obj.stringOrNull("label") ?: "").trim()
        if (label.isEmpty() || label.length > 24 || label.any { it.isISOControl() }) {
            throw IpcProtocolError("invalid_params", "HUD item label is invalid.")
        }
        val value = obj.floatOrNull("value")
            ?: throw IpcProtocolError("invalid_params", "HUD item value is required.")
        val max = obj.floatOrNull("max") ?: 1f
        if (max <= 0f) throw IpcProtocolError("invalid_params", "HUD item max is invalid.")
        val tone = obj.stringOrNull("tone")?.takeIf { it.length <= 12 }
        val icon = obj.stringOrNull("icon")?.takeIf { it.length <= 4 }
        items.add(HudItem(label, value, max, tone, icon))
    }
    return items
}

fun parseInputs(element: com.google.gson.JsonElement?): List<BubbleInput> {
    if (element == null || element.isJsonNull) return emptyList()
    if (!element.isJsonArray) throw IpcProtocolError("invalid_params", "Inputs must be an array.")
    val array = element.asJsonArray
    if (array.size() > 4) throw IpcProtocolError("invalid_params", "Too many inputs.")
    val inputs = ArrayList<BubbleInput>()
    for (item in array) {
        if (!item.isJsonObject) throw IpcProtocolError("invalid_params", "Input must be an object.")
        val obj = item.asJsonObject
        val id = obj.stringOrNull("id")
            ?: throw IpcProtocolError("invalid_params", "Input id is required.")
        if (!BUBBLE_ID.matches(id)) throw IpcProtocolError("invalid_params", "Input id is invalid.")
        val label = (obj.stringOrNull("label") ?: "").trim()
        if (label.isEmpty() || label.length > 40 || label.any { it.isISOControl() }) {
            throw IpcProtocolError("invalid_params", "Input label is invalid.")
        }
        val type = obj.stringOrNull("type") ?: "text"
        if (type !in INPUT_TYPES) throw IpcProtocolError("invalid_params", "Input type is invalid.")
        val placeholder = obj.stringOrNull("placeholder")?.takeIf { it.length <= 40 }
        inputs.add(BubbleInput(id, label, type, placeholder))
    }
    return inputs
}

// ---- Gson helpers ----

internal fun JsonObject.stringOrNull(key: String): String? {
    val el = get(key) ?: return null
    if (!el.isJsonPrimitive) return null
    val prim = el.asJsonPrimitive
    return if (prim.isString) prim.asString else null
}

internal fun JsonObject.intOrNull(key: String): Int? {
    val el = get(key) ?: return null
    if (!el.isJsonPrimitive) return null
    val prim = el.asJsonPrimitive
    return if (prim.isNumber) prim.asInt else null
}

internal fun JsonObject.floatOrNull(key: String): Float? {
    val el = get(key) ?: return null
    if (!el.isJsonPrimitive) return null
    val prim = el.asJsonPrimitive
    return if (prim.isNumber) prim.asFloat else null
}

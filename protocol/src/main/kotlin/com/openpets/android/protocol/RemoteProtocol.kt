package com.openpets.android.protocol

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Wire constants for the OpenPets remote-control protocol (`openpets-remote`, v1). */
const val REMOTE_PROTOCOL = "openpets-remote"
const val REMOTE_VERSION = 1
const val MAX_REMOTE_MESSAGE_BYTES = 4 * 1024
const val REMOTE_SOCKET_TIMEOUT_MS = 3_000
const val REMOTE_CONNECTION_DEADLINE_MS = 10_000L
const val REMOTE_MAX_CONCURRENT_SOCKETS = 64
const val REMOTE_RATE_LIMIT_MAX = 30
const val REMOTE_RATE_LIMIT_WINDOW_MS = 60_000

val REMOTE_SCOPES: List<String> = listOf("status", "react", "say")
val REMOTE_METHODS: List<String> = listOf("status", "pet.react", "pet.say")

/** Protocol violation for the remote-control protocol. */
class RemoteProtocolError(val code: String, message: String = "Remote request rejected.") : Exception(message)

data class RemoteRequest(
    val id: String,
    val protocol: String,
    val version: Int,
    val clientId: String?,
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
private val OPAQUE_ID = Regex("^[A-Za-z0-9_-]+$")
private val IPV4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")

/** Parse and validate a raw remote request line. Mirrors `parseRemoteControlRequest`. */
fun parseRemoteControlRequest(raw: String): RemoteRequest {
    val parsed: JsonElement = try {
        com.google.gson.JsonParser.parseString(raw)
    } catch (e: Exception) {
        throw RemoteProtocolError("invalid_request")
    }
    if (!parsed.isJsonObject) throw RemoteProtocolError("invalid_request")
    val obj = parsed.asJsonObject

    val id = obj.stringOrNull("id")
    if (id == null || id.length < 1 || id.length > 120 || Regex("[\\u0000\\r\\n]").containsMatchIn(id)) {
        throw RemoteProtocolError("invalid_request")
    }
    if (obj.stringOrNull("protocol") != REMOTE_PROTOCOL) throw RemoteProtocolError("invalid_request")
    val version = obj.intOrNull("version") ?: throw RemoteProtocolError("invalid_version")
    if (version != REMOTE_VERSION) throw RemoteProtocolError("invalid_version")

    val clientId = obj.stringOrNull("clientId")
    if (clientId != null && !isValidOpaqueId(clientId)) throw RemoteProtocolError("invalid_request")

    val token = obj.stringOrNull("token")
    if (token == null || !isValidRemoteToken(token)) throw RemoteProtocolError("invalid_request")

    val method = obj.stringOrNull("method")
    if (method == null || method !in REMOTE_METHODS) throw RemoteProtocolError("unknown_method")

    return RemoteRequest(
        id = id,
        protocol = REMOTE_PROTOCOL,
        version = REMOTE_VERSION,
        clientId = clientId,
        token = token,
        method = method,
        params = obj.get("params"),
    )
}

fun isValidOpaqueId(value: String?): Boolean =
    value != null && value.length in 1..120 && OPAQUE_ID.matches(value)

fun isValidRemoteToken(value: String?): Boolean =
    value != null && value.length in 43..256 && OPAQUE_ID.matches(value)

fun validateRemoteScopeList(value: List<String>?): List<String> {
    if (value == null || (value.size != 2 && value.size != REMOTE_SCOPES.size)) {
        throw RemoteProtocolError("invalid_configuration")
    }
    if (!value.withIndex().all { (i, s) -> s == REMOTE_SCOPES[i] }) {
        throw RemoteProtocolError("invalid_configuration")
    }
    return REMOTE_SCOPES.subList(0, value.size)
}

fun validateRemoteClientName(value: String?): String {
    if (value == null) throw RemoteProtocolError("invalid_configuration")
    val name = value.trim()
    if (name.isEmpty() || name.length > 80 || Regex("[\\u0000\\r\\n]").containsMatchIn(name)) {
        throw RemoteProtocolError("invalid_configuration")
    }
    return name
}

fun validateRemoteReaction(value: String?): String {
    if (value == null || value !in REACTIONS) throw RemoteProtocolError("invalid_params")
    return value
}

data class RemoteSay(val message: String, val reaction: String?)

fun validateRemoteSayParams(value: JsonElement?): RemoteSay {
    if (value == null || !value.isJsonObject) throw RemoteProtocolError("invalid_params")
    val obj = value.asJsonObject
    if (obj.keySet().any { it != "message" && it != "reaction" }) throw RemoteProtocolError("invalid_params")
    val raw = obj.stringOrNull("message") ?: throw RemoteProtocolError("invalid_params")
    val message = raw.trim()
    if (message.isEmpty() || message.length > 140) throw RemoteProtocolError("invalid_params")
    if (message.toByteArray(Charsets.UTF_8).size > 560) throw RemoteProtocolError("invalid_params")
    if (Regex("[\\u0000\\r\\n]").containsMatchIn(message)) throw RemoteProtocolError("invalid_params")
    if (CODE_LIKE.containsMatchIn(message)) throw RemoteProtocolError("invalid_params")
    if (URL_OR_PATH.containsMatchIn(message)) throw RemoteProtocolError("invalid_params")
    if (SECRET_LIKE.containsMatchIn(message)) throw RemoteProtocolError("invalid_params")
    val reactionEl = obj.get("reaction")
    val reaction = if (reactionEl == null || reactionEl.isJsonNull) {
        null
    } else {
        validateRemoteReaction(obj.stringOrNull("reaction"))
    }
    return RemoteSay(message, reaction)
}

fun validateRemoteReactParams(value: JsonElement?): String {
    if (value == null || !value.isJsonObject) throw RemoteProtocolError("invalid_params")
    val obj = value.asJsonObject
    if (obj.keySet().any { it != "reaction" }) throw RemoteProtocolError("invalid_params")
    return validateRemoteReaction(obj.stringOrNull("reaction"))
}

fun validateRemoteStatusParams(value: JsonElement?) {
    if (value == null || value.isJsonNull) return
    if (!value.isJsonObject || value.asJsonObject.size() != 0) throw RemoteProtocolError("invalid_params")
}

fun isValidRemoteBindAddress(value: String?): Boolean {
    val normalized = normalizeRemoteIpv4Address(value) ?: return false
    val parts = normalized.split(".").map { it.toInt() }
    if (parts.any { it < 0 || it > 255 }) return false
    return when {
        parts[0] == 127 || parts[0] == 10 -> true
        parts[0] == 172 && parts[1] in 16..31 -> true
        parts[0] == 192 && parts[1] == 168 -> true
        parts[0] == 169 && parts[1] == 254 -> true
        parts[0] == 100 && parts[1] in 64..127 -> true
        else -> false
    }
}

fun normalizeRemoteIpv4Address(value: String?): String? {
    if (value == null) return null
    if (isCanonicalIpv4(value)) return value
    if (value.startsWith("::ffff:")) {
        val mapped = value.removePrefix("::ffff:")
        return if (isCanonicalIpv4(mapped)) mapped else null
    }
    return null
}

fun isValidRemotePeerAddress(value: String?): Boolean {
    val normalized = normalizeRemoteIpv4Address(value) ?: return false
    return isValidRemoteBindAddress(normalized)
}

private fun isCanonicalIpv4(value: String): Boolean {
    val m = IPV4.matchEntire(value) ?: return false
    return (1..4).all { i -> m.groupValues[i].toInt().toString() == m.groupValues[i] }
}

fun okRemoteResponse(id: String, result: JsonElement?): JsonObject {
    val out = JsonObject()
    out.addProperty("id", id)
    out.addProperty("ok", true)
    if (result != null) out.add("result", result)
    return out
}

fun errorRemoteResponse(id: String, code: String): JsonObject {
    val errObj = JsonObject()
    errObj.addProperty("code", code)
    errObj.addProperty("message", "Remote request rejected.")
    val out = JsonObject()
    out.addProperty("id", id)
    out.addProperty("ok", false)
    out.add("error", errObj)
    return out
}

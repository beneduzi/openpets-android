package com.openpets.android.protocol

import com.google.gson.JsonObject
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/** Default TCP port advertised for the OpenPets remote-control server. */
const val DEFAULT_REMOTE_PORT = 18421

/**
 * Serves the OpenPets remote-control protocol (`openpets-remote`, v1) over TCP,
 * with token auth and per-peer rate limiting.
 */
class RemoteServer(
    private val sink: PetEventSink,
    private val token: String,
    bindAddress: String = "0.0.0.0",
    port: Int = DEFAULT_REMOTE_PORT,
    private val appVersion: String = "0.1.0",
    private val clock: () -> Long = System::currentTimeMillis,
    private val rateLimitMax: Int = REMOTE_RATE_LIMIT_MAX,
    private val rateLimitWindowMs: Long = REMOTE_RATE_LIMIT_WINDOW_MS.toLong(),
) {
    private val windows = ConcurrentHashMap<String, ArrayDeque<Long>>()
    private val server = LineServer(
        port = port,
        bindAddress = bindAddress,
        maxMessageBytes = MAX_REMOTE_MESSAGE_BYTES,
        socketTimeoutMs = REMOTE_SOCKET_TIMEOUT_MS,
        maxConcurrent = REMOTE_MAX_CONCURRENT_SOCKETS,
        handler = { raw, peer -> handle(raw, peer) },
    )

    val isRunning: Boolean get() = server.isRunning
    val localPort: Int get() = server.localPort

    fun start(): Int = server.start()
    fun stop() = server.stop()

    private fun handle(raw: String, peer: InetAddress): String {
        val request = try {
            parseRemoteControlRequest(raw)
        } catch (e: RemoteProtocolError) {
            return errorRemoteResponse(bestEffortId(raw), e.code).toString()
        }
        if (!constantTimeEquals(token, request.token)) {
            return errorRemoteResponse(request.id, "unauthorized").toString()
        }
        if (!allow(peer.hostAddress ?: "unknown")) {
            return errorRemoteResponse(request.id, "rate_limited").toString()
        }
        val response = try {
            dispatch(request)
        } catch (e: RemoteProtocolError) {
            errorRemoteResponse(request.id, e.code)
        } catch (e: Exception) {
            errorRemoteResponse(request.id, "internal_error")
        }
        return response.toString()
    }

    private fun dispatch(request: RemoteRequest): JsonObject = when (request.method) {
        "status" -> {
            validateRemoteStatusParams(request.params)
            okRemoteResponse(request.id, statusResult())
        }
        "pet.react" -> {
            val reaction = validateRemoteReactParams(request.params)
            sink.onReact(reaction, request.clientId)
            okRemoteResponse(request.id, okOnly())
        }
        "pet.say" -> {
            val say = validateRemoteSayParams(request.params)
            sink.onSay(SayRequest(message = say.message, reaction = say.reaction, source = request.clientId))
            okRemoteResponse(request.id, okOnly())
        }
        else -> errorRemoteResponse(request.id, "unknown_method")
    }

    private fun allow(peer: String): Boolean {
        val now = clock()
        val window = windows.getOrPut(peer) { ArrayDeque() }
        synchronized(window) {
            while (window.isNotEmpty() && now - window.first() > rateLimitWindowMs) {
                window.removeFirst()
            }
            if (window.size >= rateLimitMax) return false
            window.addLast(now)
            return true
        }
    }

    private fun statusResult(): JsonObject = JsonObject().apply {
        addProperty("ok", true)
        addProperty("appRunning", true)
        addProperty("appVersion", appVersion)
    }

    private fun okOnly(): JsonObject = JsonObject().apply { addProperty("ok", true) }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val aBytes = a.toByteArray(Charsets.UTF_8)
        val bBytes = b.toByteArray(Charsets.UTF_8)
        if (aBytes.size != bBytes.size) return false
        var result = 0
        for (i in aBytes.indices) result = result or (aBytes[i].toInt() xor bBytes[i].toInt())
        return result == 0
    }
}

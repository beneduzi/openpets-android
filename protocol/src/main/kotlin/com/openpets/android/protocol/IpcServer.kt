package com.openpets.android.protocol

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Default TCP port advertised for the OpenPets IPC server. */
const val DEFAULT_IPC_PORT = 18420

/**
 * Serves the OpenPets local IPC protocol (`openpets-ipc`, v1) over TCP so
 * existing OpenPets clients can drive this device as the pet.
 */
class IpcServer(
    sink: PetEventSink,
    private val token: String,
    bindAddress: String = "0.0.0.0",
    port: Int = DEFAULT_IPC_PORT,
    appVersion: String = "0.1.0",
    petCatalog: PetCatalogProvider = StaticPetCatalogProvider(),
    installer: PetInstaller? = null,
    enforceLease: Boolean = false,
    clock: () -> Long = System::currentTimeMillis,
    dispatcher: IpcDispatcher? = null,
) {
    val dispatcher: IpcDispatcher = dispatcher ?: IpcDispatcher(sink, appVersion, petCatalog, installer, enforceLease, clock)
    private val server = LineServer(
        port = port,
        bindAddress = bindAddress,
        maxMessageBytes = MAX_MEDIA_REQUEST_BYTES,
        socketTimeoutMs = IPC_RESPONSE_TIMEOUT_MS,
        maxConcurrent = 16,
        connectionDeadlineMs = IPC_CONNECTION_DEADLINE_MS,
        handler = { raw, _ -> handle(raw) },
    )

    val isRunning: Boolean get() = server.isRunning
    val localPort: Int get() = server.localPort

    fun start(): Int = server.start()
    fun stop() = server.stop()

    private fun handle(raw: String): String {
        val request = try {
            parseIpcRequest(raw, token)
        } catch (e: IpcProtocolError) {
            return errorResponse(bestEffortId(raw), e).toString()
        }
        val response = try {
            dispatcher.dispatch(request)
        } catch (e: IpcProtocolError) {
            errorResponse(request.id, e)
        } catch (e: Exception) {
            errorResponse(request.id, e)
        }
        return response.toString()
    }
}

/** Method dispatch for IPC requests; stateful only for leases. */
class IpcDispatcher(
    private val sink: PetEventSink,
    private val appVersion: String,
    private val petCatalog: PetCatalogProvider,
    private val installer: PetInstaller? = null,
    private val enforceLease: Boolean = false,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val leases = ConcurrentHashMap<String, Long>()
    private val leaseLock = Any()
    private val leaseSeq = AtomicLong(0)
    private val leaseTtlMs = 15_000L

    fun dispatch(request: IpcRequest): JsonObject = when (request.method) {
        "hello" -> okResponse(request.id, helloResult())
        "status" -> okResponse(request.id, statusResult())
        "pets.list" -> okResponse(request.id, petsListResult())
        "lease.acquire" -> leaseAcquire(request)
        "lease.heartbeat" -> {
            val leaseId = validateOptionalLeaseId(request.params.str("leaseId"))
            if (leaseId == null) {
                okResponse(request.id, okOnly())
            } else {
                val expiresAt = synchronized(leaseLock) {
                    val expiry = leases[leaseId]
                    if (expiry == null || expiry <= clock()) {
                        leases.remove(leaseId)
                        throw IpcProtocolError("unknown_lease", "Unknown or expired lease id.")
                    }
                    (clock() + leaseTtlMs).also { leases[leaseId] = it }
                }
                val result = JsonObject().apply {
                    addProperty("ok", true)
                    addProperty("leaseId", leaseId)
                    addProperty("expiresAt", expiresAt)
                }
                okResponse(request.id, result)
            }
        }
        "lease.release" -> {
            val leaseId = validateOptionalLeaseId(request.params.str("leaseId"))
            if (leaseId != null) synchronized(leaseLock) { leases.remove(leaseId) }
            okResponse(request.id, okOnly())
        }
        "pet.react" -> {
            requireLeaseIfNeeded(request.params)
            val reaction = validateReaction(request.params.str("reaction"))
            val source = sanitizeSource(request.params.str("source"))
            sink.onReact(reaction, source)
            okResponse(request.id, okOnly())
        }
        "pet.move" -> {
            sink.onMove(MoveRequest(validateMoveX(request.params.float("x")), validateMoveY(request.params.float("y")), validateMoveDuration(request.params.int("durationMs"))))
            okResponse(request.id, okOnly())
        }
        "pet.setScale" -> {
            sink.onScale(validateScale(request.params.float("scale")))
            okResponse(request.id, okOnly())
        }
        "pet.hide" -> { sink.onVisibility(false); okResponse(request.id, okOnly()) }
        "pet.show" -> { sink.onVisibility(true); okResponse(request.id, okOnly()) }
        "notify" -> {
            sink.onNotify(NotificationRequest(validateNotifyTitle(request.params.str("title")), validateNotifyBody(request.params.str("body"))))
            okResponse(request.id, okOnly())
        }
        "status.publish" -> {
            sink.onStatus(validateStatusText(request.params.str("text")), validateStatusTone(request.params.str("tone")))
            okResponse(request.id, okOnly())
        }
        "status.clear" -> {
            requireLeaseIfNeeded(request.params)
            sink.onStatusClear()
            okResponse(request.id, okOnly())
        }
        "pet.say" -> {
            requireLeaseIfNeeded(request.params)
            val message = validateSayMessage(request.params.str("message"))
            val reaction = request.params.str("reaction")?.let { validateReaction(it) }
            val source = sanitizeSource(request.params.str("source"))
            val obj = request.params?.takeIf { it.isJsonObject }?.asJsonObject
            val say = SayRequest(
                message = message,
                reaction = reaction,
                source = source,
                actions = parseActions(obj?.get("actions")),
                hud = parseHud(obj?.get("hud")),
                inputs = parseInputs(obj?.get("inputs")),
                submitUrl = obj?.stringOrNull("submitUrl")?.let { validateMediaUrl(it) },
                bubbleId = validateBubbleId(obj?.stringOrNull("id")),
                pinned = obj?.get("pinned")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
                silent = obj?.get("silent")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: false,
                accentHex = validateAccentHex(obj?.stringOrNull("accent")),
                label = sanitizeSource(obj?.stringOrNull("label")),
            )
            sink.onSay(say)
            okResponse(request.id, okOnly())
        }
        "pet.bubbleDismiss" -> {
            requireLeaseIfNeeded(request.params)
            sink.onDismissBubble(validateBubbleId(request.params.str("id")))
            okResponse(request.id, okOnly())
        }
        "pet.showMedia" -> {
            requireLeaseIfNeeded(request.params)
            sink.onMedia(parseMediaRequest(request.params))
            okResponse(request.id, okOnly())
        }
        "pets.install" -> {
            val petId = validateInstallPetId(request.params.str("petId"))
            val target = installer
                ?: throw IpcProtocolError("unsupported_method", "Pet install is not supported.")
            okResponse(request.id, installResult(target.installFromCatalog(petId)))
        }
        "pets.install-local" -> {
            val data = validateMediaData(request.params.str("data"))
            val url = request.params.str("url")?.let { validateMediaUrl(it) }
            if (data == null && url == null) {
                throw IpcProtocolError("invalid_params", "Install data or url is required.")
            }
            val target = installer
                ?: throw IpcProtocolError("unsupported_method", "Pet install is not supported.")
            okResponse(request.id, installResult(target.installFromZip(data, url)))
        }
        else -> errorResponse(
            request.id,
            IpcProtocolError("unsupported_method", "Method not supported by this server."),
        )
    }

    private fun leaseAcquire(request: IpcRequest): JsonObject {
        validateRequestedPetId(request.params.str("requestedPetId"))
        validateSessionNonce(request.params.str("sessionNonce"))
        val id: String
        val expiresAt: Long
        synchronized(leaseLock) {
            val now = clock()
            id = "lease-${leaseSeq.incrementAndGet()}-$now"
            expiresAt = now + leaseTtlMs
            leases[id] = expiresAt
        }
        val result = JsonObject()
        result.addProperty("ok", true)
        result.addProperty("leaseId", id)
        result.addProperty("expiresAt", expiresAt)
        result.addProperty("petId", petCatalog.selectedPetId)
        return okResponse(request.id, result)
    }

    private fun helloResult(): JsonObject = JsonObject().apply {
        addProperty("ok", true)
        addProperty("appVersion", appVersion)
        addProperty("protocol", IPC_PROTOCOL)
        addProperty("protocolVersion", IPC_VERSION)
        addProperty("platform", "linux")
    }

    private fun statusResult(): JsonObject = JsonObject().apply {
        addProperty("ok", true)
        addProperty("appRunning", true)
        addProperty("appVersion", appVersion)
        addProperty("platform", "linux")
        addProperty("defaultPetId", petCatalog.selectedPetId)
        addProperty("leases", activeLeaseCount())
        add("pets", petsListResult().getAsJsonArray("pets"))
    }

    private fun activeLeaseCount(): Int {
        val now = clock()
        return leases.count { it.value > now }
    }

    /** Enforces that a valid (non-expired) lease id is provided, when enabled. */
    private fun requireLeaseIfNeeded(params: JsonElement?) {
        if (!enforceLease) return
        val leaseId = params.str("leaseId")
        val expiry = leaseId?.let { leases[it] }
        if (leaseId == null || expiry == null || expiry < clock()) {
            throw IpcProtocolError("invalid_lease", "A valid lease id is required.")
        }
    }

    private fun petsListResult(): JsonObject {
        val array = com.google.gson.JsonArray()
        petCatalog.pets().forEach { pet ->
            val obj = JsonObject()
            obj.addProperty("id", pet.id)
            obj.addProperty("displayName", pet.displayName)
            obj.addProperty("builtIn", pet.builtIn)
            obj.addProperty("broken", false)
            array.add(obj)
        }
        return JsonObject().apply {
            addProperty("ok", true)
            addProperty("defaultPetId", petCatalog.selectedPetId)
            add("pets", array)
        }
    }

    private fun okOnly(): JsonObject = JsonObject().apply { addProperty("ok", true) }

    private fun installResult(result: PetInstallResult): JsonObject = JsonObject().apply {
        addProperty("ok", true)
        addProperty("petId", result.petId)
        addProperty("displayName", result.displayName)
        addProperty("installed", true)
    }

    private fun parseMediaRequest(params: JsonElement?): MediaRequest {
        if (params == null || !params.isJsonObject) {
            throw IpcProtocolError("invalid_params", "Media params are required.")
        }
        val obj = params.asJsonObject
        val data = validateMediaData(obj.stringOrNull("data"))
        val rawUrl = obj.stringOrNull("url") ?: obj.stringOrNull("path")
        val url = if (data == null && rawUrl != null) validateMediaUrl(rawUrl) else null
        if (data == null && url == null) {
            throw IpcProtocolError("invalid_params", "Media data or url is required.")
        }
        val message = obj.stringOrNull("message")?.let { validateSayMessage(it) }
        val reaction = obj.stringOrNull("reaction")?.let { validateReaction(it) }
        val durationMs = validateMediaDurationMs(obj.intOrNull("durationMs"))
        val clickUrl = validateMediaClickUrl(obj.stringOrNull("clickUrl"))
        val actions = parseActions(obj.get("actions"))
        val bubbleId = validateBubbleId(obj.stringOrNull("id"))
        val pinned = obj.get("pinned")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
        return MediaRequest(url, data, message, reaction, durationMs, clickUrl, actions, bubbleId, pinned)
    }
}

internal fun bestEffortId(raw: String): String = try {
    val el = JsonParser.parseString(raw)
    if (el.isJsonObject) {
        val id = el.asJsonObject.get("id")
        if (id != null && id.isJsonPrimitive && id.asJsonPrimitive.isString) id.asJsonPrimitive.asString else "unknown"
    } else {
        "unknown"
    }
} catch (_: Exception) {
    "unknown"
}

internal fun JsonElement?.str(key: String): String? {
    if (this == null || !this.isJsonObject) return null
    val el = this.asJsonObject.get(key) ?: return null
    return if (el.isJsonPrimitive && el.asJsonPrimitive.isString) el.asJsonPrimitive.asString else null
}

internal fun JsonElement?.float(key: String): Float? = this?.takeIf { it.isJsonObject }?.asJsonObject?.floatOrNull(key)
internal fun JsonElement?.int(key: String): Int? = this?.takeIf { it.isJsonObject }?.asJsonObject?.intOrNull(key)

package com.openpets.android.net

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.openpets.android.FeatureHud
import com.openpets.android.PetActionEvent
import com.openpets.android.PetBus
import com.openpets.android.protocol.IpcDispatcher
import com.openpets.android.protocol.IpcRequest
import com.openpets.android.protocol.WebSocketConnection
import com.openpets.android.protocol.WebSocketListener
import com.openpets.android.protocol.WebSocketServer
import com.openpets.android.protocol.isValidRemotePeerAddress
import com.openpets.android.companion.HostStatusController
import com.openpets.android.protocol.SayRequest
import com.openpets.android.protocol.MediaRequest
import com.openpets.android.protocol.MoveRequest
import java.util.concurrent.ConcurrentHashMap

class WebSocketBridge(
    private val context: Context,
    private val tokenProvider: () -> String,
    private val dispatcher: IpcDispatcher,
    private val appVersion: String,
) {
    private val connections = ConcurrentHashMap.newKeySet<WebSocketConnection>()
    private val gson = Gson()
    private val hostStatus = HostStatusController(context)
    private val busListener = object : PetBus.Listener {
        override fun onReact(reaction: String, source: String?) = broadcast(WsMessages.eventFromReact(JsonParser.parseString(gson.toJson(mapOf("reaction" to reaction, "source" to source)))))
        override fun onSay(request: SayRequest) = broadcast(WsMessages.eventFromSay(JsonParser.parseString(gson.toJson(request))))
        override fun onMedia(request: MediaRequest) = broadcast(WsMessages.eventFromMedia(JsonParser.parseString(gson.toJson(request))))
        override fun onDismissBubble(id: String?) = broadcast(WsMessages.eventFromDismiss(JsonParser.parseString(gson.toJson(mapOf("id" to id)))))
        override fun onHud(hud: FeatureHud) = broadcast(WsMessages.eventFromHud(JsonParser.parseString(gson.toJson(hud))))
        override fun onAction(event: PetActionEvent) = broadcast(WsMessages.eventFromAction(event))
        override fun onInputText(text: String) = broadcast(WsMessages.eventFromInputText(text))
        override fun onMove(request: MoveRequest) = broadcast(WsMessages.eventMove(request))
        override fun onScale(scale: Float) = broadcast(WsMessages.eventScale(scale))
        override fun onVisibility(visible: Boolean) = broadcast(WsMessages.eventVisibility(visible))
        override fun onPointer(kind: String, x: Float, y: Float) = broadcast(WsMessages.eventPointer(kind, x, y))
    }
    @Volatile private var server: WebSocketServer? = null
    val isRunning: Boolean get() = server?.isRunning == true
    val localPort: Int get() = server?.localPort ?: -1

    fun start(port: Int, bindAddress: String) {
        stop()
        val created = WebSocketServer(port, bindAddress, 32, authorize = { peer, _, query ->
            isValidRemotePeerAddress(peer.hostAddress) && query["token"] == tokenProvider()
        }, listener = object : WebSocketListener {
            override fun onOpen(conn: WebSocketConnection) {
                connections.add(conn)
                safeSend(conn, WsMessages.hello(appVersion))
                try {
                    val response = dispatcher.dispatch(IpcRequest("status", 1, tokenProvider(), "status", null))
                    safeSend(conn, WsMessages.status(response.getAsJsonObject("result") ?: response))
                } catch (_: Exception) {}
            }
            override fun onMessage(conn: WebSocketConnection, text: String) {
                WsMessages.parseHostStatus(text)?.let { hostStatus.update(it); return }
                try {
                    val command = WsMessages.parseCommand(text)
                    val response = dispatcher.dispatch(IpcRequest(command.id, 1, tokenProvider(), command.method, command.params))
                    val ok = response.get("ok")?.asBoolean == true
                    safeSend(conn, if (ok) WsMessages.result(command.id, true, response.get("result")) else {
                        val error = response.getAsJsonObject("error")
                        WsMessages.error(command.id, error?.get("code")?.asString ?: "error", error?.get("message")?.asString ?: "Request failed.")
                    })
                } catch (e: Exception) { safeSend(conn, WsMessages.error("unknown", "invalid_request", e.message ?: "Invalid request.")) }
            }
            override fun onClose(conn: WebSocketConnection) { connections.remove(conn) }
        })
        try { created.start(); server = created; PetBus.register(busListener) } catch (_: Exception) { created.stop() }
    }

    fun stop() {
        PetBus.unregister(busListener)
        connections.toList().forEach { try { it.close() } catch (_: Exception) {} }
        connections.clear()
        server?.stop(); server = null
    }

    private fun broadcast(text: String) { connections.toList().forEach { safeSend(it, text) } }
    private fun safeSend(conn: WebSocketConnection, text: String) { try { conn.send(text) } catch (_: Exception) { connections.remove(conn) } }
}

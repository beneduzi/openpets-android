package com.openpets.android

import android.os.Handler
import android.os.Looper
import com.openpets.android.protocol.MediaRequest
import com.openpets.android.protocol.PetEventSink
import com.openpets.android.protocol.SayRequest
import com.openpets.android.protocol.HudItem
import com.openpets.android.protocol.MoveRequest
import com.openpets.android.protocol.NotificationRequest
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-process bridge between the network servers (background threads) and the
 * UI. Delivery is always marshalled onto the main thread.
 */
data class FeatureHud(
    val bubbleId: String,
    val source: String,
    val message: String,
    val subtext: String? = null,
    val hud: List<HudItem>,
    val claim: Boolean,
)

data class PetActionEvent(val bubbleId: String?, val actionId: String)
data class CommandInvoked(val source: String, val commandId: String)

object PetBus {

    interface Listener {
        fun onReact(reaction: String, source: String?)
        fun onSay(request: SayRequest)
        fun onMedia(request: MediaRequest)
        fun onDismissBubble(id: String?)
        fun onHud(hud: FeatureHud)
        fun onAction(event: PetActionEvent)
        fun onInputText(text: String)
        fun onMove(request: MoveRequest)
        fun onScale(scale: Float)
        fun onVisibility(visible: Boolean)
        fun onPointer(kind: String, x: Float, y: Float)
        fun onNotify(request: NotificationRequest)
        fun onCommandInvoked(event: CommandInvoked)
        fun onCommandsChanged()
        fun onStatus(text: String, tone: String?)
        fun onPower(plugged: Boolean, level: Int)
    }

    private val listeners = CopyOnWriteArrayList<Listener>()
    private val main = Handler(Looper.getMainLooper())

    fun register(listener: Listener) {
        listeners.addIfAbsent(listener)
    }

    fun unregister(listener: Listener) {
        listeners.remove(listener)
    }

    fun react(reaction: String, source: String? = null) {
        main.post { listeners.forEach { it.onReact(reaction, source) } }
    }

    fun say(request: SayRequest) {
        main.post { listeners.forEach { it.onSay(request) } }
    }

    fun media(request: MediaRequest) {
        main.post { listeners.forEach { it.onMedia(request) } }
    }

    fun dismissBubble(id: String?) {
        main.post { listeners.forEach { it.onDismissBubble(id) } }
    }

    fun hud(hud: FeatureHud) {
        main.post { listeners.forEach { it.onHud(hud) } }
    }

    fun action(event: PetActionEvent) {
        main.post { listeners.forEach { it.onAction(event) } }
    }

    fun inputText(text: String) {
        main.post { listeners.forEach { it.onInputText(text) } }
    }

    fun move(request: MoveRequest) { main.post { listeners.forEach { it.onMove(request) } } }
    fun scale(value: Float) { main.post { listeners.forEach { it.onScale(value) } } }
    fun visibility(value: Boolean) { main.post { listeners.forEach { it.onVisibility(value) } } }
    fun pointer(kind: String, x: Float, y: Float) { main.post { listeners.forEach { it.onPointer(kind, x, y) } } }
    fun notify(request: NotificationRequest) { main.post { listeners.forEach { it.onNotify(request) } } }
    fun invokeCommand(source: String, commandId: String) { val event = CommandInvoked(source, commandId); main.post { listeners.forEach { it.onCommandInvoked(event) } } }
    fun commandsChanged() { main.post { listeners.forEach { it.onCommandsChanged() } } }
    fun status(text: String, tone: String?) { main.post { listeners.forEach { it.onStatus(text, tone) } } }
    fun power(plugged: Boolean, level: Int) { main.post { listeners.forEach { it.onPower(plugged, level) } } }

    /** Sink handed to the IPC/remote servers. */
    val sink: PetEventSink = object : PetEventSink {
        override fun onReact(reaction: String, source: String?) = react(reaction, source)
        override fun onSay(request: SayRequest) = say(request)
        override fun onMedia(request: MediaRequest) = media(request)
        override fun onDismissBubble(id: String?) = dismissBubble(id)
        override fun onMove(request: MoveRequest) = move(request)
        override fun onScale(scale: Float) = scale(scale)
        override fun onVisibility(visible: Boolean) = visibility(visible)
        override fun onNotify(request: NotificationRequest) = notify(request)
        override fun onStatus(text: String, tone: String?) = status(text, tone)
    }
}

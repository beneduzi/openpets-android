package com.openpets.android

import android.os.Handler
import android.os.Looper
import com.openpets.android.protocol.MediaRequest
import com.openpets.android.protocol.PetEventSink
import com.openpets.android.protocol.SayRequest
import com.openpets.android.protocol.HudItem
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

object PetBus {

    interface Listener {
        fun onReact(reaction: String, source: String?)
        fun onSay(request: SayRequest)
        fun onMedia(request: MediaRequest)
        fun onDismissBubble(id: String?)
        fun onHud(hud: FeatureHud)
        fun onAction(event: PetActionEvent)
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

    /** Sink handed to the IPC/remote servers. */
    val sink: PetEventSink = object : PetEventSink {
        override fun onReact(reaction: String, source: String?) = react(reaction, source)
        override fun onSay(request: SayRequest) = say(request)
        override fun onMedia(request: MediaRequest) = media(request)
        override fun onDismissBubble(id: String?) = dismissBubble(id)
    }
}

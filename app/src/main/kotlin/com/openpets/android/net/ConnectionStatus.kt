package com.openpets.android.net

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet

sealed interface ServerState {
    data object Disabled : ServerState
    data object Applying : ServerState
    data object Stopped : ServerState
    data class Listening(val port: Int) : ServerState
    data class Failed(val message: String) : ServerState
}

data class ServersStatus(
    val ipc: ServerState = ServerState.Disabled,
    val remote: ServerState = ServerState.Disabled,
    val webSocket: ServerState = ServerState.Disabled,
    val configuredBindAddress: String = "0.0.0.0",
)

object ConnectionStatus {
    @Volatile var current: ServersStatus = ServersStatus()
        private set
    private val listeners = CopyOnWriteArraySet<(ServersStatus) -> Unit>()
    private val pending = java.util.concurrent.ConcurrentHashMap<(ServersStatus) -> Unit, Runnable>()
    private val main = Handler(Looper.getMainLooper())

    fun addListener(listener: (ServersStatus) -> Unit) {
        listeners.add(listener)
        val snapshot = current
        if (Looper.myLooper() == Looper.getMainLooper()) listener(snapshot) else {
            val delivery=Runnable { pending.remove(listener); if(listener in listeners) listener(snapshot) }
            pending[listener]=delivery; main.post(delivery)
        }
    }
    fun removeListener(listener: (ServersStatus) -> Unit) { listeners.remove(listener); pending.remove(listener)?.let(main::removeCallbacks) }
    fun publish(status: ServersStatus) {
        current = status
        if (Looper.myLooper() == Looper.getMainLooper()) listeners.forEach { it(status) }
        else main.post { listeners.forEach { it(status) } }
    }
}

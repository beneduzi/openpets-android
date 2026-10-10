package com.openpets.android.net

import java.util.concurrent.ConcurrentHashMap

data class RemoteCommand(val source: String, val id: String, val title: String, val description: String?)

object CommandRegistry {
    private const val MAX_COMMANDS = 50
    private val commands = ConcurrentHashMap<String, List<RemoteCommand>>()

    @Synchronized fun register(source: String, commands: List<RemoteCommand>) {
        require(source.isNotBlank() && source.length <= 80) { "Invalid command source." }
        require(commands.all { it.source == source && it.id.isNotBlank() && it.id.length <= 64 && it.title.isNotBlank() && it.title.length <= 40 }) { "Invalid command." }
        require(commands.map { it.id }.distinct().size == commands.size) { "Duplicate command id." }
        val total = this.commands.filterKeys { it != source }.values.sumOf { it.size } + commands.size
        require(total <= MAX_COMMANDS) { "At most $MAX_COMMANDS commands may be registered." }
        this.commands[source] = commands.toList()
    }

    @Synchronized fun unregister(source: String) { commands.remove(source) }

    fun list(): List<RemoteCommand> = commands.values.flatten().sortedWith(compareBy<RemoteCommand> { it.source }.thenBy { it.title })
}

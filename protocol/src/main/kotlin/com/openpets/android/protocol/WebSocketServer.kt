package com.openpets.android.protocol

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

interface WebSocketListener {
    fun onOpen(conn: WebSocketConnection)
    fun onMessage(conn: WebSocketConnection, text: String)
    fun onClose(conn: WebSocketConnection)
}

interface WebSocketConnection {
    val peer: InetAddress
    fun send(text: String)
    fun close(code: Int = 1000)
    val isOpen: Boolean
}

class WebSocketServer(
    private val port: Int,
    private val bindAddress: String,
    private val maxConcurrent: Int,
    private val maxPayloadBytes: Int = 1024 * 1024,
    private val authorize: (peer: InetAddress, path: String, query: Map<String, String>) -> Boolean = { _, _, _ -> true },
    private val listener: WebSocketListener
) {
    private val running = AtomicBoolean(false)
    private val active = AtomicInteger(0)
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "openpets-websocket").apply { isDaemon = true } }
    @Volatile private var serverSocket: ServerSocket? = null
    val isRunning: Boolean get() = running.get()
    val localPort: Int get() = serverSocket?.localPort ?: -1

    fun start(): Int {
        if (!running.compareAndSet(false, true)) return localPort
        val ss = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(bindAddress, port)) }
        serverSocket = ss
        pool.execute { while (running.get()) try {
            val socket = ss.accept()
            if (active.incrementAndGet() > maxConcurrent) { active.decrementAndGet(); socket.close(); continue }
            pool.execute { try { handle(socket) } catch (_: Exception) { try { socket.close() } catch (_: Exception) {} } finally { active.decrementAndGet() } }
        } catch (_: Exception) { if (running.get()) continue else break } }
        return ss.localPort
    }

    fun stop() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) {}
        pool.shutdownNow()
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            val input = s.getInputStream(); val output = s.getOutputStream()
            val request = readHeader(input) ?: return
            val lines = request.split("\r\n")
            val parts = lines.firstOrNull()?.split(' ') ?: return
            if (parts.size != 3 || parts[0] != "GET") { output.write("HTTP/1.1 400 Bad Request\r\n\r\n".toByteArray()); return }
            val headers = lines.drop(1).mapNotNull { line -> val i = line.indexOf(':'); if (i <= 0) null else line.substring(0, i).trim().lowercase() to line.substring(i + 1).trim() }.toMap()
            val key = headers["sec-websocket-key"]
            val target = parts[1]
            val path = target.substringBefore('?')
            val query = target.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate { item ->
                val i = item.indexOf('='); URLDecoder.decode(if (i < 0) item else item.substring(0, i), "UTF-8") to URLDecoder.decode(if (i < 0) "" else item.substring(i + 1), "UTF-8")
            }
            if (!headers["upgrade"].equals("websocket", true) || headers["connection"]?.split(',')?.any { it.trim().equals("upgrade", true) } != true || key == null) {
                output.write("HTTP/1.1 400 Bad Request\r\n\r\n".toByteArray()); return
            }
            if (!authorize(s.inetAddress, path, query)) { output.write("HTTP/1.1 403 Forbidden\r\n\r\n".toByteArray()); return }
            output.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ${computeAcceptKey(key)}\r\n\r\n").toByteArray(Charsets.US_ASCII)); output.flush()
            val conn = Connection(s)
            listener.onOpen(conn)
            try {
                while (conn.isOpen) {
                    val frame = decodeClientFrame(input, maxPayloadBytes) ?: break
                    when (frame.opcode) {
                        0x1 -> listener.onMessage(conn, String(frame.payload, Charsets.UTF_8))
                        0x9 -> conn.writeFrame(encodeServerControlFrame(0xA, frame.payload))
                        0x8 -> { conn.writeFrame(encodeCloseFrame()); break }
                        else -> { conn.close(1002); break }
                    }
                }
            } finally { conn.markClosed(); listener.onClose(conn) }
        }
    }

    private fun readHeader(input: java.io.InputStream): String? {
        val out = ByteArrayOutputStream(); var state = 0
        while (out.size() < 16 * 1024) {
            val b = input.read(); if (b < 0) return null
            out.write(b)
            state = when { state == 0 && b == 13 -> 1; state == 1 && b == 10 -> 2; state == 2 && b == 13 -> 3; state == 3 && b == 10 -> return String(out.toByteArray(), Charsets.US_ASCII).dropLast(4); b == 13 -> 1; else -> 0 }
        }
        return null
    }

    private inner class Connection(private val socket: Socket) : WebSocketConnection {
        private val open = AtomicBoolean(true)
        override val peer: InetAddress get() = socket.inetAddress
        override val isOpen: Boolean get() = open.get()
        @Synchronized override fun send(text: String) { if (isOpen) writeFrame(encodeServerTextFrame(text)) }
        @Synchronized override fun close(code: Int) { if (open.compareAndSet(true, false)) { try { writeFrame(encodeCloseFrame(code)) } catch (_: Exception) {} } }
        @Synchronized fun writeFrame(bytes: ByteArray) { if (isOpen) { socket.getOutputStream().write(bytes); socket.getOutputStream().flush() } }
        fun markClosed() { open.set(false) }
    }
}

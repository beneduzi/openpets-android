package com.openpets.android.protocol

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimal newline-delimited JSON over TCP.
 *
 * One request line in (capped at [maxMessageBytes]), one response line out.
 * The [handler] returns the response line, or null to close without replying.
 * A request line exceeding the cap closes the connection silently.
 * Reads are chunked and byte-oriented so large (media) payloads are efficient.
 */
class LineServer(
    private val port: Int,
    private val bindAddress: String,
    private val maxMessageBytes: Int,
    private val socketTimeoutMs: Int,
    private val maxConcurrent: Int,
    private val connectionDeadlineMs: Long = 0L,
    private val handler: (String, InetAddress) -> String?,
) {
    private val running = AtomicBoolean(false)
    private val active = AtomicInteger(0)
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "openpets-net").apply { isDaemon = true } }
    @Volatile private var serverSocket: ServerSocket? = null

    val isRunning: Boolean get() = running.get()
    val localPort: Int get() = serverSocket?.localPort ?: -1

    /** Bind and start accepting. Returns the bound port. Throws on bind failure. */
    fun start(): Int {
        if (!running.compareAndSet(false, true)) return localPort
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(bindAddress, port))
        serverSocket = ss
        pool.execute { acceptLoop(ss) }
        return ss.localPort
    }

    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        pool.shutdownNow()
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (running.get() && !ss.isClosed) {
            val socket = try {
                ss.accept()
            } catch (_: Exception) {
                break
            }
            if (active.get() >= maxConcurrent) {
                try {
                    socket.close()
                } catch (_: Exception) {
                }
                continue
            }
            active.incrementAndGet()
            pool.execute {
                try {
                    handle(socket)
                } catch (_: Exception) {
                } finally {
                    active.decrementAndGet()
                }
            }
        }
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            s.soTimeout = socketTimeoutMs
            val input = s.getInputStream()
            val output: OutputStream = s.getOutputStream()
            val deadline = if (connectionDeadlineMs > 0) System.currentTimeMillis() + connectionDeadlineMs else 0L
            val line = readLine(input, deadline) ?: return
            if (line.isEmpty()) return
            val response = handler(String(line, Charsets.UTF_8), s.inetAddress)
            if (response != null) {
                output.write(response.toByteArray(Charsets.UTF_8))
                output.write('\n'.code)
                output.flush()
            }
        }
    }

    /** Reads one newline-terminated line as bytes, enforcing the cap. Null if oversized/EOF. */
    private fun readLine(input: InputStream, deadlineMs: Long): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            if (deadlineMs > 0 && System.currentTimeMillis() > deadlineMs) return null
            val n = input.read(buf)
            if (n == -1) return if (out.size() == 0) null else out.toByteArray()
            var i = 0
            while (i < n) {
                if (buf[i] == '\n'.code.toByte()) {
                    out.write(buf, 0, i)
                    if (out.size() > maxMessageBytes) return null
                    return out.toByteArray()
                }
                i++
            }
            out.write(buf, 0, n)
            if (out.size() > maxMessageBytes) return null
        }
    }
}

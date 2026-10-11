package com.openpets.android.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WebSocketTest {
    @Test fun computesRfcAcceptKey() {
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", computeAcceptKey("dGhlIHNhbXBsZSBub25jZQ=="))
    }

    @Test fun decodesMaskedSmallAndLargeClientTextFrames() {
        for (text in listOf("hello", "x".repeat(300))) {
            val frame = decodeClientFrame(ByteArrayInputStream(maskedTextFrame(text)))!!
            assertEquals(1, frame.opcode)
            assertEquals(text, String(frame.payload, Charsets.UTF_8))
        }
    }

    @Test fun upgradesAndReceivesTextMessage() {
        val received = CountDownLatch(1)
        val message = java.util.concurrent.atomic.AtomicReference("")
        val server = WebSocketServer(0, "127.0.0.1", 4, listener = object : WebSocketListener {
            override fun onOpen(conn: WebSocketConnection) {}
            override fun onMessage(conn: WebSocketConnection, text: String) { message.set(text); received.countDown() }
            override fun onClose(conn: WebSocketConnection) {}
        })
        try {
            val port = server.start()
            Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 2000
                val out = socket.getOutputStream()
                out.write(request(includeKey = true).toByteArray(Charsets.US_ASCII)); out.flush()
                val response = readHeaders(socket.getInputStream())
                assertTrue(response.startsWith("HTTP/1.1 101"))
                assertTrue(response.contains("Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo="))
                out.write(maskedTextFrame("test message")); out.flush()
                assertTrue("Message not delivered", received.await(2, TimeUnit.SECONDS))
                assertEquals("test message", message.get())
            }
        } finally { server.stop() }
    }

    @Test fun rejectsMissingKeyAndUnauthorizedRequest() {
        assertRejected(request(includeKey = false), null, "400")
        assertRejected(request(includeKey = true), { _, _, _ -> false }, "403")
    }

    private fun assertRejected(request: String, authorize: ((java.net.InetAddress, String, Map<String, String>) -> Boolean)?, expected: String) {
        val listener = object : WebSocketListener {
            override fun onOpen(conn: WebSocketConnection) {}
            override fun onMessage(conn: WebSocketConnection, text: String) {}
            override fun onClose(conn: WebSocketConnection) {}
        }
        val server = if (authorize == null) WebSocketServer(0, "127.0.0.1", 2, listener = listener)
        else WebSocketServer(0, "127.0.0.1", 2, authorize = authorize, listener = listener)
        try {
            Socket("127.0.0.1", server.start()).use { socket ->
                socket.soTimeout = 2000
                socket.getOutputStream().write(request.toByteArray(Charsets.US_ASCII)); socket.getOutputStream().flush()
                val response = readHeaders(socket.getInputStream())
                assertTrue(response.contains("HTTP/1.1 $expected"))
                assertFalse(response.contains("HTTP/1.1 101"))
            }
        } finally { server.stop() }
    }

    @Test fun closesConnectionsThatNeverCompleteTheHandshake() {
        val listener = object : WebSocketListener {
            override fun onOpen(conn: WebSocketConnection) {}
            override fun onMessage(conn: WebSocketConnection, text: String) {}
            override fun onClose(conn: WebSocketConnection) {}
        }
        val server = WebSocketServer(0, "127.0.0.1", 2, handshakeTimeoutMs = 300, listener = listener)
        try {
            val port = server.start()
            Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 5000
                val started = System.currentTimeMillis()
                // Send nothing: the server must close the socket once the handshake deadline passes.
                assertEquals(-1, socket.getInputStream().read())
                val elapsed = System.currentTimeMillis() - started
                assertTrue("handshake not bounded: ${elapsed}ms", elapsed < 4000)
            }
            // The freed slot is usable: a normal handshake still succeeds on the same server.
            Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 2000
                socket.getOutputStream().write(request(includeKey = true).toByteArray(Charsets.US_ASCII))
                socket.getOutputStream().flush()
                assertTrue(readHeaders(socket.getInputStream()).startsWith("HTTP/1.1 101"))
            }
        } finally { server.stop() }
    }

    private fun request(includeKey: Boolean): String = buildString {
        append("GET /?token=abc HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n")
        if (includeKey) append("Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n")
        append("Sec-WebSocket-Version: 13\r\n\r\n")
    }

    private fun maskedTextFrame(text: String): ByteArray {
        val payload = text.toByteArray(Charsets.UTF_8)
        val mask = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        val out = ByteArrayOutputStream()
        out.write(0x81)
        if (payload.size < 126) out.write(0x80 or payload.size)
        else { out.write(0x80 or 126); out.write(payload.size ushr 8); out.write(payload.size) }
        out.write(mask)
        payload.forEachIndexed { i, b -> out.write(b.toInt() xor mask[i % 4].toInt()) }
        return out.toByteArray()
    }

    private fun readHeaders(input: java.io.InputStream): String {
        val out = ByteArrayOutputStream()
        var state = 0
        while (out.size() < 8192) {
            val b = input.read()
            if (b < 0) break
            out.write(b)
            state = when { state == 0 && b == 13 -> 1; state == 1 && b == 10 -> 2; state == 2 && b == 13 -> 3; state == 3 && b == 10 -> return String(out.toByteArray(), Charsets.US_ASCII); b == 13 -> 1; else -> 0 }
        }
        return String(out.toByteArray(), Charsets.US_ASCII)
    }
}

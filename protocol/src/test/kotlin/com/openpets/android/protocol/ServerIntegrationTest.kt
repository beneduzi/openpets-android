package com.openpets.android.protocol

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

private class FakeSink : PetEventSink {
    val reactions = CopyOnWriteArrayList<String>()
    val says = CopyOnWriteArrayList<SayRequest>()
    val media = CopyOnWriteArrayList<MediaRequest>()
    val dismissals = CopyOnWriteArrayList<String?>()
    val notifications = CopyOnWriteArrayList<NotificationRequest>()
    val statuses = CopyOnWriteArrayList<Pair<String, String?>>()
    override fun onReact(reaction: String, source: String?) {
        reactions.add(reaction)
    }

    override fun onSay(request: SayRequest) {
        says.add(request)
    }

    override fun onMedia(request: MediaRequest) {
        media.add(request)
    }

    override fun onDismissBubble(id: String?) {
        dismissals.add(id)
    }
    override fun onMove(request: MoveRequest) {}
    override fun onScale(scale: Float) {}
    override fun onVisibility(visible: Boolean) {}
    override fun onNotify(request: NotificationRequest) { notifications.add(request) }
    override fun onStatus(text: String, tone: String?) { statuses.add(text to tone) }
    var statusClears = 0
    override fun onStatusClear() { statusClears++ }
}

private const val IPC_TOKEN = "0123456789abcdef0123456789abcdef"
private val REMOTE_TOKEN = "B".repeat(43)

private fun send(port: Int, line: String): String? {
    Socket("127.0.0.1", port).use { socket ->
        socket.soTimeout = 3_000
        val out = socket.getOutputStream()
        out.write((line + "\n").toByteArray(Charsets.UTF_8))
        out.flush()
        return BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8)).readLine()
    }
}

private fun json(port: Int, line: String): JsonObject {
    val response = send(port, line) ?: error("No response")
    return JsonParser.parseString(response).asJsonObject
}

class IpcServerTest {

    private fun ipc(id: String, method: String, params: String, token: String = IPC_TOKEN): String =
        """{"id":"$id","version":1,"token":"$token","method":"$method","params":$params}"""

    @Test fun rateLimitAppliesPerPeer() {
        val server = IpcServer(FakeSink(), IPC_TOKEN, "127.0.0.1", 0, rateLimitMax = 3)
        val port = server.start()
        try {
            repeat(3) { index -> assertTrue(json(port, ipc("$index", "hello", "{}")).get("ok").asBoolean) }
            val limited = json(port, ipc("fourth", "hello", "{}"))
            assertEquals(false, limited.get("ok").asBoolean)
            assertEquals("rate_limited", limited.getAsJsonObject("error").get("code").asString)
        } finally {
            server.stop()
        }
    }

    @Test fun reactAndSayReachSink() {
        val sink = FakeSink()
        val server = IpcServer(sink, IPC_TOKEN, "127.0.0.1", 0)
        val port = server.start()
        try {
            val react = json(port, ipc("1", "pet.react", """{"reaction":"thinking"}"""))
            assertTrue(react.get("ok").asBoolean)
            assertEquals("thinking", sink.reactions.first())

            val say = json(port, ipc("2", "pet.say", """{"message":"On it","reaction":"editing"}"""))
            assertTrue(say.get("ok").asBoolean)
            assertEquals("On it", sink.says.first().message)
            assertEquals("editing", sink.says.first().reaction)
            assertEquals(false, sink.says.first().silent)

            val styled = json(port, ipc("styled", "pet.say", """{"message":"x","accent":"#ff8800","label":"Build"}"""))
            assertTrue(styled.get("ok").asBoolean)
            assertEquals("#ff8800", sink.says.last().accentHex)
            assertEquals("Build", sink.says.last().label)
            val badAccent = json(port, ipc("bad-accent", "pet.say", """{"message":"x","accent":"#zz"}"""))
            assertEquals("invalid_params", badAccent.getAsJsonObject("error").get("code").asString)
            assertTrue(json(port, ipc("clear", "status.clear", "{}" )).get("ok").asBoolean)
            assertEquals(1, sink.statusClears)

            val silentSay = json(port, ipc("silent", "pet.say", """{"message":"x","silent":true}"""))
            assertTrue(silentSay.get("ok").asBoolean)
            assertEquals(true, sink.says.last().silent)
        } finally {
            server.stop()
        }
    }

    @Test fun statusPublishReachesSink() {
        val sink = FakeSink()
        val server = IpcServer(sink, IPC_TOKEN, "127.0.0.1", 0)
        val port = server.start()
        try {
            val resp = json(port, ipc("status-1", "status.publish", """{"text":"Working","tone":"success"}"""))
            assertTrue(resp.get("ok").asBoolean)
            assertEquals("Working" to "success", sink.statuses.first())
        } finally { server.stop() }
    }

    @Test fun leaseAcquireReturnsLeaseId() {
        val sink = FakeSink()
        val server = IpcServer(sink, IPC_TOKEN, "127.0.0.1", 0)
        val port = server.start()
        try {
            val resp = json(port, ipc("3", "lease.acquire", """{"requestedPetId":"tux"}"""))
            assertTrue(resp.get("ok").asBoolean)
            val result = resp.getAsJsonObject("result")
            assertTrue(result.get("leaseId").asString.isNotEmpty())
            assertTrue(result.get("expiresAt").asLong > System.currentTimeMillis())
        } finally {
            server.stop()
        }
    }

    @Test fun bubbleDismissReachesSink() {
        val sink = FakeSink()
        val server = IpcServer(sink, IPC_TOKEN, "127.0.0.1", 0)
        val port = server.start()
        try {
            val resp = json(port, ipc("40", "pet.bubbleDismiss", """{"id":"b1"}"""))
            assertTrue(resp.get("ok").asBoolean)
            assertEquals("b1", sink.dismissals.first())
        } finally {
            server.stop()
        }
    }

    @Test fun leaseHeartbeatRenewsAndReleaseRemoves() {
        val server = IpcServer(FakeSink(), IPC_TOKEN, "127.0.0.1", 0)
        val port = server.start()
        try {
            val acquired = json(port, ipc("41", "lease.acquire", """{}"""))
            val leaseId = acquired.getAsJsonObject("result").get("leaseId").asString
            assertTrue(json(port, ipc("42", "lease.heartbeat", """{"leaseId":"$leaseId"}""")).get("ok").asBoolean)
            assertEquals(1, json(port, ipc("43", "status", "{}")).getAsJsonObject("result").get("leases").asInt)
            assertTrue(json(port, ipc("44", "lease.release", """{"leaseId":"$leaseId"}""")).get("ok").asBoolean)
            assertEquals(0, json(port, ipc("45", "status", "{}")).getAsJsonObject("result").get("leases").asInt)
        } finally {
            server.stop()
        }
    }

    @Test fun leaseHeartbeatUnknownLeaseRejected() {
        val server = IpcServer(FakeSink(), IPC_TOKEN, "127.0.0.1", 0)
        val port = server.start()
        try {
            val resp = json(port, ipc("46", "lease.heartbeat", """{"leaseId":"does-not-exist"}"""))
            assertEquals(false, resp.get("ok").asBoolean)
            assertEquals("unknown_lease", resp.getAsJsonObject("error").get("code").asString)
        } finally {
            server.stop()
        }
    }

    @Test fun showMediaRequiresLeaseWhenEnforced() {
        val server = IpcServer(FakeSink(), IPC_TOKEN, "127.0.0.1", 0, enforceLease = true)
        val port = server.start()
        try {
            val rejected = json(port, ipc("47", "pet.showMedia", """{"url":"https://example.com/a.png"}"""))
            assertEquals(false, rejected.get("ok").asBoolean)
            assertEquals("invalid_lease", rejected.getAsJsonObject("error").get("code").asString)
            val acquired = json(port, ipc("48", "lease.acquire", "{}"))
            val leaseId = acquired.getAsJsonObject("result").get("leaseId").asString
            val accepted = json(port, ipc("49", "pet.showMedia", """{"url":"https://example.com/a.png","leaseId":"$leaseId"}"""))
            assertTrue(accepted.get("ok").asBoolean)
        } finally {
            server.stop()
        }
    }

    @Test fun petsListAndStatus() {
        val server = IpcServer(FakeSink(), IPC_TOKEN, "127.0.0.1", 0)
        val port = server.start()
        try {
            assertTrue(json(port, ipc("4", "pets.list", "{}")).get("ok").asBoolean)
            assertTrue(json(port, ipc("5", "status", "{}")).get("ok").asBoolean)
            assertTrue(json(port, ipc("6", "hello", "{}")).get("ok").asBoolean)
        } finally {
            server.stop()
        }
    }

    @Test fun rejectsWrongToken() {
        val server = IpcServer(FakeSink(), IPC_TOKEN, "127.0.0.1", 0)
        val port = server.start()
        try {
            val resp = json(port, ipc("7", "pet.react", """{"reaction":"idle"}""", token = "wrongtoken1234567890"))
            assertEquals(false, resp.get("ok").asBoolean)
            assertEquals("invalid_token", resp.getAsJsonObject("error").get("code").asString)
        } finally {
            server.stop()
        }
    }

    @Test fun showMediaReachesSink() {
        val sink = FakeSink()
        val server = IpcServer(sink, IPC_TOKEN, "127.0.0.1", 0)
        val port = server.start()
        try {
            val resp = json(port, ipc("9", "pet.showMedia", """{"url":"https://example.com/a.png","message":"print","durationMs":5000}"""))
            assertTrue(resp.get("ok").asBoolean)
            assertEquals("https://example.com/a.png", sink.media.first().url)

            val bad = json(port, ipc("10", "pet.showMedia", """{"url":"file:///etc/passwd"}"""))
            assertEquals(false, bad.get("ok").asBoolean)
            assertEquals("invalid_params", bad.getAsJsonObject("error").get("code").asString)
        } finally {
            server.stop()
        }
    }

    @Test fun showMediaDataReachesSink() {
        val sink = FakeSink()
        val server = IpcServer(sink, IPC_TOKEN, "127.0.0.1", 0)
        val port = server.start()
        try {
            val png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
            val resp = json(port, ipc("11", "pet.showMedia", """{"data":"$png","message":"1px"}"""))
            assertTrue(resp.get("ok").asBoolean)
            assertEquals(png, sink.media.first().dataBase64)

            val none = json(port, ipc("12", "pet.showMedia", """{"message":"no source"}"""))
            assertEquals(false, none.get("ok").asBoolean)
        } finally {
            server.stop()
        }
    }

    @Test fun installUsesInstaller() {
        val installer = object : PetInstaller {
            override fun installFromCatalog(petId: String) = PetInstallResult(petId, "Melon Capybara")

            override fun installFromZip(dataBase64: String?, url: String?) = PetInstallResult("imported", "Imported")
        }
        val server = IpcServer(FakeSink(), IPC_TOKEN, "127.0.0.1", 0, installer = installer)
        val port = server.start()
        try {
            val r = json(port, ipc("20", "pets.install", """{"petId":"melon-capybara"}"""))
            assertTrue(r.get("ok").asBoolean)
            assertEquals("melon-capybara", r.getAsJsonObject("result").get("petId").asString)
            val local = json(port, ipc("21", "pets.install-local", """{"data":"UEsDBA=="}"""))
            assertTrue(local.get("ok").asBoolean)
        } finally {
            server.stop()
        }
    }

    @Test fun sayActionsParsed() {
        val sink = FakeSink()
        val server = IpcServer(sink, IPC_TOKEN, "127.0.0.1", 0)
        val port = server.start()
        try {
            val resp = json(
                port,
                ipc("30", "pet.say", """{"message":"done","actions":[{"id":"open","label":"Abrir","style":"primary","url":"https://example.com"}]}"""),
            )
            assertTrue(resp.get("ok").asBoolean)
            assertEquals(1, sink.says.first().actions.size)
            assertEquals("open", sink.says.first().actions.first().id)

            val bad = json(port, ipc("31", "pet.say", """{"message":"x","actions":[{"id":"open","label":"Abrir","style":"weird"}]}"""))
            assertEquals(false, bad.get("ok").asBoolean)
            assertEquals("invalid_params", bad.getAsJsonObject("error").get("code").asString)
        } finally {
            server.stop()
        }
    }

    @Test fun oversizedMessageClosesConnection() {
        val server = LineServer(
            port = 0,
            bindAddress = "127.0.0.1",
            maxMessageBytes = 1024,
            socketTimeoutMs = 3000,
            maxConcurrent = 4,
            handler = { _, _ -> "ok" },
        )
        val port = server.start()
        try {
            assertNull(send(port, "x".repeat(5000)))
        } finally {
            server.stop()
        }
    }

    @Test fun connectionDeadlineClosesSlowClient() {
        val server = LineServer(
            port = 0,
            bindAddress = "127.0.0.1",
            maxMessageBytes = 1024,
            socketTimeoutMs = 5000,
            maxConcurrent = 4,
            connectionDeadlineMs = 300,
            handler = { _, _ -> "ok" },
        )
        val port = server.start()
        try {
            Socket("127.0.0.1", port).use { s ->
                s.soTimeout = 5000
                val out = s.getOutputStream()
                out.write("{\"partial\":".toByteArray(Charsets.UTF_8)); out.flush()
                Thread.sleep(900)
                out.write("1}".toByteArray(Charsets.UTF_8)); out.flush()
                val line = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8)).readLine()
                assertNull(line)
            }
        } finally {
            server.stop()
        }
    }

    @Test fun deadlineLateNewlineDoesNotDispatch() {
        val calls = AtomicInteger()
        val server = LineServer(0, "127.0.0.1", 1024, 5000, 4, 250) { _, _ ->
            calls.incrementAndGet()
            "ok"
        }
        val port = server.start()
        try {
            Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 5000
                socket.getOutputStream().write("{\"partial\":".toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
                Thread.sleep(700)
                socket.getOutputStream().write("1}\n".toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
                assertNull(BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8)).readLine())
            }
            assertEquals(0, calls.get())
        } finally {
            server.stop()
        }
    }

    @Test fun deadlineTimelyCompletionDispatches() {
        val calls = AtomicInteger()
        val server = LineServer(0, "127.0.0.1", 1024, 5000, 4, 250) { _, _ ->
            calls.incrementAndGet()
            "ok"
        }
        val port = server.start()
        try {
            assertEquals("ok", send(port, "{}"))
            assertEquals(1, calls.get())
        } finally {
            server.stop()
        }
    }

    @Test fun deadlineDisabledStillProcesses() {
        val server = LineServer(0, "127.0.0.1", 1024, 5000, 4, 0) { _, _ -> "ok" }
        val port = server.start()
        try {
            assertEquals("ok", send(port, "{}"))
        } finally {
            server.stop()
        }
    }

    @Test fun deadlineFreesSlotForReuse() {
        val server = LineServer(0, "127.0.0.1", 1024, 5000, 1, 200) { _, _ -> "ok" }
        val port = server.start()
        try {
            Socket("127.0.0.1", port).use {
                Thread.sleep(600)
                assertEquals("ok", send(port, "{}"))
            }
        } finally {
            server.stop()
        }
    }
}

class RemoteServerTest {

    private fun remote(id: String, method: String, params: String, token: String = REMOTE_TOKEN): String =
        """{"id":"$id","protocol":"openpets-remote","version":1,"token":"$token","method":"$method","params":$params}"""

    @Test fun statusReactSay() {
        val sink = FakeSink()
        val server = RemoteServer(sink, REMOTE_TOKEN, "127.0.0.1", 0)
        val port = server.start()
        try {
            assertTrue(json(port, remote("1", "status", "{}")).get("ok").asBoolean)
            assertTrue(json(port, remote("2", "pet.react", """{"reaction":"running"}""")).get("ok").asBoolean)
            assertTrue(json(port, remote("3", "pet.say", """{"message":"hello"}""")).get("ok").asBoolean)
            assertEquals("running", sink.reactions.first())
            assertEquals("hello", sink.says.first().message)
            assertNull(sink.says.first().reaction)
        } finally {
            server.stop()
        }
    }

    @Test fun rejectsWrongToken() {
        val server = RemoteServer(FakeSink(), REMOTE_TOKEN, "127.0.0.1", 0)
        val port = server.start()
        try {
            val resp = json(port, remote("1", "pet.react", """{"reaction":"idle"}""", token = "C".repeat(43)))
            assertEquals(false, resp.get("ok").asBoolean)
            assertEquals("unauthorized", resp.getAsJsonObject("error").get("code").asString)
        } finally {
            server.stop()
        }
    }

    @Test fun rateLimitEngages() {
        val server = RemoteServer(FakeSink(), REMOTE_TOKEN, "127.0.0.1", 0, rateLimitMax = 3)
        val port = server.start()
        try {
            repeat(3) { i ->
                assertTrue(json(port, remote("$i", "status", "{}")).get("ok").asBoolean)
            }
            val limited = json(port, remote("99", "status", "{}"))
            assertEquals(false, limited.get("ok").asBoolean)
            assertEquals("rate_limited", limited.getAsJsonObject("error").get("code").asString)
        } finally {
            server.stop()
        }
    }
}

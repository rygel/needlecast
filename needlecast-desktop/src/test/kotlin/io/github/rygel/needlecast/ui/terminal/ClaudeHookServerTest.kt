package io.github.rygel.needlecast.ui.terminal

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ClaudeHookServerTest {
    private val events = mutableListOf<Pair<String, AgentStatus>>()
    private lateinit var server: ClaudeHookServer

    @BeforeEach
    fun setUp() {
        server = ClaudeHookServer(port = findFreePort()) { cwd, status ->
            events.add(cwd to status)
        }
    }

    @AfterEach
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `start endpoint fires THINKING status`() {
        val latch = CountDownLatch(1)
        val port = findFreePort()
        val srv = ClaudeHookServer(port = port) { cwd, status ->
            events.add(cwd to status)
            latch.countDown()
        }
        srv.start()
        try {
            postJson("http://localhost:$port/hook/claude/start", """{"cwd":"/home/user/project"}""")
            assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should have fired")
            assertEquals(1, events.size)
            assertEquals("/home/user/project" to AgentStatus.THINKING, events[0])
        } finally {
            srv.stop()
        }
    }

    @Test
    fun `stop endpoint fires WAITING status`() {
        val latch = CountDownLatch(1)
        val port = findFreePort()
        val srv = ClaudeHookServer(port = port) { cwd, status ->
            events.add(cwd to status)
            latch.countDown()
        }
        srv.start()
        try {
            postJson("http://localhost:$port/hook/claude/stop", """{"cwd":"/tmp/test"}""")
            assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should have fired")
            assertEquals(1, events.size)
            assertEquals("/tmp/test" to AgentStatus.WAITING, events[0])
        } finally {
            srv.stop()
        }
    }

    @Test
    fun `idle endpoint fires WAITING status`() {
        val latch = CountDownLatch(1)
        val port = findFreePort()
        val srv = ClaudeHookServer(port = port) { cwd, status ->
            events.add(cwd to status)
            latch.countDown()
        }
        srv.start()
        try {
            postJson("http://localhost:$port/hook/claude/idle", """{"cwd":"/work/app"}""")
            assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback should have fired")
            assertEquals(1, events.size)
            assertEquals("/work/app" to AgentStatus.WAITING, events[0])
        } finally {
            srv.stop()
        }
    }

    @Test
    fun `missing cwd does not fire callback`() {
        val port = findFreePort()
        val srv = ClaudeHookServer(port = port) { _, _ -> events.add("fired" to AgentStatus.THINKING) }
        srv.start()
        try {
            postJson("http://localhost:$port/hook/claude/start", """{"other":"field"}""")
            Thread.sleep(200)
            assertTrue(events.isEmpty())
        } finally {
            srv.stop()
        }
    }

    @Test
    fun `blank cwd does not fire callback`() {
        val port = findFreePort()
        val srv = ClaudeHookServer(port = port) { _, _ -> events.add("fired" to AgentStatus.THINKING) }
        srv.start()
        try {
            postJson("http://localhost:$port/hook/claude/start", """{"cwd":"  "}""")
            Thread.sleep(200)
            assertTrue(events.isEmpty())
        } finally {
            srv.stop()
        }
    }

    @Test
    fun `invalid JSON does not crash server`() {
        val port = findFreePort()
        val srv = ClaudeHookServer(port = port) { _, _ -> events.add("fired" to AgentStatus.THINKING) }
        srv.start()
        try {
            postJson("http://localhost:$port/hook/claude/start", "not json at all")
            Thread.sleep(200)
            assertTrue(events.isEmpty())
        } finally {
            srv.stop()
        }
    }

    @Test
    fun `server returns 200 for valid request`() {
        val port = findFreePort()
        val srv = ClaudeHookServer(port = port) { _, _ -> }
        srv.start()
        try {
            val conn = postJson("http://localhost:$port/hook/claude/start", """{"cwd":"/test"}""")
            assertEquals(200, conn)
        } finally {
            srv.stop()
        }
    }

    private fun findFreePort(): Int {
        val socket = java.net.ServerSocket(0)
        val port = socket.localPort
        socket.close()
        return port
    }

    private fun postJson(url: String, body: String): Int {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        conn.disconnect()
        return code
    }
}

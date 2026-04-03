package io.github.rygel.needlecast.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.pty4j.WinSize
import io.javalin.Javalin
import io.javalin.http.staticfiles.Location
import org.slf4j.LoggerFactory
import java.nio.charset.Charset
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class TermMessage(
    val type: String? = null,
    val data: String? = null,
    val cols: Int? = null,
    val rows: Int? = null,
)

class WebUiServer(private val port: Int = 4000) {

    private val logger = LoggerFactory.getLogger(WebUiServer::class.java)
    private val mapper = jacksonObjectMapper()
    private val ioPool = Executors.newCachedThreadPool()

    private val app = Javalin.create { cfg ->
        cfg.staticFiles.add("/webui", Location.CLASSPATH)
        cfg.showJavalinBanner = false
    }

    fun start() {
        app.get("/") { ctx -> ctx.redirect("/index.html") }

        app.ws("/ws/term") { ws ->
            ws.onConnect { ctx ->
                val session = PtySession()
                ctx.attribute("pty", session)
                session.start(
                    onOutput = { chunk -> ctx.send(chunk) },
                    onError = { err -> logger.warn("PTY error: ${err.message}") },
                )
            }
            ws.onMessage { ctx ->
                val session = ctx.attribute<PtySession>("pty") ?: return@onMessage
                val raw = ctx.message()
                val msg = runCatching { mapper.readValue<TermMessage>(raw) }.getOrNull()
                if (msg?.type == "resize" && msg.cols != null && msg.rows != null) {
                    session.resize(msg.cols, msg.rows)
                } else if (msg?.type == "input" && msg.data != null) {
                    session.write(msg.data)
                } else {
                    session.write(raw)
                }
            }
            ws.onClose { ctx ->
                ctx.attribute<PtySession>("pty")?.close()
            }
            ws.onError { ctx ->
                ctx.attribute<PtySession>("pty")?.close()
            }
        }

        app.start(port)
        logger.info("Web UI running at http://localhost:$port")
    }

    fun stop() {
        app.stop()
        ioPool.shutdownNow()
    }

    private inner class PtySession {
        private var pty: PtyProcess? = null
        private val closed = AtomicBoolean(false)

        fun start(onOutput: (String) -> Unit, onError: (Exception) -> Unit) {
            ioPool.submit {
                try {
                    val cmd = if (isWindows()) arrayOf("cmd.exe") else arrayOf("/bin/bash", "--login")
                    pty = PtyProcessBuilder()
                        .setCommand(cmd)
                        .setDirectory(System.getProperty("user.home"))
                        .setEnvironment(System.getenv().toMutableMap().apply { put("TERM", "xterm-256color") })
                        .setInitialColumns(120)
                        .setInitialRows(30)
                        .start()

                    val input = pty!!.inputStream
                    val buf = ByteArray(8192)
                    while (!closed.get()) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        val text = String(buf, 0, n, Charset.forName("UTF-8"))
                        onOutput(text)
                    }
                } catch (e: Exception) {
                    onError(e)
                } finally {
                    close()
                }
            }
        }

        fun write(data: String) {
            try {
                pty?.outputStream?.write(data.toByteArray(Charsets.UTF_8))
                pty?.outputStream?.flush()
            } catch (_: Exception) {}
        }

        fun resize(cols: Int, rows: Int) {
            try {
                pty?.setWinSize(WinSize(cols, rows))
            } catch (_: Exception) {}
        }

        fun close() {
            if (!closed.compareAndSet(false, true)) return
            try { pty?.destroy() } catch (_: Exception) {}
        }
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name", "").lowercase().contains("win")
}

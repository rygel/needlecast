package io.github.rygel.needlecast.ui.terminal

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.sun.net.httpserver.HttpServer
import io.github.rygel.needlecast.scanner.IS_WINDOWS
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.swing.SwingUtilities

/**
 * Lightweight HTTP server that receives Claude Code lifecycle hook events and translates them
 * into [AgentStatus] callbacks.
 *
 * Claude Code is configured via [installHooks] to POST to this server on:
 *  - `UserPromptSubmit` → [AgentStatus.THINKING]
 *  - `Stop` / `Notification(idle_prompt)` → [AgentStatus.WAITING]
 *
 * All [onEvent] invocations are dispatched on the EDT.
 */
class ClaudeHookServer(
    val port: Int = PORT,
    private val onEvent: (cwd: String, status: AgentStatus) -> Unit,
) {
    private val mapper = ObjectMapper()
    private var server: HttpServer? = null

    fun start() {
        try {
            val srv = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
            srv.createContext("/hook/claude/start") { ex ->
                val cwd = readCwd(ex.requestBody.readBytes())
                if (cwd != null) SwingUtilities.invokeLater { onEvent(cwd, AgentStatus.THINKING) }
                ex.sendResponseHeaders(200, -1); ex.close()
            }
            srv.createContext("/hook/claude/stop") { ex ->
                val cwd = readCwd(ex.requestBody.readBytes())
                if (cwd != null) SwingUtilities.invokeLater { onEvent(cwd, AgentStatus.WAITING) }
                ex.sendResponseHeaders(200, -1); ex.close()
            }
            srv.createContext("/hook/claude/idle") { ex ->
                val cwd = readCwd(ex.requestBody.readBytes())
                if (cwd != null) SwingUtilities.invokeLater { onEvent(cwd, AgentStatus.WAITING) }
                ex.sendResponseHeaders(200, -1); ex.close()
            }
            srv.executor = null
            srv.start()
            server = srv
            logger.info("Claude hook server listening on port {}", port)
        } catch (e: Exception) {
            logger.warn("Could not start Claude hook server on port {}: {}", port, e.message)
        }
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    private fun readCwd(bytes: ByteArray): String? = try {
        mapper.readTree(bytes)?.get("cwd")?.asText()?.takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }

    companion object {
        const val PORT = 17312
        private val logger = LoggerFactory.getLogger(ClaudeHookServer::class.java)

        /**
         * Merges QuickLaunch hook entries into `~/.claude/settings.json`.
         * Existing entries for other tools are preserved. Idempotent.
         */
        fun installHooks(port: Int = PORT) {
            try {
                val settingsPath = Path.of(System.getProperty("user.home"), ".claude", "settings.json")
                Files.createDirectories(settingsPath.parent)

                val mapper = ObjectMapper()
                val root: ObjectNode = if (Files.exists(settingsPath)) {
                    try { mapper.readTree(settingsPath.toFile()) as? ObjectNode ?: mapper.createObjectNode() }
                    catch (_: Exception) { mapper.createObjectNode() }
                } else mapper.createObjectNode()

                val hooksNode = root.withObjectProperty("hooks")

                mergeHookEntry(mapper, hooksNode, port, "UserPromptSubmit",
                    matcher  = "",
                    command  = curlCmd(port, "start"),
                )
                mergeHookEntry(mapper, hooksNode, port, "Stop",
                    matcher  = "",
                    command  = curlCmd(port, "stop"),
                )
                mergeHookEntry(mapper, hooksNode, port, "Notification",
                    matcher  = "idle_prompt",
                    command  = curlCmd(port, "idle"),
                )

                val tmp = settingsPath.parent.resolve("settings.json.tmp")
                mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), root)
                Files.move(tmp, settingsPath, StandardCopyOption.REPLACE_EXISTING)
                logger.info("Claude hooks installed in {}", settingsPath)
            } catch (e: Exception) {
                logger.warn("Failed to install Claude hooks: {}", e.message)
            }
        }

        /**
         * Adds a hook entry to [hooksNode] for [eventName] if no existing entry already
         * references our server URL. Does not modify entries from other tools.
         */
        private fun mergeHookEntry(
            mapper: ObjectMapper,
            hooksNode: ObjectNode,
            port: Int,
            eventName: String,
            matcher: String,
            command: String,
        ) {
            val serverUrl = "localhost:$port"
            val rules = hooksNode.withArrayProperty(eventName)

            // Already present?
            if (rules.any { rule ->
                (rule.get("hooks") as? ArrayNode)?.any { h ->
                    h.get("command")?.asText()?.contains(serverUrl) == true
                } == true
            }) return

            val hookEntry = mapper.createObjectNode().apply {
                put("type", "command")
                put("command", command)
            }
            val hooksArray = mapper.createArrayNode().add(hookEntry)
            val rule = mapper.createObjectNode().apply {
                put("matcher", matcher)
                set<ArrayNode>("hooks", hooksArray)
            }
            rules.add(rule)
        }

        /**
         * Removes all Needlecast hook entries from `~/.claude/settings.json`.
         * Called on startup when [claudeHooksEnabled] is false to clean up
         * hooks left behind by a previous run.
         */
        fun uninstallHooks(port: Int = PORT) {
            try {
                val settingsPath = Path.of(System.getProperty("user.home"), ".claude", "settings.json")
                if (!Files.exists(settingsPath)) return

                val mapper = ObjectMapper()
                val root = try { mapper.readTree(settingsPath.toFile()) as? ObjectNode ?: return }
                           catch (_: Exception) { return }

                val hooksNode = root.get("hooks") as? ObjectNode ?: return
                val serverUrl = "localhost:$port"
                var modified = false

                for (eventName in listOf("UserPromptSubmit", "Stop", "Notification")) {
                    val rules = hooksNode.get(eventName) as? ArrayNode ?: continue
                    val filtered = rules.filterNot { rule ->
                        (rule.get("hooks") as? ArrayNode)?.any { h ->
                            h.get("command")?.asText()?.contains(serverUrl) == true
                        } == true
                    }
                    if (filtered.size < rules.size()) {
                        modified = true
                        if (filtered.isEmpty()) {
                            hooksNode.remove(eventName)
                        } else {
                            hooksNode.putArray(eventName).addAll(filtered.map { it as ObjectNode })
                        }
                    }
                }

                if (modified) {
                    val tmp = settingsPath.parent.resolve("settings.json.tmp")
                    mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), root)
                    Files.move(tmp, settingsPath, StandardCopyOption.REPLACE_EXISTING)
                    logger.info("Needlecast hooks removed from {}", settingsPath)
                }
            } catch (e: Exception) {
                logger.warn("Failed to uninstall Claude hooks: {}", e.message)
            }
        }

        private fun curlCmd(port: Int, event: String): String {
            val url = "http://localhost:$port/hook/claude/$event"
            return if (IS_WINDOWS)
                """curl.exe -s -X POST "$url" -H "Content-Type: application/json" -d @-"""
            else
                "curl -s -X POST '$url' -H 'Content-Type: application/json' -d @-"
        }
    }
}

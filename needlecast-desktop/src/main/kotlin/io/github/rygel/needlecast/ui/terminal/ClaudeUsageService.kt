package io.github.rygel.needlecast.ui.terminal

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.util.concurrent.Executors
import javax.swing.SwingUtilities
import javax.swing.Timer

class ClaudeUsageService(
    private val onUsage: (ClaudeUsageData?) -> Unit,
) {
    private val mapper = ObjectMapper()
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "claude-usage-poller").apply { isDaemon = true }
    }
    private var httpClient: HttpClient? = null
    private var accessToken: String? = null
    private var expiresAtMs: Long = 0
    private var lastData: ClaudeUsageData? = null
    private var timer: Timer? = null

    fun start() {
        if (!loadCredentials()) {
            logger.info("No valid Claude credentials found; quota polling not started")
            return
        }
        logger.info("Claude usage polling started")
        pollOnce()
        timer = Timer(POLL_INTERVAL_MS) { pollOnce() }.apply { isRepeats = true; start() }
    }

    fun stop() {
        timer?.stop()
        timer = null
        executor.shutdownNow()
        logger.info("Claude usage polling stopped")
    }

    private fun loadCredentials(): Boolean {
        val credPath = Path.of(System.getProperty("user.home"), ".claude", ".credentials.json")
        if (!java.nio.file.Files.exists(credPath)) return false
        return try {
            val tree = mapper.readTree(credPath.toFile())
            val oauth = tree.get("claudeAiOauth") ?: return false
            val token = oauth.get("accessToken")?.asText()?.takeIf { it.isNotBlank() } ?: return false
            val expires = oauth.get("expiresAt")?.asLong() ?: 0L
            this.accessToken = token
            this.expiresAtMs = expires
            this.httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build()
            true
        } catch (e: Exception) {
            logger.warn("Failed to read Claude credentials: {}", e.message)
            false
        }
    }

    private fun pollOnce() {
        val token = accessToken ?: return
        if (System.currentTimeMillis() > expiresAtMs) {
            logger.debug("Claude OAuth token expired; skipping poll")
            return
        }
        val client = httpClient ?: return
        executor.submit {
            try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(USAGE_URL))
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .header("anthropic-beta", BETA_HEADER)
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(15))
                    .build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() != 200) {
                    logger.warn("Claude usage API request failed with status {}", response.statusCode())
                    return@submit
                }
                val data = parseResponse(response.body())
                lastData = data
                SwingUtilities.invokeLater { onUsage(data) }
            } catch (e: Exception) {
                logger.debug("Claude usage poll failed: {}", e.message)
            }
        }
    }

    private fun parseResponse(body: String): ClaudeUsageData? {
        return try {
            val tree = mapper.readTree(body)
            if (tree.has("error")) {
                logger.warn("Claude usage API returned error payload")
                return null
            }
            ClaudeUsageData(
                fiveHourPercent = tree.doubleAtOrNull("/five_hour/utilization"),
                fiveHourResetsAt = tree.textAtOrNull("/five_hour/resets_at"),
                sevenDayPercent = tree.doubleAtOrNull("/seven_day/utilization"),
                sevenDayResetsAt = tree.textAtOrNull("/seven_day/resets_at"),
                sevenDaySonnetPercent = tree.doubleAtOrNull("/seven_day_sonnet/utilization"),
                sevenDayOpusPercent = tree.doubleAtOrNull("/seven_day_opus/utilization"),
            )
        } catch (e: Exception) {
            logger.warn("Failed to parse Claude usage response: {}", e.message)
            null
        }
    }

    private fun JsonNode.doubleAtOrNull(pointer: String): Double? {
        val node = at(pointer)
        return if (node.isMissingNode || node.isNull || !node.isNumber) null else node.asDouble()
    }

    private fun JsonNode.textAtOrNull(pointer: String): String? {
        val node = at(pointer)
        if (node.isMissingNode || node.isNull || !node.isTextual) return null
        return node.asText().takeIf { it.isNotBlank() }
    }

    companion object {
        private const val USAGE_URL = "https://api.anthropic.com/api/oauth/usage"
        private const val USER_AGENT = "claude-code/2.1.80"
        private const val BETA_HEADER = "oauth-2025-04-20"
        private const val POLL_INTERVAL_MS = 60_000
        private val logger = LoggerFactory.getLogger(ClaudeUsageService::class.java)
    }
}

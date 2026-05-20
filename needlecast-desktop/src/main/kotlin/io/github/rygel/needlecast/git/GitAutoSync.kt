package io.github.rygel.needlecast.git

import io.github.rygel.needlecast.AppContext
import java.time.Instant
import java.time.temporal.ChronoUnit

class GitAutoSync(
    private val intervalMinutes: Int = 5,
    private val autoFetchEnabled: Boolean = true,
) {
    private val lastFetch = mutableMapOf<String, Instant>()
    private var _gitService: GitService? = null

    constructor(ctx: AppContext, gitService: GitService) : this(
        intervalMinutes = ctx.config.gitAutoFetchIntervalMinutes,
        autoFetchEnabled = ctx.config.gitAutoFetch,
    ) {
        _gitService = gitService
    }

    fun shouldFetch(projectPath: String): Boolean {
        if (!autoFetchEnabled) return false
        val last = lastFetch[projectPath] ?: return true
        return ChronoUnit.MINUTES.between(last, Instant.now()) >= intervalMinutes
    }

    fun recordFetch(projectPath: String, time: Instant = Instant.now()) {
        lastFetch[projectPath] = time
    }

    fun fetchIfNeeded(projectPath: String, onLine: (String) -> Unit = {}) {
        if (!shouldFetch(projectPath)) return
        recordFetch(projectPath)
        val service = _gitService ?: return
        Thread({
            try {
                service.fetchStreaming(projectPath, onLine)
            } catch (_: Exception) {
                lastFetch.remove(projectPath)
            }
        }, "git-auto-fetch").apply { isDaemon = true }.start()
    }
}

package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.Disposable
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.GitStatus
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.scanner.BuildFileWatcher
import io.github.rygel.needlecast.ui.terminal.AgentStatus
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import javax.swing.SwingWorker
import javax.swing.Timer

internal class ProjectTreeScanCoordinator(
    val ctx: AppContext,
    private val onScanResult: (ProjectDirectory, DetectedProject) -> Unit,
    private val onGitStatusReady: (String, GitStatus) -> Unit,
    private val requestRepaint: () -> Unit,
) {
    val gitStatusCache = mutableMapOf<String, GitStatus>()
    private val agentStatuses = mutableMapOf<String, AgentStatus>()
    private val directoryLookup = mutableMapOf<String, ProjectDirectory>()

    private val scanExecutor =
        Executors.newFixedThreadPool(2).also { exec ->
            ctx.register(
                object : Disposable {
                    override fun dispose() {
                        exec.shutdownNow()
                    }
                },
            )
        }

    private val scanQueue = ConcurrentLinkedQueue<Pair<ProjectDirectory, DetectedProject>>()
    private val scanApplyTimer = Timer(25) { drainScanQueue(10) }.apply { isRepeats = false }
    private val scanApplyPending = AtomicBoolean(false)

    private val buildFileWatcher =
        BuildFileWatcher { path ->
            val dir = directoryLookup[path] ?: return@BuildFileWatcher
            rescheduleProjectScan(path, dir)
        }.also { ctx.register(it) }

    private var blinkOnField = false
    val blinkOn: Boolean get() = blinkOnField
    private val blinkTimer =
        Timer(600) {
            blinkOnField = !blinkOnField
            requestRepaint()
        }.apply { isRepeats = true }

    val blinkTimerRunning: Boolean get() = blinkTimer.isRunning

    private val repaintTimer = Timer(50) { requestRepaint() }.apply { isRepeats = false }

    fun registerDirectory(dir: ProjectDirectory) {
        directoryLookup[dir.path] = dir
    }

    fun scanProject(dir: ProjectDirectory) {
        directoryLookup[dir.path] = dir
        scanExecutor.execute {
            val result =
                try {
                    ctx.scanner.scan(dir) ?: DetectedProject(dir, emptySet(), emptyList())
                } catch (e: Exception) {
                    logger.warn("Failed to scan '${dir.label()}'", e)
                    DetectedProject(dir, emptySet(), emptyList(), scanFailed = true)
                }
            scanQueue.add(dir to result)
            scheduleScanApply()
        }
    }

    fun rescheduleProjectScan(
        path: String,
        dir: ProjectDirectory,
    ) {
        scanExecutor.execute {
            val result =
                try {
                    ctx.scanner.scan(dir)
                } catch (e: Exception) {
                    logger.warn("Project rescan failed", e)
                    null
                } ?: return@execute
            scanQueue.add(dir to result)
            scheduleScanApply()
        }
    }

    fun drainScanQueue(maxPerTick: Int) {
        var processed = 0
        while (processed < maxPerTick) {
            val next = scanQueue.poll() ?: break
            val (dir, result) = next
            onScanResult(dir, result)
            if (!result.scanFailed) {
                fetchGitStatus(dir.path)
                Thread {
                    buildFileWatcher.watch(dir.path)
                }.apply {
                    isDaemon = true
                    name = "build-file-watch-${dir.label()}"
                }.start()
            }
            processed++
        }
        if (scanQueue.isNotEmpty()) {
            scanApplyTimer.restart()
        } else {
            scanApplyPending.set(false)
        }
    }

    fun fetchGitStatus(path: String) {
        object : SwingWorker<GitStatus, Void>() {
            override fun doInBackground(): GitStatus = ctx.gitService.readStatus(path)

            override fun done() {
                val status =
                    try {
                        get()
                    } catch (_: Exception) {
                        return
                    }
                gitStatusCache[path] = status
                repaintTimer.restart()
                onGitStatusReady(path, status)
            }
        }.execute()
    }

    fun updateAgentStatus(
        path: String,
        status: AgentStatus,
    ) {
        agentStatuses[path] = status
        if (agentStatuses.values.any { it == AgentStatus.THINKING }) {
            blinkTimer.start()
        } else {
            blinkTimer.stop()
        }
        requestRepaint()
    }

    fun unwatchAllBuildFiles() {
        buildFileWatcher.unwatchAll()
    }

    fun clearAll() {
        gitStatusCache.clear()
        agentStatuses.clear()
    }

    fun dispose() {
        blinkTimer.stop()
        scanApplyTimer.stop()
        repaintTimer.stop()
    }

    internal fun enqueueForTest(
        dir: ProjectDirectory,
        result: DetectedProject,
    ) {
        scanQueue.add(dir to result)
    }

    private fun scheduleScanApply() {
        if (scanApplyPending.compareAndSet(false, true)) {
            SwingUtilities.invokeLater { scanApplyTimer.restart() }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ProjectTreeScanCoordinator::class.java)
    }
}

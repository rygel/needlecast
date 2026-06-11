package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.config.JsonConfigStore
import io.github.rygel.needlecast.config.PromptLibraryStore
import io.github.rygel.needlecast.config.SkillLibraryStore
import io.github.rygel.needlecast.git.GitService
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.GitStatus
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.scanner.ProjectScanner
import io.github.rygel.needlecast.ui.terminal.AgentStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ProjectTreeScanCoordinatorTest {
    @TempDir
    lateinit var tempDir: Path

    private val noopScanner =
        object : ProjectScanner {
            override fun scan(directory: ProjectDirectory): DetectedProject? = null
        }

    private val noopGitService =
        object : GitService {
            override fun readStatus(dir: String): GitStatus = GitStatus(branch = null, isDirty = false)

            override fun log(
                dir: String,
                maxEntries: Int,
            ): String? = null

            override fun show(
                dir: String,
                hash: String,
            ): String? = null

            override fun changedFiles(dir: String): List<Nothing> = emptyList()

            override fun stage(
                dir: String,
                files: List<String>,
            ) {}

            override fun commit(
                dir: String,
                message: String,
            ) {}

            override fun fetchStreaming(
                dir: String,
                onLine: (String) -> Unit,
            ): Int = 0

            override fun pushStreaming(
                dir: String,
                onLine: (String) -> Unit,
            ): Int = 0

            override fun pullStreaming(
                dir: String,
                onLine: (String) -> Unit,
            ): Int = 0

            override fun branches(dir: String): List<String> = emptyList()

            override fun currentBranch(dir: String): String? = null

            override fun checkout(
                dir: String,
                branch: String,
            ): String? = null
        }

    private fun newContext(): AppContext {
        val store = JsonConfigStore(tempDir.resolve("config.json"))
        val promptStore = PromptLibraryStore(tempDir.resolve("prompts"), tempDir.resolve("commands"))
        val skillStore = SkillLibraryStore(tempDir.resolve("skills"))
        return AppContext(
            configStore = store,
            scanner = noopScanner,
            gitService = noopGitService,
            promptLibraryStore = promptStore,
            skillLibraryStore = skillStore,
        )
    }

    @Test
    fun `scanProject calls onScanResult with detected project`() {
        val ctx = newContext()
        val testDir = tempDir.resolve("myproject")
        testDir.toFile().mkdirs()
        val dir = ProjectDirectory(path = testDir.toString(), displayName = "myproject")
        val latch = CountDownLatch(1)
        var capturedResult: DetectedProject? = null
        val coordinator =
            ProjectTreeScanCoordinator(
                ctx = ctx,
                onScanResult = { _, result ->
                    capturedResult = result
                    latch.countDown()
                },
                onGitStatusReady = { _, _ -> },
                requestRepaint = {},
            )

        coordinator.scanProject(dir)

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(capturedResult).isNotNull
        assertThat(capturedResult!!.directory).isEqualTo(dir)
        coordinator.dispose()
    }

    @Test
    fun `scanProject handles scan failure gracefully`() {
        val throwingScanner =
            object : ProjectScanner {
                override fun scan(directory: ProjectDirectory): DetectedProject? = throw RuntimeException("scan boom")
            }
        val store = JsonConfigStore(tempDir.resolve("config-fail.json"))
        val ctx =
            AppContext(
                configStore = store,
                scanner = throwingScanner,
                gitService = noopGitService,
                promptLibraryStore = PromptLibraryStore(tempDir.resolve("p"), tempDir.resolve("c")),
                skillLibraryStore = SkillLibraryStore(tempDir.resolve("s")),
            )
        val dir = ProjectDirectory(path = "/nonexistent/path", displayName = "broken")
        val latch = CountDownLatch(1)
        var capturedResult: DetectedProject? = null
        val coordinator =
            ProjectTreeScanCoordinator(
                ctx = ctx,
                onScanResult = { _, result ->
                    capturedResult = result
                    latch.countDown()
                },
                onGitStatusReady = { _, _ -> },
                requestRepaint = {},
            )

        coordinator.scanProject(dir)

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(capturedResult).isNotNull
        assertThat(capturedResult!!.scanFailed).isTrue()
        coordinator.dispose()
    }

    @Test
    fun `clearAll clears git status cache`() {
        val ctx = newContext()
        val coordinator =
            ProjectTreeScanCoordinator(
                ctx = ctx,
                onScanResult = { _, _ -> },
                onGitStatusReady = { _, _ -> },
                requestRepaint = {},
            )

        coordinator.gitStatusCache["/foo"] = GitStatus(branch = "main", isDirty = true)
        assertThat(coordinator.gitStatusCache).hasSize(1)

        coordinator.clearAll()

        assertThat(coordinator.gitStatusCache).isEmpty()
        coordinator.dispose()
    }

    @Test
    fun `updateAgentStatus starts blink timer when any agent thinking`() {
        val ctx = newContext()
        val coordinator =
            ProjectTreeScanCoordinator(
                ctx = ctx,
                onScanResult = { _, _ -> },
                onGitStatusReady = { _, _ -> },
                requestRepaint = {},
            )

        assertThat(coordinator.blinkTimerRunning).isFalse()

        coordinator.updateAgentStatus("/project-a", AgentStatus.THINKING)

        assertThat(coordinator.blinkTimerRunning).isTrue()
        coordinator.dispose()
    }

    @Test
    fun `updateAgentStatus keeps blink running if other agents still thinking`() {
        val ctx = newContext()
        val coordinator =
            ProjectTreeScanCoordinator(
                ctx = ctx,
                onScanResult = { _, _ -> },
                onGitStatusReady = { _, _ -> },
                requestRepaint = {},
            )

        coordinator.updateAgentStatus("/project-a", AgentStatus.THINKING)
        coordinator.updateAgentStatus("/project-b", AgentStatus.THINKING)

        coordinator.updateAgentStatus("/project-a", AgentStatus.WAITING)

        assertThat(coordinator.blinkTimerRunning).isTrue()
        coordinator.dispose()
    }

    @Test
    fun `drainScanQueue processes enqueued results`() {
        val ctx = newContext()
        val results = mutableListOf<Pair<ProjectDirectory, DetectedProject>>()
        val coordinator =
            ProjectTreeScanCoordinator(
                ctx = ctx,
                onScanResult = { dir, result -> results.add(dir to result) },
                onGitStatusReady = { _, _ -> },
                requestRepaint = {},
            )

        val dir = ProjectDirectory(path = "/test", displayName = "test")
        val detected = DetectedProject(dir, emptySet(), emptyList())
        coordinator.enqueueForTest(dir, detected)

        coordinator.drainScanQueue(10)

        assertThat(results).hasSize(1)
        assertThat(results[0].first).isEqualTo(dir)
        assertThat(results[0].second).isEqualTo(detected)
        coordinator.dispose()
    }

    @Test
    fun `drainScanQueue respects maxPerTick limit`() {
        val ctx = newContext()
        val results = mutableListOf<Pair<ProjectDirectory, DetectedProject>>()
        val coordinator =
            ProjectTreeScanCoordinator(
                ctx = ctx,
                onScanResult = { dir, result -> results.add(dir to result) },
                onGitStatusReady = { _, _ -> },
                requestRepaint = {},
            )

        for (i in 1..5) {
            val dir = ProjectDirectory(path = "/proj-$i", displayName = "proj-$i")
            coordinator.enqueueForTest(dir, DetectedProject(dir, emptySet(), emptyList()))
        }

        coordinator.drainScanQueue(3)

        assertThat(results).hasSize(3)
        coordinator.dispose()
    }

    @Test
    fun `dispose stops timers`() {
        val ctx = newContext()
        val coordinator =
            ProjectTreeScanCoordinator(
                ctx = ctx,
                onScanResult = { _, _ -> },
                onGitStatusReady = { _, _ -> },
                requestRepaint = {},
            )

        coordinator.updateAgentStatus("/project-a", AgentStatus.THINKING)
        assertThat(coordinator.blinkTimerRunning).isTrue()

        coordinator.dispose()

        assertThat(coordinator.blinkTimerRunning).isFalse()
    }
}

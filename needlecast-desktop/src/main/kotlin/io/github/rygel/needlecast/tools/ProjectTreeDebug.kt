package io.github.rygel.needlecast.tools

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.ThemeRegistry
import io.github.rygel.needlecast.config.ConfigStore
import io.github.rygel.needlecast.model.AppConfig
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.model.ProjectTreeEntry
import io.github.rygel.needlecast.ui.ProjectTreePanel
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * Lightweight debug harness for tree layout issues.
 * Run with:
 *   java -cp needlecast.jar io.github.rygel.needlecast.tools.ProjectTreeDebugKt
 */
fun main() {
    val demoRoot = Files.createTempDirectory("needlecast-tree-debug-")
    val projects = listOf(
        DebugDemoProject("alpha-service", "alpha-service", listOf("kotlin", "mvn"), "main", dirty = false),
        DebugDemoProject("beta-ui", "beta-ui", listOf("react", "ts"), "feature/ux-refresh", dirty = true),
        DebugDemoProject("gamma-ml", "gamma-ml", listOf("python", "ml"), "experiment/ablation", dirty = true),
        DebugDemoProject("delta-gateway", "delta-gateway", listOf("spring-boot", "kubernetes", "api-gateway"), "hotfix/auth", dirty = false),
    ).map { it.materialize(demoRoot) }

    val config = AppConfig(
        theme = "dark-purple",
        windowWidth = 900,
        windowHeight = 700,
        projectTree = listOf(
            ProjectTreeEntry.Folder(
                name = "Debug",
                color = "#7C4DFF",
                children = projects.map { p ->
                    ProjectTreeEntry.Project(
                        directory = ProjectDirectory(
                            path = p.dir.absolutePath,
                            displayName = p.displayName,
                        ),
                        tags = p.tags,
                    )
                },
            ),
        ),
    )

    ThemeRegistry.apply("dark-purple")
    val ctx = AppContext(configStore = DebugConfigStore(config))

    SwingUtilities.invokeLater {
        val frame = JFrame("Project Tree Debug")
        frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        frame.contentPane.add(ProjectTreePanel(ctx, onProjectSelected = {}))
        frame.setSize(900, 700)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}

private data class DebugDemoProject(
    val dirName: String,
    val displayName: String,
    val tags: List<String>,
    val branch: String,
    val dirty: Boolean,
) {
    fun materialize(root: Path): MaterializedProject {
        val dir = root.resolve(dirName).toFile().also { it.mkdirs() }
        dir.resolve("README.md").writeText("# $displayName\n")
        initGitRepo(dir, branch, dirty)
        return MaterializedProject(dir, displayName, tags)
    }
}

private data class MaterializedProject(
    val dir: File,
    val displayName: String,
    val tags: List<String>,
)

private fun initGitRepo(dir: File, branch: String, dirty: Boolean) {
    if (!runGit(dir, "init")) return
    runGit(dir, "config", "user.email", "demo@needlecast.local")
    runGit(dir, "config", "user.name", "Needlecast Demo")
    runGit(dir, "add", ".")
    runGit(dir, "commit", "-m", "Initial commit")
    runGit(dir, "branch", "-M", branch)
    if (dirty) {
        dir.resolve("README.md").appendText("\nUncommitted change.\n")
    }
}

private fun runGit(dir: File, vararg args: String): Boolean {
    return try {
        val cmd = listOf("git", "-C", dir.absolutePath) + args.toList()
        val proc = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()
        proc.inputStream.bufferedReader().use { it.readText() }
        proc.waitFor() == 0
    } catch (_: Exception) {
        false
    }
}

private class DebugConfigStore(private val initial: AppConfig) : ConfigStore {
    private var current = initial
    override fun load() = current
    override fun save(config: AppConfig) { current = config }
    override fun import(path: Path) = initial
    override fun export(config: AppConfig, path: Path) {}
}

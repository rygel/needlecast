package io.github.rygel.needlecast.tools

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.ThemeRegistry
import io.github.rygel.needlecast.config.ConfigStore
import io.github.rygel.needlecast.model.AppConfig
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.model.ProjectTreeEntry
import io.github.rygel.needlecast.ui.EnvEditorDialog
import io.github.rygel.needlecast.ui.MainWindow
import io.github.rygel.needlecast.ui.PromptLibraryDialog
import io.github.rygel.needlecast.ui.ProjectSwitcherDialog
import io.github.rygel.needlecast.ui.SettingsDialog
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Window
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import javax.imageio.ImageIO
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * Headless screenshot tour.
 *
 * Launches the full Needlecast UI with synthetic demo data, captures a PNG of each major
 * screen/dialog, and exits. Intended to run inside the Xvfb Docker container on CI so the
 * output always reflects the current UI — no hand-maintained screenshots.
 *
 * Usage:
 *   java -cp needlecast.jar io.github.rygel.needlecast.tools.ScreenshotTourKt [output-dir]
 *
 * Default output-dir: target/screenshots
 *
 * Requires a real display (DISPLAY env var). On CI use Xvfb:
 *   Xvfb :99 -screen 0 1280x900x24 & DISPLAY=:99 java ...
 */
fun main(args: Array<String>) {
    val outputDir = Path.of(args.getOrElse(0) { "target/screenshots" })
    Files.createDirectories(outputDir)

    // Isolated home so docking state and config don't bleed in from the developer's machine
    val fakeHome = Files.createTempDirectory("needlecast-tour-")
    System.setProperty("user.home", fakeHome.toString())
    System.setProperty("awt.useSystemAAFontSettings", "lcd")
    System.setProperty("swing.aatext", "true")
    System.setProperty("apple.awt.application.name", "Needlecast")

    val demoRoot = fakeHome.resolve("demo-projects").also { Files.createDirectories(it) }
    val projects = createDemoProjects(demoRoot)
    val demoConfig = buildDemoConfig(projects)

    ThemeRegistry.apply("one-dark")

    val ctx = AppContext(configStore = FixedConfigStore(demoConfig))

    val windowReady = CountDownLatch(1)
    var mainWindow: MainWindow? = null

    SwingUtilities.invokeLater {
        val w = MainWindow(ctx)
        w.setSize(1280, 840)
        w.setLocationRelativeTo(null)
        w.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        w.isVisible = true
        mainWindow = w
        windowReady.countDown()
    }

    // Hard timeout — kill the JVM if the tour takes longer than 2 minutes
    val watchdog = Thread {
        Thread.sleep(120_000)
        System.err.println("Screenshot tour timed out after 2 minutes")
        Runtime.getRuntime().halt(1)
    }.apply { isDaemon = true; start() }

    windowReady.await()
    val robot = Robot()
    // Allow the window and all panels to fully render before the first screenshot
    Thread.sleep(3000)

    val w = mainWindow!!

    try {
        // ── 1: Main window ────────────────────────────────────────────────────
        screenshot(robot, w, outputDir.resolve("01-main-window.png"))
        println("  > 01-main-window.png")

        // ── 2: Settings dialog ────────────────────────────────────────────────
        dialogShot(robot, outputDir.resolve("02-settings.png")) {
            SettingsDialog(w, ctx,
                sendToTerminal       = {},
                onShortcutsChanged   = {},
                onLayoutChanged      = {},
                onTerminalColorsChanged = { _, _ -> },
            ).isVisible = true
        }

        // ── 3: Prompt Library ─────────────────────────────────────────────────
        dialogShot(robot, outputDir.resolve("03-prompt-library.png")) {
            PromptLibraryDialog(w, ctx, sendToTerminal = {}).isVisible = true
        }

        // ── 4: Command Library ────────────────────────────────────────────────
        dialogShot(robot, outputDir.resolve("04-command-library.png")) {
            PromptLibraryDialog(
                w, ctx,
                sendToTerminal  = {},
                title           = "Command Library",
                sendButtonLabel = "Run in Terminal",
                loadLibrary     = { ctx.config.commandLibrary },
                saveLibrary     = { ctx.updateConfig(ctx.config.copy(commandLibrary = it)) },
            ).isVisible = true
        }

        // ── 5: Project Switcher ───────────────────────────────────────────────
        dialogShot(robot, outputDir.resolve("05-project-switcher.png")) {
            ProjectSwitcherDialog(w, ctx, onSelect = { _, _ -> }).isVisible = true
        }

        // ── 6: Environment Variables editor ──────────────────────────────────
        dialogShot(robot, outputDir.resolve("06-env-editor.png")) {
            EnvEditorDialog(
                owner        = w,
                projectLabel = "needlecast-app",
                initial      = mapOf("JAVA_OPTS" to "-Xmx512m", "MAVEN_OPTS" to "-Xss4m", "NODE_ENV" to "development"),
                onSave       = {},
            ).isVisible = true
        }

        println("Screenshots written to $outputDir")
    } catch (e: Exception) {
        System.err.println("Screenshot tour failed: ${e.message}")
        e.printStackTrace()
    } finally {
        // Force exit — MainWindow spawns non-daemon threads (terminal, hook server)
        // that would keep the JVM alive forever
        Runtime.getRuntime().halt(0)
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Show a dialog, wait for it to render, take a screenshot, close it. Skips on timeout. */
private fun dialogShot(robot: Robot, dest: Path, showDialog: () -> Unit) {
    SwingUtilities.invokeLater(showDialog)
    // Wait up to 5s for a dialog to appear
    val deadline = System.currentTimeMillis() + 5000
    while (System.currentTimeMillis() < deadline) {
        val visible = Window.getWindows().filterIsInstance<JDialog>().any { it.isVisible }
        if (visible) break
        Thread.sleep(100)
    }
    Thread.sleep(800) // let it paint
    screenshotTopDialog(robot, dest)
    val name = dest.fileName.toString()
    println("  > $name")
    closeTopDialog()
    Thread.sleep(300)
}

private fun screenshot(robot: Robot, window: Window, dest: Path) {
    SwingUtilities.invokeAndWait {
        val bounds = window.bounds
        val img = robot.createScreenCapture(Rectangle(bounds.x, bounds.y, bounds.width, bounds.height))
        ImageIO.write(img, "PNG", dest.toFile())
    }
}

private fun screenshotTopDialog(robot: Robot, dest: Path) {
    SwingUtilities.invokeAndWait {
        val dlg = Window.getWindows()
            .filterIsInstance<JDialog>()
            .lastOrNull { it.isVisible } ?: return@invokeAndWait
        val bounds = dlg.bounds
        val img = robot.createScreenCapture(Rectangle(bounds.x, bounds.y, bounds.width, bounds.height))
        ImageIO.write(img, "PNG", dest.toFile())
    }
}

private fun closeTopDialog() {
    SwingUtilities.invokeAndWait {
        Window.getWindows()
            .filterIsInstance<JDialog>()
            .filter { it.isVisible }
            .forEach { it.dispose() }
    }
}

// ── Demo data ─────────────────────────────────────────────────────────────────

private data class DemoProject(val dir: File, val displayName: String)

private fun createDemoProjects(root: Path): List<DemoProject> {
    val projects = listOf(
        Triple("needlecast-app",  "needlecast-app",  ::scaffoldMaven),
        Triple("web-dashboard",   "web-dashboard",   ::scaffoldNpm),
        Triple("api-service",     "api-service",     ::scaffoldGradle),
        Triple("data-pipeline",   "data-pipeline",   ::scaffoldMaven),
    )
    return projects.map { (dirName, label, scaffold) ->
        val dir = root.resolve(dirName).toFile().also { it.mkdirs() }
        scaffold(dir)
        DemoProject(dir, label)
    }
}

private fun scaffoldMaven(dir: File) {
    dir.resolve("pom.xml").writeText("""
        <?xml version="1.0" encoding="UTF-8"?>
        <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>io.example</groupId>
          <artifactId>${dir.name}</artifactId>
          <version>1.0.0</version>
          <packaging>jar</packaging>
        </project>
    """.trimIndent())
    dir.resolve("README.md").writeText("""
        # ${dir.name}

        A sample Maven project used for Needlecast screenshots.

        ## Build

        ```bash
        mvn package
        mvn verify
        ```
    """.trimIndent())
    dir.resolve("src/main/kotlin").mkdirs()
    dir.resolve("src/test/kotlin").mkdirs()
    dir.resolve("src/main/kotlin/Main.kt").writeText("""
        fun main() {
            println("Hello from ${dir.name}")
        }
    """.trimIndent())
}

private fun scaffoldNpm(dir: File) {
    dir.resolve("package.json").writeText("""
        {
          "name": "${dir.name}",
          "version": "1.0.0",
          "scripts": {
            "start": "node src/index.js",
            "build": "webpack --mode production",
            "test": "jest"
          }
        }
    """.trimIndent())
    dir.resolve("README.md").writeText("""
        # ${dir.name}

        A sample npm project used for Needlecast screenshots.

        ## Dev

        ```bash
        npm install
        npm run dev
        ```
    """.trimIndent())
    dir.resolve("src").mkdirs()
    dir.resolve("src/index.js").writeText("console.log('Hello from ${dir.name}');")
}

private fun scaffoldGradle(dir: File) {
    dir.resolve("build.gradle").writeText("""
        plugins {
            id 'java'
        }
        group = 'io.example'
        version = '1.0.0'

        repositories { mavenCentral() }
    """.trimIndent())
    dir.resolve("README.md").writeText("""
        # ${dir.name}

        A sample Gradle project used for Needlecast screenshots.

        ## Build

        ```bash
        ./gradlew build
        ./gradlew test
        ```
    """.trimIndent())
    dir.resolve("src/main/java").mkdirs()
    dir.resolve("src/main/java/Main.java").writeText("""
        public class Main {
            public static void main(String[] args) {
                System.out.println("Hello from ${dir.name}");
            }
        }
    """.trimIndent())
}

private fun buildDemoConfig(projects: List<DemoProject>): AppConfig {
    val (mavenApp, npmApp, gradleService, mavenPipeline) = projects

    val projectTree = listOf(
        ProjectTreeEntry.Folder(
            name  = "Apps",
            color = "#3f88c5",
            children = listOf(
                ProjectTreeEntry.Project(
                    directory = ProjectDirectory(
                        path        = mavenApp.dir.absolutePath,
                        displayName = mavenApp.displayName,
                    )
                ),
                ProjectTreeEntry.Project(
                    directory = ProjectDirectory(
                        path        = npmApp.dir.absolutePath,
                        displayName = npmApp.displayName,
                    )
                ),
            ),
        ),
        ProjectTreeEntry.Folder(
            name  = "Services",
            color = "#2a9d8f",
            children = listOf(
                ProjectTreeEntry.Project(
                    directory = ProjectDirectory(
                        path        = gradleService.dir.absolutePath,
                        displayName = gradleService.displayName,
                    )
                ),
                ProjectTreeEntry.Project(
                    directory = ProjectDirectory(
                        path        = mavenPipeline.dir.absolutePath,
                        displayName = mavenPipeline.displayName,
                    )
                ),
            ),
        ),
    )

    return AppConfig(
        theme       = "one-dark",
        projectTree = projectTree,
        windowWidth  = 1280,
        windowHeight = 840,
    )
}

// ── In-memory ConfigStore ─────────────────────────────────────────────────────

private class FixedConfigStore(private val initial: AppConfig) : ConfigStore {
    private var current = initial
    override fun load()                                = current
    override fun save(config: AppConfig)               { current = config }
    override fun import(path: Path)                    = initial
    override fun export(config: AppConfig, path: Path) {}
}

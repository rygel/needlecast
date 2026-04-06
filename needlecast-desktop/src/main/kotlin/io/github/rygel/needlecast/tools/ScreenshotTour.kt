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
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

/**
 * Headless screenshot tour — captures every major screen of the Needlecast UI.
 *
 * Runs inside the Xvfb Docker container on CI to produce up-to-date screenshots
 * for the user manual. Uses Dark Purple theme with realistic demo data.
 *
 * Usage:
 *   java -cp needlecast.jar io.github.rygel.needlecast.tools.ScreenshotTourKt [output-dir]
 */
fun main(args: Array<String>) {
    val outputDir = Path.of(args.getOrElse(0) { "target/screenshots" })
    Files.createDirectories(outputDir)

    val fakeHome = Files.createTempDirectory("needlecast-tour-")
    System.setProperty("user.home", fakeHome.toString())
    System.setProperty("awt.useSystemAAFontSettings", "lcd")
    System.setProperty("swing.aatext", "true")
    System.setProperty("apple.awt.application.name", "Needlecast")

    val demoRoot = fakeHome.resolve("demo-projects").also { Files.createDirectories(it) }
    val projects = createDemoProjects(demoRoot)
    val demoConfig = buildDemoConfig(projects)

    // Create demo log file before the window opens so the log viewer discovers it
    val demoLogDir = File(projects[0].dir.absolutePath, "target")
    demoLogDir.mkdirs()
    File(demoLogDir, "app.log").writeText(buildDemoLog())

    ThemeRegistry.apply("dark-purple")

    val ctx = AppContext(configStore = FixedConfigStore(demoConfig))

    val windowReady = CountDownLatch(1)
    var mainWindow: MainWindow? = null

    SwingUtilities.invokeLater {
        val w = MainWindow(ctx)
        w.setSize(1440, 900)
        w.setLocationRelativeTo(null)
        w.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        w.isVisible = true
        mainWindow = w
        windowReady.countDown()
    }

    // Hard timeout
    Thread {
        Thread.sleep(120_000)
        System.err.println("Screenshot tour timed out after 2 minutes")
        Runtime.getRuntime().halt(1)
    }.apply { isDaemon = true; start() }

    windowReady.await()
    val robot = Robot()
    Thread.sleep(3000)

    val w = mainWindow!!

    // Force tree to recalculate cell widths now that the window is fully laid out
    SwingUtilities.invokeAndWait {
        // Access the project tree panel and invalidate its layout
        try {
            val field = w.javaClass.getDeclaredField("projectTreePanel")
            field.isAccessible = true
            val treePanel = field.get(w)
            treePanel.javaClass.getMethod("invalidateTreeLayout").invoke(treePanel)
        } catch (e: Exception) { e.printStackTrace() }
    }
    Thread.sleep(500)

    try {
        // ── 01: Main window — project tree with folders and projects ──────
        screenshot(robot, w, outputDir.resolve("01-main-window.png"))
        println("  > 01-main-window.png")

        // ── 02: Settings — General tab ───────────────────────────────────
        dialogShot(robot, outputDir.resolve("02-settings.png")) {
            SettingsDialog(w, ctx,
                sendToTerminal       = {},
                onShortcutsChanged   = {},
                onLayoutChanged      = {},
                onTerminalColorsChanged = { _, _ -> },
            ).isVisible = true
        }

        // ── 03: Prompt Library ───────────────────────────────────────────
        dialogShot(robot, outputDir.resolve("03-prompt-library.png")) {
            PromptLibraryDialog(w, ctx, sendToTerminal = {}).isVisible = true
        }

        // ── 04: Command Library ──────────────────────────────────────────
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

        // ── 05: Project Switcher (Ctrl+P) ────────────────────────────────
        dialogShot(robot, outputDir.resolve("05-project-switcher.png")) {
            ProjectSwitcherDialog(w, ctx, onSelect = { _, _ -> }).isVisible = true
        }

        // ── 06: Environment Variables ────────────────────────────────────
        dialogShot(robot, outputDir.resolve("06-env-editor.png")) {
            EnvEditorDialog(
                owner        = w,
                projectLabel = "needlecast-app",
                initial      = mapOf(
                    "JAVA_HOME" to "/usr/lib/jvm/java-21",
                    "MAVEN_OPTS" to "-Xmx2g -Xss4m",
                    "NODE_ENV" to "development",
                    "DATABASE_URL" to "postgres://localhost:5432/mydb",
                ),
                onSave       = {},
            ).isVisible = true
        }

        // ── 08: Renovate panel (show via Panels menu, with project loaded) ─
        SwingUtilities.invokeAndWait {
            try {
                val toggleRenovate = w.javaClass.getDeclaredMethod("toggleRenovate", Boolean::class.java)
                toggleRenovate.isAccessible = true
                toggleRenovate.invoke(w, true)
                // Load the first project into the renovate panel
                val renovatePanel = w.javaClass.getDeclaredField("renovatePanel")
                renovatePanel.isAccessible = true
                val panel = renovatePanel.get(w)
                val loadMethod = panel.javaClass.getMethod("loadProject", String::class.java)
                loadMethod.invoke(panel, projects[0].dir.absolutePath)
            } catch (e: Exception) { e.printStackTrace() }
        }
        Thread.sleep(1000)
        screenshot(robot, w, outputDir.resolve("08-renovate.png"))
        println("  > 08-renovate.png")

        // ── 09: Log Viewer panel (with demo log content) ─────────────────
        SwingUtilities.invokeAndWait {
            try {
                // Hide renovate, show log viewer
                val toggleRenovate = w.javaClass.getDeclaredMethod("toggleRenovate", Boolean::class.java)
                toggleRenovate.isAccessible = true
                toggleRenovate.invoke(w, false)
                // Load project into log viewer
                val logViewer = w.javaClass.getDeclaredField("logViewerPanel")
                logViewer.isAccessible = true
                val panel = logViewer.get(w)
                val loadMethod = panel.javaClass.getMethod("loadProject", String::class.java)
                loadMethod.invoke(panel, projects[0].dir.absolutePath)
            } catch (e: Exception) { e.printStackTrace() }
        }
        Thread.sleep(2000)
        screenshot(robot, w, outputDir.resolve("09-log-viewer.png"))
        println("  > 09-log-viewer.png")

        // ── 07: About dialog ─────────────────────────────────────────────
        dialogShot(robot, outputDir.resolve("07-about.png")) {
            // Trigger the about dialog by calling showAbout via reflection
            try {
                val method = w.javaClass.getDeclaredMethod("showAbout")
                method.isAccessible = true
                method.invoke(w)
            } catch (_: Exception) {
                // Fallback: show a simple about-like dialog
                JOptionPane.showMessageDialog(w, "Needlecast", "About", JOptionPane.INFORMATION_MESSAGE)
            }
        }

        println("Screenshots written to $outputDir")
    } catch (e: Exception) {
        System.err.println("Screenshot tour failed: ${e.message}")
        e.printStackTrace()
    } finally {
        Runtime.getRuntime().halt(0)
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun dialogShot(robot: Robot, dest: Path, showDialog: () -> Unit) {
    SwingUtilities.invokeLater(showDialog)
    val deadline = System.currentTimeMillis() + 5000
    while (System.currentTimeMillis() < deadline) {
        val visible = Window.getWindows().filterIsInstance<JDialog>().any { it.isVisible }
        if (visible) break
        Thread.sleep(100)
    }
    Thread.sleep(800)
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
        Triple("needlecast",                      "needlecast",                       ::scaffoldMaven),
        Triple("web-dashboard",                   "web-dashboard",                    ::scaffoldNpm),
        Triple("api-service",                     "api-service",                      ::scaffoldGradle),
        Triple("ml-pipeline",                     "ml-pipeline",                      ::scaffoldPython),
        Triple("rust-engine",                     "rust-engine",                      ::scaffoldRust),
        Triple("go-service",                      "go-service",                       ::scaffoldGo),
        Triple("enterprise-microservice-gateway", "enterprise-microservice-gateway",  ::scaffoldMaven),
        Triple("react-native-shopping-app",       "react-native-shopping-app",        ::scaffoldNpm),
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
          <build><plugins>
            <plugin><artifactId>exec-maven-plugin</artifactId></plugin>
          </plugins></build>
        </project>
    """.trimIndent())
    dir.resolve("README.md").writeText("# ${dir.name}\n\nA sample Maven project.\n")
    dir.resolve("src/main/kotlin").mkdirs()
    dir.resolve("src/test/kotlin").mkdirs()
    dir.resolve("src/main/kotlin/Main.kt").writeText("fun main() {\n    println(\"Hello from ${dir.name}\")\n}\n")
}

private fun scaffoldNpm(dir: File) {
    dir.resolve("package.json").writeText("""
        {
          "name": "${dir.name}",
          "version": "1.0.0",
          "scripts": {
            "dev": "vite",
            "build": "vite build",
            "test": "vitest",
            "lint": "eslint .",
            "preview": "vite preview"
          }
        }
    """.trimIndent())
    dir.resolve("README.md").writeText("# ${dir.name}\n\nA sample frontend project.\n")
    dir.resolve("src").mkdirs()
    dir.resolve("src/index.ts").writeText("console.log('Hello from ${dir.name}');\n")
    dir.resolve("src/App.tsx").writeText("export default function App() {\n  return <div>Hello</div>;\n}\n")
}

private fun scaffoldGradle(dir: File) {
    dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"${dir.name}\"\n")
    dir.resolve("build.gradle.kts").writeText("""
        plugins {
            id("java")
            id("application")
        }
        group = "io.example"
        version = "1.0.0"
        application { mainClass.set("Main") }
        repositories { mavenCentral() }
    """.trimIndent())
    dir.resolve("README.md").writeText("# ${dir.name}\n\nA sample Gradle project.\n")
    dir.resolve("src/main/java").mkdirs()
    dir.resolve("src/main/java/Main.java").writeText("public class Main {\n    public static void main(String[] a) {\n        System.out.println(\"Hello\");\n    }\n}\n")
}

private fun buildDemoConfig(projects: List<DemoProject>): AppConfig {
    val needlecast = projects[0]
    val webDashboard = projects[1]
    val apiService = projects[2]
    val mlPipeline = projects[3]
    val rustEngine = projects[4]
    val goService = projects[5]
    val enterpriseGw = projects[6]
    val shoppingApp = projects[7]

    val projectTree = listOf(
        ProjectTreeEntry.Folder(
            name  = "Java / Kotlin",
            color = "#7C4DFF",
            children = listOf(
                ProjectTreeEntry.Project(
                    directory = ProjectDirectory(
                        path = needlecast.dir.absolutePath,
                        displayName = needlecast.displayName,
                        color = "#7C4DFF",
                    ),
                    tags = listOf("kotlin", "swing"),
                ),
                ProjectTreeEntry.Project(
                    directory = ProjectDirectory(
                        path = apiService.dir.absolutePath,
                        displayName = apiService.displayName,
                    ),
                    tags = listOf("java", "rest"),
                ),
            ),
        ),
        ProjectTreeEntry.Folder(
            name  = "Frontend",
            color = "#FF6D00",
            children = listOf(
                ProjectTreeEntry.Project(
                    directory = ProjectDirectory(
                        path = webDashboard.dir.absolutePath,
                        displayName = webDashboard.displayName,
                        color = "#FF6D00",
                    ),
                    tags = listOf("react", "ts"),
                ),
            ),
        ),
        ProjectTreeEntry.Folder(
            name  = "Python / Rust / Go",
            color = "#00BFA5",
            children = listOf(
                ProjectTreeEntry.Project(
                    directory = ProjectDirectory(
                        path = mlPipeline.dir.absolutePath,
                        displayName = mlPipeline.displayName,
                    ),
                    tags = listOf("python", "ml"),
                ),
                ProjectTreeEntry.Project(
                    directory = ProjectDirectory(
                        path = rustEngine.dir.absolutePath,
                        displayName = rustEngine.displayName,
                    ),
                    tags = listOf("rust", "wasm"),
                ),
                ProjectTreeEntry.Project(
                    directory = ProjectDirectory(
                        path = goService.dir.absolutePath,
                        displayName = goService.displayName,
                    ),
                    tags = listOf("go", "grpc"),
                ),
            ),
        ),
        ProjectTreeEntry.Folder(
            name  = "Enterprise",
            color = "#E65100",
            children = listOf(
                ProjectTreeEntry.Project(
                    directory = ProjectDirectory(
                        path = enterpriseGw.dir.absolutePath,
                        displayName = enterpriseGw.displayName,
                    ),
                    tags = listOf("spring-boot", "kubernetes", "microservice", "api-gateway"),
                ),
                ProjectTreeEntry.Project(
                    directory = ProjectDirectory(
                        path = shoppingApp.dir.absolutePath,
                        displayName = shoppingApp.displayName,
                    ),
                    tags = listOf("react-native", "typescript", "mobile", "e-commerce"),
                ),
            ),
        ),
    )

    return AppConfig(
        theme       = "dark-purple",
        projectTree = projectTree,
        windowWidth  = 1440,
        windowHeight = 900,
    )
}

private fun scaffoldPython(dir: File) {
    dir.resolve("pyproject.toml").writeText("""
        [project]
        name = "${dir.name}"
        version = "0.1.0"
        requires-python = ">=3.12"
        dependencies = ["numpy", "pandas", "scikit-learn"]

        [tool.uv]
        dev-dependencies = ["pytest", "ruff"]

        [project.scripts]
        train = "ml_pipeline.train:main"
        serve = "ml_pipeline.serve:main"
    """.trimIndent())
    dir.resolve("uv.lock").writeText("")
    dir.resolve("README.md").writeText("# ${dir.name}\n\nML pipeline with uv.\n")
    dir.resolve("src").mkdirs()
}

private fun scaffoldRust(dir: File) {
    dir.resolve("Cargo.toml").writeText("""
        [package]
        name = "${dir.name}"
        version = "0.1.0"
        edition = "2024"

        [dependencies]
        serde = { version = "1.0", features = ["derive"] }
        tokio = { version = "1", features = ["full"] }

        [workspace]
        members = ["core", "cli"]
    """.trimIndent())
    dir.resolve("README.md").writeText("# ${dir.name}\n\nA Rust workspace project.\n")
    dir.resolve("src").mkdirs()
    dir.resolve("src/main.rs").writeText("fn main() {\n    println!(\"Hello from ${dir.name}\");\n}\n")
}

private fun scaffoldGo(dir: File) {
    dir.resolve("go.mod").writeText("module github.com/example/${dir.name}\n\ngo 1.22\n")
    dir.resolve("main.go").writeText("package main\n\nimport \"fmt\"\n\nfunc main() {\n\tfmt.Println(\"Hello from ${dir.name}\")\n}\n")
    dir.resolve("README.md").writeText("# ${dir.name}\n\nA Go service.\n")
    val cmdDir = dir.resolve("cmd/server")
    cmdDir.mkdirs()
    cmdDir.resolve("main.go").writeText("package main\n\nfunc main() {}\n")
}

private fun buildDemoLog(): String = buildString {
    appendLine("10:23:45.123 [main] INFO  io.example.Application - Starting application v1.0.0")
    appendLine("10:23:45.456 [main] INFO  io.example.config.ConfigLoader - Loading configuration from application.yml")
    appendLine("10:23:45.789 [main] DEBUG io.example.db.ConnectionPool - Initializing connection pool: maxSize=10, timeout=30s")
    appendLine("10:23:46.012 [main] INFO  io.example.db.ConnectionPool - Database connection established: jdbc:postgresql://localhost:5432/mydb")
    appendLine("10:23:46.234 [main] INFO  io.example.web.Server - Starting HTTP server on port 8080")
    appendLine("10:23:46.567 [main] INFO  io.example.web.Server - Server started in 1.4s")
    appendLine("10:23:50.100 [http-1] INFO  io.example.web.RequestLogger - GET /api/health -> 200 (2ms)")
    appendLine("10:23:51.200 [http-2] INFO  io.example.web.RequestLogger - GET /api/users -> 200 (45ms)")
    appendLine("10:23:52.300 [http-3] WARN  io.example.web.RateLimiter - Rate limit approaching for IP 192.168.1.42 (85/100 requests)")
    appendLine("10:23:53.400 [http-4] INFO  io.example.web.RequestLogger - POST /api/users -> 201 (120ms)")
    appendLine("10:23:54.500 [scheduler-1] DEBUG io.example.jobs.CleanupJob - Running scheduled cleanup: removing sessions older than 24h")
    appendLine("10:23:55.600 [http-5] ERROR io.example.web.ExceptionHandler - Unhandled exception in request handler")
    appendLine("    at io.example.service.UserService.findById(UserService.kt:42)")
    appendLine("    at io.example.web.UserController.getUser(UserController.kt:28)")
    appendLine("    at io.example.web.Router.handleRequest(Router.kt:65)")
    appendLine("Caused by: java.sql.SQLException: Connection timed out")
    appendLine("    at io.example.db.ConnectionPool.acquire(ConnectionPool.kt:91)")
    appendLine("    ... 12 more")
    appendLine("10:23:56.700 [http-6] INFO  io.example.web.RequestLogger - GET /api/products?page=1 -> 200 (32ms)")
    appendLine("10:23:57.800 [http-7] WARN  io.example.auth.TokenValidator - Expired JWT token for user=admin")
    appendLine("10:23:58.900 [http-8] INFO  io.example.web.RequestLogger - GET /api/dashboard -> 200 (78ms)")
    appendLine("10:24:00.000 [scheduler-1] INFO  io.example.jobs.CleanupJob - Cleanup complete: removed 23 expired sessions")
}

// ── In-memory ConfigStore ─────────────────────────────────────────────────────

private class FixedConfigStore(private val initial: AppConfig) : ConfigStore {
    private var current = initial
    override fun load()                                = current
    override fun save(config: AppConfig)               { current = config }
    override fun import(path: Path)                    = initial
    override fun export(config: AppConfig, path: Path) {}
}

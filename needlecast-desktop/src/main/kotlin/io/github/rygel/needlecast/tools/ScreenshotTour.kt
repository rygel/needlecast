package io.github.rygel.needlecast.tools

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.ThemeRegistry
import io.github.rygel.needlecast.config.ConfigStore
import io.github.rygel.needlecast.model.AppConfig
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.model.ProjectTreeEntry
import io.github.rygel.needlecast.model.PromptTemplate
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
        try {
            val field = w.javaClass.getDeclaredField("projectTreePanel")
            field.isAccessible = true
            val treePanel = field.get(w)
            treePanel.javaClass.getMethod("invalidateTreeLayout").invoke(treePanel)
        } catch (e: Exception) { e.printStackTrace() }
    }
    Thread.sleep(500)
    waitForProjectTreeData(w, projects.size)

    try {
        // ── 01: Main window — project tree with folders and projects ──────
        try {
            screenshot(robot, w, outputDir.resolve("01-main-window.png"))
            println("  > 01-main-window.png")
        } catch (e: Exception) { System.err.println("01-main-window failed: ${e.message}") }

        // ── 02: Commands panel ────────────────────────────────────────────
        try {
            SwingUtilities.invokeAndWait {
                try {
                    // Ensure commands panel is visible
                    val toggleCommands = w.javaClass.getDeclaredMethod("toggleCommands", Boolean::class.java)
                    toggleCommands.isAccessible = true
                    toggleCommands.invoke(w, true)
                    // Load first project into commands panel
                    val commandsField = w.javaClass.getDeclaredField("commandPanel")
                    commandsField.isAccessible = true
                    val commandPanel = commandsField.get(w)
                    // Get scan results from the project tree panel to get a DetectedProject
                    val treeField = w.javaClass.getDeclaredField("projectTreePanel")
                    treeField.isAccessible = true
                    val treePanel = treeField.get(w)
                    val scanField = treePanel.javaClass.getDeclaredField("scanResults")
                    scanField.isAccessible = true
                    val scanResults = scanField.get(treePanel) as? Map<*, *>
                    val firstProject = scanResults?.values?.firstOrNull()
                    if (firstProject != null) {
                        val loadMethod = commandPanel.javaClass.getMethod("loadProject", firstProject.javaClass)
                        loadMethod.invoke(commandPanel, firstProject)
                    }
                    // Select the commands dockable tab
                    val commandsDockable = w.javaClass.getDeclaredField("commandsDockable")
                    commandsDockable.isAccessible = true
                    val dockable = commandsDockable.get(w)
                    val selectTab = w.javaClass.getDeclaredMethod("selectDockableTab", dockable.javaClass)
                    selectTab.isAccessible = true
                    selectTab.invoke(w, dockable)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(800)
            screenshot(robot, w, outputDir.resolve("02-commands.png"))
            println("  > 02-commands.png")
        } catch (e: Exception) { System.err.println("02-commands failed: ${e.message}") }

        // ── 03: Terminal panel — idle ─────────────────────────────────────
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val termField = w.javaClass.getDeclaredField("terminalDockable")
                    termField.isAccessible = true
                    val dockable = termField.get(w)
                    val selectTab = w.javaClass.getDeclaredMethod("selectDockableTab", dockable.javaClass)
                    selectTab.isAccessible = true
                    selectTab.invoke(w, dockable)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(800)
            screenshot(robot, w, outputDir.resolve("03-terminal-idle.png"))
            println("  > 03-terminal-idle.png")
        } catch (e: Exception) { System.err.println("03-terminal-idle failed: ${e.message}") }

        // ── 04: Terminal panel — active ───────────────────────────────────
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val termField = w.javaClass.getDeclaredField("terminalPanel")
                    termField.isAccessible = true
                    val terminalPanel = termField.get(w)
                    val activateMethod = terminalPanel.javaClass.getMethod(
                        "activateProject",
                        String::class.java,
                        Map::class.java,
                        String::class.java,
                        String::class.java,
                    )
                    activateMethod.invoke(terminalPanel, projects[0].dir.absolutePath, emptyMap<String, String>(), null, null)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(2000)
            screenshot(robot, w, outputDir.resolve("04-terminal-active.png"))
            println("  > 04-terminal-active.png")
        } catch (e: Exception) { System.err.println("04-terminal-active failed: ${e.message}") }

        // ── 05: Git log panel ─────────────────────────────────────────────
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val toggleGitLog = w.javaClass.getDeclaredMethod("toggleGitLog", Boolean::class.java)
                    toggleGitLog.isAccessible = true
                    toggleGitLog.invoke(w, true)
                    val gitLogField = w.javaClass.getDeclaredField("gitLogPanel")
                    gitLogField.isAccessible = true
                    val gitLogPanel = gitLogField.get(w)
                    val loadMethod = gitLogPanel.javaClass.getMethod("loadProject", String::class.java)
                    loadMethod.invoke(gitLogPanel, projects[0].dir.absolutePath)
                    val dockableField = w.javaClass.getDeclaredField("gitLogDockable")
                    dockableField.isAccessible = true
                    val dockable = dockableField.get(w)
                    val selectTab = w.javaClass.getDeclaredMethod("selectDockableTab", dockable.javaClass)
                    selectTab.isAccessible = true
                    selectTab.invoke(w, dockable)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(1500)
            screenshot(robot, w, outputDir.resolve("05-git-log.png"))
            println("  > 05-git-log.png")
        } catch (e: Exception) { System.err.println("05-git-log failed: ${e.message}") }

        // ── 06: Explorer panel — directory listing ────────────────────────
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val toggleExplorer = w.javaClass.getDeclaredMethod("toggleExplorer", Boolean::class.java)
                    toggleExplorer.isAccessible = true
                    toggleExplorer.invoke(w, true)
                    val explorerField = w.javaClass.getDeclaredField("explorerPanel")
                    explorerField.isAccessible = true
                    val explorerPanel = explorerField.get(w)
                    val setRootDir = explorerPanel.javaClass.getMethod("setRootDirectory", File::class.java)
                    setRootDir.invoke(explorerPanel, projects[0].dir)
                    val explorerDockable = w.javaClass.getDeclaredField("explorerDockable")
                    explorerDockable.isAccessible = true
                    val dockable = explorerDockable.get(w)
                    val selectTab = w.javaClass.getDeclaredMethod("selectDockableTab", dockable.javaClass)
                    selectTab.isAccessible = true
                    selectTab.invoke(w, dockable)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(800)
            screenshot(robot, w, outputDir.resolve("06-explorer-directory.png"))
            println("  > 06-explorer-directory.png")
        } catch (e: Exception) { System.err.println("06-explorer-directory failed: ${e.message}") }

        // ── 07: Explorer panel — hidden files toggled on ──────────────────
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val explorerField = w.javaClass.getDeclaredField("explorerPanel")
                    explorerField.isAccessible = true
                    val explorerPanel = explorerField.get(w)
                    val showHiddenField = explorerPanel.javaClass.getDeclaredField("showHidden")
                    showHiddenField.isAccessible = true
                    showHiddenField.set(explorerPanel, true)
                    val setRootDir = explorerPanel.javaClass.getMethod("setRootDirectory", File::class.java)
                    setRootDir.invoke(explorerPanel, projects[0].dir)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(800)
            screenshot(robot, w, outputDir.resolve("07-explorer-hidden-files.png"))
            println("  > 07-explorer-hidden-files.png")
            // Reset showHidden
            SwingUtilities.invokeAndWait {
                try {
                    val explorerField = w.javaClass.getDeclaredField("explorerPanel")
                    explorerField.isAccessible = true
                    val explorerPanel = explorerField.get(w)
                    val showHiddenField = explorerPanel.javaClass.getDeclaredField("showHidden")
                    showHiddenField.isAccessible = true
                    showHiddenField.set(explorerPanel, false)
                } catch (_: Exception) {}
            }
        } catch (e: Exception) { System.err.println("07-explorer-hidden-files failed: ${e.message}") }

        // ── 08: Editor panel — single source file open ───────────────────
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val toggleEditor = w.javaClass.getDeclaredMethod("toggleEditor", Boolean::class.java)
                    toggleEditor.isAccessible = true
                    toggleEditor.invoke(w, true)
                    val explorerField = w.javaClass.getDeclaredField("explorerPanel")
                    explorerField.isAccessible = true
                    val explorerPanel = explorerField.get(w)
                    val sourceFile = projects[0].dir.resolve("src/main/kotlin/Main.kt")
                    if (sourceFile.exists()) {
                        val openFile = explorerPanel.javaClass.getMethod("openFile", File::class.java)
                        openFile.invoke(explorerPanel, sourceFile)
                    }
                    val editorDockable = w.javaClass.getDeclaredField("editorDockable")
                    editorDockable.isAccessible = true
                    val dockable = editorDockable.get(w)
                    val selectTab = w.javaClass.getDeclaredMethod("selectDockableTab", dockable.javaClass)
                    selectTab.isAccessible = true
                    selectTab.invoke(w, dockable)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(800)
            screenshot(robot, w, outputDir.resolve("08-editor-source-file.png"))
            println("  > 08-editor-source-file.png")
        } catch (e: Exception) { System.err.println("08-editor-source-file failed: ${e.message}") }

        // ── 09: Editor panel — two files open in tabs ────────────────────
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val explorerField = w.javaClass.getDeclaredField("explorerPanel")
                    explorerField.isAccessible = true
                    val explorerPanel = explorerField.get(w)
                    val readmeFile = projects[0].dir.resolve("README.md")
                    if (readmeFile.exists()) {
                        val openFile = explorerPanel.javaClass.getMethod("openFile", File::class.java)
                        openFile.invoke(explorerPanel, readmeFile)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(800)
            screenshot(robot, w, outputDir.resolve("09-editor-multi-tab.png"))
            println("  > 09-editor-multi-tab.png")
        } catch (e: Exception) { System.err.println("09-editor-multi-tab failed: ${e.message}") }

        // ── 10: Renovate panel ────────────────────────────────────────────
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val toggleRenovate = w.javaClass.getDeclaredMethod("toggleRenovate", Boolean::class.java)
                    toggleRenovate.isAccessible = true
                    toggleRenovate.invoke(w, true)
                    val renovateField = w.javaClass.getDeclaredField("renovatePanel")
                    renovateField.isAccessible = true
                    val renovatePanel = renovateField.get(w)
                    val loadMethod = renovatePanel.javaClass.getMethod("loadProject", String::class.java)
                    loadMethod.invoke(renovatePanel, projects[0].dir.absolutePath)
                    val renovateDockable = w.javaClass.getDeclaredField("renovateDockable")
                    renovateDockable.isAccessible = true
                    val dockable = renovateDockable.get(w)
                    val selectTab = w.javaClass.getDeclaredMethod("selectDockableTab", dockable.javaClass)
                    selectTab.isAccessible = true
                    selectTab.invoke(w, dockable)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(1000)
            screenshot(robot, w, outputDir.resolve("10-renovate.png"))
            println("  > 10-renovate.png")
        } catch (e: Exception) { System.err.println("10-renovate failed: ${e.message}") }

        // ── 11: Console panel — empty ─────────────────────────────────────
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val toggleConsole = w.javaClass.getDeclaredMethod("toggleConsole", Boolean::class.java)
                    toggleConsole.isAccessible = true
                    toggleConsole.invoke(w, true)
                    val consoleField = w.javaClass.getDeclaredField("consolePanel")
                    consoleField.isAccessible = true
                    val consolePanel = consoleField.get(w)
                    consolePanel.javaClass.getMethod("clear").invoke(consolePanel)
                    val consoleDockable = w.javaClass.getDeclaredField("consoleDockable")
                    consoleDockable.isAccessible = true
                    val dockable = consoleDockable.get(w)
                    val selectTab = w.javaClass.getDeclaredMethod("selectDockableTab", dockable.javaClass)
                    selectTab.isAccessible = true
                    selectTab.invoke(w, dockable)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(800)
            screenshot(robot, w, outputDir.resolve("11-console-empty.png"))
            println("  > 11-console-empty.png")
        } catch (e: Exception) { System.err.println("11-console-empty failed: ${e.message}") }

        // ── 12: Console panel — with output ──────────────────────────────
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val consoleField = w.javaClass.getDeclaredField("consolePanel")
                    consoleField.isAccessible = true
                    val consolePanel = consoleField.get(w)
                    val appendLine = consolePanel.javaClass.getMethod("appendLine", String::class.java)
                    listOf(
                        "[INFO] Scanning for projects...",
                        "[INFO] Building needlecast-desktop 0.6.18",
                        "[INFO] --- maven-compiler-plugin:3.13.0:compile ---",
                        "[INFO] Compiling 47 source files",
                        "[WARNING] Some warnings generated",
                        "[INFO] --- maven-surefire-plugin:3.2.5:test ---",
                        "[INFO] Tests run: 142, Failures: 0, Errors: 0, Skipped: 0",
                        "[INFO] BUILD SUCCESS",
                        "[INFO] Total time: 23.4 s",
                        "[INFO] Finished at: 2024-11-12T14:32:01+01:00",
                    ).forEach { appendLine.invoke(consolePanel, it) }
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(800)
            screenshot(robot, w, outputDir.resolve("12-console-output.png"))
            println("  > 12-console-output.png")
        } catch (e: Exception) { System.err.println("12-console-output failed: ${e.message}") }

        // ── 13: Log viewer panel ──────────────────────────────────────────
        try {
            SwingUtilities.invokeAndWait {
                try {
                    // Hide renovate first, then show log viewer
                    val toggleRenovate = w.javaClass.getDeclaredMethod("toggleRenovate", Boolean::class.java)
                    toggleRenovate.isAccessible = true
                    toggleRenovate.invoke(w, false)
                    val logViewerField = w.javaClass.getDeclaredField("logViewerPanel")
                    logViewerField.isAccessible = true
                    val logViewerPanel = logViewerField.get(w)
                    val loadMethod = logViewerPanel.javaClass.getMethod("loadProject", String::class.java)
                    loadMethod.invoke(logViewerPanel, projects[0].dir.absolutePath)
                    val logViewerDockable = w.javaClass.getDeclaredField("logViewerDockable")
                    logViewerDockable.isAccessible = true
                    val dockable = logViewerDockable.get(w)
                    val selectTab = w.javaClass.getDeclaredMethod("selectDockableTab", dockable.javaClass)
                    selectTab.isAccessible = true
                    selectTab.invoke(w, dockable)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(2000)
            screenshot(robot, w, outputDir.resolve("13-log-viewer.png"))
            println("  > 13-log-viewer.png")
        } catch (e: Exception) { System.err.println("13-log-viewer failed: ${e.message}") }

        // ── 14: Log viewer panel — with filter text ───────────────────────
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val logViewerField = w.javaClass.getDeclaredField("logViewerPanel")
                    logViewerField.isAccessible = true
                    val logViewerPanel = logViewerField.get(w)
                    // Set the search field text via reflection
                    val searchField = logViewerPanel.javaClass.getDeclaredField("searchField")
                    searchField.isAccessible = true
                    val tf = searchField.get(logViewerPanel) as javax.swing.JTextField
                    tf.text = "ERROR"
                    // Trigger action listeners on the search field
                    tf.actionListeners.forEach {
                        it.actionPerformed(java.awt.event.ActionEvent(tf, 0, ""))
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(800)
            screenshot(robot, w, outputDir.resolve("14-log-viewer-filter.png"))
            println("  > 14-log-viewer-filter.png")
        } catch (e: Exception) { System.err.println("14-log-viewer-filter failed: ${e.message}") }

        // ── 15: Search panel — blank state ────────────────────────────────
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val toggleSearch = w.javaClass.getDeclaredMethod("toggleSearch", Boolean::class.java)
                    toggleSearch.isAccessible = true
                    toggleSearch.invoke(w, true)
                    val searchField2 = w.javaClass.getDeclaredField("searchPanel")
                    searchField2.isAccessible = true
                    val searchPanel = searchField2.get(w)
                    val loadMethod = searchPanel.javaClass.getMethod("loadProject", String::class.java)
                    loadMethod.invoke(searchPanel, projects[0].dir.absolutePath)
                    val searchDockable = w.javaClass.getDeclaredField("searchDockable")
                    searchDockable.isAccessible = true
                    val dockable = searchDockable.get(w)
                    val selectTab = w.javaClass.getDeclaredMethod("selectDockableTab", dockable.javaClass)
                    selectTab.isAccessible = true
                    selectTab.invoke(w, dockable)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(800)
            screenshot(robot, w, outputDir.resolve("15-search-empty.png"))
            println("  > 15-search-empty.png")
        } catch (e: Exception) { System.err.println("15-search-empty failed: ${e.message}") }

        // ── 16: Search panel — with results ───────────────────────────────
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val searchField2 = w.javaClass.getDeclaredField("searchPanel")
                    searchField2.isAccessible = true
                    val searchPanel = searchField2.get(w)
                    val queryField = searchPanel.javaClass.getDeclaredField("queryField")
                    queryField.isAccessible = true
                    val tf = queryField.get(searchPanel) as javax.swing.JTextField
                    tf.text = "main"
                    tf.actionListeners.forEach {
                        it.actionPerformed(java.awt.event.ActionEvent(tf, 0, ""))
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(2000)
            screenshot(robot, w, outputDir.resolve("16-search-results.png"))
            println("  > 16-search-results.png")
        } catch (e: Exception) { System.err.println("16-search-results failed: ${e.message}") }

        // ── 17: Docs panel ────────────────────────────────────────────────
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val docsField = w.javaClass.getDeclaredField("docsPanel")
                    docsField.isAccessible = true
                    val docsPanel = docsField.get(w)
                    val loadMethod = docsPanel.javaClass.getMethod("loadProject", String::class.java)
                    loadMethod.invoke(docsPanel, projects[0].dir.absolutePath)
                    val docsDockable = w.javaClass.getDeclaredField("docsDockable")
                    docsDockable.isAccessible = true
                    val dockable = docsDockable.get(w)
                    val selectTab = w.javaClass.getDeclaredMethod("selectDockableTab", dockable.javaClass)
                    selectTab.isAccessible = true
                    selectTab.invoke(w, dockable)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(800)
            screenshot(robot, w, outputDir.resolve("17-docs.png"))
            println("  > 17-docs.png")
        } catch (e: Exception) { System.err.println("17-docs failed: ${e.message}") }

        // ── 18: Prompt input panel ────────────────────────────────────────
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val togglePromptInput = w.javaClass.getDeclaredMethod("togglePromptInput", Boolean::class.java)
                    togglePromptInput.isAccessible = true
                    togglePromptInput.invoke(w, true)
                    val promptDockable = w.javaClass.getDeclaredField("promptInputDockable")
                    promptDockable.isAccessible = true
                    val dockable = promptDockable.get(w)
                    val selectTab = w.javaClass.getDeclaredMethod("selectDockableTab", dockable.javaClass)
                    selectTab.isAccessible = true
                    selectTab.invoke(w, dockable)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(800)
            screenshot(robot, w, outputDir.resolve("18-prompt-input.png"))
            println("  > 18-prompt-input.png")
        } catch (e: Exception) { System.err.println("18-prompt-input failed: ${e.message}") }

        // ── 19: Command input panel ───────────────────────────────────────
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val toggleCommandInput = w.javaClass.getDeclaredMethod("toggleCommandInput", Boolean::class.java)
                    toggleCommandInput.isAccessible = true
                    toggleCommandInput.invoke(w, true)
                    val cmdDockable = w.javaClass.getDeclaredField("commandInputDockable")
                    cmdDockable.isAccessible = true
                    val dockable = cmdDockable.get(w)
                    val selectTab = w.javaClass.getDeclaredMethod("selectDockableTab", dockable.javaClass)
                    selectTab.isAccessible = true
                    selectTab.invoke(w, dockable)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(800)
            screenshot(robot, w, outputDir.resolve("19-command-input.png"))
            println("  > 19-command-input.png")
        } catch (e: Exception) { System.err.println("19-command-input failed: ${e.message}") }

        // ── 20: Doc viewer panel ──────────────────────────────────────────
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val toggleDocViewer = w.javaClass.getDeclaredMethod("toggleDocViewer", Boolean::class.java)
                    toggleDocViewer.isAccessible = true
                    toggleDocViewer.invoke(w, true)
                    val docViewerField = w.javaClass.getDeclaredField("docViewerPanel")
                    docViewerField.isAccessible = true
                    val docViewerPanel = docViewerField.get(w)
                    // Try to load with a DetectedProject via reflection
                    try {
                        val treeField = w.javaClass.getDeclaredField("projectTreePanel")
                        treeField.isAccessible = true
                        val treePanel = treeField.get(w)
                        val scanField = treePanel.javaClass.getDeclaredField("scanResults")
                        scanField.isAccessible = true
                        val scanResults = scanField.get(treePanel) as? Map<*, *>
                        val firstProject = scanResults?.values?.firstOrNull()
                        if (firstProject != null) {
                            val loadMethod = docViewerPanel.javaClass.getMethod("loadProject", firstProject.javaClass)
                            loadMethod.invoke(docViewerPanel, firstProject)
                        } else {
                            // Try null overload
                            val loadMethod = docViewerPanel.javaClass.methods
                                .firstOrNull { it.name == "loadProject" }
                            loadMethod?.invoke(docViewerPanel, null)
                        }
                    } catch (_: Exception) {}
                    val docViewerDockable = w.javaClass.getDeclaredField("docViewerDockable")
                    docViewerDockable.isAccessible = true
                    val dockable = docViewerDockable.get(w)
                    val selectTab = w.javaClass.getDeclaredMethod("selectDockableTab", dockable.javaClass)
                    selectTab.isAccessible = true
                    selectTab.invoke(w, dockable)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(800)
            screenshot(robot, w, outputDir.resolve("20-doc-viewer.png"))
            println("  > 20-doc-viewer.png")
        } catch (e: Exception) { System.err.println("20-doc-viewer failed: ${e.message}") }

        // ── Settings tabs ─────────────────────────────────────────────────
        // Sidebar indices:
        //   0 = Header "GENERAL"
        //   1 = "Appearance"
        //   2 = "Layout"
        //   3 = "Terminal"
        //   4 = Header "INTEGRATIONS"
        //   5 = "External Editors"
        //   6 = "AI Tools"
        //   7 = "Renovate"
        //   8 = Header "ADVANCED"
        //   9 = "APM"
        //  10 = "Shortcuts"
        //  11 = "Language"

        val settingsTabs = listOf(
            Triple(1,  "21-settings-appearance.png", "Appearance"),
            Triple(2,  "22-settings-layout.png",     "Layout"),
            Triple(3,  "23-settings-terminal.png",   "Terminal"),
            Triple(5,  "24-settings-editors.png",    "External Editors"),
            Triple(6,  "25-settings-ai-tools.png",   "AI Tools"),
            Triple(7,  "26-settings-renovate.png",   "Renovate"),
            Triple(9,  "27-settings-apm.png",        "APM"),
            Triple(10, "28-settings-shortcuts.png",  "Shortcuts"),
            Triple(11, "29-settings-language.png",   "Language"),
        )
        for ((tabIndex, fileName, tabName) in settingsTabs) {
            try {
                settingsTabShot(robot, w, ctx, tabIndex, outputDir.resolve(fileName), tabName)
            } catch (e: Exception) { System.err.println("$fileName failed: ${e.message}") }
        }

        // ── 30: Prompt Library ────────────────────────────────────────────
        try {
            dialogShot(robot, outputDir.resolve("30-prompt-library.png")) {
                PromptLibraryDialog(w, ctx, sendToTerminal = {}).isVisible = true
            }
        } catch (e: Exception) { System.err.println("30-prompt-library failed: ${e.message}") }

        // ── 31: Command Library ───────────────────────────────────────────
        try {
            dialogShot(robot, outputDir.resolve("31-command-library.png")) {
                PromptLibraryDialog(
                    w, ctx,
                    sendToTerminal  = {},
                    title           = "Command Library",
                    sendButtonLabel = "Run in Terminal",
                    loadLibrary     = { ctx.config.commandLibrary },
                    saveLibrary     = { ctx.updateConfig(ctx.config.copy(commandLibrary = it)) },
                ).isVisible = true
            }
        } catch (e: Exception) { System.err.println("31-command-library failed: ${e.message}") }

        // ── 32: Project Switcher ──────────────────────────────────────────
        try {
            dialogShot(robot, outputDir.resolve("32-project-switcher.png")) {
                ProjectSwitcherDialog(w, ctx, onSelect = { _, _ -> }).isVisible = true
            }
        } catch (e: Exception) { System.err.println("32-project-switcher failed: ${e.message}") }

        // ── 33: Environment Editor ────────────────────────────────────────
        try {
            dialogShot(robot, outputDir.resolve("33-env-editor.png")) {
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
        } catch (e: Exception) { System.err.println("33-env-editor failed: ${e.message}") }

        // ── 34: About dialog ──────────────────────────────────────────────
        try {
            dialogShot(robot, outputDir.resolve("34-about.png")) {
                try {
                    val method = w.javaClass.getDeclaredMethod("showAbout")
                    method.isAccessible = true
                    method.invoke(w)
                } catch (_: Exception) {
                    JOptionPane.showMessageDialog(w, "Needlecast", "About", JOptionPane.INFORMATION_MESSAGE)
                }
            }
        } catch (e: Exception) { System.err.println("34-about failed: ${e.message}") }

        println("Screenshots written to $outputDir")
    } catch (e: Exception) {
        System.err.println("Screenshot tour failed: ${e.message}")
        e.printStackTrace()
    } finally {
        Runtime.getRuntime().halt(0)
    }
}

// ── Settings tab helper ───────────────────────────────────────────────────────

private fun settingsTabShot(robot: Robot, window: JFrame, ctx: AppContext, tabIndex: Int, dest: Path, tabName: String) {
    var dlg: JDialog? = null
    SwingUtilities.invokeAndWait {
        val d = SettingsDialog(window, ctx, sendToTerminal = {})
        try {
            // The sidebarList is a local var inside init, but the JList is part of the JScrollPane
            // added to the dialog's content pane. We locate it by traversing the component tree.
            val sidebarList = findJListIn(d)
            if (sidebarList != null) {
                sidebarList.selectedIndex = tabIndex
            }
        } catch (e: Exception) { e.printStackTrace() }
        d.isVisible = true
        dlg = d
    }
    Thread.sleep(600)
    screenshotTopDialog(robot, dest)
    println("  > ${dest.fileName}")
    SwingUtilities.invokeAndWait { dlg?.dispose() }
    Thread.sleep(300)
}

private fun findJListIn(container: java.awt.Container): javax.swing.JList<*>? {
    for (i in 0 until container.componentCount) {
        val c = container.getComponent(i)
        if (c is javax.swing.JList<*>) return c
        if (c is javax.swing.JScrollPane) {
            val view = c.viewport.view
            if (view is javax.swing.JList<*>) return view
        }
        if (c is java.awt.Container) {
            val found = findJListIn(c)
            if (found != null) return found
        }
    }
    return null
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

private fun waitForProjectTreeData(window: MainWindow, expectedProjects: Int) {
    val deadline = System.currentTimeMillis() + 8000
    while (System.currentTimeMillis() < deadline) {
        var scanSize = 0
        var gitSize = 0
        SwingUtilities.invokeAndWait {
            try {
                val field = window.javaClass.getDeclaredField("projectTreePanel")
                field.isAccessible = true
                val treePanel = field.get(window)
                val scanField = treePanel.javaClass.getDeclaredField("scanResults")
                val gitField = treePanel.javaClass.getDeclaredField("gitStatusCache")
                scanField.isAccessible = true
                gitField.isAccessible = true
                scanSize = (scanField.get(treePanel) as? Map<*, *>)?.size ?: 0
                gitSize = (gitField.get(treePanel) as? Map<*, *>)?.size ?: 0
            } catch (_: Exception) {
                scanSize = expectedProjects
                gitSize = expectedProjects
            }
        }
        if (scanSize >= expectedProjects && gitSize >= expectedProjects) return
        Thread.sleep(150)
    }
}

// ── Demo data ─────────────────────────────────────────────────────────────────

private data class DemoProject(val dir: File, val displayName: String)

private fun createDemoProjects(root: Path): List<DemoProject> {
    data class DemoSpec(
        val dirName: String,
        val label: String,
        val scaffold: (File) -> Unit,
        val branch: String,
        val dirty: Boolean,
    )
    val projects = listOf(
        DemoSpec("needlecast",                      "needlecast",                      ::scaffoldMaven,  "main",                false),
        DemoSpec("web-dashboard",                   "web-dashboard",                   ::scaffoldNpm,    "feature/ux-refresh",   true),
        DemoSpec("api-service",                     "api-service",                     ::scaffoldGradle, "develop",              false),
        DemoSpec("ml-pipeline",                     "ml-pipeline",                     ::scaffoldPython, "experiment/ablation",  true),
        DemoSpec("rust-engine",                     "rust-engine",                     ::scaffoldRust,   "perf/fast-path",       false),
        DemoSpec("go-service",                      "go-service",                      ::scaffoldGo,     "release/v1.8",         false),
        DemoSpec("enterprise-microservice-gateway", "enterprise-microservice-gateway", ::scaffoldMaven,  "hotfix/auth-headers",  true),
        DemoSpec("react-native-shopping-app",       "react-native-shopping-app",       ::scaffoldNpm,    "feature/cart-v2",      false),
    )
    return projects.map { (dirName, label, scaffold, branch, dirty) ->
        val dir = root.resolve(dirName).toFile().also { it.mkdirs() }
        scaffold(dir)
        initGitRepo(dir, branch, dirty)
        DemoProject(dir, label)
    }
}

private fun initGitRepo(dir: File, branch: String, dirty: Boolean) {
    if (!runGit(dir, "init")) return
    runGit(dir, "config", "user.email", "demo@needlecast.local")
    runGit(dir, "config", "user.name", "Needlecast Demo")
    runGit(dir, "add", ".")
    runGit(dir, "commit", "-m", "Initial commit")
    runGit(dir, "branch", "-M", branch)
    if (dirty) {
        val readme = dir.resolve("README.md")
        readme.appendText("\nUncommitted demo change.\n")
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
        theme        = "dark-purple",
        projectTree  = projectTree,
        windowWidth  = 1440,
        windowHeight = 900,
        promptLibrary = listOf(
            PromptTemplate(
                name = "Explain Code",
                category = "Analysis",
                description = "Explain what the selected code does.",
                body = "Explain the following code:\n\n\${selection}",
            ),
            PromptTemplate(
                name = "Code Review",
                category = "Analysis",
                description = "Review code for issues and improvements.",
                body = "Review this code for issues:\n\n\${selection}",
            ),
            PromptTemplate(
                name = "Write Tests",
                category = "Testing",
                description = "Generate unit tests for the selected code.",
                body = "Write unit tests for:\n\n\${selection}",
            ),
            PromptTemplate(
                name = "Refactor",
                category = "Refactoring",
                description = "Suggest refactoring improvements.",
                body = "Refactor this code:\n\n\${selection}",
            ),
        ),
        commandLibrary = listOf(
            PromptTemplate(
                name = "Full Build",
                category = "Maven",
                description = "Clean and build the entire project.",
                body = "mvn clean install -T 4",
            ),
            PromptTemplate(
                name = "Unit Tests",
                category = "Maven",
                description = "Run unit tests only.",
                body = "mvn test -Dsurefire.failIfNoSpecifiedTests=false",
            ),
            PromptTemplate(
                name = "Docker Build",
                category = "Docker",
                description = "Build a Docker image for the project.",
                body = "docker build -t \${project.name}:latest .",
            ),
        ),
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

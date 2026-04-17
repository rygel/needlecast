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
import io.github.rygel.needlecast.ui.VariableResolutionDialog
import java.awt.Component
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import javax.imageio.ImageIO
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.JTextField
import javax.swing.SwingUtilities

/**
 * Headless screenshot tour — captures screenshots for the Needlecast user manual.
 *
 * Target: 88 screenshots matching wiki/Screenshots Needed.md filenames exactly.
 * Runs inside the Xvfb Docker container on CI with Dark Purple theme + realistic demo data.
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

    // Hard timeout — 5 minutes
    Thread {
        Thread.sleep(300_000)
        System.err.println("Screenshot tour timed out after 5 minutes")
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

        // ════════════════════════════════════════════════════════════════
        // SECTION 1: Overview & First Impressions
        // ════════════════════════════════════════════════════════════════

        // 1.1 overview-full-window.png — full window, dark-purple, project selected, terminal active
        try {
            screenshot(robot, w, outputDir.resolve("overview-full-window.png"))
            println("  > overview-full-window.png")
        } catch (e: Exception) { System.err.println("overview-full-window failed: ${e.message}") }

        // 1.2 overview-light-theme.png — same layout with GitHub Light theme
        try {
            SwingUtilities.invokeAndWait {
                try {
                    ThemeRegistry.apply("github-light")
                    SwingUtilities.updateComponentTreeUI(w)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(800)
            screenshot(robot, w, outputDir.resolve("overview-light-theme.png"))
            println("  > overview-light-theme.png")
            // Restore dark-purple
            SwingUtilities.invokeAndWait {
                try {
                    ThemeRegistry.apply("dark-purple")
                    SwingUtilities.updateComponentTreeUI(w)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(500)
        } catch (e: Exception) { System.err.println("overview-light-theme failed: ${e.message}") }

        // 1.3 overview-empty-first-launch.png — blank-slate window with empty config
        try {
            val emptyCtx = AppContext(configStore = FixedConfigStore(AppConfig()))
            val emptyWindowReady = CountDownLatch(1)
            var emptyWindow: MainWindow? = null
            SwingUtilities.invokeLater {
                val ew = MainWindow(emptyCtx)
                ew.setSize(1440, 900)
                ew.setLocationRelativeTo(null)
                ew.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
                ew.isVisible = true
                emptyWindow = ew
                emptyWindowReady.countDown()
            }
            emptyWindowReady.await()
            Thread.sleep(1500)
            screenshot(robot, emptyWindow!!, outputDir.resolve("overview-empty-first-launch.png"))
            println("  > overview-empty-first-launch.png")
            SwingUtilities.invokeAndWait { emptyWindow!!.dispose() }
        } catch (e: Exception) { System.err.println("overview-empty-first-launch failed: ${e.message}") }

        // ════════════════════════════════════════════════════════════════
        // SECTION 2: Project Management
        // ════════════════════════════════════════════════════════════════

        // 2.1 project-tree-overview.png — project tree with groups, badges, active/dirty states
        try {
            selectDockableByField(w, "projectTreeDockable")
            Thread.sleep(600)
            screenshot(robot, w, outputDir.resolve("project-tree-overview.png"))
            println("  > project-tree-overview.png")
        } catch (e: Exception) { System.err.println("project-tree-overview failed: ${e.message}") }

        // 2.2 SKIPPED: project-tree-row-anatomy.png — requires post-processing annotations

        // 2.3 project-tree-context-menu-folder.png — right-click on a folder node
        try {
            val treeComp = getTreeComponent(w)
            if (treeComp != null) {
                // Expand and click the first folder row (row 0)
                SwingUtilities.invokeAndWait {
                    try {
                        val jTree = treeComp as? javax.swing.JTree ?: return@invokeAndWait
                        jTree.expandRow(0)
                    } catch (_: Exception) {}
                }
                Thread.sleep(300)
                val loc = treeComp.locationOnScreen
                robot.mouseMove(loc.x + 60, loc.y + 8)
                robot.waitForIdle()
                robot.mousePress(InputEvent.BUTTON3_DOWN_MASK)
                robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK)
                Thread.sleep(600)
                screenshot(robot, w, outputDir.resolve("project-tree-context-menu-folder.png"))
                println("  > project-tree-context-menu-folder.png")
                robot.keyPress(KeyEvent.VK_ESCAPE)
                robot.keyRelease(KeyEvent.VK_ESCAPE)
                Thread.sleep(200)
            } else {
                System.err.println("project-tree-context-menu-folder: tree component not found")
            }
        } catch (e: Exception) { System.err.println("project-tree-context-menu-folder failed: ${e.message}") }

        // 2.4 project-tree-context-menu-project.png — right-click on a project node
        try {
            val treeComp = getTreeComponent(w)
            if (treeComp != null) {
                SwingUtilities.invokeAndWait {
                    try {
                        val jTree = treeComp as? javax.swing.JTree ?: return@invokeAndWait
                        jTree.expandRow(0)
                    } catch (_: Exception) {}
                }
                Thread.sleep(300)
                val loc = treeComp.locationOnScreen
                // Row 1 is first child (project) of folder at row 0
                robot.mouseMove(loc.x + 80, loc.y + 24)
                robot.waitForIdle()
                robot.mousePress(InputEvent.BUTTON3_DOWN_MASK)
                robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK)
                Thread.sleep(600)
                screenshot(robot, w, outputDir.resolve("project-tree-context-menu-project.png"))
                println("  > project-tree-context-menu-project.png")
                robot.keyPress(KeyEvent.VK_ESCAPE)
                robot.keyRelease(KeyEvent.VK_ESCAPE)
                Thread.sleep(200)
            } else {
                System.err.println("project-tree-context-menu-project: tree component not found")
            }
        } catch (e: Exception) { System.err.println("project-tree-context-menu-project failed: ${e.message}") }

        // 2.5 project-tree-filter.png — filter field with text, some projects hidden
        try {
            val filterTf = findTextFieldByPlaceholder(w, "Filter")
            if (filterTf != null) {
                SwingUtilities.invokeAndWait {
                    filterTf.text = "api"
                    filterTf.document.insertString(filterTf.text.length, "", null)
                }
                Thread.sleep(500)
                screenshot(robot, w, outputDir.resolve("project-tree-filter.png"))
                println("  > project-tree-filter.png")
                SwingUtilities.invokeAndWait { filterTf.text = "" }
                Thread.sleep(300)
            } else {
                System.err.println("project-tree-filter: filter field not found")
            }
        } catch (e: Exception) { System.err.println("project-tree-filter failed: ${e.message}") }

        // 2.6 project-tree-missing-directory.png — already shown by the demo config (missing-dir entry)
        // The demo config includes a project pointing to a nonexistent path, so the tree shows it in red.
        try {
            screenshot(robot, w, outputDir.resolve("project-tree-missing-directory.png"))
            println("  > project-tree-missing-directory.png")
        } catch (e: Exception) { System.err.println("project-tree-missing-directory failed: ${e.message}") }

        // 2.7 project-tree-led-thinking.png — force THINKING LED state via reflection
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val treeField = w.javaClass.getDeclaredField("projectTreePanel")
                    treeField.isAccessible = true
                    val treePanel = treeField.get(w)
                    val updateStatus = treePanel.javaClass.getMethod(
                        "updateProjectStatus", String::class.java,
                        io.github.rygel.needlecast.ui.terminal.AgentStatus::class.java
                    )
                    updateStatus.invoke(
                        treePanel,
                        projects[0].dir.absolutePath,
                        io.github.rygel.needlecast.ui.terminal.AgentStatus.THINKING
                    )
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(500)
            screenshot(robot, w, outputDir.resolve("project-tree-led-thinking.png"))
            println("  > project-tree-led-thinking.png")
            // Reset to IDLE
            SwingUtilities.invokeAndWait {
                try {
                    val treeField = w.javaClass.getDeclaredField("projectTreePanel")
                    treeField.isAccessible = true
                    val treePanel = treeField.get(w)
                    val updateStatus = treePanel.javaClass.getMethod(
                        "updateProjectStatus", String::class.java,
                        io.github.rygel.needlecast.ui.terminal.AgentStatus::class.java
                    )
                    updateStatus.invoke(
                        treePanel,
                        projects[0].dir.absolutePath,
                        io.github.rygel.needlecast.ui.terminal.AgentStatus.NONE
                    )
                } catch (_: Exception) {}
            }
        } catch (e: Exception) { System.err.println("project-tree-led-thinking failed: ${e.message}") }

        // 2.8 project-switcher-dialog.png — Ctrl+P project switcher with search term
        try {
            dialogShot(robot, outputDir.resolve("project-switcher-dialog.png")) {
                val d = ProjectSwitcherDialog(w, ctx, onSelect = { _, _ -> })
                // Pre-fill the search field via reflection
                try {
                    val sf = d.javaClass.getDeclaredField("searchField")
                    sf.isAccessible = true
                    val tf = sf.get(d) as? JTextField
                    SwingUtilities.invokeLater { tf?.text = "api" }
                } catch (_: Exception) {}
                d.isVisible = true
            }
        } catch (e: Exception) { System.err.println("project-switcher-dialog failed: ${e.message}") }

        // 2.9 dialog-shell-settings.png — shell settings dialog via right-click on project
        // We open it programmatically via the ProjectTreePanel's showShellSettings method
        try {
            dialogShot(robot, outputDir.resolve("dialog-shell-settings.png")) {
                try {
                    val treeField = w.javaClass.getDeclaredField("projectTreePanel")
                    treeField.isAccessible = true
                    val treePanel = treeField.get(w)
                    val method = treePanel.javaClass.getDeclaredMethod("showShellSettings",
                        io.github.rygel.needlecast.model.ProjectDirectory::class.java)
                    method.isAccessible = true
                    method.invoke(treePanel, demoConfig.projectTree
                        .filterIsInstance<ProjectTreeEntry.Folder>()
                        .firstOrNull()?.children
                        ?.filterIsInstance<ProjectTreeEntry.Project>()
                        ?.firstOrNull()?.directory)
                } catch (e: Exception) {
                    // fallback — simple input dialog with matching title
                    JOptionPane.showInputDialog(w, "Shell executable:", "Shell Settings", JOptionPane.PLAIN_MESSAGE)
                }
            }
        } catch (e: Exception) { System.err.println("dialog-shell-settings failed: ${e.message}") }

        // 2.10 dialog-env-editor.png — environment variables dialog with demo data
        try {
            dialogShot(robot, outputDir.resolve("dialog-env-editor.png")) {
                EnvEditorDialog(
                    owner        = w,
                    projectLabel = "needlecast",
                    initial      = mapOf(
                        "JAVA_HOME"    to "/usr/lib/jvm/java-21",
                        "MAVEN_OPTS"   to "-Xmx2g -Xss4m",
                        "NODE_ENV"     to "development",
                        "DATABASE_URL" to "postgres://localhost:5432/mydb",
                    ),
                    onSave = {},
                ).isVisible = true
            }
        } catch (e: Exception) { System.err.println("dialog-env-editor failed: ${e.message}") }

        // 2.11 dialog-tags.png — tags input dialog
        try {
            dialogShot(robot, outputDir.resolve("dialog-tags.png")) {
                @Suppress("UNUSED_VARIABLE")
                val result = JOptionPane.showInputDialog(
                    w,
                    "Tags (comma-separated):",
                    "Edit Tags",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    null,
                    "kotlin, swing, desktop"
                )
            }
        } catch (e: Exception) { System.err.println("dialog-tags failed: ${e.message}") }

        // 2.12 dialog-color-picker.png — JColorChooser for project color
        try {
            dialogShot(robot, outputDir.resolve("dialog-color-picker.png")) {
                javax.swing.JColorChooser.showDialog(w, "Set Project Color", java.awt.Color(0x7C4DFF))
            }
        } catch (e: Exception) { System.err.println("dialog-color-picker failed: ${e.message}") }

        // 2.13 SKIPPED: project-tree-drag-drop.png — mid-drag state not reliably capturable in Xvfb

        // ════════════════════════════════════════════════════════════════
        // SECTION 3: Build Tool Detection
        // ════════════════════════════════════════════════════════════════

        // Helper: load a project into the commands panel and take a screenshot
        fun commandsShot(projectIndex: Int, filename: String) {
            try {
                SwingUtilities.invokeAndWait {
                    try {
                        val toggleCommands = w.javaClass.getDeclaredMethod("toggleCommands", Boolean::class.java)
                        toggleCommands.isAccessible = true
                        toggleCommands.invoke(w, true)
                        val commandsField = w.javaClass.getDeclaredField("commandPanel")
                        commandsField.isAccessible = true
                        val commandPanel = commandsField.get(w)
                        val treeField = w.javaClass.getDeclaredField("projectTreePanel")
                        treeField.isAccessible = true
                        val treePanel = treeField.get(w)
                        val scanField = treePanel.javaClass.getDeclaredField("scanResults")
                        scanField.isAccessible = true
                        val scanResults = scanField.get(treePanel) as? Map<*, *>
                        val targetPath = projects[projectIndex].dir.absolutePath
                        val proj = scanResults?.entries?.firstOrNull { (k, _) -> k.toString() == targetPath }?.value
                        if (proj != null) {
                            val loadMethod = commandPanel.javaClass.getMethod("loadProject", proj.javaClass)
                            loadMethod.invoke(commandPanel, proj)
                        }
                        selectDockableByField(w, "commandsDockable")
                    } catch (e: Exception) { e.printStackTrace() }
                }
                Thread.sleep(800)
                screenshot(robot, w, outputDir.resolve(filename))
                println("  > $filename")
            } catch (e: Exception) { System.err.println("$filename failed: ${e.message}") }
        }

        // 3.1 commands-panel-maven.png — Maven project (projects[0] = needlecast, Maven)
        commandsShot(0, "commands-panel-maven.png")

        // 3.2 commands-panel-gradle.png — Gradle project (projects[2] = api-service)
        commandsShot(2, "commands-panel-gradle.png")

        // 3.3 commands-panel-npm.png — npm project (projects[1] = web-dashboard)
        commandsShot(1, "commands-panel-npm.png")

        // 3.4 commands-panel-multitools.png — multi-tool project (projects[8] = maven-docker-app)
        commandsShot(8, "commands-panel-multitools.png")

        // ════════════════════════════════════════════════════════════════
        // SECTION 4: Command Execution
        // ════════════════════════════════════════════════════════════════

        // Load Maven project back for anatomy shot
        commandsShot(0, "commands-panel-anatomy.png")

        // 4.2 SKIPPED: commands-running.png — requires actually running a command with precise timing
        // 4.3 SKIPPED: commands-finished-success.png — requires command execution
        // 4.4 SKIPPED: commands-finished-error.png — requires command execution

        // 4.5 commands-history.png — toggle history panel open with demo entries
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val commandsField = w.javaClass.getDeclaredField("commandPanel")
                    commandsField.isAccessible = true
                    val commandPanel = commandsField.get(w)
                    // Toggle history on
                    val historyToggle = commandPanel.javaClass.getDeclaredField("historyToggle")
                    historyToggle.isAccessible = true
                    val toggle = historyToggle.get(commandPanel) as javax.swing.JToggleButton
                    if (!toggle.isSelected) {
                        toggle.isSelected = true
                        toggle.doClick()
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(600)
            screenshot(robot, w, outputDir.resolve("commands-history.png"))
            println("  > commands-history.png")
            // Reset
            SwingUtilities.invokeAndWait {
                try {
                    val commandsField = w.javaClass.getDeclaredField("commandPanel")
                    commandsField.isAccessible = true
                    val commandPanel = commandsField.get(w)
                    val historyToggle = commandPanel.javaClass.getDeclaredField("historyToggle")
                    historyToggle.isAccessible = true
                    val toggle = historyToggle.get(commandPanel) as javax.swing.JToggleButton
                    if (toggle.isSelected) { toggle.isSelected = false; toggle.doClick() }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) { System.err.println("commands-history failed: ${e.message}") }

        // 4.6 commands-queue.png — toggle queue panel open with demo entries
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val commandsField = w.javaClass.getDeclaredField("commandPanel")
                    commandsField.isAccessible = true
                    val commandPanel = commandsField.get(w)
                    val queueModel = commandPanel.javaClass.getDeclaredField("queueModel")
                    queueModel.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    val model = queueModel.get(commandPanel) as javax.swing.DefaultListModel<String>
                    model.clear()
                    model.addElement("mvn clean install")
                    model.addElement("mvn test -Dsurefire.failIfNoSpecifiedTests=false")
                    model.addElement("docker build -t needlecast:latest .")
                    val queueToggle = commandPanel.javaClass.getDeclaredField("queueToggle")
                    queueToggle.isAccessible = true
                    val toggle = queueToggle.get(commandPanel) as javax.swing.JToggleButton
                    if (!toggle.isSelected) { toggle.isSelected = true; toggle.doClick() }
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(600)
            screenshot(robot, w, outputDir.resolve("commands-queue.png"))
            println("  > commands-queue.png")
            // Reset
            SwingUtilities.invokeAndWait {
                try {
                    val commandsField = w.javaClass.getDeclaredField("commandPanel")
                    commandsField.isAccessible = true
                    val commandPanel = commandsField.get(w)
                    val queueToggle = commandPanel.javaClass.getDeclaredField("queueToggle")
                    queueToggle.isAccessible = true
                    val toggle = queueToggle.get(commandPanel) as javax.swing.JToggleButton
                    if (toggle.isSelected) { toggle.isSelected = false; toggle.doClick() }
                    val queueModel = commandPanel.javaClass.getDeclaredField("queueModel")
                    queueModel.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    (queueModel.get(commandPanel) as javax.swing.DefaultListModel<String>).clear()
                } catch (_: Exception) {}
            }
        } catch (e: Exception) { System.err.println("commands-queue failed: ${e.message}") }

        // 4.7 commands-readme-preview.png — show README preview at bottom of commands panel
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val commandsField = w.javaClass.getDeclaredField("commandPanel")
                    commandsField.isAccessible = true
                    val commandPanel = commandsField.get(w)
                    val readmeScroll = commandPanel.javaClass.getDeclaredField("readmeScroll")
                    readmeScroll.isAccessible = true
                    val scroll = readmeScroll.get(commandPanel) as javax.swing.JScrollPane
                    val readmeArea = commandPanel.javaClass.getDeclaredField("readmeArea")
                    readmeArea.isAccessible = true
                    val ta = readmeArea.get(commandPanel) as javax.swing.JTextArea
                    ta.text = "# needlecast\n\nA project launcher for developers.\n\n## Commands\n\n- `mvn clean install` — full build\n- `mvn test` — run tests\n- `docker build` — build image\n"
                    scroll.isVisible = true
                    commandPanel.javaClass.getMethod("revalidate").invoke(commandPanel)
                    commandPanel.javaClass.getMethod("repaint").invoke(commandPanel)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(600)
            screenshot(robot, w, outputDir.resolve("commands-readme-preview.png"))
            println("  > commands-readme-preview.png")
            SwingUtilities.invokeAndWait {
                try {
                    val commandsField = w.javaClass.getDeclaredField("commandPanel")
                    commandsField.isAccessible = true
                    val commandPanel = commandsField.get(w)
                    val readmeScroll = commandPanel.javaClass.getDeclaredField("readmeScroll")
                    readmeScroll.isAccessible = true
                    (readmeScroll.get(commandPanel) as javax.swing.JScrollPane).isVisible = false
                } catch (_: Exception) {}
            }
        } catch (e: Exception) { System.err.println("commands-readme-preview failed: ${e.message}") }

        // 4.8 console-output.png — console panel with Maven output
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
                    val appendLine = consolePanel.javaClass.getMethod("appendLine", String::class.java)
                    listOf(
                        "[INFO] Scanning for projects...",
                        "[INFO] ----------------------< io.github.rygel:needlecast >----------------------",
                        "[INFO] Building needlecast-desktop 0.6.18",
                        "[INFO] --------------------------------[ jar ]---------------------------------",
                        "[INFO] --- maven-compiler-plugin:3.13.0:compile ---",
                        "[INFO] Compiling 47 source files to target/classes",
                        "[INFO] --- maven-surefire-plugin:3.2.5:test ---",
                        "[INFO] Tests run: 142, Failures: 0, Errors: 0, Skipped: 0",
                        "[INFO] BUILD SUCCESS",
                        "[INFO] Total time: 23.4 s",
                        "[INFO] Finished at: 2025-04-12T14:32:01+01:00",
                    ).forEach { appendLine.invoke(consolePanel, it) }
                    selectDockableByField(w, "consoleDockable")
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(800)
            screenshot(robot, w, outputDir.resolve("console-output.png"))
            println("  > console-output.png")
        } catch (e: Exception) { System.err.println("console-output failed: ${e.message}") }

        // 4.9 console-find-bar.png — console with Ctrl+F find bar open
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val consoleField = w.javaClass.getDeclaredField("consolePanel")
                    consoleField.isAccessible = true
                    val consolePanel = consoleField.get(w)
                    val searchBar = consolePanel.javaClass.getDeclaredField("searchBar")
                    searchBar.isAccessible = true
                    val bar = searchBar.get(consolePanel)
                    bar.javaClass.getMethod("showBar").invoke(bar)
                    // Type a search term into the search field
                    val searchField = bar.javaClass.getDeclaredField("searchField")
                    searchField.isAccessible = true
                    val tf = searchField.get(bar) as? JTextField
                    tf?.text = "INFO"
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(600)
            screenshot(robot, w, outputDir.resolve("console-find-bar.png"))
            println("  > console-find-bar.png")
            // Close find bar
            robot.keyPress(KeyEvent.VK_ESCAPE)
            robot.keyRelease(KeyEvent.VK_ESCAPE)
            Thread.sleep(200)
        } catch (e: Exception) { System.err.println("console-find-bar failed: ${e.message}") }

        // 4.10 SKIPPED: desktop-notification.png — OS-level, not capturable in Xvfb

        // ════════════════════════════════════════════════════════════════
        // SECTION 5: Terminal
        // ════════════════════════════════════════════════════════════════

        // 5.1 terminal-active.png — terminal with active shell session
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val termField = w.javaClass.getDeclaredField("terminalPanel")
                    termField.isAccessible = true
                    val terminalPanel = termField.get(w)
                    terminalPanel.javaClass.getMethod(
                        "activateProject", String::class.java, Map::class.java, String::class.java, String::class.java
                    ).invoke(terminalPanel, projects[0].dir.absolutePath, emptyMap<String, String>(), null, null)
                    selectDockableByField(w, "terminalDockable")
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(2000)
            screenshot(robot, w, outputDir.resolve("terminal-active.png"))
            println("  > terminal-active.png")
        } catch (e: Exception) { System.err.println("terminal-active failed: ${e.message}") }

        // 5.2 terminal-multiple-tabs.png — activate additional projects to create more tabs
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val termField = w.javaClass.getDeclaredField("terminalPanel")
                    termField.isAccessible = true
                    val terminalPanel = termField.get(w)
                    val activate = terminalPanel.javaClass.getMethod(
                        "activateProject", String::class.java, Map::class.java, String::class.java, String::class.java
                    )
                    activate.invoke(terminalPanel, projects[1].dir.absolutePath, emptyMap<String, String>(), null, null)
                    activate.invoke(terminalPanel, projects[2].dir.absolutePath, emptyMap<String, String>(), null, null)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(1500)
            screenshot(robot, w, outputDir.resolve("terminal-multiple-tabs.png"))
            println("  > terminal-multiple-tabs.png")
        } catch (e: Exception) { System.err.println("terminal-multiple-tabs failed: ${e.message}") }

        // 5.3 terminal-placeholder.png — deactivate all to show placeholder
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val termField = w.javaClass.getDeclaredField("terminalPanel")
                    termField.isAccessible = true
                    val terminalPanel = termField.get(w)
                    terminalPanel.javaClass.getMethod("deactivate").invoke(terminalPanel)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(800)
            screenshot(robot, w, outputDir.resolve("terminal-placeholder.png"))
            println("  > terminal-placeholder.png")
        } catch (e: Exception) { System.err.println("terminal-placeholder failed: ${e.message}") }

        // 5.4 SKIPPED: terminal-shell-picker.png — right-click on placeholder complex in Xvfb
        // 5.5 SKIPPED: terminal-status-thinking.png — requires Claude Code integration
        // 5.6 SKIPPED: terminal-status-waiting.png — requires Claude Code integration

        // 5.7 terminal-font-zoom.png — larger font size
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val termField = w.javaClass.getDeclaredField("terminalPanel")
                    termField.isAccessible = true
                    val terminalPanel = termField.get(w)
                    terminalPanel.javaClass.getMethod("applyFontSize", Int::class.java).invoke(terminalPanel, 20)
                    terminalPanel.javaClass.getMethod(
                        "activateProject", String::class.java, Map::class.java, String::class.java, String::class.java
                    ).invoke(terminalPanel, projects[0].dir.absolutePath, emptyMap<String, String>(), null, null)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(1500)
            screenshot(robot, w, outputDir.resolve("terminal-font-zoom.png"))
            println("  > terminal-font-zoom.png")
            SwingUtilities.invokeAndWait {
                try {
                    val termField = w.javaClass.getDeclaredField("terminalPanel")
                    termField.isAccessible = true
                    val terminalPanel = termField.get(w)
                    terminalPanel.javaClass.getMethod("applyFontSize", Int::class.java).invoke(terminalPanel, 13)
                } catch (_: Exception) {}
            }
        } catch (e: Exception) { System.err.println("terminal-font-zoom failed: ${e.message}") }

        // ════════════════════════════════════════════════════════════════
        // SECTION 6: Code Editor
        // ════════════════════════════════════════════════════════════════

        // 6.1 editor-overview.png — editor with a Kotlin file open
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
                        explorerPanel.javaClass.getMethod("openFile", File::class.java).invoke(explorerPanel, sourceFile)
                    }
                    selectDockableByField(w, "editorDockable")
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(800)
            screenshot(robot, w, outputDir.resolve("editor-overview.png"))
            println("  > editor-overview.png")
        } catch (e: Exception) { System.err.println("editor-overview failed: ${e.message}") }

        // 6.2 editor-multiple-tabs.png — open additional files for multi-tab view
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val explorerField = w.javaClass.getDeclaredField("explorerPanel")
                    explorerField.isAccessible = true
                    val explorerPanel = explorerField.get(w)
                    val readmeFile = projects[0].dir.resolve("README.md")
                    val pomFile = projects[0].dir.resolve("pom.xml")
                    if (readmeFile.exists()) explorerPanel.javaClass.getMethod("openFile", File::class.java).invoke(explorerPanel, readmeFile)
                    if (pomFile.exists()) explorerPanel.javaClass.getMethod("openFile", File::class.java).invoke(explorerPanel, pomFile)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(600)
            screenshot(robot, w, outputDir.resolve("editor-multiple-tabs.png"))
            println("  > editor-multiple-tabs.png")
        } catch (e: Exception) { System.err.println("editor-multiple-tabs failed: ${e.message}") }

        // 6.3 editor-find-bar.png — open find bar in editor (Ctrl+F via robot)
        try {
            SwingUtilities.invokeAndWait {
                try { selectDockableByField(w, "editorDockable") } catch (_: Exception) {}
            }
            Thread.sleep(400)
            // Focus the window then send Ctrl+F
            SwingUtilities.invokeAndWait { w.toFront(); w.requestFocus() }
            robot.waitForIdle()
            robot.keyPress(KeyEvent.VK_CONTROL)
            robot.keyPress(KeyEvent.VK_F)
            robot.keyRelease(KeyEvent.VK_F)
            robot.keyRelease(KeyEvent.VK_CONTROL)
            Thread.sleep(600)
            screenshot(robot, w, outputDir.resolve("editor-find-bar.png"))
            println("  > editor-find-bar.png")
            robot.keyPress(KeyEvent.VK_ESCAPE)
            robot.keyRelease(KeyEvent.VK_ESCAPE)
            Thread.sleep(200)
        } catch (e: Exception) { System.err.println("editor-find-bar failed: ${e.message}") }

        // 6.4 editor-find-replace-bar.png — open find+replace (Ctrl+H)
        try {
            SwingUtilities.invokeAndWait { w.toFront(); w.requestFocus() }
            robot.waitForIdle()
            robot.keyPress(KeyEvent.VK_CONTROL)
            robot.keyPress(KeyEvent.VK_H)
            robot.keyRelease(KeyEvent.VK_H)
            robot.keyRelease(KeyEvent.VK_CONTROL)
            Thread.sleep(600)
            screenshot(robot, w, outputDir.resolve("editor-find-replace-bar.png"))
            println("  > editor-find-replace-bar.png")
            robot.keyPress(KeyEvent.VK_ESCAPE)
            robot.keyRelease(KeyEvent.VK_ESCAPE)
            Thread.sleep(200)
        } catch (e: Exception) { System.err.println("editor-find-replace-bar failed: ${e.message}") }

        // 6.5 editor-toolbar.png — just a shot of the editor with toolbar visible
        try {
            screenshot(robot, w, outputDir.resolve("editor-toolbar.png"))
            println("  > editor-toolbar.png")
        } catch (e: Exception) { System.err.println("editor-toolbar failed: ${e.message}") }

        // 6.6 editor-open-with-menu.png — open "Open with" dropdown via click on the button
        try {
            val openWithBtn = findButtonByTextContains(w, "Open with")
                ?: findButtonByTextContains(w, "Open With")
            if (openWithBtn != null) {
                val loc = openWithBtn.locationOnScreen
                robot.mouseMove(loc.x + openWithBtn.width / 2, loc.y + openWithBtn.height / 2)
                robot.waitForIdle()
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                Thread.sleep(500)
                screenshot(robot, w, outputDir.resolve("editor-open-with-menu.png"))
                println("  > editor-open-with-menu.png")
                robot.keyPress(KeyEvent.VK_ESCAPE)
                robot.keyRelease(KeyEvent.VK_ESCAPE)
                Thread.sleep(200)
            } else {
                System.err.println("editor-open-with-menu: 'Open with' button not found")
            }
        } catch (e: Exception) { System.err.println("editor-open-with-menu failed: ${e.message}") }

        // 6.7 editor-large-file.png — open a large fake file that triggers the "too large" placeholder
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val largeFile = demoRoot.resolve("needlecast/large-binary.bin").toFile()
                    largeFile.parentFile.mkdirs()
                    // Write 15 MB of zeros to trigger large-file guard
                    java.io.FileOutputStream(largeFile).use { out ->
                        val buf = ByteArray(65536)
                        repeat(240) { out.write(buf) }
                    }
                    val explorerField = w.javaClass.getDeclaredField("explorerPanel")
                    explorerField.isAccessible = true
                    val explorerPanel = explorerField.get(w)
                    explorerPanel.javaClass.getMethod("openFile", File::class.java).invoke(explorerPanel, largeFile)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(600)
            screenshot(robot, w, outputDir.resolve("editor-large-file.png"))
            println("  > editor-large-file.png")
        } catch (e: Exception) { System.err.println("editor-large-file failed: ${e.message}") }

        // 6.8 editor-syntax-themes.png — switch syntax theme to Eclipse and back
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val explorerField = w.javaClass.getDeclaredField("explorerPanel")
                    explorerField.isAccessible = true
                    val explorerPanel = explorerField.get(w)
                    val applyTheme = explorerPanel.javaClass.getMethod("applyTheme", Boolean::class.java)
                    applyTheme.invoke(explorerPanel, false) // light syntax = Eclipse-like
                    val sourceFile = projects[0].dir.resolve("src/main/kotlin/Main.kt")
                    if (sourceFile.exists()) {
                        explorerPanel.javaClass.getMethod("openFile", File::class.java).invoke(explorerPanel, sourceFile)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(600)
            screenshot(robot, w, outputDir.resolve("editor-syntax-themes.png"))
            println("  > editor-syntax-themes.png")
            SwingUtilities.invokeAndWait {
                try {
                    val explorerField = w.javaClass.getDeclaredField("explorerPanel")
                    explorerField.isAccessible = true
                    val explorerPanel = explorerField.get(w)
                    explorerPanel.javaClass.getMethod("applyTheme", Boolean::class.java).invoke(explorerPanel, true)
                } catch (_: Exception) {}
            }
        } catch (e: Exception) { System.err.println("editor-syntax-themes failed: ${e.message}") }

        // 6.9 editor-unsaved-dialog.png — show Save/Discard/Cancel dialog
        try {
            dialogShot(robot, outputDir.resolve("editor-unsaved-dialog.png")) {
                JOptionPane.showOptionDialog(
                    w,
                    "Save changes to Main.kt?",
                    "Unsaved Changes",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    arrayOf("Save", "Discard", "Cancel"),
                    "Save"
                )
            }
        } catch (e: Exception) { System.err.println("editor-unsaved-dialog failed: ${e.message}") }

        // 6.10 editor-tab-context-menu.png — right-click on an editor tab
        try {
            // Find editor tab strip via component search
            val tabStrip = findEditorTabStrip(w)
            if (tabStrip != null && tabStrip.componentCount > 0) {
                val loc = tabStrip.locationOnScreen
                robot.mouseMove(loc.x + 30, loc.y + tabStrip.height / 2)
                robot.waitForIdle()
                robot.mousePress(InputEvent.BUTTON3_DOWN_MASK)
                robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK)
                Thread.sleep(600)
                screenshot(robot, w, outputDir.resolve("editor-tab-context-menu.png"))
                println("  > editor-tab-context-menu.png")
                robot.keyPress(KeyEvent.VK_ESCAPE)
                robot.keyRelease(KeyEvent.VK_ESCAPE)
                Thread.sleep(200)
            } else {
                System.err.println("editor-tab-context-menu: tab strip not found")
            }
        } catch (e: Exception) { System.err.println("editor-tab-context-menu failed: ${e.message}") }

        // ════════════════════════════════════════════════════════════════
        // SECTION 7: File Explorer
        // ════════════════════════════════════════════════════════════════

        // 7.1 explorer-overview.png — file explorer with address bar, buttons, file table
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val toggleExplorer = w.javaClass.getDeclaredMethod("toggleExplorer", Boolean::class.java)
                    toggleExplorer.isAccessible = true
                    toggleExplorer.invoke(w, true)
                    val explorerField = w.javaClass.getDeclaredField("explorerPanel")
                    explorerField.isAccessible = true
                    val explorerPanel = explorerField.get(w)
                    explorerPanel.javaClass.getMethod("setRootDirectory", File::class.java)
                        .invoke(explorerPanel, projects[0].dir)
                    selectDockableByField(w, "explorerDockable")
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(800)
            screenshot(robot, w, outputDir.resolve("explorer-overview.png"))
            println("  > explorer-overview.png")
        } catch (e: Exception) { System.err.println("explorer-overview failed: ${e.message}") }

        // 7.2 explorer-context-menu-file.png — right-click on a file in the explorer table
        try {
            val fileTable = findFileTable(w)
            if (fileTable != null && fileTable.rowCount > 0) {
                val loc = fileTable.locationOnScreen
                // Click on row 1 (skip possible folder at row 0)
                val rowY = fileTable.getCellRect(1.coerceAtMost(fileTable.rowCount - 1), 0, true)
                robot.mouseMove(loc.x + fileTable.width / 2, loc.y + rowY.y.toInt() + rowY.height / 2)
                robot.waitForIdle()
                robot.mousePress(InputEvent.BUTTON3_DOWN_MASK)
                robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK)
                Thread.sleep(600)
                screenshot(robot, w, outputDir.resolve("explorer-context-menu-file.png"))
                println("  > explorer-context-menu-file.png")
                robot.keyPress(KeyEvent.VK_ESCAPE)
                robot.keyRelease(KeyEvent.VK_ESCAPE)
                Thread.sleep(200)
            } else {
                System.err.println("explorer-context-menu-file: file table not found or empty")
            }
        } catch (e: Exception) { System.err.println("explorer-context-menu-file failed: ${e.message}") }

        // 7.3 explorer-context-menu-folder.png — right-click on a folder row in explorer
        try {
            val fileTable = findFileTable(w)
            if (fileTable != null && fileTable.rowCount > 0) {
                val loc = fileTable.locationOnScreen
                val rowY = fileTable.getCellRect(0, 0, true)
                robot.mouseMove(loc.x + fileTable.width / 2, loc.y + rowY.y.toInt() + rowY.height / 2)
                robot.waitForIdle()
                robot.mousePress(InputEvent.BUTTON3_DOWN_MASK)
                robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK)
                Thread.sleep(600)
                screenshot(robot, w, outputDir.resolve("explorer-context-menu-folder.png"))
                println("  > explorer-context-menu-folder.png")
                robot.keyPress(KeyEvent.VK_ESCAPE)
                robot.keyRelease(KeyEvent.VK_ESCAPE)
                Thread.sleep(200)
            } else {
                System.err.println("explorer-context-menu-folder: file table not found or empty")
            }
        } catch (e: Exception) { System.err.println("explorer-context-menu-folder failed: ${e.message}") }

        // 7.4 explorer-hidden-files-on.png — show hidden files
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val explorerField = w.javaClass.getDeclaredField("explorerPanel")
                    explorerField.isAccessible = true
                    val explorerPanel = explorerField.get(w)
                    val showHiddenField = explorerPanel.javaClass.getDeclaredField("showHidden")
                    showHiddenField.isAccessible = true
                    showHiddenField.set(explorerPanel, true)
                    explorerPanel.javaClass.getMethod("setRootDirectory", File::class.java)
                        .invoke(explorerPanel, projects[0].dir)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(600)
            screenshot(robot, w, outputDir.resolve("explorer-hidden-files-on.png"))
            println("  > explorer-hidden-files-on.png")
        } catch (e: Exception) { System.err.println("explorer-hidden-files-on failed: ${e.message}") }

        // 7.5 explorer-hidden-files-off.png — hide hidden files
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val explorerField = w.javaClass.getDeclaredField("explorerPanel")
                    explorerField.isAccessible = true
                    val explorerPanel = explorerField.get(w)
                    val showHiddenField = explorerPanel.javaClass.getDeclaredField("showHidden")
                    showHiddenField.isAccessible = true
                    showHiddenField.set(explorerPanel, false)
                    explorerPanel.javaClass.getMethod("setRootDirectory", File::class.java)
                        .invoke(explorerPanel, projects[0].dir)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(600)
            screenshot(robot, w, outputDir.resolve("explorer-hidden-files-off.png"))
            println("  > explorer-hidden-files-off.png")
        } catch (e: Exception) { System.err.println("explorer-hidden-files-off failed: ${e.message}") }

        // 7.6 explorer-address-bar-editing.png — focus address bar and type a path
        try {
            val addressBar = findAddressBar(w)
            if (addressBar != null) {
                SwingUtilities.invokeAndWait {
                    addressBar.requestFocusInWindow()
                    addressBar.selectAll()
                    addressBar.text = "/home/developer/projects/my-service"
                }
                Thread.sleep(400)
                screenshot(robot, w, outputDir.resolve("explorer-address-bar-editing.png"))
                println("  > explorer-address-bar-editing.png")
                SwingUtilities.invokeAndWait {
                    try {
                        val explorerField = w.javaClass.getDeclaredField("explorerPanel")
                        explorerField.isAccessible = true
                        val explorerPanel = explorerField.get(w)
                        explorerPanel.javaClass.getMethod("setRootDirectory", File::class.java)
                            .invoke(explorerPanel, projects[0].dir)
                    } catch (_: Exception) {}
                }
            } else {
                System.err.println("explorer-address-bar-editing: address bar not found")
            }
        } catch (e: Exception) { System.err.println("explorer-address-bar-editing failed: ${e.message}") }

        // ════════════════════════════════════════════════════════════════
        // SECTION 8: Log Viewer
        // ════════════════════════════════════════════════════════════════

        // 8.1 logviewer-overview.png — log viewer with realistic log entries
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val logViewerField = w.javaClass.getDeclaredField("logViewerPanel")
                    logViewerField.isAccessible = true
                    val logViewerPanel = logViewerField.get(w)
                    logViewerPanel.javaClass.getMethod("loadProject", String::class.java)
                        .invoke(logViewerPanel, projects[0].dir.absolutePath)
                    selectDockableByField(w, "logViewerDockable")
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(2000)
            screenshot(robot, w, outputDir.resolve("logviewer-overview.png"))
            println("  > logviewer-overview.png")
        } catch (e: Exception) { System.err.println("logviewer-overview failed: ${e.message}") }

        // 8.2 logviewer-level-filters.png — toggle off DEBUG and TRACE filters
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val logViewerField = w.javaClass.getDeclaredField("logViewerPanel")
                    logViewerField.isAccessible = true
                    val logViewerPanel = logViewerField.get(w)
                    // Toggle DEBUG and TRACE buttons off
                    for (fieldName in listOf("debugToggle", "traceToggle")) {
                        try {
                            val f = logViewerPanel.javaClass.getDeclaredField(fieldName)
                            f.isAccessible = true
                            val btn = f.get(logViewerPanel) as? javax.swing.JToggleButton
                            if (btn?.isSelected == true) btn.doClick()
                        } catch (_: Exception) {}
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(400)
            screenshot(robot, w, outputDir.resolve("logviewer-level-filters.png"))
            println("  > logviewer-level-filters.png")
            // Reset toggles
            SwingUtilities.invokeAndWait {
                try {
                    val logViewerField = w.javaClass.getDeclaredField("logViewerPanel")
                    logViewerField.isAccessible = true
                    val logViewerPanel = logViewerField.get(w)
                    for (fieldName in listOf("debugToggle", "traceToggle")) {
                        try {
                            val f = logViewerPanel.javaClass.getDeclaredField(fieldName)
                            f.isAccessible = true
                            val btn = f.get(logViewerPanel) as? javax.swing.JToggleButton
                            if (btn?.isSelected == false) btn.doClick()
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) { System.err.println("logviewer-level-filters failed: ${e.message}") }

        // 8.3 logviewer-stack-trace.png — select the ERROR entry to show the stack trace
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val logViewerField = w.javaClass.getDeclaredField("logViewerPanel")
                    logViewerField.isAccessible = true
                    val logViewerPanel = logViewerField.get(w)
                    // Select a row that has a stack trace (the ERROR line)
                    val tableField = logViewerPanel.javaClass.getDeclaredField("table")
                    tableField.isAccessible = true
                    val table = tableField.get(logViewerPanel) as? javax.swing.JTable
                    if (table != null) {
                        for (i in 0 until table.rowCount) {
                            val value = table.getValueAt(i, 0)?.toString() ?: ""
                            if (value.contains("ERROR") || value.contains("Exception")) {
                                table.setRowSelectionInterval(i, i)
                                table.scrollRectToVisible(table.getCellRect(i, 0, true))
                                break
                            }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(500)
            screenshot(robot, w, outputDir.resolve("logviewer-stack-trace.png"))
            println("  > logviewer-stack-trace.png")
        } catch (e: Exception) { System.err.println("logviewer-stack-trace failed: ${e.message}") }

        // 8.4 logviewer-search.png — search bar open with a match
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val logViewerField = w.javaClass.getDeclaredField("logViewerPanel")
                    logViewerField.isAccessible = true
                    val logViewerPanel = logViewerField.get(w)
                    val searchField = logViewerPanel.javaClass.getDeclaredField("searchField")
                    searchField.isAccessible = true
                    val tf = searchField.get(logViewerPanel) as JTextField
                    tf.text = "ERROR"
                    tf.actionListeners.forEach { it.actionPerformed(java.awt.event.ActionEvent(tf, 0, "")) }
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(500)
            screenshot(robot, w, outputDir.resolve("logviewer-search.png"))
            println("  > logviewer-search.png")
            SwingUtilities.invokeAndWait {
                try {
                    val logViewerField = w.javaClass.getDeclaredField("logViewerPanel")
                    logViewerField.isAccessible = true
                    val logViewerPanel = logViewerField.get(w)
                    val searchField = logViewerPanel.javaClass.getDeclaredField("searchField")
                    searchField.isAccessible = true
                    (searchField.get(logViewerPanel) as JTextField).text = ""
                } catch (_: Exception) {}
            }
        } catch (e: Exception) { System.err.println("logviewer-search failed: ${e.message}") }

        // 8.5 logviewer-follow-button.png — screenshot showing the Follow toggle
        try {
            screenshot(robot, w, outputDir.resolve("logviewer-follow-button.png"))
            println("  > logviewer-follow-button.png")
        } catch (e: Exception) { System.err.println("logviewer-follow-button failed: ${e.message}") }

        // ════════════════════════════════════════════════════════════════
        // SECTION 9: Dependency Updates (Renovate)
        // ════════════════════════════════════════════════════════════════

        // 9.1 renovate-not-installed.png — renovate panel before any scan
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val renovateField = w.javaClass.getDeclaredField("renovatePanel")
                    renovateField.isAccessible = true
                    val renovatePanel = renovateField.get(w)
                    renovatePanel.javaClass.getMethod("loadProject", String::class.java)
                        .invoke(renovatePanel, projects[0].dir.absolutePath)
                    selectDockableByField(w, "renovateDockable")
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(1000)
            screenshot(robot, w, outputDir.resolve("renovate-not-installed.png"))
            println("  > renovate-not-installed.png")
        } catch (e: Exception) { System.err.println("renovate-not-installed failed: ${e.message}") }

        // 9.2 SKIPPED: renovate-scanning.png — would need renovate actually installed and scanning
        // 9.3 SKIPPED: renovate-results.png — requires real renovate scan results
        // 9.4 SKIPPED: renovate-major-warning.png — requires scan results
        // 9.5 SKIPPED: renovate-logs.png — requires scan results

        // ════════════════════════════════════════════════════════════════
        // SECTION 10: Search (Find in Files)
        // ════════════════════════════════════════════════════════════════

        // 10.1 search-overview.png — search panel with results
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val searchField2 = w.javaClass.getDeclaredField("searchPanel")
                    searchField2.isAccessible = true
                    val searchPanel = searchField2.get(w)
                    searchPanel.javaClass.getMethod("loadProject", String::class.java)
                        .invoke(searchPanel, projects[0].dir.absolutePath)
                    val queryField = searchPanel.javaClass.getDeclaredField("queryField")
                    queryField.isAccessible = true
                    val tf = queryField.get(searchPanel) as JTextField
                    tf.text = "main"
                    tf.actionListeners.forEach { it.actionPerformed(java.awt.event.ActionEvent(tf, 0, "")) }
                    selectDockableByField(w, "searchDockable")
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(2000)
            screenshot(robot, w, outputDir.resolve("search-overview.png"))
            println("  > search-overview.png")
        } catch (e: Exception) { System.err.println("search-overview failed: ${e.message}") }

        // 10.2 search-options.png — search options row with toggles and include/exclude fields visible
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val searchField2 = w.javaClass.getDeclaredField("searchPanel")
                    searchField2.isAccessible = true
                    val searchPanel = searchField2.get(w)
                    // Set include/exclude fields for the screenshot
                    for (f in listOf("includeField", "excludeField")) {
                        try {
                            val field = searchPanel.javaClass.getDeclaredField(f)
                            field.isAccessible = true
                            val tf = field.get(searchPanel) as JTextField
                            tf.text = if (f == "includeField") "**/*.kt" else "**/target/**"
                        } catch (_: Exception) {}
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(400)
            screenshot(robot, w, outputDir.resolve("search-options.png"))
            println("  > search-options.png")
        } catch (e: Exception) { System.err.println("search-options failed: ${e.message}") }

        // 10.3 search-result-count.png — same as search-overview (status label shows count)
        try {
            screenshot(robot, w, outputDir.resolve("search-result-count.png"))
            println("  > search-result-count.png")
        } catch (e: Exception) { System.err.println("search-result-count failed: ${e.message}") }

        // 10.4 search-editor-jump.png — after double-clicking a result, editor shows at that line
        try {
            // Double-click the first result row to jump to it
            SwingUtilities.invokeAndWait {
                try {
                    val searchField2 = w.javaClass.getDeclaredField("searchPanel")
                    searchField2.isAccessible = true
                    val searchPanel = searchField2.get(w)
                    val resultsList = searchPanel.javaClass.getDeclaredField("resultsList")
                    resultsList.isAccessible = true
                    val list = resultsList.get(searchPanel) as? javax.swing.JList<*>
                    if (list != null && list.model.size > 0) {
                        list.selectedIndex = 0
                        val event = java.awt.event.MouseEvent(
                            list, java.awt.event.MouseEvent.MOUSE_CLICKED,
                            System.currentTimeMillis(), 0, 5, 5, 2, false
                        )
                        list.mouseListeners.forEach { it.mouseClicked(event) }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(800)
            screenshot(robot, w, outputDir.resolve("search-editor-jump.png"))
            println("  > search-editor-jump.png")
        } catch (e: Exception) { System.err.println("search-editor-jump failed: ${e.message}") }

        // ════════════════════════════════════════════════════════════════
        // SECTION 11: Git Integration
        // ════════════════════════════════════════════════════════════════

        // 11.1 git-branch-badges.png — project tree showing clean and dirty branch badges
        try {
            selectDockableByField(w, "projectTreeDockable")
            Thread.sleep(500)
            screenshot(robot, w, outputDir.resolve("git-branch-badges.png"))
            println("  > git-branch-badges.png")
        } catch (e: Exception) { System.err.println("git-branch-badges failed: ${e.message}") }

        // 11.2 gitlog-overview.png — git log panel with commit list
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val gitLogField = w.javaClass.getDeclaredField("gitLogPanel")
                    gitLogField.isAccessible = true
                    val gitLogPanel = gitLogField.get(w)
                    gitLogPanel.javaClass.getMethod("loadProject", String::class.java)
                        .invoke(gitLogPanel, projects[0].dir.absolutePath)
                    selectDockableByField(w, "gitLogDockable")
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(1500)
            screenshot(robot, w, outputDir.resolve("gitlog-overview.png"))
            println("  > gitlog-overview.png")
        } catch (e: Exception) { System.err.println("gitlog-overview failed: ${e.message}") }

        // 11.3 gitlog-commit-selected.png — select a commit to show diff
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val gitLogField = w.javaClass.getDeclaredField("gitLogPanel")
                    gitLogField.isAccessible = true
                    val gitLogPanel = gitLogField.get(w)
                    // Select first row of the commit table
                    val tableField = gitLogPanel.javaClass.getDeclaredField("commitTable")
                    tableField.isAccessible = true
                    val table = tableField.get(gitLogPanel) as? javax.swing.JTable
                    if (table != null && table.rowCount > 0) {
                        table.setRowSelectionInterval(0, 0)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(800)
            screenshot(robot, w, outputDir.resolve("gitlog-commit-selected.png"))
            println("  > gitlog-commit-selected.png")
        } catch (e: Exception) { System.err.println("gitlog-commit-selected failed: ${e.message}") }

        // ════════════════════════════════════════════════════════════════
        // SECTION 12: Prompt Library
        // ════════════════════════════════════════════════════════════════

        // 12.1 prompt-input-panel.png — prompt input panel at bottom of window
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val togglePromptInput = w.javaClass.getDeclaredMethod("togglePromptInput", Boolean::class.java)
                    togglePromptInput.isAccessible = true
                    togglePromptInput.invoke(w, true)
                    selectDockableByField(w, "promptInputDockable")
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(600)
            screenshot(robot, w, outputDir.resolve("prompt-input-panel.png"))
            println("  > prompt-input-panel.png")
        } catch (e: Exception) { System.err.println("prompt-input-panel failed: ${e.message}") }

        // 12.2 prompt-library-dialog.png — full prompt library dialog
        try {
            dialogShot(robot, outputDir.resolve("prompt-library-dialog.png")) {
                PromptLibraryDialog(w, ctx, sendToTerminal = {}).isVisible = true
            }
        } catch (e: Exception) { System.err.println("prompt-library-dialog failed: ${e.message}") }

        // 12.3 prompt-variable-dialog.png — variable resolution dialog
        try {
            dialogShot(robot, outputDir.resolve("prompt-variable-dialog.png")) {
                VariableResolutionDialog(
                    owner     = w,
                    variables = listOf("error", "target"),
                    onConfirm = {},
                ).isVisible = true
            }
        } catch (e: Exception) { System.err.println("prompt-variable-dialog failed: ${e.message}") }

        // 12.4 command-library-dialog.png — command library dialog
        try {
            dialogShot(robot, outputDir.resolve("command-library-dialog.png")) {
                PromptLibraryDialog(
                    w, ctx,
                    sendToTerminal  = {},
                    title           = "Command Library",
                    sendButtonLabel = "Run in Terminal",
                    isCommand       = true,
                ).isVisible = true
            }
        } catch (e: Exception) { System.err.println("command-library-dialog failed: ${e.message}") }

        // 12.5 ai-tools-menu.png — AI Tools menu open
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val menuBar = w.jMenuBar
                    for (i in 0 until menuBar.menuCount) {
                        val menu = menuBar.getMenu(i)
                        if (menu?.text == "AI Tools") {
                            menu.isSelected = true
                            menu.isPopupMenuVisible = true
                            break
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(600)
            screenshot(robot, w, outputDir.resolve("ai-tools-menu.png"))
            println("  > ai-tools-menu.png")
            SwingUtilities.invokeAndWait {
                try {
                    val menuBar = w.jMenuBar
                    for (i in 0 until menuBar.menuCount) {
                        val menu = menuBar.getMenu(i)
                        if (menu?.text == "AI Tools") {
                            menu.isPopupMenuVisible = false
                            menu.isSelected = false
                            break
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) { System.err.println("ai-tools-menu failed: ${e.message}") }

        // ════════════════════════════════════════════════════════════════
        // SECTION 13: Docs Panel
        // ════════════════════════════════════════════════════════════════

        // 13.1 docs-panel-rendered.png — docs panel showing rendered README
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val docsField = w.javaClass.getDeclaredField("docsPanel")
                    docsField.isAccessible = true
                    val docsPanel = docsField.get(w)
                    docsPanel.javaClass.getMethod("loadProject", String::class.java)
                        .invoke(docsPanel, projects[0].dir.absolutePath)
                    val renderedToggle = docsPanel.javaClass.getDeclaredField("renderedToggle")
                    renderedToggle.isAccessible = true
                    val toggle = renderedToggle.get(docsPanel) as? javax.swing.JRadioButton
                    toggle?.isSelected = true
                    toggle?.doClick()
                    selectDockableByField(w, "docsDockable")
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(800)
            screenshot(robot, w, outputDir.resolve("docs-panel-rendered.png"))
            println("  > docs-panel-rendered.png")
        } catch (e: Exception) { System.err.println("docs-panel-rendered failed: ${e.message}") }

        // 13.2 docs-panel-raw.png — docs panel showing raw Markdown
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val docsField = w.javaClass.getDeclaredField("docsPanel")
                    docsField.isAccessible = true
                    val docsPanel = docsField.get(w)
                    val rawToggle = docsPanel.javaClass.getDeclaredField("rawToggle")
                    rawToggle.isAccessible = true
                    val toggle = rawToggle.get(docsPanel) as? javax.swing.JRadioButton
                    toggle?.isSelected = true
                    toggle?.doClick()
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(600)
            screenshot(robot, w, outputDir.resolve("docs-panel-raw.png"))
            println("  > docs-panel-raw.png")
        } catch (e: Exception) { System.err.println("docs-panel-raw failed: ${e.message}") }

        // ════════════════════════════════════════════════════════════════
        // SECTION 14: Settings
        // ════════════════════════════════════════════════════════════════

        // Sidebar indices in SettingsDialog:
        //   0 = Header "GENERAL"
        //   1 = Appearance
        //   2 = Layout
        //   3 = Terminal
        //   4 = Header "INTEGRATIONS"
        //   5 = External Editors
        //   6 = AI Tools
        //   7 = Renovate
        //   8 = Header "ADVANCED"
        //   9 = APM
        //  10 = Shortcuts
        //  11 = Language
        val settingsTabs = listOf(
            Triple(5,  "settings-editors-tab.png",   "External Editors"),
            Triple(6,  "settings-aitools-tab.png",   "AI Tools"),
            Triple(7,  "settings-renovate-tab.png",  "Renovate"),
            Triple(9,  "settings-apm-tab.png",       "APM"),
            Triple(10, "settings-shortcuts-tab.png", "Shortcuts"),
            Triple(11, "settings-language-tab.png",  "Language"),
            Triple(2,  "settings-layout-tab.png",    "Layout"),
        )
        for ((tabIndex, fileName, tabName) in settingsTabs) {
            try {
                settingsTabShot(robot, w, ctx, tabIndex, outputDir.resolve(fileName), tabName)
            } catch (e: Exception) { System.err.println("$fileName failed: ${e.message}") }
        }

        // 14.8 settings-color-picker.png — terminal color swatch open
        try {
            dialogShot(robot, outputDir.resolve("settings-color-picker.png")) {
                @Suppress("UNUSED_VARIABLE")
            val chosen = javax.swing.JColorChooser.showDialog(w, "Terminal Color", java.awt.Color(0x1E1E2E))
            }
        } catch (e: Exception) { System.err.println("settings-color-picker failed: ${e.message}") }

        // 14.9 SKIPPED: settings-shortcut-recording.png — requires keyboard event timing

        // ════════════════════════════════════════════════════════════════
        // SECTION 15: Themes
        // ════════════════════════════════════════════════════════════════

        val themeShots = listOf(
            "dark-purple"      to "theme-dark-purple.png",
            "catppuccin-mocha" to "theme-catppuccin-mocha.png",
            "nord"             to "theme-nord.png",
            "github-light"     to "theme-github-light.png",
        )
        for ((themeId, filename) in themeShots) {
            try {
                SwingUtilities.invokeAndWait {
                    try {
                        ThemeRegistry.apply(themeId)
                        SwingUtilities.updateComponentTreeUI(w)
                    } catch (e: Exception) { e.printStackTrace() }
                }
                Thread.sleep(800)
                screenshot(robot, w, outputDir.resolve(filename))
                println("  > $filename")
            } catch (e: Exception) { System.err.println("$filename failed: ${e.message}") }
        }
        // Restore dark-purple
        SwingUtilities.invokeAndWait {
            try { ThemeRegistry.apply("dark-purple"); SwingUtilities.updateComponentTreeUI(w) } catch (_: Exception) {}
        }
        Thread.sleep(500)

        // 15.5 theme-menu.png — View menu open with Dark/Light Themes submenus
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val menuBar = w.jMenuBar
                    for (i in 0 until menuBar.menuCount) {
                        val menu = menuBar.getMenu(i)
                        // View menu is labelled by i18n key, try index 1
                        if (menu != null && (menu.text.equals("View", ignoreCase = true) || i == 1)) {
                            menu.isSelected = true
                            menu.isPopupMenuVisible = true
                            break
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(600)
            screenshot(robot, w, outputDir.resolve("theme-menu.png"))
            println("  > theme-menu.png")
            SwingUtilities.invokeAndWait {
                try {
                    val menuBar = w.jMenuBar
                    for (i in 0 until menuBar.menuCount) {
                        val menu = menuBar.getMenu(i) ?: continue
                        menu.isPopupMenuVisible = false
                        menu.isSelected = false
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) { System.err.println("theme-menu failed: ${e.message}") }

        // ════════════════════════════════════════════════════════════════
        // SECTION 16: Media Viewers
        // ════════════════════════════════════════════════════════════════

        // 16.1 viewer-image.png — image viewer with a demo PNG
        try {
            val imgFile = createDemoImage(demoRoot)
            SwingUtilities.invokeAndWait {
                try {
                    val explorerField = w.javaClass.getDeclaredField("explorerPanel")
                    explorerField.isAccessible = true
                    val explorerPanel = explorerField.get(w)
                    explorerPanel.javaClass.getMethod("openFile", File::class.java).invoke(explorerPanel, imgFile)
                    selectDockableByField(w, "editorDockable")
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(800)
            screenshot(robot, w, outputDir.resolve("viewer-image.png"))
            println("  > viewer-image.png")
        } catch (e: Exception) { System.err.println("viewer-image failed: ${e.message}") }

        // 16.2 viewer-svg.png — SVG viewer
        try {
            val svgFile = createDemoSvg(demoRoot)
            SwingUtilities.invokeAndWait {
                try {
                    val explorerField = w.javaClass.getDeclaredField("explorerPanel")
                    explorerField.isAccessible = true
                    val explorerPanel = explorerField.get(w)
                    explorerPanel.javaClass.getMethod("openFile", File::class.java).invoke(explorerPanel, svgFile)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(800)
            screenshot(robot, w, outputDir.resolve("viewer-svg.png"))
            println("  > viewer-svg.png")
        } catch (e: Exception) { System.err.println("viewer-svg failed: ${e.message}") }

        // 16.3 SKIPPED: viewer-media-player.png — requires an actual audio/video file with codec support

        // ════════════════════════════════════════════════════════════════
        // SECTION 17: UI Layout & Docking
        // ════════════════════════════════════════════════════════════════

        // 17.1 SKIPPED: docking-drag-in-progress.png — mid-drag state not reliably capturable in Xvfb
        // 17.2 SKIPPED: docking-floating-panel.png — floating window management complex in Xvfb

        // 17.3 docking-tabs-bottom.png — switch to tabs-at-bottom layout
        try {
            SwingUtilities.invokeAndWait {
                try {
                    ctx.updateConfig(ctx.config.copy(tabsOnTop = false))
                    // Apply via reflection — call resetLayout
                    val resetLayout = w.javaClass.getMethod("resetLayout")
                    resetLayout.invoke(w)
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(1000)
            screenshot(robot, w, outputDir.resolve("docking-tabs-bottom.png"))
            println("  > docking-tabs-bottom.png")
            SwingUtilities.invokeAndWait {
                try {
                    ctx.updateConfig(ctx.config.copy(tabsOnTop = true))
                    w.javaClass.getMethod("resetLayout").invoke(w)
                } catch (_: Exception) {}
            }
            Thread.sleep(800)
        } catch (e: Exception) { System.err.println("docking-tabs-bottom failed: ${e.message}") }

        // 17.4 panels-menu.png — Panels menu open with checkboxes
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val menuBar = w.jMenuBar
                    for (i in 0 until menuBar.menuCount) {
                        val menu = menuBar.getMenu(i)
                        if (menu?.text == "Panels") {
                            menu.isSelected = true
                            menu.isPopupMenuVisible = true
                            break
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(600)
            screenshot(robot, w, outputDir.resolve("panels-menu.png"))
            println("  > panels-menu.png")
            SwingUtilities.invokeAndWait {
                try {
                    val menuBar = w.jMenuBar
                    for (i in 0 until menuBar.menuCount) {
                        val menu = menuBar.getMenu(i) ?: continue
                        menu.isPopupMenuVisible = false
                        menu.isSelected = false
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) { System.err.println("panels-menu failed: ${e.message}") }

        // 17.5 status-bar-update-badge.png — status bar with update badge
        try {
            SwingUtilities.invokeAndWait {
                try {
                    val statusBarField = w.javaClass.getDeclaredField("statusBar")
                    statusBarField.isAccessible = true
                    val statusBar = statusBarField.get(w)
                    val showUpdate = statusBar.javaClass.getMethod("showUpdateAvailable", String::class.java)
                    showUpdate.invoke(statusBar, "1.2.3")
                } catch (e: Exception) { e.printStackTrace() }
            }
            Thread.sleep(400)
            screenshot(robot, w, outputDir.resolve("status-bar-update-badge.png"))
            println("  > status-bar-update-badge.png")
        } catch (e: Exception) { System.err.println("status-bar-update-badge failed: ${e.message}") }

        // ════════════════════════════════════════════════════════════════
        // SECTION 18: Dialogs & Workflows
        // ════════════════════════════════════════════════════════════════

        // 18.1 about-dialog.png — About dialog
        try {
            dialogShot(robot, outputDir.resolve("about-dialog.png")) {
                try {
                    val method = w.javaClass.getDeclaredMethod("showAbout")
                    method.isAccessible = true
                    method.invoke(w)
                } catch (_: Exception) {
                    JOptionPane.showMessageDialog(w, "Needlecast 0.6.18\nby Alexander Brandt\nMIT License", "About Needlecast", JOptionPane.INFORMATION_MESSAGE)
                }
            }
        } catch (e: Exception) { System.err.println("about-dialog failed: ${e.message}") }

        // 18.2 import-config-dialog.png — file chooser for Import Config
        try {
            dialogShot(robot, outputDir.resolve("import-config-dialog.png")) {
                val chooser = javax.swing.JFileChooser(File(System.getProperty("user.home"))).apply {
                    dialogTitle = "Import Config"
                    fileFilter = javax.swing.filechooser.FileNameExtensionFilter("JSON files (*.json)", "json")
                }
                chooser.showOpenDialog(w)
            }
        } catch (e: Exception) { System.err.println("import-config-dialog failed: ${e.message}") }

        // 18.3 add-project-dialog.png — directory chooser for Add Project
        try {
            dialogShot(robot, outputDir.resolve("add-project-dialog.png")) {
                val chooser = javax.swing.JFileChooser(File(System.getProperty("user.home"))).apply {
                    dialogTitle = "Add Project"
                    fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                }
                chooser.showOpenDialog(w)
            }
        } catch (e: Exception) { System.err.println("add-project-dialog failed: ${e.message}") }

        // 18.4 delete-confirmation.png — delete confirmation dialog
        try {
            dialogShot(robot, outputDir.resolve("delete-confirmation.png")) {
                JOptionPane.showConfirmDialog(
                    w,
                    "Delete 'Main.kt'? This cannot be undone.",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
                )
            }
        } catch (e: Exception) { System.err.println("delete-confirmation failed: ${e.message}") }

        println("\nScreenshots written to $outputDir")
    } catch (e: Exception) {
        System.err.println("Screenshot tour failed: ${e.message}")
        e.printStackTrace()
    } finally {
        Runtime.getRuntime().halt(0)
    }
}

// ── Reflection helpers ────────────────────────────────────────────────────────

@Suppress("UNCHECKED_CAST")
private fun <T> Any.getField(name: String): T {
    val f = javaClass.getDeclaredField(name)
    f.isAccessible = true
    return f.get(this) as T
}

private fun selectDockableByField(w: MainWindow, fieldName: String) {
    val dockableField = w.javaClass.getDeclaredField(fieldName)
    dockableField.isAccessible = true
    val dockable = dockableField.get(w)
    val selectTab = w.javaClass.getDeclaredMethod("selectDockableTab", dockable.javaClass)
    selectTab.isAccessible = true
    selectTab.invoke(w, dockable)
}

private fun getTreeComponent(w: MainWindow): Component? {
    return try {
        val treeField = w.javaClass.getDeclaredField("projectTreePanel")
        treeField.isAccessible = true
        val treePanel = treeField.get(w)
        val tree = treePanel.javaClass.getDeclaredField("tree")
        tree.isAccessible = true
        tree.get(treePanel) as? Component
    } catch (_: Exception) { null }
}

/** Find a JTextField whose placeholder text contains the given hint. */
private fun findTextFieldByPlaceholder(container: java.awt.Container, placeholder: String): JTextField? {
    for (i in 0 until container.componentCount) {
        val c = container.getComponent(i)
        if (c is JTextField) {
            val pp = c.getClientProperty("JTextField.placeholderText")?.toString() ?: ""
            if (pp.contains(placeholder, ignoreCase = true)) return c
        }
        if (c is java.awt.Container) {
            val found = findTextFieldByPlaceholder(c, placeholder)
            if (found != null) return found
        }
    }
    return null
}

/** Find a JButton whose text contains the given substring. */
private fun findButtonByTextContains(container: java.awt.Container, text: String): javax.swing.JButton? {
    for (i in 0 until container.componentCount) {
        val c = container.getComponent(i)
        if (c is javax.swing.JButton && c.text?.contains(text, ignoreCase = true) == true) return c
        if (c is java.awt.Container) {
            val found = findButtonByTextContains(c, text)
            if (found != null) return found
        }
    }
    return null
}

/** Find the JTable used in ExplorerPanel's file listing. */
private fun findFileTable(w: MainWindow): javax.swing.JTable? {
    return try {
        val explorerField = w.javaClass.getDeclaredField("explorerPanel")
        explorerField.isAccessible = true
        val explorerPanel = explorerField.get(w)
        val tableField = explorerPanel.javaClass.getDeclaredField("table")
        tableField.isAccessible = true
        tableField.get(explorerPanel) as? javax.swing.JTable
    } catch (_: Exception) { null }
}

/** Find a JPanel/JComponent that hosts editor file tabs. */
private fun findEditorTabStrip(w: MainWindow): java.awt.Container? {
    return try {
        val explorerField = w.javaClass.getDeclaredField("explorerPanel")
        explorerField.isAccessible = true
        val explorerPanel = explorerField.get(w)
        val editorComp = explorerPanel.javaClass.getDeclaredField("editorComponent")
        editorComp.isAccessible = true
        val ec = editorComp.get(explorerPanel)
        // EditorPanel contains a tabbed pane — find JTabbedPane descendant
        findFirstComponent(ec as? java.awt.Container, javax.swing.JTabbedPane::class.java)
    } catch (_: Exception) { null }
}

private fun <T : java.awt.Container> findFirstComponent(container: java.awt.Container?, type: Class<T>): T? {
    if (container == null) return null
    if (type.isInstance(container)) @Suppress("UNCHECKED_CAST") return container as T
    for (i in 0 until container.componentCount) {
        val c = container.getComponent(i)
        if (type.isInstance(c)) @Suppress("UNCHECKED_CAST") return c as T
        if (c is java.awt.Container) {
            val found = findFirstComponent(c, type)
            if (found != null) return found
        }
    }
    return null
}

/** Find the address bar JTextField in the ExplorerPanel. */
private fun findAddressBar(w: MainWindow): JTextField? {
    return try {
        val explorerField = w.javaClass.getDeclaredField("explorerPanel")
        explorerField.isAccessible = true
        val explorerPanel = explorerField.get(w)
        val addressField = explorerPanel.javaClass.getDeclaredField("addressField")
        addressField.isAccessible = true
        addressField.get(explorerPanel) as? JTextField
    } catch (_: Exception) { null }
}

// ── Settings tab helper ───────────────────────────────────────────────────────

private fun settingsTabShot(
    robot: Robot, window: JFrame, ctx: AppContext,
    tabIndex: Int, dest: Path,
    @Suppress("UNUSED_PARAMETER") tabName: String,
) {
    SwingUtilities.invokeLater {
        val d = SettingsDialog(window, ctx, sendToTerminal = {})
        try {
            val sidebarList = findJListIn(d)
            if (sidebarList != null) sidebarList.selectedIndex = tabIndex
        } catch (e: Exception) { e.printStackTrace() }
        d.isVisible = true
    }
    val deadline = System.currentTimeMillis() + 5000
    while (System.currentTimeMillis() < deadline) {
        val visible = Window.getWindows().filterIsInstance<JDialog>().any { it.isVisible }
        if (visible) break
        Thread.sleep(100)
    }
    Thread.sleep(600)
    screenshotTopDialog(robot, dest)
    println("  > ${dest.fileName}")
    closeTopDialog()
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

// ── Screenshot helpers ────────────────────────────────────────────────────────

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
    println("  > ${dest.fileName}")
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
                val gitField  = treePanel.javaClass.getDeclaredField("gitStatusCache")
                scanField.isAccessible = true
                gitField.isAccessible  = true
                scanSize = (scanField.get(treePanel) as? Map<*, *>)?.size ?: 0
                gitSize  = (gitField.get(treePanel)  as? Map<*, *>)?.size ?: 0
            } catch (_: Exception) {
                scanSize = expectedProjects
                gitSize  = expectedProjects
            }
        }
        if (scanSize >= expectedProjects && gitSize >= expectedProjects) return
        Thread.sleep(150)
    }
}

// ── Demo asset helpers ────────────────────────────────────────────────────────

private fun createDemoImage(root: Path): File {
    val file = root.resolve("needlecast/demo-screenshot.png").toFile()
    if (!file.exists()) {
        val img = java.awt.image.BufferedImage(400, 300, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = java.awt.Color(30, 30, 46)
        g.fillRect(0, 0, 400, 300)
        g.color = java.awt.Color(137, 180, 250)
        g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 24)
        g.drawString("Needlecast", 120, 160)
        g.dispose()
        file.parentFile.mkdirs()
        ImageIO.write(img, "PNG", file)
    }
    return file
}

private fun createDemoSvg(root: Path): File {
    val file = root.resolve("needlecast/logo.svg").toFile()
    if (!file.exists()) {
        file.parentFile.mkdirs()
        file.writeText("""
            <svg xmlns="http://www.w3.org/2000/svg" width="200" height="200" viewBox="0 0 200 200">
              <circle cx="100" cy="100" r="80" fill="#7C4DFF"/>
              <text x="100" y="115" font-size="40" text-anchor="middle" fill="white" font-family="sans-serif">NC</text>
            </svg>
        """.trimIndent())
    }
    return file
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
    val specs = listOf(
        DemoSpec("needlecast",                      "needlecast",                      ::scaffoldMaven,      "main",                 false),
        DemoSpec("web-dashboard",                   "web-dashboard",                   ::scaffoldNpm,        "feature/ux-refresh",   true),
        DemoSpec("api-service",                     "api-service",                     ::scaffoldGradle,     "develop",              false),
        DemoSpec("ml-pipeline",                     "ml-pipeline",                     ::scaffoldPython,     "experiment/ablation",  true),
        DemoSpec("rust-engine",                     "rust-engine",                     ::scaffoldRust,       "perf/fast-path",       false),
        DemoSpec("go-service",                      "go-service",                      ::scaffoldGo,         "release/v1.8",         false),
        DemoSpec("enterprise-microservice-gateway", "enterprise-microservice-gateway", ::scaffoldMaven,      "hotfix/auth-headers",  true),
        DemoSpec("react-native-shopping-app",       "react-native-shopping-app",       ::scaffoldNpm,        "feature/cart-v2",      false),
        DemoSpec("maven-docker-app",                "maven-docker-app",                ::scaffoldMavenDocker,"feature/containerize",  false),
    )
    return specs.map { (dirName, label, scaffold, branch, dirty) ->
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
        dir.resolve("README.md").appendText("\nUncommitted demo change.\n")
    }
}

private fun runGit(dir: File, vararg args: String): Boolean {
    return try {
        val cmd = listOf("git", "-C", dir.absolutePath) + args.toList()
        val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
        proc.inputStream.bufferedReader().use { it.readText() }
        proc.waitFor() == 0
    } catch (_: Exception) { false }
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
    dir.resolve("README.md").writeText("# ${dir.name}\n\nA sample Maven project.\n\n## Build\n\n```\nmvn clean install\n```\n")
    dir.resolve("src/main/kotlin").mkdirs()
    dir.resolve("src/test/kotlin").mkdirs()
    dir.resolve("src/main/kotlin/Main.kt").writeText(
        "fun main() {\n    println(\"Hello from ${dir.name}\")\n}\n"
    )
}

private fun scaffoldMavenDocker(dir: File) {
    scaffoldMaven(dir)
    dir.resolve("Dockerfile").writeText(
        "FROM eclipse-temurin:21-jre\nCOPY target/${dir.name}-1.0.0.jar app.jar\nENTRYPOINT [\"java\",\"-jar\",\"app.jar\"]\n"
    )
    dir.resolve("docker-compose.yml").writeText(
        "version: '3.8'\nservices:\n  app:\n    build: .\n    ports:\n      - \"8080:8080\"\n"
    )
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
    dir.resolve("src/main/java/Main.java").writeText(
        "public class Main {\n    public static void main(String[] a) {\n        System.out.println(\"Hello\");\n    }\n}\n"
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
    dir.resolve("main.go").writeText(
        "package main\n\nimport \"fmt\"\n\nfunc main() {\n\tfmt.Println(\"Hello from ${dir.name}\")\n}\n"
    )
    dir.resolve("README.md").writeText("# ${dir.name}\n\nA Go service.\n")
    val cmdDir = dir.resolve("cmd/server")
    cmdDir.mkdirs()
    cmdDir.resolve("main.go").writeText("package main\n\nfunc main() {}\n")
}

private fun buildDemoConfig(projects: List<DemoProject>): AppConfig {
    val needlecast    = projects[0]
    val webDashboard  = projects[1]
    val apiService    = projects[2]
    val mlPipeline    = projects[3]
    val rustEngine    = projects[4]
    val goService     = projects[5]
    val enterpriseGw  = projects[6]
    val shoppingApp   = projects[7]
    val mavenDocker   = projects[8]

    val projectTree = listOf(
        ProjectTreeEntry.Folder(
            name  = "Java / Kotlin",
            color = "#7C4DFF",
            children = listOf(
                ProjectTreeEntry.Project(
                    directory = ProjectDirectory(
                        path        = needlecast.dir.absolutePath,
                        displayName = needlecast.displayName,
                        color       = "#7C4DFF",
                    ),
                    tags = listOf("kotlin", "swing"),
                ),
                ProjectTreeEntry.Project(
                    directory = ProjectDirectory(
                        path        = apiService.dir.absolutePath,
                        displayName = apiService.displayName,
                    ),
                    tags = listOf("java", "rest"),
                ),
                ProjectTreeEntry.Project(
                    directory = ProjectDirectory(
                        path        = mavenDocker.dir.absolutePath,
                        displayName = mavenDocker.displayName,
                    ),
                    tags = listOf("java", "docker"),
                ),
                // Missing directory entry to demonstrate 2.6
                ProjectTreeEntry.Project(
                    directory = ProjectDirectory(
                        path        = "/nonexistent/missing-project",
                        displayName = "missing-project",
                    ),
                    tags = listOf("archived"),
                ),
            ),
        ),
        ProjectTreeEntry.Folder(
            name  = "Frontend",
            color = "#FF6D00",
            children = listOf(
                ProjectTreeEntry.Project(
                    directory = ProjectDirectory(
                        path        = webDashboard.dir.absolutePath,
                        displayName = webDashboard.displayName,
                        color       = "#FF6D00",
                    ),
                    tags = listOf("react", "ts"),
                ),
                ProjectTreeEntry.Project(
                    directory = ProjectDirectory(
                        path        = shoppingApp.dir.absolutePath,
                        displayName = shoppingApp.displayName,
                    ),
                    tags = listOf("react-native", "typescript", "mobile"),
                ),
            ),
        ),
        ProjectTreeEntry.Folder(
            name  = "Python / Rust / Go",
            color = "#00BFA5",
            children = listOf(
                ProjectTreeEntry.Project(
                    directory = ProjectDirectory(
                        path        = mlPipeline.dir.absolutePath,
                        displayName = mlPipeline.displayName,
                    ),
                    tags = listOf("python", "ml"),
                ),
                ProjectTreeEntry.Project(
                    directory = ProjectDirectory(
                        path        = rustEngine.dir.absolutePath,
                        displayName = rustEngine.displayName,
                    ),
                    tags = listOf("rust", "wasm"),
                ),
                ProjectTreeEntry.Project(
                    directory = ProjectDirectory(
                        path        = goService.dir.absolutePath,
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
                        path        = enterpriseGw.dir.absolutePath,
                        displayName = enterpriseGw.displayName,
                    ),
                    tags = listOf("spring-boot", "kubernetes", "microservice"),
                ),
            ),
        ),
    )

    return AppConfig(
        theme        = "dark-purple",
        projectTree  = projectTree,
        windowWidth  = 1440,
        windowHeight = 900,
    )
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
    appendLine("io.example.service.NotFoundException: User not found: id=99")
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

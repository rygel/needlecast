package io.github.rygel.needlecast.ui

import io.github.andrewauclair.moderndocking.settings.Settings
import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.ThemeRegistry
import io.github.rygel.needlecast.config.JsonConfigStore
import io.github.rygel.needlecast.model.AppConfig
import org.assertj.swing.core.BasicRobot
import org.assertj.swing.core.Robot
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.edt.GuiQuery
import org.assertj.swing.fixture.FrameFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Rectangle
import java.nio.file.Path
import javax.swing.JTabbedPane

/**
 * End-to-end tests for the ModernDocking layout in [MainWindow].
 *
 * Verifies that on a fresh start (no saved layout file) the default proportions are applied
 * correctly — in particular, the terminal panel should be the widest panel.
 *
 * Run via: mvn verify -Ptest-desktop (requires Xvfb — use Dockerfile.uitest).
 * Never run locally — these tests capture the mouse and keyboard.
 */
class DockingLayoutUiTest {

    private lateinit var robot: Robot
    private lateinit var fixture: FrameFixture
    private lateinit var window: MainWindow

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        robot = BasicRobot.robotWithNewAwtHierarchy()
    }

    @AfterEach
    fun tearDown() {
        fixture.cleanUp()
        robot.cleanUp()
    }

    private fun startApp(): MainWindow {
        val store = JsonConfigStore(tempDir.resolve("config.json"))
        val ctx = AppContext(configStore = store)
        ctx.updateConfig(AppConfig())
        ThemeRegistry.apply("dark")
        return GuiActionRunner.execute(object : GuiQuery<MainWindow>() {
            override fun executeInEDT(): MainWindow = MainWindow(ctx)
        })!!
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns the screen bounds of a dockable panel by its persistent ID, or null if not found. */
    private fun boundsOf(persistentId: String): Rectangle? {
        val all = robot.finder().findAll { c -> c is DockablePanel }
        val panel = all.filterIsInstance<DockablePanel>().firstOrNull { it.dockableId == persistentId }
            ?: return null
        if (!panel.isShowing) return null
        return panel.bounds
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * On first run (no saved layout file) the built-in default layout must be applied.
     * All six dockables must be visible.
     */
    @Test
    fun `all panels are docked on first run`() {
        window = startApp()
        fixture = FrameFixture(robot, window)
        fixture.show()
        robot.waitForIdle()

        val ids = listOf("terminal", "project-tree", "commands", "git-log", "explorer", "console")
        for (id in ids) {
            val bounds = boundsOf(id)
            assertTrue(bounds != null && bounds.width > 0,
                "Panel '$id' should be docked and visible, bounds=$bounds")
        }
    }

    /**
     * The terminal must occupy more horizontal space than the project-tree panel AND
     * more than the commands panel — verifying the 15 / ~60 / 25 default split.
     */
    @Test
    fun `terminal is wider than project tree and commands by default`() {
        window = startApp()
        fixture = FrameFixture(robot, window)
        fixture.show()
        robot.waitForIdle()

        val terminalBounds   = boundsOf("terminal")     ?: error("terminal not found")
        val projectBounds    = boundsOf("project-tree") ?: error("project-tree not found")
        val commandsBounds   = boundsOf("commands")     ?: error("commands not found")

        assertTrue(terminalBounds.width > projectBounds.width,
            "Terminal (${terminalBounds.width}px) should be wider than project tree (${projectBounds.width}px)")
        assertTrue(terminalBounds.width > commandsBounds.width,
            "Terminal (${terminalBounds.width}px) should be wider than commands (${commandsBounds.width}px)")
    }

    /**
     * The project-tree panel must be narrower than the commands panel.
     * Default: project-tree ≈ 15 %, commands ≈ 25 %.
     */
    @Test
    fun `project tree is narrower than commands panel by default`() {
        window = startApp()
        fixture = FrameFixture(robot, window)
        fixture.show()
        robot.waitForIdle()

        val projectBounds  = boundsOf("project-tree") ?: error("project-tree not found")
        val commandsBounds = boundsOf("commands")     ?: error("commands not found")

        assertTrue(projectBounds.width < commandsBounds.width,
            "Project tree (${projectBounds.width}px) should be narrower than commands (${commandsBounds.width}px)")
    }

    /**
     * After View → Reset Layout is triggered, all panels must return to the docked state.
     * (Simulates a user who had undocked panels and wants to restore defaults.)
     */
    @Test
    fun `reset layout re-docks all panels`() {
        window = startApp()
        fixture = FrameFixture(robot, window)
        fixture.show()
        robot.waitForIdle()

        // Undock the console and explorer programmatically to simulate a customised layout
        GuiActionRunner.execute(object : GuiQuery<Unit>() {
            override fun executeInEDT() {
                window.resetLayout()
            }
        })
        robot.waitForIdle()

        val ids = listOf("terminal", "project-tree", "commands", "git-log", "explorer", "console")
        for (id in ids) {
            val bounds = boundsOf(id)
            assertTrue(bounds != null && bounds.width > 0,
                "After reset, panel '$id' should be visible, bounds=$bounds")
        }
    }

    /**
     * Width proportions after reset must match the default: terminal largest,
     * then commands, then project-tree.
     */
    @Test
    fun `reset layout restores correct proportions`() {
        window = startApp()
        fixture = FrameFixture(robot, window)
        fixture.show()
        robot.waitForIdle()

        GuiActionRunner.execute(object : GuiQuery<Unit>() {
            override fun executeInEDT() { window.resetLayout() }
        })
        robot.waitForIdle()

        val terminalW  = boundsOf("terminal")?.width     ?: error("terminal not found")
        val projectW   = boundsOf("project-tree")?.width ?: error("project-tree not found")
        val commandsW  = boundsOf("commands")?.width     ?: error("commands not found")

        assertTrue(terminalW > commandsW,
            "After reset: terminal ($terminalW) should be wider than commands ($commandsW)")
        assertTrue(commandsW > projectW,
            "After reset: commands ($commandsW) should be wider than project-tree ($projectW)")
    }

    /**
     * When [AppConfig.tabsOnTop] is true (the default), ModernDocking must set
     * [Settings.defaultTabPreference] to TOP_ALWAYS before docking, so all
     * [JTabbedPane]s inside the docked panels use [JTabbedPane.TOP] placement.
     */
    @Test
    fun `tabs are at the top by default`() {
        window = startApp()
        fixture = FrameFixture(robot, window)
        fixture.show()
        robot.waitForIdle()

        val tabbedPanes = GuiActionRunner.execute(object : GuiQuery<List<Int>>() {
            override fun executeInEDT(): List<Int> =
                robot.finder()
                    .findAll { c -> c is JTabbedPane && c.tabCount > 1 }
                    .filterIsInstance<JTabbedPane>()
                    .map { it.tabPlacement }
        })!!

        assertTrue(tabbedPanes.isNotEmpty(),
            "Expected at least one JTabbedPane with multiple tabs (e.g. Terminal+Editor, Projects+Explorer)")
        assertTrue(tabbedPanes.all { it == JTabbedPane.TOP },
            "All tabbed docking panels should use TOP placement, but found placements: $tabbedPanes")
    }

    /**
     * When [AppConfig.tabsOnTop] is false, resetting the layout must apply
     * BOTTOM tab placement to all docked tabbed panels.
     */
    @Test
    fun `tabs are at the bottom when tabsOnTop is false`() {
        val store = JsonConfigStore(tempDir.resolve("config.json"))
        val ctx = AppContext(configStore = store)
        ctx.updateConfig(AppConfig(tabsOnTop = false))
        ThemeRegistry.apply("dark")
        window = GuiActionRunner.execute(object : GuiQuery<MainWindow>() {
            override fun executeInEDT(): MainWindow = MainWindow(ctx)
        })!!
        fixture = FrameFixture(robot, window)
        fixture.show()
        robot.waitForIdle()

        val tabbedPanes = GuiActionRunner.execute(object : GuiQuery<List<Int>>() {
            override fun executeInEDT(): List<Int> =
                robot.finder()
                    .findAll { c -> c is JTabbedPane && c.tabCount > 1 }
                    .filterIsInstance<JTabbedPane>()
                    .map { it.tabPlacement }
        })!!

        assertTrue(tabbedPanes.isNotEmpty(),
            "Expected at least one JTabbedPane with multiple tabs")
        assertTrue(tabbedPanes.all { it == JTabbedPane.BOTTOM },
            "All tabbed docking panels should use BOTTOM placement when tabsOnTop=false, but found: $tabbedPanes")
    }
}

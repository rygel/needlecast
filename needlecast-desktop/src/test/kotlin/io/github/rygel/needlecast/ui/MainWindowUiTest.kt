package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.config.JsonConfigStore
import io.github.rygel.needlecast.model.AppConfig
import org.assertj.swing.core.BasicRobot
import org.assertj.swing.core.Robot
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.fixture.FrameFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import javax.swing.JMenu

/**
 * Smoke tests for [MainWindow].
 *
 * Run via: mvn verify -Ptest-desktop (requires Xvfb — use Dockerfile.uitest).
 * Never run locally without a virtual display.
 */
class MainWindowUiTest {

    private lateinit var robot: Robot
    private lateinit var fixture: FrameFixture
    private lateinit var window: MainWindow
    private var previousSkipDocking: String? = null

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        previousSkipDocking = System.getProperty("needlecast.skipDocking")
        System.setProperty("needlecast.skipDocking", "true")
        robot = BasicRobot.robotWithNewAwtHierarchy()
        val store = JsonConfigStore(tempDir.resolve("config.json"))
        val ctx = AppContext(configStore = store)
        ctx.updateConfig(AppConfig())

        window = GuiActionRunner.execute<MainWindow> { MainWindow(ctx) }
        fixture = FrameFixture(robot, window)
        fixture.show()
    }

    @AfterEach
    fun tearDown() {
        fixture.cleanUp()
        robot.cleanUp()
        if (previousSkipDocking == null) {
            System.clearProperty("needlecast.skipDocking")
        } else {
            System.setProperty("needlecast.skipDocking", previousSkipDocking)
        }
    }

    @Test
    fun `main window is visible after startup`() {
        fixture.requireVisible()
    }

    @Test
    fun `window title starts with Needlecast`() {
        assertTrue(window.title.startsWith("Needlecast"),
            "Expected title to start with 'Needlecast' but was: '${window.title}'")
    }

    @Test
    fun `window has a non-empty menu bar`() {
        val menuBar = window.jMenuBar
        assertNotNull(menuBar, "JMenuBar must be present")
        assertTrue(menuBar.componentCount > 0, "Menu bar must have at least one menu")
    }

    @Test
    fun `File menu is present in the menu bar`() {
        val menuBar = window.jMenuBar
        val names = menuBar.components.filterIsInstance<JMenu>().map { it.text }
        assertTrue(names.contains("File"), "Expected 'File' menu, found: $names")
    }
}

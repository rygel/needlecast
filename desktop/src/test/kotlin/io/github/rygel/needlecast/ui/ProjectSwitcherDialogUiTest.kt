package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.config.JsonConfigStore
import io.github.rygel.needlecast.model.AppConfig
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.model.ProjectGroup
import org.assertj.swing.core.BasicRobot
import org.assertj.swing.core.KeyPressInfo.keyCode
import org.assertj.swing.core.Robot
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.fixture.DialogFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.event.KeyEvent
import java.nio.file.Path
import javax.swing.JDialog
import javax.swing.JFrame

/**
 * Swing UI tests for [ProjectSwitcherDialog].
 *
 * Run via: mvn verify -Ptest-desktop (requires Xvfb — use Dockerfile.uitest).
 */
class ProjectSwitcherDialogUiTest {

    private lateinit var robot: Robot
    private lateinit var ownerFrame: JFrame

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        robot = BasicRobot.robotWithNewAwtHierarchy()
        ownerFrame = GuiActionRunner.execute<JFrame> { JFrame() }
    }

    @AfterEach
    fun tearDown() {
        robot.cleanUp()
    }

    private fun makeCtxWithProjects(): AppContext {
        val store = JsonConfigStore(tempDir.resolve("config.json"))
        val ctx = AppContext(configStore = store)
        val config = AppConfig(
            groups = listOf(
                ProjectGroup(
                    id = "g1", name = "Work",
                    directories = listOf(
                        ProjectDirectory(path = "/work/frontend",  displayName = "Frontend"),
                        ProjectDirectory(path = "/work/backend",   displayName = "Backend"),
                        ProjectDirectory(path = "/work/auth-api",  displayName = "Auth API"),
                    ),
                ),
                ProjectGroup(
                    id = "g2", name = "Personal",
                    directories = listOf(
                        ProjectDirectory(path = "/personal/blog", displayName = "Blog"),
                    ),
                ),
            ),
        )
        ctx.updateConfig(config)
        return ctx
    }

    @Test
    fun `dialog opens and shows all projects when search is empty`() {
        val ctx = makeCtxWithProjects()
        var selectedGroupId: String? = null
        var selectedPath: String? = null

        val dialog = GuiActionRunner.execute<JDialog> {
            ProjectSwitcherDialog(ownerFrame, ctx) { gid, path ->
                selectedGroupId = gid; selectedPath = path
            }
        }
        val fixture = DialogFixture(robot, dialog)
        fixture.show()
        fixture.requireVisible()
        // Search field is empty → all 4 projects visible in the list
        fixture.list().requireItemCount(4)
        fixture.cleanUp()
    }

    @Test
    fun `typing in search field filters the project list`() {
        val ctx = makeCtxWithProjects()
        val dialog = GuiActionRunner.execute<JDialog> {
            ProjectSwitcherDialog(ownerFrame, ctx) { _, _ -> }
        }
        val fixture = DialogFixture(robot, dialog)
        fixture.show()

        fixture.textBox().enterText("front")
        fixture.list().requireItemCount(1)
        fixture.cleanUp()
    }

    @Test
    fun `search is case-insensitive`() {
        val ctx = makeCtxWithProjects()
        val dialog = GuiActionRunner.execute<JDialog> {
            ProjectSwitcherDialog(ownerFrame, ctx) { _, _ -> }
        }
        val fixture = DialogFixture(robot, dialog)
        fixture.show()

        fixture.textBox().enterText("BACK")
        fixture.list().requireItemCount(1)
        fixture.cleanUp()
    }

    @Test
    fun `search that matches nothing shows empty list`() {
        val ctx = makeCtxWithProjects()
        val dialog = GuiActionRunner.execute<JDialog> {
            ProjectSwitcherDialog(ownerFrame, ctx) { _, _ -> }
        }
        val fixture = DialogFixture(robot, dialog)
        fixture.show()

        fixture.textBox().enterText("xyzzy")
        fixture.list().requireItemCount(0)
        fixture.cleanUp()
    }

    @Test
    fun `Escape closes the dialog`() {
        val ctx = makeCtxWithProjects()
        val dialog = GuiActionRunner.execute<JDialog> {
            ProjectSwitcherDialog(ownerFrame, ctx) { _, _ -> }
        }
        val fixture = DialogFixture(robot, dialog)
        fixture.show()
        fixture.requireVisible()

        fixture.pressAndReleaseKey(keyCode(KeyEvent.VK_ESCAPE))
        fixture.requireNotVisible()
    }

    @Test
    fun `Enter on selected item invokes onSelect callback`() {
        val ctx = makeCtxWithProjects()
        var selectedPath: String? = null

        val dialog = GuiActionRunner.execute<JDialog> {
            ProjectSwitcherDialog(ownerFrame, ctx) { _, path -> selectedPath = path }
        }
        val fixture = DialogFixture(robot, dialog)
        fixture.show()

        // Filter to a single result and confirm with Enter
        fixture.textBox().enterText("frontend")
        fixture.list().requireItemCount(1)
        fixture.pressAndReleaseKey(keyCode(KeyEvent.VK_ENTER))

        assertEquals("/work/frontend", selectedPath)
    }

    @Test
    fun `Down arrow moves selection and Up arrow returns to first item`() {
        val ctx = makeCtxWithProjects()
        val dialog = GuiActionRunner.execute<JDialog> {
            ProjectSwitcherDialog(ownerFrame, ctx) { _, _ -> }
        }
        val fixture = DialogFixture(robot, dialog)
        fixture.show()

        // Initially first item (index 0) is selected
        assertEquals(0, fixture.list().target().selectedIndex)

        fixture.textBox().pressAndReleaseKeys(KeyEvent.VK_DOWN)
        assertEquals(1, fixture.list().target().selectedIndex)

        fixture.textBox().pressAndReleaseKeys(KeyEvent.VK_UP)
        assertEquals(0, fixture.list().target().selectedIndex)
        fixture.cleanUp()
    }
}

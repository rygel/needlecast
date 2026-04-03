package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.config.JsonConfigStore
import io.github.rygel.needlecast.model.AppConfig
import org.assertj.swing.core.BasicRobot
import org.assertj.swing.core.Robot
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.fixture.DialogFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import javax.swing.JDialog
import javax.swing.JFrame

/**
 * Swing UI tests for [SettingsDialog].
 *
 * Run via: mvn verify -Ptest-desktop (requires Xvfb — use Dockerfile.uitest).
 * Never run locally without a virtual display.
 */
class SettingsDialogUiTest {

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

    private fun makeCtx(): AppContext {
        val store = JsonConfigStore(tempDir.resolve("config.json"))
        val ctx = AppContext(configStore = store)
        ctx.updateConfig(AppConfig())
        return ctx
    }

    // ── Regression: NPE when opening Shortcuts tab ─────────────────────────

    @Test
    fun `settings dialog opens without NullPointerException`() {
        val dialog = GuiActionRunner.execute<JDialog> {
            SettingsDialog(ownerFrame, makeCtx(), {})
        }
        val fixture = DialogFixture(robot, dialog)
        fixture.show()
        fixture.requireVisible()
        fixture.cleanUp()
    }

    @Test
    fun `shortcuts tab is reachable and renders without error`() {
        val dialog = GuiActionRunner.execute<JDialog> {
            SettingsDialog(ownerFrame, makeCtx(), {})
        }
        val fixture = DialogFixture(robot, dialog)
        fixture.show()
        fixture.tabbedPane().selectTab("Shortcuts")
        fixture.tabbedPane().requireVisible()
        fixture.cleanUp()
    }

    // ── Tab navigation ─────────────────────────────────────────────────────

    @Test
    fun `all four tabs are present`() {
        val dialog = GuiActionRunner.execute<JDialog> {
            SettingsDialog(ownerFrame, makeCtx(), {})
        }
        val fixture = DialogFixture(robot, dialog)
        fixture.show()
        fixture.tabbedPane().requireTabTitles("External Editors", "Renovate", "APM", "Shortcuts", "Language")
        fixture.cleanUp()
    }

    @Test
    fun `external editors tab is accessible`() {
        val dialog = GuiActionRunner.execute<JDialog> {
            SettingsDialog(ownerFrame, makeCtx(), {})
        }
        val fixture = DialogFixture(robot, dialog)
        fixture.show()
        fixture.tabbedPane().selectTab("External Editors")
        fixture.tabbedPane().requireVisible()
        fixture.cleanUp()
    }

    @Test
    fun `renovate tab is accessible`() {
        val dialog = GuiActionRunner.execute<JDialog> {
            SettingsDialog(ownerFrame, makeCtx(), {})
        }
        val fixture = DialogFixture(robot, dialog)
        fixture.show()
        fixture.tabbedPane().selectTab("Renovate")
        fixture.tabbedPane().requireVisible()
        fixture.cleanUp()
    }

    @Test
    fun `APM tab is accessible`() {
        val dialog = GuiActionRunner.execute<JDialog> {
            SettingsDialog(ownerFrame, makeCtx(), {})
        }
        val fixture = DialogFixture(robot, dialog)
        fixture.show()
        fixture.tabbedPane().selectTab("APM")
        fixture.tabbedPane().requireVisible()
        fixture.cleanUp()
    }

    // ── Shortcut field values ──────────────────────────────────────────────

    @Test
    fun `shortcut fields are pre-filled with defaults when config is empty`() {
        val dialog = GuiActionRunner.execute<JDialog> {
            SettingsDialog(ownerFrame, makeCtx(), {})
        }
        val fixture = DialogFixture(robot, dialog)
        fixture.show()
        fixture.tabbedPane().selectTab("Shortcuts")

        SettingsDialog.defaultShortcuts.forEach { (id, default) ->
            fixture.textBox(id).requireText(default)
        }
        fixture.cleanUp()
    }

    @Test
    fun `shortcut fields show overridden value from config`() {
        val ctx = makeCtx()
        ctx.updateConfig(ctx.config.copy(shortcuts = mapOf("rescan" to "F9")))

        val dialog = GuiActionRunner.execute<JDialog> {
            SettingsDialog(ownerFrame, ctx, {})
        }
        val fixture = DialogFixture(robot, dialog)
        fixture.show()
        fixture.tabbedPane().selectTab("Shortcuts")
        fixture.textBox("rescan").requireText("F9")
        fixture.cleanUp()
    }
}

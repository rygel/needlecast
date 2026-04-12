package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.config.JsonConfigStore
import io.github.rygel.needlecast.model.AppConfig
import io.github.rygel.needlecast.ui.settings.ShortcutsSettingsPanel
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
    fun `settings dialog has correct size`() {
        val dialog = GuiActionRunner.execute<JDialog> {
            SettingsDialog(ownerFrame, makeCtx(), {})
        }
        val fixture = DialogFixture(robot, dialog)
        fixture.show()
        robot.waitForIdle()
        fixture.requireSize(java.awt.Dimension(760, 560))
        fixture.cleanUp()
    }

    // ── Sidebar navigation ─────────────────────────────────────────────────

    @Test
    fun `sidebar list is present`() {
        val dialog = GuiActionRunner.execute<JDialog> {
            SettingsDialog(ownerFrame, makeCtx(), {})
        }
        val fixture = DialogFixture(robot, dialog)
        fixture.show()
        fixture.list().requireVisible()
        fixture.cleanUp()
    }

    @Test
    fun `sidebar contains expected categories`() {
        val dialog = GuiActionRunner.execute<JDialog> {
            SettingsDialog(ownerFrame, makeCtx(), {})
        }
        val fixture = DialogFixture(robot, dialog)
        fixture.show()
        val contents = fixture.list().contents()
        val labels = contents.map { it.toString() }
        assert(labels.any { it.contains("Appearance") }) { "Expected 'Appearance' in sidebar" }
        assert(labels.any { it.contains("Shortcuts") })  { "Expected 'Shortcuts' in sidebar" }
        assert(labels.any { it.contains("Language") })   { "Expected 'Language' in sidebar" }
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
        // Navigate to Shortcuts via sidebar (index 9 in the model)
        fixture.list().selectItem("Shortcuts")
        robot.waitForIdle()

        ShortcutsSettingsPanel.defaultShortcuts.forEach { (id, default) ->
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
        // Navigate to Shortcuts via sidebar
        fixture.list().selectItem("Shortcuts")
        robot.waitForIdle()
        fixture.textBox("rescan").requireText("F9")
        fixture.cleanUp()
    }
}

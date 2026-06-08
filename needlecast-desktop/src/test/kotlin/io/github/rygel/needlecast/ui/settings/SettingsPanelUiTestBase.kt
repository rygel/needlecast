package io.github.rygel.needlecast.ui.settings

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.config.JsonConfigStore
import io.github.rygel.needlecast.model.AppConfig
import io.github.rygel.needlecast.ui.SettingsDialog
import org.assertj.swing.core.BasicRobot
import org.assertj.swing.core.Robot
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.fixture.DialogFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import javax.swing.JDialog
import javax.swing.JFrame

abstract class SettingsPanelUiTestBase {
    protected lateinit var robot: Robot
    protected lateinit var ownerFrame: JFrame

    @TempDir
    protected lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        robot = BasicRobot.robotWithNewAwtHierarchy()
        ownerFrame = GuiActionRunner.execute<JFrame> { JFrame() }
    }

    @AfterEach
    fun tearDown() {
        robot.cleanUp()
    }

    protected fun makeCtx(): AppContext {
        val store = JsonConfigStore(tempDir.resolve("config.json"))
        val ctx = AppContext(configStore = store)
        ctx.updateConfig(AppConfig())
        return ctx
    }

    protected fun openSettings(ctx: AppContext): DialogFixture {
        val dialog =
            GuiActionRunner.execute<JDialog> {
                SettingsDialog(ownerFrame, ctx, {})
            }
        val fixture = DialogFixture(robot, dialog)
        fixture.show()
        robot.waitForIdle()
        return fixture
    }
}

package io.github.rygel.needlecast.ui.settings

import org.assertj.swing.fixture.JSpinnerFixture
import org.junit.jupiter.api.Test

class TerminalSettingsPanelUiTest : SettingsPanelUiTestBase() {
    @Test
    fun `terminal font size spinner persists to config`() {
        val ctx = makeCtx()
        val fixture = openSettings(ctx)
        fixture.list().selectItem("Terminal")
        robot.waitForIdle()

        val spinner: JSpinnerFixture = fixture.spinner()
        val original = ctx.config.terminalFontSize
        spinner.increment()
        robot.waitForIdle()
        assert(ctx.config.terminalFontSize == original + 1) {
            "Expected terminalFontSize=${original + 1}, got ${ctx.config.terminalFontSize}"
        }

        spinner.decrement()
        robot.waitForIdle()
        assert(ctx.config.terminalFontSize == original) {
            "Expected terminalFontSize=$original, got ${ctx.config.terminalFontSize}"
        }

        fixture.cleanUp()
    }
}

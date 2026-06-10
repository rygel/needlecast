package io.github.rygel.needlecast.ui.settings

import org.junit.jupiter.api.Test

class AppearanceSettingsPanelUiTest : SettingsPanelUiTestBase() {
    @Test
    fun `syntax theme combo persists to config`() {
        val ctx = makeCtx()
        val fixture = openSettings(ctx)
        fixture.list().selectItem("Appearance")
        robot.waitForIdle()

        fixture.comboBox("syntaxThemeCombo").selectItem("Monokai (dark)")
        robot.waitForIdle()
        assert(ctx.config.syntaxTheme == "monokai") { "Expected syntaxTheme=monokai, got ${ctx.config.syntaxTheme}" }

        fixture.comboBox("syntaxThemeCombo").selectItem("Auto (follows app theme)")
        robot.waitForIdle()
        assert(ctx.config.syntaxTheme == "auto") { "Expected syntaxTheme=auto, got ${ctx.config.syntaxTheme}" }

        fixture.cleanUp()
    }
}

package io.github.rygel.needlecast.ui.settings

import org.junit.jupiter.api.Test

class AiToolsSettingsPanelUiTest : SettingsPanelUiTestBase() {
    @Test
    fun `quota toggle persists to config`() {
        val ctx = makeCtx()
        val fixture = openSettings(ctx)
        fixture.list().selectItem("AI Tools")
        robot.waitForIdle()

        val toggle = fixture.checkBox("Show Claude quota in status bar")
        toggle.requireSelected()
        toggle.uncheck()
        robot.waitForIdle()
        assert(ctx.config.claudeQuotaEnabled == false) { "Expected claudeQuotaEnabled=false" }

        toggle.check()
        robot.waitForIdle()
        assert(ctx.config.claudeQuotaEnabled) { "Expected claudeQuotaEnabled=true" }

        fixture.cleanUp()
    }
}

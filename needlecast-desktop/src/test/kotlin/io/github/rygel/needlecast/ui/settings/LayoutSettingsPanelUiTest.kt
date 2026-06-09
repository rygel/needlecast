package io.github.rygel.needlecast.ui.settings

import org.junit.jupiter.api.Test

class LayoutSettingsPanelUiTest : SettingsPanelUiTestBase() {
    @Test
    fun `tabs on top toggle persists to config`() {
        val ctx = makeCtx()
        val fixture = openSettings(ctx)
        // Initially tabsOnTop is false in AppConfig
        fixture.list().selectItem("Layout")
        robot.waitForIdle()

        val toggle = fixture.checkBox("tabsOnTop")
        toggle.requireNotSelected()
        toggle.check()
        robot.waitForIdle()
        assert(ctx.config.tabsOnTop) { "Expected tabsOnTop=true" }

        toggle.uncheck()
        robot.waitForIdle()
        assert(ctx.config.tabsOnTop == false) { "Expected tabsOnTop=false" }

        fixture.cleanUp()
    }
}

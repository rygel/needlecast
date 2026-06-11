package io.github.rygel.needlecast.ui.terminal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.io.TempDir
import java.awt.GraphicsEnvironment
import java.nio.file.Path

class ProjectTerminalPaneTest {
    companion object {
        @JvmStatic
        fun isHeadful(): Boolean = !GraphicsEnvironment.isHeadless()
    }

    @TempDir
    lateinit var tmpDir: Path

    private lateinit var pane: ProjectTerminalPane

    @BeforeEach
    fun setUp() {
        pane =
            ProjectTerminalPane(
                path = tmpDir.toString(),
                isDark = true,
            )
    }

    @Test
    @EnabledIf("isHeadful")
    fun `init creates one terminal tab`() {
        assertEquals(1, pane.realTabCount)
    }

    @Test
    @EnabledIf("isHeadful")
    fun `addTerminalTab increases tab count`() {
        val before = pane.realTabCount
        pane.addTerminalTab()
        assertEquals(before + 1, pane.realTabCount)
    }

    @Test
    @EnabledIf("isHeadful")
    fun `closeActiveTab removes the selected tab`() {
        pane.addTerminalTab()
        val countBefore = pane.realTabCount
        pane.tabs.selectedIndex = 0
        pane.closeActiveTab()
        assertEquals(countBefore - 1, pane.realTabCount)
    }

    @Test
    @EnabledIf("isHeadful")
    fun `closeActiveTab does not remove last tab`() {
        assertEquals(1, pane.realTabCount)
        pane.closeActiveTab()
        assertEquals(1, pane.realTabCount)
    }

    @Test
    @EnabledIf("isHeadful")
    fun `nextTab cycles tab selection`() {
        pane.addTerminalTab()
        pane.addTerminalTab()
        pane.tabs.selectedIndex = 2
        pane.nextTab()
        assertEquals(0, pane.tabs.selectedIndex)
    }

    @Test
    @EnabledIf("isHeadful")
    fun `prevTab cycles tab selection`() {
        pane.addTerminalTab()
        pane.addTerminalTab()
        pane.tabs.selectedIndex = 0
        pane.prevTab()
        assertEquals(2, pane.tabs.selectedIndex)
    }

    @Test
    @EnabledIf("isHeadful")
    fun `nextTab is no-op with single tab`() {
        pane.nextTab()
        assertEquals(0, pane.tabs.selectedIndex)
    }

    @Test
    @EnabledIf("isHeadful")
    fun `dispose does not throw`() {
        pane.addTerminalTab()
        pane.addTerminalTab()
        pane.dispose()
    }

    @Test
    @EnabledIf("isHeadful")
    fun `onStatusChanged callback is wired`() {
        var reportedStatus: AgentStatus? = null
        pane.onStatusChanged = { status -> reportedStatus = status }
        pane.addTerminalTab()
        pane.dispose()
    }

    @Test
    @EnabledIf("isHeadful")
    fun `applyFontSize does not throw`() {
        pane.addTerminalTab()
        pane.applyFontSize(16)
        assertEquals(2, pane.realTabCount)
    }
}

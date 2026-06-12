package io.github.rygel.needlecast.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import java.awt.GraphicsEnvironment

class ShellSettingsDialogTest {
    companion object {
        @JvmStatic
        fun isHeadful(): Boolean = !GraphicsEnvironment.isHeadless()
    }

    @Test
    @EnabledIf("isHeadful")
    fun `dialog has correct title`() {
        val dialog =
            ShellSettingsDialog(
                owner = null,
                projectLabel = "my-project",
                currentShell = "zsh",
                currentStartup = "conda activate ml",
                onSave = { _, _ -> },
            )
        assertEquals("Shell Settings \u2014 my-project", dialog.title)
        dialog.dispose()
    }

    @Test
    @EnabledIf("isHeadful")
    fun `shell field shows current value`() {
        val dialog =
            ShellSettingsDialog(
                owner = null,
                projectLabel = "test",
                currentShell = "fish",
                currentStartup = null,
                onSave = { _, _ -> },
            )
        assertEquals("fish", dialog.shellText)
        dialog.dispose()
    }

    @Test
    @EnabledIf("isHeadful")
    fun `startup field shows current value`() {
        val dialog =
            ShellSettingsDialog(
                owner = null,
                projectLabel = "test",
                currentShell = null,
                currentStartup = "echo hello",
                onSave = { _, _ -> },
            )
        assertEquals("echo hello", dialog.startupText)
        dialog.dispose()
    }

    @Test
    @EnabledIf("isHeadful")
    fun `shell field is empty when null`() {
        val dialog =
            ShellSettingsDialog(
                owner = null,
                projectLabel = "test",
                currentShell = null,
                currentStartup = null,
                onSave = { _, _ -> },
            )
        assertEquals("", dialog.shellText)
        dialog.dispose()
    }

    @Test
    @EnabledIf("isHeadful")
    fun `onSave receives trimmed values`() {
        var savedShell: String? = "unset"
        var savedStartup: String? = "unset"
        val dialog =
            ShellSettingsDialog(
                owner = null,
                projectLabel = "test",
                currentShell = "  zsh  ",
                currentStartup = "  echo hi  ",
                onSave = { shell, startup ->
                    savedShell = shell
                    savedStartup = startup
                },
            )
        dialog.simulateOk()
        assertEquals("zsh", savedShell)
        assertEquals("echo hi", savedStartup)
        dialog.dispose()
    }

    @Test
    @EnabledIf("isHeadful")
    fun `onSave receives null for blank shell`() {
        var savedShell: String? = "unset"
        val dialog =
            ShellSettingsDialog(
                owner = null,
                projectLabel = "test",
                currentShell = "   ",
                currentStartup = null,
                onSave = { shell, _ -> savedShell = shell },
            )
        dialog.simulateOk()
        assertNull(savedShell)
        dialog.dispose()
    }
}

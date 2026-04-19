package io.github.rygel.needlecast.ui.terminal

import io.github.rygel.needlecast.scanner.IS_WINDOWS
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

class TerminalPtyPlatformTest {

    @Test
    fun `IS_WINDOWS flag matches platform`() {
        if (System.getProperty("os.name").lowercase().contains("win")) {
            assertTrue(IS_WINDOWS, "IS_WINDOWS must be true on Windows")
        } else {
            assertFalse(IS_WINDOWS, "IS_WINDOWS must be false on non-Windows (macOS/Linux)")
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun `setUseWinConPty is not called on non-Windows platforms`() {
        assertFalse(IS_WINDOWS,
            "setUseWinConPty(true) must only be called when IS_WINDOWS is true")
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `setUseWinConPty is called on Windows`() {
        assertTrue(IS_WINDOWS,
            "setUseWinConPty(true) must be called on Windows for proper PTY behavior")
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun `shell command resolves to bash on unix`() {
        val cmd = invokeResolveShellCommand(shellExecutable = null)
        assertTrue(cmd.isNotEmpty())
        assertFalse(cmd.any { it.contains("cmd.exe", ignoreCase = true) },
            "Unix must not use cmd.exe")
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `shell command resolves to cmd on windows`() {
        val cmd = invokeResolveShellCommand(shellExecutable = null)
        assertTrue(cmd.isNotEmpty())
        assertTrue(cmd.any { it.contains("cmd.exe", ignoreCase = true) },
            "Windows must use cmd.exe by default")
    }

    @Test
    fun `shouldConsumeRemoteMouseWheelEvent does not consume Ctrl+wheel`() {
        val event = java.awt.event.MouseWheelEvent(
            javax.swing.JPanel(),
            java.awt.event.MouseEvent.MOUSE_WHEEL,
            System.currentTimeMillis(),
            java.awt.event.InputEvent.CTRL_DOWN_MASK,
            10, 10, 10, 10, 0, false,
            java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1, 1.0,
        )
        assertFalse(shouldConsumeRemoteMouseWheelEvent(event, isRemoteMouseAction = true),
            "Ctrl+wheel must not be consumed so font zoom works")
    }

    @Test
    fun `shouldConsumeRemoteMouseWheelEvent consumes plain remote wheel`() {
        val event = java.awt.event.MouseWheelEvent(
            javax.swing.JPanel(),
            java.awt.event.MouseEvent.MOUSE_WHEEL,
            System.currentTimeMillis(),
            0,
            10, 10, 10, 10, 0, false,
            java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1, 1.0,
        )
        assertTrue(shouldConsumeRemoteMouseWheelEvent(event, isRemoteMouseAction = true),
            "Plain remote wheel must be consumed to prevent outer Swing scrolling")
    }

    @Test
    fun `shouldConsumeRemoteMouseWheelEvent ignores local wheel`() {
        val event = java.awt.event.MouseWheelEvent(
            javax.swing.JPanel(),
            java.awt.event.MouseEvent.MOUSE_WHEEL,
            System.currentTimeMillis(),
            0,
            10, 10, 10, 10, 0, false,
            java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1, 1.0,
        )
        assertFalse(shouldConsumeRemoteMouseWheelEvent(event, isRemoteMouseAction = false),
            "Local wheel events must not be consumed by remote routing")
    }

    @Test
    fun `os name is logged during PTY startup`() {
        val os = System.getProperty("os.name")
        if (IS_WINDOWS) {
            assertTrue(os.lowercase().contains("win"))
        } else {
            assertFalse(os.lowercase().contains("win"))
            assertTrue(
                os.lowercase().contains("mac") || os.lowercase().contains("linux") || os.lowercase().contains("nix"),
                "Expected macOS or Linux or similar Unix, got: $os"
            )
        }
    }

    private fun invokeResolveShellCommand(shellExecutable: String?): Array<String> {
        val constructor = TerminalPanel::class.java.getDeclaredConstructor(
            String::class.java,
            Boolean::class.javaPrimitiveType,
            Map::class.java,
            String::class.java,
            String::class.java,
            java.awt.Color::class.java,
            java.awt.Color::class.java,
            Int::class.javaPrimitiveType,
            String::class.java,
        )
        constructor.isAccessible = true
        val panel = constructor.newInstance(
            System.getProperty("user.home"),
            true,
            emptyMap<String, String>(),
            shellExecutable,
            null,
            null,
            null,
            13,
            null,
        )
        val m = TerminalPanel::class.java.getDeclaredMethod("resolveShellCommand")
        m.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return m.invoke(panel) as Array<String>
    }
}

package io.github.rygel.needlecast.ui.terminal

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.Color

class QuickLaunchTerminalSettingsTest {

    // ── Default styles ────────────────────────────────────────────────────────

    @Test
    fun `dark theme default foreground is light grey`() {
        val settings = QuickLaunchTerminalSettings(dark = true)
        val style = settings.getDefaultStyle()
        val fg = style.foreground?.toAwtColor()
        assertNotNull(fg)
        // VSCode dark foreground: #D4D4D4
        assertEquals(0xD4, fg!!.red)
        assertEquals(0xD4, fg.green)
        assertEquals(0xD4, fg.blue)
    }

    @Test
    fun `dark theme default background is near-black`() {
        val settings = QuickLaunchTerminalSettings(dark = true)
        val style = settings.getDefaultStyle()
        val bg = style.background?.toAwtColor()
        assertNotNull(bg)
        // VSCode dark background: #1E1E1E
        assertEquals(0x1E, bg!!.red)
        assertEquals(0x1E, bg.green)
        assertEquals(0x1E, bg.blue)
    }

    @Test
    fun `light theme default background is white`() {
        val settings = QuickLaunchTerminalSettings(dark = false)
        val bg = settings.getDefaultStyle().background?.toAwtColor()
        assertNotNull(bg)
        assertEquals(255, bg!!.red)
        assertEquals(255, bg.green)
        assertEquals(255, bg.blue)
    }

    // ── Custom colors via constructor ─────────────────────────────────────────

    @Test
    fun `custom background supplied at construction is used in getDefaultStyle`() {
        val custom = Color(0x12, 0x34, 0x56)
        val settings = QuickLaunchTerminalSettings(dark = true, initialBg = custom)
        val bg = settings.getDefaultStyle().background?.toAwtColor()
        assertNotNull(bg)
        assertEquals(custom.red,   bg!!.red)
        assertEquals(custom.green, bg.green)
        assertEquals(custom.blue,  bg.blue)
    }

    @Test
    fun `custom foreground supplied at construction is used in getDefaultStyle`() {
        val custom = Color(0xAB, 0xCD, 0xEF)
        val settings = QuickLaunchTerminalSettings(dark = true, initialFg = custom)
        val fg = settings.getDefaultStyle().foreground?.toAwtColor()
        assertNotNull(fg)
        assertEquals(custom.red,   fg!!.red)
        assertEquals(custom.green, fg.green)
        assertEquals(custom.blue,  fg.blue)
    }

    // ── Live applyColors ──────────────────────────────────────────────────────

    @Test
    fun `applyColors overrides background at runtime`() {
        val settings = QuickLaunchTerminalSettings(dark = true)
        val custom = Color(0xFF, 0x00, 0x80)
        settings.applyColors(fg = null, bg = custom)
        val bg = settings.getDefaultStyle().background?.toAwtColor()
        assertNotNull(bg)
        assertEquals(custom.red,   bg!!.red)
        assertEquals(custom.green, bg.green)
        assertEquals(custom.blue,  bg.blue)
    }

    @Test
    fun `applyColors with null restores to theme color when set`() {
        val themeBg = Color(0x28, 0x2C, 0x34)
        val settings = QuickLaunchTerminalSettings(dark = true, initialBg = Color(0x11, 0x22, 0x33))
        settings.applyThemeColors(fg = null, bg = themeBg)
        settings.applyColors(fg = null, bg = null)
        val bg = settings.getDefaultStyle().background?.toAwtColor()
        // Should fall back to theme color, not hardcoded default
        assertNotNull(bg)
        assertEquals(themeBg.red,   bg!!.red)
        assertEquals(themeBg.green, bg.green)
        assertEquals(themeBg.blue,  bg.blue)
    }

    @Test
    fun `applyColors with null restores hardcoded default when no theme color`() {
        val settings = QuickLaunchTerminalSettings(dark = true, initialBg = Color(0x11, 0x22, 0x33))
        settings.applyColors(fg = null, bg = null)
        val bg = settings.getDefaultStyle().background?.toAwtColor()
        assertNotNull(bg)
        assertEquals(0x1E, bg!!.red)
    }

    @Test
    fun `applyDark does not reset custom colors`() {
        val customBg = Color(0x20, 0x20, 0x40)
        val settings = QuickLaunchTerminalSettings(dark = true, initialBg = customBg)
        settings.applyDark(false)   // switch to light theme
        val bg = settings.getDefaultStyle().background?.toAwtColor()
        // Custom color should still win over the light-theme default
        assertNotNull(bg)
        assertEquals(customBg.red,   bg!!.red)
        assertEquals(customBg.green, bg.green)
        assertEquals(customBg.blue,  bg.blue)
    }

    // ── applyThemeColors ──────────────────────────────────────────────────────

    @Test
    fun `applyThemeColors is used when no manual override`() {
        val themeBg = Color(0x1A, 0x1B, 0x26)
        val themeFg = Color(0xC0, 0xCA, 0xF5)
        val settings = QuickLaunchTerminalSettings(dark = true)
        settings.applyThemeColors(fg = themeFg, bg = themeBg)
        val style = settings.getDefaultStyle()
        assertEquals(themeBg.red,   style.background!!.toAwtColor().red)
        assertEquals(themeFg.red,   style.foreground!!.toAwtColor().red)
    }

    @Test
    fun `manual override takes priority over theme colors`() {
        val themeBg  = Color(0x1A, 0x1B, 0x26)
        val customBg = Color(0xFF, 0x00, 0x00)
        val settings = QuickLaunchTerminalSettings(dark = true)
        settings.applyThemeColors(fg = null, bg = themeBg)
        settings.applyColors(fg = null, bg = customBg)
        val bg = settings.getDefaultStyle().background?.toAwtColor()
        assertNotNull(bg)
        assertEquals(customBg.red, bg!!.red)
    }

    @Test
    fun `applyThemeColors null falls back to hardcoded default`() {
        val settings = QuickLaunchTerminalSettings(dark = true)
        settings.applyThemeColors(fg = null, bg = null)
        val bg = settings.getDefaultStyle().background?.toAwtColor()
        assertNotNull(bg)
        assertEquals(0x1E, bg!!.red)
    }
}

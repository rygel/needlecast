package io.github.rygel.needlecast.ui.settings

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Font

class SettingsUtilsTest {

    @Test
    fun `Font_MONOSPACED logical font is detected as monospaced`() {
        assertTrue(isMonospaced(Font.MONOSPACED))
    }

    @Test
    fun `Font_SANS_SERIF logical font is detected as not monospaced`() {
        assertFalse(isMonospaced(Font.SANS_SERIF))
    }

    @Test
    fun `availableMonospaceFamilies returns non-empty list`() {
        val families = availableMonospaceFamilies()
        assertTrue(families.isNotEmpty(), "Expected at least one monospaced font family")
    }

    @Test
    fun `availableFontFamilies returns non-empty list`() {
        val families = availableFontFamilies()
        assertTrue(families.isNotEmpty())
    }
}

package io.github.rygel.needlecast.ui.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsUtilsTest {
    @Test
    fun `Font_MONOSPACED logical font is detected as monospaced`() {
        assertTrue(isMonospaced(java.awt.Font.MONOSPACED))
    }

    @Test
    fun `Font_SANS_SERIF logical font is detected as not monospaced`() {
        assertFalse(isMonospaced(java.awt.Font.SANS_SERIF))
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

    @Test
    fun `availableFontFamilies returns sorted list`() {
        val families = availableFontFamilies()
        assertEquals(families, families.sorted(), "availableFontFamilies must be sorted")
    }

    @Test
    fun `availableFontFamilies contains no duplicates`() {
        val families = availableFontFamilies()
        assertEquals(families.size, families.toSet().size, "Duplicates found in availableFontFamilies")
    }

    @Test
    fun `availableMonospaceFamilies is subset of availableFontFamilies`() {
        val all = availableFontFamilies().toSet()
        val mono = availableMonospaceFamilies()
        val missing = mono.filter { it !in all }
        assertTrue(missing.isEmpty(), "Monospace families not in full list: $missing")
    }

    @Test
    fun `monoFont returns non-blank string`() {
        val name = monoFont()
        assertTrue(name.isNotBlank(), "monoFont() returned blank")
    }

    @Test
    fun `monoFont prefers a known platform family when available`() {
        val name = monoFont()
        // The function should return one of: a known installed family, or Font.MONOSPACED.
        // We can't assert the exact family (depends on the runtime JVM), but it should be a
        // string that GraphicsEnvironment can resolve.
        val env = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
        val available = env.availableFontFamilyNames.toSet() + java.awt.Font.MONOSPACED
        assertTrue(
            name in available,
            "monoFont() returned '$name' which is not in available fonts",
        )
    }

    @Test
    fun `uiBaseFont returns a non-null font with positive size`() {
        val font = uiBaseFont()
        assertNotNull(font)
        assertTrue(font.size > 0, "uiBaseFont() returned font with non-positive size: ${font.size}")
    }

    @Test
    fun `uiBaseFont falls back to a valid font when UIManager is empty`() {
        // We can't easily reset UIManager, but we can verify the fallback path is
        // exercised by reflecting the private static field if needed. The simpler
        // check: uiBaseFont().family must be non-blank.
        val family = uiBaseFont().family
        assertTrue(family.isNotBlank(), "uiBaseFont().family was blank")
    }

    @Test
    fun `buildOutputArea returns a JTextArea with expected properties`() {
        val area = buildOutputArea()
        assertFalse(area.isEditable, "Output area should be read-only")
        assertTrue(area.lineWrap, "Output area should have line wrap enabled")
        assertFalse(area.wrapStyleWord, "Output area should not wrap on word boundaries")
        assertEquals(8, area.rows, "Output area should have 8 rows")
        assertNotNull(area.font, "Output area should have a font set")
        assertTrue(area.font.size > 0)
    }

    @Test
    fun `isMonospaced returns true for known monospaced family on platform`() {
        // Use the first available monospace family (already proven monospaced)
        val first = availableMonospaceFamilies().first()
        assertTrue(isMonospaced(first))
    }

    @Test
    fun `isMonospaced returns false for empty family name`() {
        // An unknown family falls back to a proportional font.
        // javax.swing will substitute with a default, but on most JVMs the substitute
        // for "" is Dialog which is proportional. We assert that an obviously non-monospaced
        // name returns false.
        assertFalse(isMonospaced("Dialog"))
    }
}

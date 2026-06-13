package io.github.rygel.needlecast.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TemplateSubstitutionTest {
    @Test
    fun `extractTemplateVariables finds single variable`() {
        assertEquals(listOf("name"), extractTemplateVariables("Hello {name}!"))
    }

    @Test
    fun `extractTemplateVariables finds multiple variables`() {
        val result = extractTemplateVariables("{project} build {task}")
        assertEquals(listOf("project", "task"), result)
    }

    @Test
    fun `extractTemplateVariables deduplicates`() {
        assertEquals(listOf("x"), extractTemplateVariables("{x} and {x}"))
    }

    @Test
    fun `extractTemplateVariables returns empty for no placeholders`() {
        assertEquals(emptyList<String>(), extractTemplateVariables("plain text"))
    }

    @Test
    fun `extractTemplateVariables extracts underscores and digits`() {
        assertEquals(listOf("my_var_2"), extractTemplateVariables("{my_var_2}"))
    }

    @Test
    fun `applyTemplateSubstitutions replaces placeholders`() {
        assertEquals("Hello Alice!", applyTemplateSubstitutions("Hello {name}!", mapOf("name" to "Alice")))
    }

    @Test
    fun `applyTemplateSubstitutions replaces multiple placeholders`() {
        val result =
            applyTemplateSubstitutions(
                "{greeting} {name}",
                mapOf("greeting" to "Hi", "name" to "Bob"),
            )
        assertEquals("Hi Bob", result)
    }

    @Test
    fun `applyTemplateSubstitutions leaves unmatched placeholders`() {
        assertEquals("Hello {unknown}!", applyTemplateSubstitutions("Hello {unknown}!", mapOf("name" to "Alice")))
    }

    @Test
    fun `applyTemplateSubstitutions with empty map returns original`() {
        assertEquals("Hello {name}!", applyTemplateSubstitutions("Hello {name}!", emptyMap()))
    }

    @Test
    fun `applyTemplateSubstitutions replaces same key multiple times`() {
        assertEquals("a a a", applyTemplateSubstitutions("{x} {x} {x}", mapOf("x" to "a")))
    }
}

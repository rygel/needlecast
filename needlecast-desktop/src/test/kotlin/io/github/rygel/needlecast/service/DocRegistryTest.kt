package io.github.rygel.needlecast.service

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.DocCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DocRegistryTest {

    @Test
    fun `targetsFor empty set returns empty list`() {
        val result = DocRegistry.targetsFor(emptySet())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `targetsFor maven returns exactly 5 entries`() {
        val result = DocRegistry.targetsFor(setOf(BuildTool.MAVEN))
        assertEquals(5, result.size)
    }

    @Test
    fun `maven api docs paths are correct`() {
        val result = DocRegistry.targetsFor(setOf(BuildTool.MAVEN))
        val apiDocs = result.filter { it.category == DocCategory.API_DOCS }
        assertTrue(apiDocs.any { it.relativePath == "target/site/apidocs/index.html" })
        assertTrue(apiDocs.any { it.relativePath == "target/site/testapidocs/index.html" })
    }

    @Test
    fun `maven has one coverage and one test-reports and one site entry`() {
        val result = DocRegistry.targetsFor(setOf(BuildTool.MAVEN))
        assertEquals(1, result.count { it.category == DocCategory.COVERAGE })
        assertEquals(1, result.count { it.category == DocCategory.TEST_REPORTS })
        assertEquals(1, result.count { it.category == DocCategory.SITE })
    }

    @Test
    fun `gradle returns exactly 6 entries`() {
        val result = DocRegistry.targetsFor(setOf(BuildTool.GRADLE))
        assertEquals(6, result.size)
    }

    @Test
    fun `cargo returns exactly 1 entry pointing to target dot doc`() {
        val result = DocRegistry.targetsFor(setOf(BuildTool.CARGO))
        assertEquals(1, result.size)
        assertEquals("target/doc/index.html", result[0].relativePath)
        assertEquals(DocCategory.API_DOCS, result[0].category)
    }

    @Test
    fun `mix returns exactly 1 ExDoc entry`() {
        val result = DocRegistry.targetsFor(setOf(BuildTool.MIX))
        assertEquals(1, result.size)
        assertEquals("doc/index.html", result[0].relativePath)
        assertEquals("ExDoc", result[0].label)
    }

    @Test
    fun `each documented build tool has at least one target`() {
        val documentedTools = setOf(
            BuildTool.MAVEN, BuildTool.GRADLE, BuildTool.NPM, BuildTool.CARGO,
            BuildTool.UV, BuildTool.POETRY, BuildTool.PIP,
            BuildTool.MIX, BuildTool.SBT, BuildTool.BUNDLER, BuildTool.COMPOSER,
            BuildTool.PUB, BuildTool.FLUTTER, BuildTool.SPM,
            BuildTool.CMAKE, BuildTool.MAKE, BuildTool.DOTNET,
        )
        for (tool in documentedTools) {
            val result = DocRegistry.targetsFor(setOf(tool))
            assertTrue(result.isNotEmpty(), "$tool should have at least one doc target")
        }
    }

    @Test
    fun `undocumented build tools return empty list`() {
        val documentedTools = setOf(
            BuildTool.MAVEN, BuildTool.GRADLE, BuildTool.NPM, BuildTool.CARGO,
            BuildTool.UV, BuildTool.POETRY, BuildTool.PIP,
            BuildTool.MIX, BuildTool.SBT, BuildTool.BUNDLER, BuildTool.COMPOSER,
            BuildTool.PUB, BuildTool.FLUTTER, BuildTool.SPM,
            BuildTool.CMAKE, BuildTool.MAKE, BuildTool.DOTNET,
        )
        val undocumentedTools = BuildTool.entries.filter { it !in documentedTools }
        for (tool in undocumentedTools) {
            val result = DocRegistry.targetsFor(setOf(tool))
            assertTrue(result.isEmpty(), "$tool should have no doc targets")
        }
    }

    @Test
    fun `multiple build tools returns union of targets`() {
        val maven  = DocRegistry.targetsFor(setOf(BuildTool.MAVEN)).size
        val gradle = DocRegistry.targetsFor(setOf(BuildTool.GRADLE)).size
        val both   = DocRegistry.targetsFor(setOf(BuildTool.MAVEN, BuildTool.GRADLE)).size
        assertEquals(maven + gradle, both)
    }
}

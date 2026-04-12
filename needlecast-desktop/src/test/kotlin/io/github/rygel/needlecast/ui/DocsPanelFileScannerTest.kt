package io.github.rygel.needlecast.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class DocsPanelFileScannerTest {

    @Test
    fun `returns empty list when directory has no md files`(@TempDir dir: Path) {
        File(dir.toFile(), "build.gradle").writeText("// nothing")
        assertEquals(emptyList<String>(), DocsPanel.collectMarkdownFiles(dir.toFile()))
    }

    @Test
    fun `README md is pinned first regardless of case`(@TempDir dir: Path) {
        val root = dir.toFile()
        File(root, "CHANGELOG.md").writeText("")
        File(root, "readme.md").writeText("")
        File(root, "ARCH.md").writeText("")
        val result = DocsPanel.collectMarkdownFiles(root)
        assertEquals("readme.md", result.first())
    }

    @Test
    fun `remaining files are sorted alphabetically by relative path`(@TempDir dir: Path) {
        val root = dir.toFile()
        File(root, "docs").mkdirs()
        File(root, "docs/GUIDE.md").writeText("")
        File(root, "docs/ARCH.md").writeText("")
        File(root, "CHANGELOG.md").writeText("")
        val result = DocsPanel.collectMarkdownFiles(root)
        assertEquals(listOf("CHANGELOG.md", "docs/ARCH.md", "docs/GUIDE.md"), result)
    }

    @Test
    fun `skips dot-git and build directories`(@TempDir dir: Path) {
        val root = dir.toFile()
        File(root, ".git/objects").mkdirs()
        File(root, ".git/objects/packed.md").writeText("")
        File(root, "target").mkdirs()
        File(root, "target/classes.md").writeText("")
        File(root, "node_modules").mkdirs()
        File(root, "node_modules/readme.md").writeText("")
        File(root, "build").mkdirs()
        File(root, "build/output.md").writeText("")
        File(root, ".gradle").mkdirs()
        File(root, ".gradle/config.md").writeText("")
        File(root, "REAL.md").writeText("")
        assertEquals(listOf("REAL.md"), DocsPanel.collectMarkdownFiles(root))
    }

    @Test
    fun `returns empty list when path is null`() {
        assertEquals(emptyList<String>(), DocsPanel.collectMarkdownFiles(null))
    }
}

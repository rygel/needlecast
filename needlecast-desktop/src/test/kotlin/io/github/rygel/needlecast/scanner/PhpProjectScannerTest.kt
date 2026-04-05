package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.ProjectDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class PhpProjectScannerTest {

    private val scanner = PhpProjectScanner()

    @Test
    fun `returns null when no composer_json`(@TempDir dir: Path) {
        assertNull(scanner.scan(ProjectDirectory(dir.toString())))
    }

    @Test
    fun `detects php project`(@TempDir dir: Path) {
        File(dir.toFile(), "composer.json").writeText("""{"name":"test/app"}""")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertEquals(setOf(BuildTool.COMPOSER), result.buildTools)
        assertTrue(result.commands.any { it.label == "composer install" })
    }

    @Test
    fun `extracts scripts from composer_json`(@TempDir dir: Path) {
        File(dir.toFile(), "composer.json").writeText("""{"scripts":{"test":"phpunit","lint":"phpstan"}}""")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertTrue(result.commands.any { it.label == "composer run test" })
        assertTrue(result.commands.any { it.label == "composer run lint" })
    }

    @Test
    fun `detects laravel project`(@TempDir dir: Path) {
        File(dir.toFile(), "composer.json").writeText("""{"name":"test/app"}""")
        File(dir.toFile(), "artisan").writeText("#!/usr/bin/env php")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertTrue(result.commands.any { it.label == "php artisan serve" })
        assertTrue(result.commands.any { it.label == "php artisan test" })
    }
}

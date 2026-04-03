package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.ProjectDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class MavenProjectScannerTest {

    private val scanner = MavenProjectScanner()

    @Test
    fun `returns null when no pom_xml present`(@TempDir dir: Path) {
        val result = scanner.scan(ProjectDirectory(dir.toString()))
        assertNull(result)
    }

    @Test
    fun `detects Maven project and returns six standard lifecycle commands`(@TempDir dir: Path) {
        File(dir.toFile(), "pom.xml").writeText("<project/>")

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!

        assertEquals(setOf(BuildTool.MAVEN), result.buildTools)
        assertEquals(6, result.commands.size)
        val labels = result.commands.map { it.label }
        assertTrue(labels.contains("mvn clean"))
        assertTrue(labels.contains("mvn verify"))
        assertTrue(labels.contains("mvn install"))
    }

    @Test
    fun `all commands have working directory set to scanned dir`(@TempDir dir: Path) {
        File(dir.toFile(), "pom.xml").writeText("<project/>")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        result.commands.forEach { assertEquals(dir.toString(), it.workingDirectory) }
    }

    @Test
    fun `all commands reference MAVEN build tool`(@TempDir dir: Path) {
        File(dir.toFile(), "pom.xml").writeText("<project/>")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        result.commands.forEach { assertEquals(BuildTool.MAVEN, it.buildTool) }
    }

    @Test
    fun `detects JavaFX plugin and adds javafx run and compile commands`(@TempDir dir: Path) {
        File(dir.toFile(), "pom.xml").writeText("""
            <project>
              <build><plugins>
                <plugin>
                  <groupId>org.openjfx</groupId>
                  <artifactId>javafx-maven-plugin</artifactId>
                </plugin>
              </plugins></build>
            </project>
        """.trimIndent())

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        val labels = result.commands.map { it.label }

        assertTrue(labels.contains("mvn javafx:run"),     "Expected mvn javafx:run, got: $labels")
        assertTrue(labels.contains("mvn javafx:compile"), "Expected mvn javafx:compile, got: $labels")
    }

    @Test
    fun `adds javafx jlink command only when pom mentions jlink`(@TempDir dir: Path) {
        val pomWithJlink = """
            <project>
              <groupId>org.openjfx</groupId>
              <build><plugins>
                <plugin><groupId>org.openjfx</groupId></plugin>
              </plugins></build>
              <!-- jlink configuration here -->
            </project>
        """.trimIndent()
        val pomWithout = "<project><groupId>org.openjfx</groupId></project>"

        File(dir.toFile(), "pom.xml").writeText(pomWithJlink)
        val withJlink = scanner.scan(ProjectDirectory(dir.toString()))!!.commands.map { it.label }
        assertTrue(withJlink.contains("mvn javafx:jlink"), "Expected jlink when pom mentions jlink")

        File(dir.toFile(), "pom.xml").writeText(pomWithout)
        val withoutJlink = scanner.scan(ProjectDirectory(dir.toString()))!!.commands.map { it.label }
        assertFalse(withoutJlink.contains("mvn javafx:jlink"), "Expected no jlink when pom has no jlink reference")
    }

    @Test
    fun `detects exec-maven-plugin and adds exec_java command`(@TempDir dir: Path) {
        File(dir.toFile(), "pom.xml").writeText("""
            <project>
              <build><plugins>
                <plugin>
                  <artifactId>exec-maven-plugin</artifactId>
                </plugin>
              </plugins></build>
            </project>
        """.trimIndent())

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        val labels = result.commands.map { it.label }
        assertTrue(labels.contains("mvn exec:java"), "Expected mvn exec:java, got: $labels")
    }

    @Test
    fun `no extra commands for plain pom without plugins`(@TempDir dir: Path) {
        File(dir.toFile(), "pom.xml").writeText("<project/>")
        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertEquals(6, result.commands.size, "Plain pom should produce exactly 6 lifecycle commands")
    }
}

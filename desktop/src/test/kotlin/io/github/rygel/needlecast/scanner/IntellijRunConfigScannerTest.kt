package io.github.rygel.needlecast.scanner

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.ProjectDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class IntellijRunConfigScannerTest {

    private val scanner = IntellijRunConfigScanner()

    @Test
    fun `returns null when no run config directories present`(@TempDir dir: Path) {
        val result = scanner.scan(ProjectDirectory(dir.toString()))
        assertNull(result)
    }

    @Test
    fun `detects application run config from _idea_runConfigurations`(@TempDir dir: Path) {
        val configDir = File(dir.toFile(), ".idea/runConfigurations")
        configDir.mkdirs()
        File(configDir, "MyApp.xml").writeText("""
            <component name="ProjectRunConfigurationManager">
              <configuration default="false" name="MyApp" type="Application" factoryName="Application">
                <option name="MAIN_CLASS_NAME" value="com.example.Main"/>
                <option name="VM_PARAMETERS" value="-Xmx512m"/>
              </configuration>
            </component>
        """.trimIndent())

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!

        assertEquals(setOf(BuildTool.INTELLIJ_RUN), result.buildTools)
        assertEquals(1, result.commands.size)
        assertEquals("MyApp", result.commands[0].label)
        assertTrue(result.commands[0].argv.contains("com.example.Main"))
        assertTrue(result.commands[0].isSupported)
    }

    @Test
    fun `detects run config from _run directory`(@TempDir dir: Path) {
        val runDir = File(dir.toFile(), ".run")
        runDir.mkdirs()
        File(runDir, "Server.xml").writeText("""
            <component name="ProjectRunConfigurationManager">
              <configuration name="Server" type="Application">
                <option name="MAIN_CLASS_NAME" value="com.example.Server"/>
              </configuration>
            </component>
        """.trimIndent())

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertEquals(1, result.commands.size)
        assertEquals("Server", result.commands[0].label)
    }

    @Test
    fun `marks unsupported run config types as not supported`(@TempDir dir: Path) {
        val configDir = File(dir.toFile(), ".idea/runConfigurations")
        configDir.mkdirs()
        File(configDir, "Docker.xml").writeText("""
            <component name="ProjectRunConfigurationManager">
              <configuration name="Docker Compose" type="docker-deploy">
              </configuration>
            </component>
        """.trimIndent())

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertFalse(result.commands[0].isSupported)
    }

    @Test
    fun `collects multiple run configs`(@TempDir dir: Path) {
        val configDir = File(dir.toFile(), ".idea/runConfigurations")
        configDir.mkdirs()
        listOf("App1", "App2", "App3").forEach { name ->
            File(configDir, "$name.xml").writeText("""
                <component>
                  <configuration name="$name" type="Application">
                    <option name="MAIN_CLASS_NAME" value="com.example.$name"/>
                  </configuration>
                </component>
            """.trimIndent())
        }

        val result = scanner.scan(ProjectDirectory(dir.toString()))!!
        assertEquals(3, result.commands.size)
    }
}

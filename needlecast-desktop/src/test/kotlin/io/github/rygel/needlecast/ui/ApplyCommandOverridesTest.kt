package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.CommandOverride
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApplyCommandOverridesTest {
    private fun cmd(
        label: String,
        argv: List<String>,
    ) = CommandDescriptor(
        label = label,
        buildTool = BuildTool.MAVEN,
        argv = argv,
        workingDirectory = ".",
    )

    @Test
    fun `empty overrides returns original list`() {
        val commands = listOf(cmd("build", listOf("mvn", "package")))
        assertEquals(commands, applyCommandOverrides(commands, emptyList()))
    }

    @Test
    fun `matching override replaces label and argv`() {
        val commands = listOf(cmd("build", listOf("mvn", "package")))
        val overrides =
            listOf(
                CommandOverride(
                    originalArgv = listOf("mvn", "package"),
                    label = "package",
                    argv = listOf("mvn", "package", "-DskipTests"),
                ),
            )
        val result = applyCommandOverrides(commands, overrides)
        assertEquals(1, result.size)
        assertEquals("package", result[0].label)
        assertEquals(listOf("mvn", "package", "-DskipTests"), result[0].argv)
    }

    @Test
    fun `non-matching override leaves command unchanged`() {
        val commands = listOf(cmd("build", listOf("mvn", "package")))
        val overrides =
            listOf(
                CommandOverride(
                    originalArgv = listOf("gradle", "build"),
                    label = "gradle build",
                    argv = listOf("gradle", "build", "-x", "test"),
                ),
            )
        val result = applyCommandOverrides(commands, overrides)
        assertEquals(commands, result)
    }

    @Test
    fun `working directory and build tool are preserved`() {
        val original =
            CommandDescriptor(
                label = "run",
                buildTool = BuildTool.GRADLE,
                argv = listOf("./gradlew", "run"),
                workingDirectory = "subproject",
                env = mapOf("FOO" to "bar"),
            )
        val overrides =
            listOf(
                CommandOverride(
                    originalArgv = listOf("./gradlew", "run"),
                    label = "run app",
                    argv = listOf("./gradlew", "bootRun"),
                ),
            )
        val result = applyCommandOverrides(listOf(original), overrides)
        assertEquals("run app", result[0].label)
        assertEquals(listOf("./gradlew", "bootRun"), result[0].argv)
        assertEquals(BuildTool.GRADLE, result[0].buildTool)
        assertEquals("subproject", result[0].workingDirectory)
        assertEquals(mapOf("FOO" to "bar"), result[0].env)
    }

    @Test
    fun `multiple commands with partial overrides`() {
        val commands =
            listOf(
                cmd("build", listOf("mvn", "package")),
                cmd("test", listOf("mvn", "test")),
            )
        val overrides =
            listOf(
                CommandOverride(
                    originalArgv = listOf("mvn", "test"),
                    label = "fast test",
                    argv = listOf("mvn", "test", "-DskipITs"),
                ),
            )
        val result = applyCommandOverrides(commands, overrides)
        assertEquals("build", result[0].label)
        assertEquals(listOf("mvn", "package"), result[0].argv)
        assertEquals("fast test", result[1].label)
        assertEquals(listOf("mvn", "test", "-DskipITs"), result[1].argv)
    }

    @Test
    fun `duplicate overrides last one wins`() {
        val commands = listOf(cmd("run", listOf("java", "-jar", "app.jar")))
        val overrides =
            listOf(
                CommandOverride(listOf("java", "-jar", "app.jar"), "v1", listOf("java", "-jar", "app-v1.jar")),
                CommandOverride(listOf("java", "-jar", "app.jar"), "v2", listOf("java", "-jar", "app-v2.jar")),
            )
        val result = applyCommandOverrides(commands, overrides)
        assertEquals("v2", result[0].label)
        assertEquals(listOf("java", "-jar", "app-v2.jar"), result[0].argv)
    }
}

package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class BuildCommandsKeyTest {
    private fun project(
        label: String,
        argv: List<String>,
        workingDirectory: String = ".",
    ) = DetectedProject(
        directory = ProjectDirectory("/tmp"),
        buildTools = emptySet(),
        commands =
            listOf(
                CommandDescriptor(
                    label = label,
                    buildTool = BuildTool.MAVEN,
                    argv = argv,
                    workingDirectory = workingDirectory,
                ),
            ),
    )

    @Test
    fun `single command produces expected key`() {
        val p = project("build", listOf("mvn", "package"), ".")
        val key = buildCommandsKey(p)
        assertEquals("build\u0000mvn\u0000package\u0000.", key)
    }

    @Test
    fun `two commands are joined by pipe`() {
        val p =
            DetectedProject(
                directory = ProjectDirectory("/tmp"),
                buildTools = emptySet(),
                commands =
                    listOf(
                        CommandDescriptor("run", BuildTool.GRADLE, listOf("./gradlew", "run"), "."),
                        CommandDescriptor("test", BuildTool.GRADLE, listOf("./gradlew", "test"), "."),
                    ),
            )
        val key = buildCommandsKey(p)
        assertEquals("run\u0000./gradlew\u0000run\u0000.|test\u0000./gradlew\u0000test\u0000.", key)
    }

    @Test
    fun `same commands produce same key`() {
        val p1 = project("build", listOf("mvn", "package"), ".")
        val p2 = project("build", listOf("mvn", "package"), ".")
        assertEquals(buildCommandsKey(p1), buildCommandsKey(p2))
    }

    @Test
    fun `different label produces different key`() {
        val p1 = project("build", listOf("mvn", "package"), ".")
        val p2 = project("compile", listOf("mvn", "package"), ".")
        assertNotEquals(buildCommandsKey(p1), buildCommandsKey(p2))
    }

    @Test
    fun `different argv produces different key`() {
        val p1 = project("build", listOf("mvn", "package"), ".")
        val p2 = project("build", listOf("mvn", "verify"), ".")
        assertNotEquals(buildCommandsKey(p1), buildCommandsKey(p2))
    }

    @Test
    fun `different working directory produces different key`() {
        val p1 = project("build", listOf("mvn", "package"), ".")
        val p2 = project("build", listOf("mvn", "package"), "subdir")
        assertNotEquals(buildCommandsKey(p1), buildCommandsKey(p2))
    }

    @Test
    fun `different command order produces different key`() {
        val p1 =
            DetectedProject(
                directory = ProjectDirectory("/tmp"),
                buildTools = emptySet(),
                commands =
                    listOf(
                        CommandDescriptor("a", BuildTool.MAVEN, listOf("mvn"), "."),
                        CommandDescriptor("b", BuildTool.MAVEN, listOf("mvn"), "."),
                    ),
            )
        val p2 =
            DetectedProject(
                directory = ProjectDirectory("/tmp"),
                buildTools = emptySet(),
                commands =
                    listOf(
                        CommandDescriptor("b", BuildTool.MAVEN, listOf("mvn"), "."),
                        CommandDescriptor("a", BuildTool.MAVEN, listOf("mvn"), "."),
                    ),
            )
        assertNotEquals(buildCommandsKey(p1), buildCommandsKey(p2))
    }

    @Test
    fun `empty commands produces empty key`() {
        val p =
            DetectedProject(
                directory = ProjectDirectory("/tmp"),
                buildTools = emptySet(),
                commands = emptyList(),
            )
        assertEquals("", buildCommandsKey(p))
    }

    @Test
    fun `argv elements are separated by null char`() {
        val p = project("run", listOf("npm", "start", "--verbose"), ".")
        val key = buildCommandsKey(p)
        assertEquals("run\u0000npm\u0000start\u0000--verbose\u0000.", key)
    }
}

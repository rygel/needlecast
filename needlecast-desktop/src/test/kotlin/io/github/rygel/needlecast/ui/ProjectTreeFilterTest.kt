package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.model.ProjectTreeEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.system.measureNanoTime

class ProjectTreeFilterTest {
    private fun project(
        name: String,
        vararg tags: String,
    ) = ProjectTreeEntry.Project(
        directory = ProjectDirectory(path = "/some/$name", displayName = name),
        tags = tags.toList(),
    )

    private fun folder(
        name: String,
        vararg children: ProjectTreeEntry,
    ) = ProjectTreeEntry.Folder(name = name, children = children.toList())

    @Test
    fun `matches project by display name`() {
        assertTrue(ProjectTreeFilter.matches(project("MyProject"), "myproject"))
    }

    @Test
    fun `matches project by partial display name`() {
        assertTrue(ProjectTreeFilter.matches(project("MyProject"), "proj"))
    }

    @Test
    fun `matches project by tag`() {
        assertTrue(ProjectTreeFilter.matches(project("P", "java", "web"), "web"))
    }

    @Test
    fun `does not match non-matching project`() {
        assertFalse(ProjectTreeFilter.matches(project("Alpha"), "beta"))
    }

    @Test
    fun `matches folder when any child matches`() {
        val f = folder("Work", project("Alpha"), project("Beta"))
        assertTrue(ProjectTreeFilter.matches(f, "beta"))
    }

    @Test
    fun `does not match folder when no child matches`() {
        val f = folder("Work", project("Alpha"), project("Beta"))
        assertFalse(ProjectTreeFilter.matches(f, "gamma"))
    }

    @Test
    fun `filterTree returns all entries for empty filter`() {
        val entries = listOf(project("A"), project("B"))
        assertEquals(entries, ProjectTreeFilter.filterTree(entries, ""))
    }

    @Test
    fun `filterTree returns all entries for blank filter`() {
        val entries = listOf(project("A"), project("B"))
        assertEquals(entries, ProjectTreeFilter.filterTree(entries, "  "))
    }

    @Test
    fun `filterTree returns only matching projects`() {
        val entries = listOf(project("Alpha"), project("Beta"))
        val result = ProjectTreeFilter.filterTree(entries, "alpha")
        assertEquals(1, result.size)
        assertEquals("Alpha", (result[0] as ProjectTreeEntry.Project).directory.displayName)
    }

    @Test
    fun `filterTree preserves folder with matching child`() {
        val entries = listOf(folder("Work", project("Alpha"), project("Beta")))
        val result = ProjectTreeFilter.filterTree(entries, "alpha")
        assertEquals(1, result.size)
        val folder = result[0] as ProjectTreeEntry.Folder
        assertEquals(1, folder.children.size)
        assertEquals("Alpha", (folder.children[0] as ProjectTreeEntry.Project).directory.displayName)
    }

    @Test
    fun `filterTree removes empty folders`() {
        val entries = listOf(folder("Empty", project("Alpha")), project("Beta"))
        val result = ProjectTreeFilter.filterTree(entries, "beta")
        assertEquals(1, result.size)
        assertTrue(result[0] is ProjectTreeEntry.Project)
    }

    @Test
    fun `filterTree matches by tag`() {
        val entries = listOf(project("Alpha", "java"), project("Beta", "python"))
        val result = ProjectTreeFilter.filterTree(entries, "python")
        assertEquals(1, result.size)
        assertEquals("Beta", (result[0] as ProjectTreeEntry.Project).directory.displayName)
    }

    @Test
    fun `filterTree matches by partial name`() {
        val entries = listOf(project("AlphaCentauri"), project("Beta"))
        val result = ProjectTreeFilter.filterTree(entries, "alpha")
        assertEquals(1, result.size)
        assertEquals("AlphaCentauri", (result[0] as ProjectTreeEntry.Project).directory.displayName)
    }

    @Test
    fun `filterTree is case insensitive`() {
        val entries = listOf(project("MyProject"))
        val result = ProjectTreeFilter.filterTree(entries, "MYPROJECT")
        assertEquals(1, result.size)
    }

    @Test
    fun `perf test with 100+ synthetic nodes completes quickly`() {
        val entries =
            (1..100).map { i ->
                folder(
                    name = "Folder$i",
                    *(1..3)
                        .map { j ->
                            project(
                                name = "Project-$i-$j",
                                if (j % 2 == 0) "even" else "odd",
                            )
                        }.toTypedArray(),
                )
            }

        val elapsed =
            measureNanoTime {
                repeat(5) {
                    ProjectTreeFilter.filterTree(entries, "even")
                    ProjectTreeFilter.filterTree(entries, "Project-5")
                    ProjectTreeFilter.filterTree(entries, "nonexistent")
                }
            }

        val ms = elapsed / 1_000_000.0
        assertTrue(ms < 500.0) { "Perf test took ${ms}ms (expected <500ms for 15 filter operations on 300 nodes)" }
    }
}

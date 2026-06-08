package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.model.ProjectTreeEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProjectTreeFilterTest {

    private fun project(name: String, path: String, tags: List<String> = emptyList()) =
        ProjectTreeEntry.Project(directory = ProjectDirectory(path = path, displayName = name), tags = tags)

    private fun folder(name: String, children: List<ProjectTreeEntry>) =
        ProjectTreeEntry.Folder(name = name, children = children)

    @Test
    fun `empty query returns entries unchanged`() {
        val entries = listOf(project("foo", "/p/foo"), project("bar", "/p/bar"))
        assertThat(ProjectTreeFilter.filter(entries, ""))
            .isEqualTo(entries)
        assertThat(ProjectTreeFilter.filter(entries, "   "))
            .isEqualTo(entries)
    }

    @Test
    fun `matches by display name (case-insensitive)`() {
        val needlecast = project("Needlecast", "/p/needlecast")
        val otherProj = project("Other", "/p/other")
        val result = ProjectTreeFilter.filter(listOf(needlecast, otherProj), "needle")
        assertThat(result.map { (it as ProjectTreeEntry.Project).directory.label() })
            .containsExactly("Needlecast")
    }

    @Test
    fun `matches by directory path`() {
        val result = ProjectTreeFilter.filter(
            listOf(project("foo", "/home/user/projects/needlecast")),
            "needlecast",
        )
        assertThat(result).hasSize(1)
    }

    @Test
    fun `matches by tag`() {
        val result = ProjectTreeFilter.filter(
            listOf(
                project("foo", "/p/foo", tags = listOf("kotlin", "jvm")),
                project("bar", "/p/bar", tags = listOf("python")),
            ),
            "kot",
        )
        assertThat(result).hasSize(1)
        assertThat((result.first() as ProjectTreeEntry.Project).directory.label()).isEqualTo("foo")
    }

    @Test
    fun `folder is kept when at least one child matches`() {
        val folder = folder(
            "projects",
            listOf(
                project("a", "/p/a"),
                project("needlecast", "/p/needlecast"),
            ),
        )
        val result = ProjectTreeFilter.filter(listOf(folder), "needle")
        assertThat(result).hasSize(1)
        assertThat(result.first()).isInstanceOf(ProjectTreeEntry.Folder::class.java)
        val kept = result.first() as ProjectTreeEntry.Folder
        assertThat(kept.name).isEqualTo("projects")
        assertThat(kept.children).hasSize(1)
    }

    @Test
    fun `folder is removed when no children match`() {
        val folder = folder("projects", listOf(project("a", "/p/a"), project("b", "/p/b")))
        val result = ProjectTreeFilter.filter(listOf(folder), "needlecast")
        assertThat(result).isEmpty()
    }

    @Test
    fun `nested folders prune empty branches`() {
        val root = folder(
            "root",
            listOf(
                folder("a", listOf(project("apple", "/p/apple"))),
                folder("b", listOf(project("banana", "/p/banana"))),
            ),
        )
        val result = ProjectTreeFilter.filter(listOf(root), "app")
        assertThat(result).hasSize(1)
        val keptRoot = result.first() as ProjectTreeEntry.Folder
        assertThat(keptRoot.children.map { (it as ProjectTreeEntry.Folder).name }).containsExactly("a")
    }

    @Test
    fun `no match returns empty list`() {
        val result = ProjectTreeFilter.filter(
            listOf(project("foo", "/p/foo")),
            "needlecast",
        )
        assertThat(result).isEmpty()
    }

    @Test
    fun `matches helper returns true for empty query`() {
        val entry = project("foo", "/p/foo")
        assertThat(ProjectTreeFilter.matches(entry, "")).isTrue()
        assertThat(ProjectTreeFilter.matches(entry, "   ")).isTrue()
    }

    @Test
    fun `matches helper returns true when project matches`() {
        val entry = project("needlecast", "/p/needlecast", tags = listOf("kotlin"))
        assertThat(ProjectTreeFilter.matches(entry, "needle")).isTrue()
        assertThat(ProjectTreeFilter.matches(entry, "NEEDLE")).isTrue()
        assertThat(ProjectTreeFilter.matches(entry, "/p/needle")).isTrue()
        assertThat(ProjectTreeFilter.matches(entry, "kot")).isTrue()
    }

    @Test
    fun `matches helper returns true when nested child matches`() {
        val folder = folder("root", listOf(project("needlecast", "/p/n")))
        assertThat(ProjectTreeFilter.matches(folder, "needle")).isTrue()
    }

    @Test
    fun `matches helper returns false when nothing matches`() {
        val entry = project("foo", "/p/foo")
        assertThat(ProjectTreeFilter.matches(entry, "bar")).isFalse()
    }
}

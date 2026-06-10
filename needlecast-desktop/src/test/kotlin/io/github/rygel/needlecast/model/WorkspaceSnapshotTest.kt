package io.github.rygel.needlecast.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WorkspaceSnapshotTest {
    @Test
    fun `toWorkspaceSnapshot preserves workspace fields`() {
        val config =
            AppConfig(
                groups = listOf(ProjectGroup(id = "g1", name = "Work")),
                projectTree = listOf(ProjectTreeEntry.Folder("folder-1", "My Folder")),
                lastSelectedGroupId = "g1",
                lastSelectedProjectPath = "/home/user/project",
            )
        val snapshot = config.toWorkspaceSnapshot()
        assertEquals(1, snapshot.groups.size)
        assertEquals("Work", snapshot.groups[0].name)
        assertEquals(1, snapshot.projectTree.size)
        assertEquals("g1", snapshot.lastSelectedGroupId)
        assertEquals("/home/user/project", snapshot.lastSelectedProjectPath)
    }

    @Test
    fun `withWorkspaceSnapshot restores workspace fields`() {
        val snapshot =
            WorkspaceSnapshot(
                groups = listOf(ProjectGroup(id = "g2", name = "Personal")),
                projectTree = emptyList(),
                lastSelectedGroupId = "g2",
                lastSelectedProjectPath = null,
            )
        val config = AppConfig().withWorkspaceSnapshot(snapshot)
        assertEquals("g2", config.lastSelectedGroupId)
        assertNull(config.lastSelectedProjectPath)
        assertEquals(1, config.groups.size)
    }

    @Test
    fun `round-trip preserves all workspace fields`() {
        val original =
            AppConfig(
                groups =
                    listOf(
                        ProjectGroup(id = "a", name = "A"),
                        ProjectGroup(id = "b", name = "B"),
                    ),
                projectTree =
                    listOf(
                        ProjectTreeEntry.Folder("f1", "Folder"),
                        ProjectTreeEntry.Project(directory = ProjectDirectory("/path/to/proj")),
                    ),
                lastSelectedGroupId = "a",
                lastSelectedProjectPath = "/path/to/proj",
            )
        val restored = original.toWorkspaceSnapshot().let { original.withWorkspaceSnapshot(it) }
        assertEquals(original.groups, restored.groups)
        assertEquals(original.projectTree, restored.projectTree)
        assertEquals(original.lastSelectedGroupId, restored.lastSelectedGroupId)
        assertEquals(original.lastSelectedProjectPath, restored.lastSelectedProjectPath)
    }

    @Test
    fun `round-trip does not alter non-workspace fields`() {
        val original =
            AppConfig(
                configVersion = 7,
                theme = "dark-purple",
                windowWidth = 1400,
                windowHeight = 900,
                showConsole = false,
            )
        val restored = original.toWorkspaceSnapshot().let { original.withWorkspaceSnapshot(it) }
        assertEquals(7, restored.configVersion)
        assertEquals("dark-purple", restored.theme)
        assertEquals(1400, restored.windowWidth)
        assertEquals(900, restored.windowHeight)
        assertEquals(false, restored.showConsole)
    }

    @Test
    fun `empty config round-trips cleanly`() {
        val original = AppConfig()
        val restored = original.toWorkspaceSnapshot().let { original.withWorkspaceSnapshot(it) }
        assertEquals(original, restored)
    }
}

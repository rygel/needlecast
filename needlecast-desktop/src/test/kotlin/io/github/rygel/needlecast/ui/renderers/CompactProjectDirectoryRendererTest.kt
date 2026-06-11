package io.github.rygel.needlecast.ui.renderers

import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.GitStatus
import io.github.rygel.needlecast.model.ProjectDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.swing.JList

class CompactProjectDirectoryRendererTest {
    private val list = JList<DetectedProject>()

    private fun makeProject(
        path: String = "/test",
        label: String = "test-project",
        tools: Set<BuildTool> = emptySet(),
        color: String? = null,
        scanFailed: Boolean = false,
    ) = DetectedProject(
        directory = ProjectDirectory(path = path, displayName = label, color = color),
        buildTools = tools,
        commands = emptyList<CommandDescriptor>(),
        scanFailed = scanFailed,
    )

    private fun render(
        renderer: CompactProjectDirectoryRenderer,
        value: DetectedProject?,
    ) = renderer.getListCellRendererComponent(list, value, 0, false, false)

    @Test
    fun `renders project name`() {
        val renderer =
            CompactProjectDirectoryRenderer(
                activePathsProvider = { emptySet() },
                gitStatusProvider = { null },
            )
        val project = makeProject()
        val c = render(renderer, project)
        assertNotNull(c)
    }

    @Test
    fun `active dot visible when path in activePaths`() {
        val renderer =
            CompactProjectDirectoryRenderer(
                activePathsProvider = { setOf("/test") },
                gitStatusProvider = { null },
            )
        render(renderer, makeProject())
        assertTrue(renderer.activeDotVisible)
    }

    @Test
    fun `active dot hidden when path not in activePaths`() {
        val renderer =
            CompactProjectDirectoryRenderer(
                activePathsProvider = { emptySet() },
                gitStatusProvider = { null },
            )
        render(renderer, makeProject())
        assertFalse(renderer.activeDotVisible)
    }

    @Test
    fun `color stripe visible when project has color`() {
        val renderer =
            CompactProjectDirectoryRenderer(
                activePathsProvider = { emptySet() },
                gitStatusProvider = { null },
            )
        render(renderer, makeProject(color = "#FF0000"))
        assertTrue(renderer.colorStripeVisible)
    }

    @Test
    fun `color stripe hidden when project has no color`() {
        val renderer =
            CompactProjectDirectoryRenderer(
                activePathsProvider = { emptySet() },
                gitStatusProvider = { null },
            )
        render(renderer, makeProject())
        assertFalse(renderer.colorStripeVisible)
    }

    @Test
    fun `handles null value without exception`() {
        val renderer =
            CompactProjectDirectoryRenderer(
                activePathsProvider = { emptySet() },
                gitStatusProvider = { null },
            )
        val c = render(renderer, null)
        assertNotNull(c)
    }

    @Test
    fun `scan failed shows tags`() {
        val renderer =
            CompactProjectDirectoryRenderer(
                activePathsProvider = { emptySet() },
                gitStatusProvider = { null },
            )
        render(renderer, makeProject(scanFailed = true))
        assertTrue(renderer.hasTags)
    }

    @Test
    fun `branch shown when git status available`() {
        val renderer =
            CompactProjectDirectoryRenderer(
                activePathsProvider = { emptySet() },
                gitStatusProvider = { GitStatus(branch = "main", isDirty = false) },
            )
        render(renderer, makeProject())
        assertEquals("main", renderer.branchText)
    }
}

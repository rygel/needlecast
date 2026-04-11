package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.config.JsonConfigStore
import io.github.rygel.needlecast.model.AppConfig
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.model.ProjectTreeEntry
import io.github.rygel.needlecast.scanner.ProjectScanner
import io.github.rygel.needlecast.scanner.IS_WINDOWS
import org.assertj.swing.edt.GuiActionRunner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Tests for the external (Finder / Explorer / file-manager) drop code path
 * in [ProjectTreePanel] — the behaviour added in PR #124 / #125.
 *
 * Why not a real drag? A Finder→JVM drop requires a genuine
 * `DropTargetDropEvent`, which can only be produced by the native OS drag
 * session. There is no supported way to synthesise one in-process, and
 * [javax.swing.TransferHandler.TransferSupport]'s public constructor creates
 * only the non-drop (paste) variant. So this test invokes the test-only
 * hook [ProjectTreePanel.simulateExternalDropForTest] which exercises
 * everything downstream of flavor parsing and drop-target resolution:
 * duplicate rejection, tree insertion, folder expansion, loose-file
 * callback, and config persistence.
 *
 * Unlike the other `*UiTest` files in this package this test does not use
 * `Robot` and does not capture mouse/keyboard, but it still constructs a
 * full [ProjectTreePanel] — [javax.swing.JTree.setDragEnabled] throws
 * `HeadlessException`, so the test requires a display.
 *
 * Run via: `mvn verify -Ptest-desktop` (Xvfb inside Dockerfile.uitest).
 */
class ProjectTreePanelExternalDropUiTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var ctx: AppContext
    private var droppedFiles: List<File> = emptyList()

    private val noopScanner = object : ProjectScanner {
        override fun scan(directory: ProjectDirectory): DetectedProject? = null
    }

    @AfterEach
    fun tearDown() {
        if (::ctx.isInitialized) ctx.disposeAll()
    }

    private fun buildPanel(config: AppConfig = AppConfig()): ProjectTreePanel {
        val store = JsonConfigStore(tempDir.resolve("config.json"))
        ctx = AppContext(configStore = store, scanner = noopScanner)
        ctx.updateConfig(config)
        return GuiActionRunner.execute<ProjectTreePanel> {
            ProjectTreePanel(
                ctx = ctx,
                onProjectSelected = {},
                onExternalFilesDropped = { droppedFiles = it },
            )
        }
    }

    private fun newDir(name: String): File =
        Files.createDirectory(tempDir.resolve(name)).toFile()

    private fun newFile(name: String): File =
        Files.createFile(tempDir.resolve(name)).toFile()

    private fun projectPaths(tree: List<ProjectTreeEntry>): List<String> =
        tree.flatMap {
            when (it) {
                is ProjectTreeEntry.Project -> listOf(it.directory.path)
                is ProjectTreeEntry.Folder  -> projectPaths(it.children)
            }
        }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    fun `dropping a single directory at root adds it as a project`() {
        val panel = buildPanel()
        val dir = newDir("alpha")

        val changed = GuiActionRunner.execute<Boolean> {
            panel.simulateExternalDropForTest(listOf(dir))
        }

        assertTrue(changed, "drop should report a change")
        val paths = projectPaths(ctx.config.projectTree)
        assertEquals(listOf(dir.absolutePath), paths)
    }

    @Test
    fun `dropping multiple directories preserves order at root`() {
        val panel = buildPanel()
        val dirs = listOf(newDir("alpha"), newDir("beta"), newDir("gamma"))

        GuiActionRunner.execute<Boolean> { panel.simulateExternalDropForTest(dirs) }

        val paths = projectPaths(ctx.config.projectTree)
        assertEquals(dirs.map { it.absolutePath }, paths)
    }

    @Test
    fun `dropping a directory into an existing folder appends it there`() {
        val existing = newDir("existing")
        val config = AppConfig(
            projectTree = listOf(
                ProjectTreeEntry.Folder(
                    name = "Work",
                    children = listOf(
                        ProjectTreeEntry.Project(directory = ProjectDirectory(path = existing.absolutePath)),
                    ),
                ),
            ),
        )
        val panel = buildPanel(config)
        val dropped = newDir("dropped")

        GuiActionRunner.execute<Boolean> {
            panel.simulateExternalDropForTest(listOf(dropped), targetFolder = "Work")
        }

        val work = ctx.config.projectTree.single() as ProjectTreeEntry.Folder
        assertEquals(
            listOf(existing.absolutePath, dropped.absolutePath),
            work.children.map { (it as ProjectTreeEntry.Project).directory.path },
            "dropped dir should be appended after the existing child",
        )
    }

    @Test
    fun `dropping a directory already in the tree is skipped`() {
        val existing = newDir("existing")
        val fresh = newDir("fresh")
        val config = AppConfig(
            projectTree = listOf(
                ProjectTreeEntry.Project(directory = ProjectDirectory(path = existing.absolutePath)),
            ),
        )
        val panel = buildPanel(config)

        GuiActionRunner.execute<Boolean> {
            panel.simulateExternalDropForTest(listOf(existing, fresh))
        }

        val paths = projectPaths(ctx.config.projectTree)
        assertEquals(
            listOf(existing.absolutePath, fresh.absolutePath),
            paths,
            "existing should stay at index 0, fresh should be appended, duplicate skipped",
        )
    }

    @Test
    fun `dropping loose files invokes onExternalFilesDropped and does not touch the tree`() {
        val panel = buildPanel()
        val f1 = newFile("a.txt")
        val f2 = newFile("b.txt")

        val changed = GuiActionRunner.execute<Boolean> {
            panel.simulateExternalDropForTest(listOf(f1, f2))
        }

        assertTrue(changed, "drop with files-only should still report a change")
        assertEquals(listOf(f1, f2), droppedFiles)
        assertTrue(ctx.config.projectTree.isEmpty(), "loose files should not add tree nodes")
    }

    @Test
    fun `dropping a mix of a directory and a loose file adds the dir and fires the file callback`() {
        val panel = buildPanel()
        val dir = newDir("alpha")
        val file = newFile("loose.txt")

        GuiActionRunner.execute<Boolean> {
            panel.simulateExternalDropForTest(listOf(dir, file))
        }

        assertEquals(listOf(dir.absolutePath), projectPaths(ctx.config.projectTree))
        assertEquals(listOf(file), droppedFiles)
        assertFalse(droppedFiles.isEmpty())
    }

    // ── findMissingMatch tests ───────────────────────────────────────────────

    @Test
    fun `findMissingMatch returns node when name matches missing project`() {
        // Use a path that doesn't exist on disk so it becomes a missing project
        val missingPath = tempDir.resolve("ghost").resolve("myapp").toString()
        val config = AppConfig(
            projectTree = listOf(
                ProjectTreeEntry.Project(directory = ProjectDirectory(path = missingPath))
            )
        )
        val panel = buildPanel(config)

        val match = GuiActionRunner.execute<DefaultMutableTreeNode?> {
            panel.findMissingMatch("myapp")
        }

        assertNotNull(match, "should find the missing node")
        val entry = (match!!.userObject as ProjectTreeEntry.Project)
        assertEquals(missingPath, entry.directory.path)
    }

    @Test
    fun `findMissingMatch returns null when name matches a present project`() {
        val presentDir = newDir("myapp")   // exists on disk
        val config = AppConfig(
            projectTree = listOf(
                ProjectTreeEntry.Project(directory = ProjectDirectory(path = presentDir.absolutePath))
            )
        )
        val panel = buildPanel(config)

        val match = GuiActionRunner.execute<DefaultMutableTreeNode?> {
            panel.findMissingMatch("myapp")
        }

        assertNull(match, "present projects should not be candidates for repair")
    }

    @Test
    fun `findMissingMatch returns null when no project name matches`() {
        val missingPath = tempDir.resolve("ghost").resolve("myapp").toString()
        val config = AppConfig(
            projectTree = listOf(
                ProjectTreeEntry.Project(directory = ProjectDirectory(path = missingPath))
            )
        )
        val panel = buildPanel(config)

        val match = GuiActionRunner.execute<DefaultMutableTreeNode?> {
            panel.findMissingMatch("different-name")
        }

        assertNull(match, "no match expected for unrelated name")
    }

    @Test
    fun `findMissingMatch is case-insensitive on Windows`() {
        assumeTrue(IS_WINDOWS, "Windows-only: case-insensitive name matching")
        val missingPath = tempDir.resolve("ghost").resolve("MyApp").toString()
        val config = AppConfig(
            projectTree = listOf(
                ProjectTreeEntry.Project(directory = ProjectDirectory(path = missingPath))
            )
        )
        val panel = buildPanel(config)

        val match = GuiActionRunner.execute<DefaultMutableTreeNode?> {
            panel.findMissingMatch("myapp")
        }

        assertNotNull(match, "Windows: 'myapp' should match 'MyApp'")
    }

    @Test
    fun `findMissingMatch returns first match in tree order when two missing projects share a name`() {
        val firstPath  = tempDir.resolve("first").resolve("myapp").toString()
        val secondPath = tempDir.resolve("second").resolve("myapp").toString()
        val config = AppConfig(
            projectTree = listOf(
                ProjectTreeEntry.Project(directory = ProjectDirectory(path = firstPath)),
                ProjectTreeEntry.Project(directory = ProjectDirectory(path = secondPath)),
            )
        )
        val panel = buildPanel(config)

        val match = GuiActionRunner.execute<DefaultMutableTreeNode?> {
            panel.findMissingMatch("myapp")
        }

        assertNotNull(match)
        val entry = (match!!.userObject as ProjectTreeEntry.Project)
        assertEquals(firstPath, entry.directory.path, "first entry in tree order should win")
    }
}

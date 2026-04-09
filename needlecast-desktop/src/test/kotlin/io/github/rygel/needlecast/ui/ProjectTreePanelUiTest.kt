package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.config.JsonConfigStore
import io.github.rygel.needlecast.model.AppConfig
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.model.ProjectTreeEntry
import io.github.rygel.needlecast.scanner.ProjectScanner
import org.assertj.swing.core.BasicRobot
import org.assertj.swing.core.MouseButton
import org.assertj.swing.core.Robot
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.edt.GuiQuery
import org.assertj.swing.fixture.FrameFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.nio.file.Path
import javax.swing.JFrame
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

/**
 * End-to-end UI tests for [ProjectTreePanel].
 *
 * Verifies:
 * 1. Projects are visible under folders (regression: zero-width projectPanel)
 * 2. Tags survive drag-and-drop operations
 * 3. Project tree width does not grow on each project add
 * 4. Projects with null displayName show directory name
 *
 * Run via: mvn verify -Ptest-desktop (requires Xvfb — use Dockerfile.uitest).
 */
class ProjectTreePanelUiTest {

    private lateinit var robot: Robot
    private lateinit var fixture: FrameFixture
    private lateinit var ctx: AppContext
    private lateinit var tree: JTree

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        robot = BasicRobot.robotWithNewAwtHierarchy()
        robot.settings().delayBetweenEvents(1)
    }

    @AfterEach
    fun tearDown() {
        fixture.cleanUp()
        robot.cleanUp()
    }

    private fun buildCtxAndPanel(
        config: AppConfig,
        scanner: ProjectScanner = object : ProjectScanner {
            override fun scan(directory: ProjectDirectory): DetectedProject? = null
        },
    ): ProjectTreePanel {
        val store = JsonConfigStore(tempDir.resolve("config.json"))
        ctx = AppContext(configStore = store, scanner = scanner)
        ctx.updateConfig(config)
        return GuiActionRunner.execute<ProjectTreePanel> { ProjectTreePanel(ctx, {}) }
    }

    private fun showInFrame(panel: ProjectTreePanel, width: Int = 400, height: Int = 600): FrameFixture {
        val frame = GuiActionRunner.execute<JFrame> {
            JFrame("ProjectTree Test").apply {
                contentPane.add(panel)
                setSize(width, height)
            }
        }
        val fix = FrameFixture(robot, frame)
        fix.show()
        robot.waitForIdle()
        tree = robot.finder().findByType(panel, JTree::class.java, true)
        return fix
    }

    private fun rowCenter(row: Int): Point? {
        val bounds = GuiActionRunner.execute(object : GuiQuery<Rectangle?>() {
            override fun executeInEDT(): Rectangle? = tree.getRowBounds(row)
        }) ?: return null
        val loc = tree.locationOnScreen
        return Point(loc.x + bounds.centerX.toInt(), loc.y + bounds.centerY.toInt())
    }

    private fun rowTopEdge(row: Int): Point? {
        val bounds = GuiActionRunner.execute(object : GuiQuery<Rectangle?>() {
            override fun executeInEDT(): Rectangle? = tree.getRowBounds(row)
        }) ?: return null
        val loc = tree.locationOnScreen
        return Point(loc.x + bounds.centerX.toInt(), loc.y + bounds.y + (bounds.height * 0.15).toInt())
    }

    private fun dragTo(src: Point, dst: Point) {
        robot.pressMouse(src, MouseButton.LEFT_BUTTON)
        robot.waitForIdle()
        val steps = 8
        val dx = (dst.x - src.x).toDouble() / steps
        val dy = (dst.y - src.y).toDouble() / steps
        for (i in 1..steps) {
            robot.moveMouse(Point(src.x + (dx * i).toInt(), src.y + (dy * i).toInt()))
        }
        robot.moveMouse(dst)
        robot.waitForIdle()
        robot.releaseMouse(MouseButton.LEFT_BUTTON)
        robot.waitForIdle()
    }

    // ── Project visibility ───────────────────────────────────────────────────

    @Test
    fun `projects are visible under folders on initial render`() {
        val projectPath = tempDir.resolve("my-project").also { it.toFile().mkdirs() }.toString()
        val config = AppConfig(
            projectTree = listOf(
                ProjectTreeEntry.Folder(
                    name = "Work",
                    children = listOf(
                        ProjectTreeEntry.Project(
                            directory = ProjectDirectory(path = projectPath, displayName = "My Project"),
                        ),
                    ),
                ),
            ),
        )
        val panel = buildCtxAndPanel(config)
        fixture = showInFrame(panel)

        // Row 0 = Work folder, Row 1 = My Project
        val rowCount = GuiActionRunner.execute(object : GuiQuery<Int>() {
            override fun executeInEDT() = tree.rowCount
        })
        assertEquals(2, rowCount, "Expected folder + project = 2 rows")

        // Project row must have non-zero bounds (visible)
        val bounds = GuiActionRunner.execute(object : GuiQuery<Rectangle?>() {
            override fun executeInEDT() = tree.getRowBounds(1)
        })
        assertNotNull(bounds, "Project row bounds should not be null")
        assertTrue(bounds!!.width > 50, "Project row width should be > 50px, was ${bounds.width}")
        assertTrue(bounds.height > 10, "Project row height should be > 10px, was ${bounds.height}")
    }

    @Test
    fun `project with null displayName shows directory name`() {
        val projectPath = tempDir.resolve("cool-app").also { it.toFile().mkdirs() }.toString()
        val config = AppConfig(
            projectTree = listOf(
                ProjectTreeEntry.Folder(
                    name = "Apps",
                    children = listOf(
                        ProjectTreeEntry.Project(
                            directory = ProjectDirectory(path = projectPath, displayName = null),
                        ),
                    ),
                ),
            ),
        )
        val panel = buildCtxAndPanel(config)
        fixture = showInFrame(panel)

        // Verify the node's userObject has the right label
        val label = GuiActionRunner.execute(object : GuiQuery<String>() {
            override fun executeInEDT(): String {
                val root = tree.model.root as DefaultMutableTreeNode
                val folder = root.getChildAt(0) as DefaultMutableTreeNode
                val project = folder.getChildAt(0) as DefaultMutableTreeNode
                val entry = project.userObject as ProjectTreeEntry.Project
                return entry.directory.label()
            }
        })
        assertEquals("cool-app", label, "Should show directory name when displayName is null")
    }

    // ── Tags preserved after DnD ─────────────────────────────────────────────

    @Test
    fun `tags are preserved after drag-and-drop reorder`() {
        val alphaPath = tempDir.resolve("alpha").also { it.toFile().mkdirs() }.toString()
        val betaPath = tempDir.resolve("beta").also { it.toFile().mkdirs() }.toString()

        val config = AppConfig(
            projectTree = listOf(
                ProjectTreeEntry.Folder(
                    name = "Work",
                    children = listOf(
                        ProjectTreeEntry.Project(
                            directory = ProjectDirectory(path = alphaPath, displayName = "Alpha"),
                            tags = listOf("kotlin", "backend"),
                        ),
                        ProjectTreeEntry.Project(
                            directory = ProjectDirectory(path = betaPath, displayName = "Beta"),
                            tags = listOf("react", "frontend"),
                        ),
                    ),
                ),
            ),
        )
        val panel = buildCtxAndPanel(config)
        fixture = showInFrame(panel)

        // Drag Beta above Alpha
        val src = rowCenter(2) ?: error("Beta row not visible")
        val dst = rowTopEdge(1) ?: error("Alpha row not visible")
        dragTo(src, dst)

        // Verify tags survived
        val folder = ctx.config.projectTree.first() as ProjectTreeEntry.Folder
        assertEquals(2, folder.children.size)

        val projects = folder.children.filterIsInstance<ProjectTreeEntry.Project>()
        val alpha = projects.find { it.directory.path == alphaPath }!!
        val beta = projects.find { it.directory.path == betaPath }!!

        assertEquals(listOf("kotlin", "backend"), alpha.tags, "Alpha tags should be preserved after DnD")
        assertEquals(listOf("react", "frontend"), beta.tags, "Beta tags should be preserved after DnD")
    }

    @Test
    fun `tags are preserved after cross-folder drag`() {
        val alphaPath = tempDir.resolve("alpha").also { it.toFile().mkdirs() }.toString()

        val config = AppConfig(
            projectTree = listOf(
                ProjectTreeEntry.Folder(
                    name = "Source",
                    children = listOf(
                        ProjectTreeEntry.Project(
                            directory = ProjectDirectory(path = alphaPath, displayName = "Alpha"),
                            tags = listOf("important", "v2"),
                        ),
                    ),
                ),
                ProjectTreeEntry.Folder(
                    name = "Target",
                    children = emptyList(),
                ),
            ),
        )
        val panel = buildCtxAndPanel(config)
        fixture = showInFrame(panel)

        // Drag Alpha onto Target folder
        val src = rowCenter(1) ?: error("Alpha row not visible")
        val dst = rowCenter(2) ?: error("Target folder not visible")
        dragTo(src, dst)

        val target = ctx.config.projectTree[1] as ProjectTreeEntry.Folder
        val moved = target.children.filterIsInstance<ProjectTreeEntry.Project>()
            .find { it.directory.path == alphaPath }
        assertNotNull(moved, "Alpha should be in Target folder after drag")
        assertEquals(listOf("important", "v2"), moved!!.tags, "Tags should survive cross-folder drag")
    }

    // ── Hit testing ─────────────────────────────────────────────────────────

    @Test
    fun `clicking lower half and empty row space selects the project`() {
        val projectPath = tempDir.resolve("clicky-app").also { it.toFile().mkdirs() }.toString()
        val config = AppConfig(
            projectTree = listOf(
                ProjectTreeEntry.Folder(
                    name = "Apps",
                    children = listOf(
                        ProjectTreeEntry.Project(
                            directory = ProjectDirectory(path = projectPath, displayName = "Clicky App"),
                        ),
                    ),
                ),
            ),
        )
        val panel = buildCtxAndPanel(config)
        fixture = showInFrame(panel)

        val clickPoint = GuiActionRunner.execute(object : GuiQuery<Point>() {
            override fun executeInEDT(): Point {
                val bounds = tree.getRowBounds(1) ?: error("Project row not visible")
                val loc = tree.locationOnScreen
                val x = loc.x + (tree.width - 6).coerceAtLeast(1)
                val y = loc.y + bounds.y + (bounds.height * 0.85).toInt()
                return Point(x, y)
            }
        })
        robot.pressMouse(clickPoint, MouseButton.LEFT_BUTTON)
        robot.releaseMouse(MouseButton.LEFT_BUTTON)
        robot.waitForIdle()

        val selectedRow = GuiActionRunner.execute(object : GuiQuery<Int?>() {
            override fun executeInEDT(): Int? = tree.selectionRows?.firstOrNull()
        })
        assertEquals(1, selectedRow, "Clicking the lower-right area of a row should select it")
    }

    // ── Timing / latency ────────────────────────────────────────────────────

    @Test
    fun `selection latency stays low under load`() {
        val children = (1..120).map { i ->
            val path = tempDir.resolve("project-$i").also { it.toFile().mkdirs() }.toString()
            ProjectTreeEntry.Project(
                directory = ProjectDirectory(path = path, displayName = "Project $i"),
                tags = listOf("tag-$i", "longer-tag-$i"),
            )
        }
        val config = AppConfig(
            projectTree = listOf(ProjectTreeEntry.Folder(name = "Work", children = children)),
        )
        val scanner = object : ProjectScanner {
            override fun scan(directory: ProjectDirectory): DetectedProject =
                DetectedProject(directory, emptySet(), emptyList())
        }
        val panel = buildCtxAndPanel(config, scanner)
        fixture = showInFrame(panel, width = 500, height = 700)

        val clickRows = (1..10).toList() + (1..10).toList() // 20 clicks
        val latenciesMs = mutableListOf<Long>()
        for (row in clickRows) {
            val p = rowCenter(row) ?: error("Row $row not visible")
            val start = System.nanoTime()
            robot.pressMouse(p, MouseButton.LEFT_BUTTON)
            robot.releaseMouse(MouseButton.LEFT_BUTTON)
            waitForSelectionRow(row, 750)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            latenciesMs.add(elapsedMs)
        }

        val max = latenciesMs.maxOrNull() ?: 0
        println("Selection latency ms: ${latenciesMs.joinToString()} (max=$max)")
        assertTrue(max < 250, "Selection latency too high: max=${max}ms")
    }

    private fun waitForSelectionRow(row: Int, timeoutMs: Long) {
        val deadline = System.nanoTime() + (timeoutMs * 1_000_000)
        while (System.nanoTime() < deadline) {
            val selected = GuiActionRunner.execute(object : GuiQuery<Int?>() {
                override fun executeInEDT(): Int? = tree.selectionRows?.firstOrNull()
            })
            if (selected == row) return
            Thread.sleep(5)
        }
        throw AssertionError("Timed out waiting for selection row $row")
    }

    // ── Layout stability ─────────────────────────────────────────────────────

    @Test
    fun `tree width does not grow when projects are added`() {
        val config = AppConfig(
            projectTree = listOf(
                ProjectTreeEntry.Folder(name = "Work", children = emptyList()),
            ),
        )
        val panel = buildCtxAndPanel(config)
        fixture = showInFrame(panel, width = 300, height = 400)

        val initialWidth = GuiActionRunner.execute(object : GuiQuery<Int>() {
            override fun executeInEDT() = tree.width
        })

        // Add 5 projects programmatically
        for (i in 1..5) {
            val path = tempDir.resolve("project-$i").also { it.toFile().mkdirs() }.toString()
            val current = ctx.config
            val folder = current.projectTree.first() as ProjectTreeEntry.Folder
            val newProject = ProjectTreeEntry.Project(
                directory = ProjectDirectory(path = path, displayName = "Project $i"),
                tags = listOf("tag-$i"),
            )
            val updated = folder.copy(children = folder.children + newProject)
            ctx.updateConfig(current.copy(projectTree = listOf(updated)))
        }
        robot.waitForIdle()
        Thread.sleep(500) // let layout settle

        val finalWidth = GuiActionRunner.execute(object : GuiQuery<Int>() {
            override fun executeInEDT() = tree.width
        })

        // Width should not have grown (allow 2px tolerance for border/scrollbar)
        assertTrue(
            finalWidth <= initialWidth + 2,
            "Tree width should not grow after adding projects: initial=$initialWidth, final=$finalWidth"
        )
    }
}

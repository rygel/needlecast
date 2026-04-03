package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.config.JsonConfigStore
import io.github.rygel.needlecast.model.AppConfig
import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.model.ProjectTreeEntry
import org.assertj.swing.core.BasicRobot
import org.assertj.swing.core.MouseButton
import org.assertj.swing.core.Robot
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.edt.GuiQuery
import org.assertj.swing.fixture.FrameFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Point
import java.awt.Rectangle
import java.nio.file.Path
import javax.swing.JFrame
import javax.swing.JTree

/**
 * End-to-end drag-and-drop tests for [ProjectTreePanel].
 *
 * Verifies that:
 * 1. Projects can be reordered within a folder by dragging.
 * 2. Projects can be moved between folders by dragging.
 *
 * The second test also covers the specific bug where dragging an *unselected* node
 * failed because [BasicTreeUI] defers the selection update on drag-start, making
 * [JTree.lastSelectedPathComponent] stale. The fix stores the pressed path in
 * a [mousePressed] handler before the drag gesture fires.
 *
 * Run via: mvn verify -Ptest-desktop (requires Xvfb — use Dockerfile.uitest).
 * Never run locally — these tests capture the mouse and keyboard.
 */
class ProjectTreePanelDndUiTest {

    private lateinit var robot: Robot
    private lateinit var fixture: FrameFixture
    private lateinit var ctx: AppContext
    private lateinit var tree: JTree

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        robot = BasicRobot.robotWithNewAwtHierarchy()
    }

    @AfterEach
    fun tearDown() {
        fixture.cleanUp()
        robot.cleanUp()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun buildCtxAndPanel(config: AppConfig): ProjectTreePanel {
        val store = JsonConfigStore(tempDir.resolve("config.json"))
        ctx = AppContext(configStore = store)
        ctx.updateConfig(config)
        return GuiActionRunner.execute<ProjectTreePanel> { ProjectTreePanel(ctx, {}) }
    }

    private fun showInFrame(panel: ProjectTreePanel): FrameFixture {
        val frame = GuiActionRunner.execute<JFrame> {
            JFrame("DnD Test").apply {
                contentPane.add(panel)
                setSize(500, 600)
            }
        }
        val fix = FrameFixture(robot, frame)
        fix.show()
        robot.waitForIdle()
        tree = robot.finder().findByType(panel, JTree::class.java, true)
        return fix
    }

    /**
     * Returns the screen-space center of a tree row, or null if the row is not visible.
     */
    private fun rowCenter(row: Int): Point? {
        val bounds = GuiActionRunner.execute(object : GuiQuery<Rectangle?>() {
            override fun executeInEDT(): Rectangle? = tree.getRowBounds(row)
        }) ?: return null
        val loc = tree.locationOnScreen
        return Point(loc.x + bounds.centerX.toInt(), loc.y + bounds.centerY.toInt())
    }

    /**
     * Returns a point at the TOP EDGE of a tree row (used to trigger INSERT-before
     * rather than ON, in DropMode.ON_OR_INSERT).
     */
    private fun rowTopEdge(row: Int): Point? {
        val bounds = GuiActionRunner.execute(object : GuiQuery<Rectangle?>() {
            override fun executeInEDT(): Rectangle? = tree.getRowBounds(row)
        }) ?: return null
        val loc = tree.locationOnScreen
        // Top ~20 % of the row → JTree interprets as INSERT before this row
        return Point(loc.x + bounds.centerX.toInt(), loc.y + bounds.y + (bounds.height * 0.15).toInt())
    }

    /**
     * Simulates a drag-and-drop gesture using the AWT robot:
     * 1. Press at [src].
     * 2. Move in small increments toward [dst] so the DnD gesture recognizer fires.
     * 3. Release at [dst].
     */
    private fun dragTo(src: Point, dst: Point) {
        robot.pressMouse(src, MouseButton.LEFT_BUTTON)
        robot.waitForIdle()

        // Move in 8 steps — the gesture recognizer fires after ~4px of movement
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

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Drag the second project above the first (INSERT-before) within the same folder.
     * This replicates the most common reorder use-case.
     *
     * Initial tree (root hidden):
     *   Row 0  Work  [folder]
     *   Row 1  Alpha [project]
     *   Row 2  Beta  [project]
     *
     * Action: drag Beta (row 2) to the top-edge of Alpha (row 1) → INSERT at index 0.
     *
     * Expected result after drop: folder children = [Beta, Alpha].
     */
    @Test
    fun `dragging second project above first reorders them within the folder`() {
        val alphaPath = tempDir.resolve("alpha").toString()
        val betaPath  = tempDir.resolve("beta").toString()

        val config = AppConfig(
            projectTree = listOf(
                ProjectTreeEntry.Folder(
                    name = "Work",
                    children = listOf(
                        ProjectTreeEntry.Project(directory = ProjectDirectory(path = alphaPath, displayName = "Alpha")),
                        ProjectTreeEntry.Project(directory = ProjectDirectory(path = betaPath,  displayName = "Beta")),
                    ),
                ),
            ),
        )
        val panel = buildCtxAndPanel(config)
        fixture = showInFrame(panel)

        // Row counts: 0=Work, 1=Alpha, 2=Beta
        assertEquals(3, GuiActionRunner.execute(object : GuiQuery<Int>() {
            override fun executeInEDT() = tree.rowCount
        }), "Expected 3 visible rows")

        // Select Alpha first so that lastSelectedPathComponent points to Alpha — this
        // simulates the pre-fix bug scenario where dragging Beta (unselected) would
        // incorrectly drag Alpha because createTransferable read lastSelectedPathComponent.
        GuiActionRunner.execute(object : GuiQuery<Unit>() {
            override fun executeInEDT() { tree.setSelectionRow(1) }
        })
        robot.waitForIdle()

        val src = rowCenter(2)   ?: error("Beta row not visible")
        val dst = rowTopEdge(1)  ?: error("Alpha row not visible")
        dragTo(src, dst)

        val folder = ctx.config.projectTree.first() as ProjectTreeEntry.Folder
        assertEquals(2, folder.children.size, "Folder should still have 2 children")
        val first  = (folder.children[0] as ProjectTreeEntry.Project).directory.path
        val second = (folder.children[1] as ProjectTreeEntry.Project).directory.path
        assertEquals(betaPath,  first,  "Beta should be first after drag")
        assertEquals(alphaPath, second, "Alpha should be second after drag")
    }

    /**
     * Drag a project from one folder and drop it ON another folder (inserts at end).
     * This verifies cross-folder moves work end-to-end.
     *
     * Initial tree:
     *   Row 0  Group A [folder]
     *   Row 1  Alpha   [project]
     *   Row 2  Group B [folder]
     *   Row 3  Beta    [project]
     *
     * Action: drag Alpha (row 1) onto Group B (row 2) → moved to end of Group B.
     *
     * Expected: Group A = [], Group B = [Beta, Alpha].
     */
    @Test
    fun `dragging project onto another folder moves it there`() {
        val alphaPath = tempDir.resolve("alpha").toString()
        val betaPath  = tempDir.resolve("beta").toString()

        val config = AppConfig(
            projectTree = listOf(
                ProjectTreeEntry.Folder(
                    name = "Group A",
                    children = listOf(
                        ProjectTreeEntry.Project(directory = ProjectDirectory(path = alphaPath, displayName = "Alpha")),
                    ),
                ),
                ProjectTreeEntry.Folder(
                    name = "Group B",
                    children = listOf(
                        ProjectTreeEntry.Project(directory = ProjectDirectory(path = betaPath, displayName = "Beta")),
                    ),
                ),
            ),
        )
        val panel = buildCtxAndPanel(config)
        fixture = showInFrame(panel)

        // Row counts: 0=Group A, 1=Alpha, 2=Group B, 3=Beta
        assertEquals(4, GuiActionRunner.execute(object : GuiQuery<Int>() {
            override fun executeInEDT() = tree.rowCount
        }), "Expected 4 visible rows")

        val src = rowCenter(1) ?: error("Alpha row not visible")
        val dst = rowCenter(2) ?: error("Group B row not visible")
        dragTo(src, dst)

        val updated = ctx.config.projectTree
        val folderA = updated[0] as ProjectTreeEntry.Folder
        val folderB = updated[1] as ProjectTreeEntry.Folder

        assertEquals(0, folderA.children.size, "Group A should be empty after move")
        assertEquals(2, folderB.children.size, "Group B should have 2 projects after move")

        val alphaInB = folderB.children
            .filterIsInstance<ProjectTreeEntry.Project>()
            .find { it.directory.path == alphaPath }
        assertNotNull(alphaInB, "Alpha should be present in Group B after drag")
    }
}

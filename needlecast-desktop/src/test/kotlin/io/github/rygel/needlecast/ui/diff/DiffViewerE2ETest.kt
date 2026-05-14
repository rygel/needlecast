package io.github.rygel.needlecast.ui.diff

import io.github.rygel.needlecast.git.ChangedFile
import io.github.rygel.needlecast.git.GitService
import io.github.rygel.needlecast.model.GitStatus
import org.assertj.swing.core.BasicRobot
import org.assertj.swing.core.Robot
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.edt.GuiQuery
import org.assertj.swing.fixture.FrameFixture
import io.github.rygel.needlecast.ui.GitLogPanel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Container
import java.awt.Dimension
import java.nio.file.Path
import javax.swing.JFrame
import javax.swing.JList
import javax.swing.JSplitPane
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel

private class E2EFakeGitService(
    private val logLines: String? = "",
    private val showOutput: String? = "",
) : GitService {
    override fun readStatus(dir: String): GitStatus = GitStatus.NotARepo
    override fun log(dir: String, maxEntries: Int): String? = logLines
    override fun show(dir: String, hash: String): String? = showOutput
    override fun changedFiles(dir: String): List<ChangedFile> = emptyList()
    override fun stage(dir: String, files: List<String>) {}
    override fun commit(dir: String, message: String) {}
    override fun fetchStreaming(dir: String, onLine: (String) -> Unit): Int = 0
    override fun pushStreaming(dir: String, onLine: (String) -> Unit): Int = 0
    override fun pullStreaming(dir: String, onLine: (String) -> Unit): Int = 0
}

class DiffViewerE2ETest {

    private lateinit var robot: Robot
    private lateinit var fixture: FrameFixture
    private lateinit var panel: GitLogPanel

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

    private val sampleDiff = """
commit abc123
Author: Test
Date:   Now

    test commit

 2 files changed, 3 insertions(+), 2 deletions(-)

diff --git a/src/Main.kt b/src/Main.kt
--- a/src/Main.kt
+++ b/src/Main.kt
@@ -10,7 +10,8 @@ class Main {
     fun old() {
-        println("old")
+        println("new")
+        println("extra")
     }
 }
diff --git a/README.md b/README.md
--- a/README.md
+++ b/README.md
@@ -1 +1 @@
-Old readme
+New readme
    """.trimIndent()

    @Test
    fun `DiffViewerPanel renders left and right panes with diff content`() {
        val fake = E2EFakeGitService(
            logLines = "abc123 Test commit\n",
            showOutput = sampleDiff,
        )
        panel = GuiActionRunner.execute(object : GuiQuery<GitLogPanel>() {
            override fun executeInEDT(): GitLogPanel = GitLogPanel(fake)
        })
        val frame = GuiActionRunner.execute(object : GuiQuery<JFrame>() {
            override fun executeInEDT(): JFrame = JFrame("E2E Test").apply {
                contentPane.add(panel)
                setSize(900, 600)
            }
        })
        fixture = FrameFixture(robot, frame)
        fixture.show()
        robot.waitForIdle()

        GuiActionRunner.execute(object : GuiQuery<Unit>() {
            override fun executeInEDT() { panel.loadProject(tempDir.toString()) }
        })
        waitForCondition(2_000) {
            robot.finder().findByName(panel, "log-list", JList::class.java, true).model.size == 1
        }

        val logList = robot.finder().findByName(panel, "log-list", JList::class.java, true)
        GuiActionRunner.execute(object : GuiQuery<Unit>() {
            override fun executeInEDT() { logList.selectedIndex = 0 }
        })

        waitForCondition(5_000) {
            val viewer = GuiActionRunner.execute(object : GuiQuery<DiffViewerPanel?>() {
                override fun executeInEDT(): DiffViewerPanel? = findDescendant(panel, DiffViewerPanel::class.java)
            })
            viewer != null && GuiActionRunner.execute(object : GuiQuery<Boolean>() {
                override fun executeInEDT(): Boolean = viewer.contentPanel.leftPane.styledDocument.length > 0
            })
        }

        val viewer = GuiActionRunner.execute(object : GuiQuery<DiffViewerPanel?>() {
            override fun executeInEDT(): DiffViewerPanel? = findDescendant(panel, DiffViewerPanel::class.java)
        })!!
        val contentPanel = viewer.contentPanel

        val leftText = GuiActionRunner.execute(object : GuiQuery<String>() {
            override fun executeInEDT(): String {
                val doc = contentPanel.leftPane.styledDocument
                return doc.getText(0, doc.length)
            }
        })
        val rightText = GuiActionRunner.execute(object : GuiQuery<String>() {
            override fun executeInEDT(): String {
                val doc = contentPanel.rightPane.styledDocument
                return doc.getText(0, doc.length)
            }
        })

        assertTrue(leftText.contains("println"), "Left pane should contain removed code. Got: [$leftText]")
        assertTrue(rightText.contains("println"), "Right pane should contain added code. Got: [$rightText]")
        assertTrue(leftText.contains("old"), "Left pane should show 'old'. Got: [$leftText]")
        assertTrue(rightText.contains("new"), "Right pane should show 'new'. Got: [$rightText]")
        assertTrue(rightText.contains("extra"), "Right pane should show 'extra'. Got: [$rightText]")

        assertTrue(contentPanel.leftPane.lineCount > 0, "Left pane should have lines rendered")
        assertTrue(contentPanel.rightPane.lineCount > 0, "Right pane should have lines rendered")
    }

    @Test
    fun `file tree shows all changed files from diff`() {
        val fake = E2EFakeGitService(
            logLines = "abc123 Test commit\n",
            showOutput = sampleDiff,
        )
        panel = GuiActionRunner.execute(object : GuiQuery<GitLogPanel>() {
            override fun executeInEDT(): GitLogPanel = GitLogPanel(fake)
        })
        val frame = GuiActionRunner.execute(object : GuiQuery<JFrame>() {
            override fun executeInEDT(): JFrame = JFrame("E2E Test").apply {
                contentPane.add(panel)
                setSize(900, 600)
            }
        })
        fixture = FrameFixture(robot, frame)
        fixture.show()
        robot.waitForIdle()

        GuiActionRunner.execute(object : GuiQuery<Unit>() {
            override fun executeInEDT() { panel.loadProject(tempDir.toString()) }
        })
        waitForCondition(2_000) {
            robot.finder().findByName(panel, "log-list", JList::class.java, true).model.size == 1
        }

        val logList = robot.finder().findByName(panel, "log-list", JList::class.java, true)
        GuiActionRunner.execute(object : GuiQuery<Unit>() {
            override fun executeInEDT() { logList.selectedIndex = 0 }
        })

        waitForCondition(5_000) {
            val tree = GuiActionRunner.execute(object : GuiQuery<JTree?>() {
                override fun executeInEDT(): JTree? {
                    val viewer = findDescendant(panel, DiffViewerPanel::class.java) ?: return null
                    return findDescendant(viewer as Container, JTree::class.java)
                }
            })
            tree != null && (tree.model as? DefaultTreeModel)?.root?.let { (it as javax.swing.tree.TreeNode).childCount } == 2
        }

        val fileTree = GuiActionRunner.execute(object : GuiQuery<JTree?>() {
            override fun executeInEDT(): JTree? {
                val viewer = findDescendant(panel, DiffViewerPanel::class.java) ?: return null
                return findDescendant(viewer as Container, JTree::class.java)
            }
        })!!
        val root = fileTree.model.root as javax.swing.tree.TreeNode
        assertEquals(2, root.childCount, "File tree should show 2 files")
    }

    @Test
    fun `side-by-side split panes exist in content panel`() {
        val fake = E2EFakeGitService(
            logLines = "abc123 Test commit\n",
            showOutput = sampleDiff,
        )
        panel = GuiActionRunner.execute(object : GuiQuery<GitLogPanel>() {
            override fun executeInEDT(): GitLogPanel = GitLogPanel(fake)
        })
        val frame = GuiActionRunner.execute(object : GuiQuery<JFrame>() {
            override fun executeInEDT(): JFrame = JFrame("E2E Test").apply {
                contentPane.add(panel)
                setSize(900, 600)
            }
        })
        fixture = FrameFixture(robot, frame)
        fixture.show()
        robot.waitForIdle()

        GuiActionRunner.execute(object : GuiQuery<Unit>() {
            override fun executeInEDT() { panel.loadProject(tempDir.toString()) }
        })
        waitForCondition(2_000) {
            robot.finder().findByName(panel, "log-list", JList::class.java, true).model.size == 1
        }

        val logList = robot.finder().findByName(panel, "log-list", JList::class.java, true)
        GuiActionRunner.execute(object : GuiQuery<Unit>() {
            override fun executeInEDT() { logList.selectedIndex = 0 }
        })

        waitForCondition(5_000) {
            val splitFound = GuiActionRunner.execute(object : GuiQuery<Boolean>() {
                override fun executeInEDT(): Boolean {
                    val viewer = findDescendant(panel, DiffViewerPanel::class.java) ?: return false
                    val content = viewer.contentPanel
                    val hasSplit = findDescendant(content as Container, JSplitPane::class.java) != null
                    val leftHasText = content.leftPane.styledDocument.length > 0
                    return hasSplit && leftHasText
                }
            })
            splitFound
        }

        val hasSplit = GuiActionRunner.execute(object : GuiQuery<Boolean>() {
            override fun executeInEDT(): Boolean {
                val viewer = findDescendant(panel, DiffViewerPanel::class.java)!!
                return findDescendant(viewer.contentPanel as Container, JSplitPane::class.java) != null
            }
        })
        assertTrue(hasSplit, "Content panel should contain a JSplitPane for side-by-side view")
    }

    private fun waitForCondition(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.nanoTime() + (timeoutMs * 1_000_000L)
        while (System.nanoTime() < deadline) {
            val met = GuiActionRunner.execute(object : GuiQuery<Boolean>() {
                override fun executeInEDT(): Boolean = condition()
            })
            if (met) return
            Thread.sleep(50)
        }
        throw AssertionError("Timed out after ${timeoutMs}ms waiting for condition")
    }

    private fun <T : java.awt.Component> findDescendant(parent: Container, type: Class<T>): T? {
        for (comp in parent.components) {
            if (type.isInstance(comp)) return type.cast(comp)
            if (comp is Container) {
                val found = findDescendant(comp, type)
                if (found != null) return found
            }
        }
        return null
    }
}

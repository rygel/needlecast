package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.git.ChangedFile
import io.github.rygel.needlecast.git.GitService
import io.github.rygel.needlecast.model.GitStatus
import io.github.rygel.needlecast.ui.diff.DiffResult
import org.assertj.swing.core.BasicRobot
import org.assertj.swing.core.Robot
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.edt.GuiQuery
import org.assertj.swing.fixture.FrameFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import javax.swing.JFrame
import javax.swing.JList
import javax.swing.JTextArea
import javax.swing.JToggleButton

private class FakeGitService(
    val logLines: String? = "",
    val showOutput: String? = "",
    val changedFilesList: List<ChangedFile> = emptyList(),
    val streamingLines: List<String> = emptyList(),
    val streamingExitCode: Int = 0,
) : GitService {
    var stagedFiles: List<String>? = null
    var committedMessage: String? = null

    override fun readStatus(dir: String): GitStatus = GitStatus.NotARepo

    override fun log(
        dir: String,
        maxEntries: Int,
    ): String? = logLines

    override fun show(
        dir: String,
        hash: String,
    ): String? = showOutput

    override fun changedFiles(dir: String): List<ChangedFile> = changedFilesList

    override fun stage(
        dir: String,
        files: List<String>,
    ) {
        stagedFiles = files
    }

    override fun commit(
        dir: String,
        message: String,
    ) {
        committedMessage = message
    }

    override fun fetchStreaming(
        dir: String,
        onLine: (String) -> Unit,
    ): Int {
        streamingLines.forEach { onLine(it) }
        return streamingExitCode
    }

    override fun pushStreaming(
        dir: String,
        onLine: (String) -> Unit,
    ): Int {
        streamingLines.forEach { onLine(it) }
        return streamingExitCode
    }

    override fun pullStreaming(
        dir: String,
        onLine: (String) -> Unit,
    ): Int {
        streamingLines.forEach { onLine(it) }
        return streamingExitCode
    }
}

class GitLogPanelUiTest {
    private lateinit var robot: Robot
    private lateinit var fixture: FrameFixture
    private lateinit var panel: GitLogPanel
    private lateinit var list: JList<*>

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

    private fun showInFrame(
        panel: GitLogPanel,
        width: Int = 700,
        height: Int = 500,
    ): FrameFixture {
        val frame =
            GuiActionRunner.execute<JFrame> {
                JFrame("GitLog Test").apply {
                    contentPane.add(panel)
                    setSize(width, height)
                }
            }
        val fix = FrameFixture(robot, frame)
        fix.show()
        robot.waitForIdle()
        list = robot.finder().findByName(panel, "log-list", JList::class.java, true)
        return fix
    }

    @Test
    fun `clicking a commit invokes onCommitSelected with parsed diff result`() {
        val diffOutput =
            buildString {
                appendLine("commit abc123")
                appendLine("Author: Test")
                appendLine("Date:   Now")
                appendLine()
                appendLine("    test commit")
                appendLine()
                appendLine(" 1 file changed, 1 insertion(+), 1 deletion(-)")
                appendLine()
                appendLine("diff --git a/src/Main.kt b/src/Main.kt")
                appendLine("--- a/src/Main.kt")
                appendLine("+++ b/src/Main.kt")
                appendLine("@@ -1 +1 @@")
                appendLine("-old line")
                appendLine("+new line")
            }
        val fake = FakeGitService(logLines = "abc123 Commit one\n", showOutput = diffOutput)
        panel = GuiActionRunner.execute<GitLogPanel> { GitLogPanel(fake) }

        var capturedResult: DiffResult? = null
        panel.onCommitSelected = { result -> capturedResult = result }

        fixture = showInFrame(panel)
        GuiActionRunner.execute { panel.loadProject(tempDir.toString()) }
        waitForListSize(1, 2_000)

        GuiActionRunner.execute { list.selectedIndex = 0 }

        waitUntil(5_000) { capturedResult != null }

        val result = capturedResult!!
        assertEquals(1, result.files.size, "Expected 1 file in diff result")
        assertEquals("src/Main.kt", result.files[0].filePath)
    }

    @Test
    fun `toolbar has Log and Commit toggle buttons and Fetch Push Pull action buttons`() {
        val fake = FakeGitService()
        panel = GuiActionRunner.execute<GitLogPanel> { GitLogPanel(fake) }
        fixture = showInFrame(panel)

        fixture.toggleButton("toggle-log").requireVisible()
        fixture.toggleButton("toggle-commit").requireVisible()
        fixture.button("btn-fetch").requireVisible()
        fixture.button("btn-push").requireVisible()
        fixture.button("btn-pull").requireVisible()
    }

    @Test
    fun `commit card shows changed files returned by git service`() {
        val files =
            listOf(
                ChangedFile("src/Main.kt", " M"),
                ChangedFile("new-file.txt", "??"),
            )
        val fake = FakeGitService(changedFilesList = files)
        panel = GuiActionRunner.execute<GitLogPanel> { GitLogPanel(fake) }
        fixture = showInFrame(panel)
        GuiActionRunner.execute { panel.loadProject(tempDir.toString()) }

        fixture.toggleButton("toggle-commit").click()
        robot.waitForIdle()
        val fileList = robot.finder().findByName(panel, "changed-files-list", JList::class.java, true)
        waitUntil(2_000) { (fileList as JList<*>).model.size == 2 }

        val count =
            GuiActionRunner.execute(
                object : GuiQuery<Int>() {
                    override fun executeInEDT(): Int = fileList.model.size
                },
            )
        assertEquals(2, count)
    }

    @Test
    fun `commit button stages checked files and commits with the typed message`() {
        val files = listOf(ChangedFile("src/Main.kt", " M"))
        val fake = FakeGitService(changedFilesList = files)
        panel = GuiActionRunner.execute<GitLogPanel> { GitLogPanel(fake) }
        fixture = showInFrame(panel)
        GuiActionRunner.execute { panel.loadProject(tempDir.toString()) }

        fixture.toggleButton("toggle-commit").click()
        robot.waitForIdle()
        val fileListInner = robot.finder().findByName(panel, "changed-files-list", JList::class.java, true)
        waitUntil(2_000) { (fileListInner as JList<*>).model.size == 1 }

        fixture.textBox("commit-message").enterText("my commit message")
        fixture.button("btn-commit-ok").click()
        waitUntil(2_000) { fake.committedMessage != null }
        robot.waitForIdle()

        assertEquals(listOf("src/Main.kt"), fake.stagedFiles)
        assertEquals("my commit message", fake.committedMessage)
    }

    @Test
    fun `clicking Fetch switches to output card and streams git output`() {
        val fake = FakeGitService(streamingLines = listOf("remote: Counting objects: 3", "remote: done."))
        panel = GuiActionRunner.execute<GitLogPanel> { GitLogPanel(fake) }
        fixture = showInFrame(panel)
        GuiActionRunner.execute { panel.loadProject(tempDir.toString()) }
        robot.waitForIdle()

        fixture.button("btn-fetch").click()

        // Use requireShowing=false: the card switch happens on the EDT but may not be
        // visible to the component hierarchy until the next repaint cycle. We poll for
        // both visibility and expected content together.
        val area = robot.finder().findByName(panel, "output-area", JTextArea::class.java, false)
        waitUntil(3_000) { area.isShowing && area.text.contains("✓ Done") }
        robot.waitForIdle()

        val text =
            GuiActionRunner.execute(
                object : GuiQuery<String>() {
                    override fun executeInEDT(): String = area.text
                },
            )
        assertTrue(text.contains("remote: Counting objects: 3"), "Expected first streamed line in output area")
        assertTrue(text.contains("remote: done."), "Expected second streamed line in output area")
        assertTrue(text.contains("✓ Done"), "Expected done marker in output area")
        fixture.button("btn-output-close").requireEnabled()
    }

    @Test
    fun `clicking Close on output card returns to log view`() {
        val fake = FakeGitService(streamingLines = emptyList())
        panel = GuiActionRunner.execute<GitLogPanel> { GitLogPanel(fake) }
        fixture = showInFrame(panel)
        GuiActionRunner.execute { panel.loadProject(tempDir.toString()) }
        robot.waitForIdle()

        fixture.button("btn-fetch").click()
        waitUntil(3_000) {
            robot
                .finder()
                .findByName(panel, "output-area", JTextArea::class.java, true)
                .text
                .contains("✓ Done")
        }
        robot.waitForIdle()

        fixture.button("btn-output-close").click()
        robot.waitForIdle()

        val logToggleSelected =
            GuiActionRunner.execute(
                object : GuiQuery<Boolean>() {
                    override fun executeInEDT(): Boolean =
                        robot.finder().findByName(panel, "toggle-log", JToggleButton::class.java, true).isSelected
                },
            )
        assertTrue(logToggleSelected, "Expected Log toggle to be selected after Close")
    }

    private fun waitUntil(
        timeoutMs: Long,
        condition: () -> Boolean,
    ) {
        val deadline = System.nanoTime() + (timeoutMs * 1_000_000L)
        while (System.nanoTime() < deadline) {
            val met =
                GuiActionRunner.execute(
                    object : GuiQuery<Boolean>() {
                        override fun executeInEDT(): Boolean = condition()
                    },
                ) == true
            if (met) return
            Thread.sleep(10)
        }
        throw AssertionError("Timed out after ${timeoutMs}ms waiting for condition")
    }

    private fun waitForListSize(
        size: Int,
        timeoutMs: Long,
    ) {
        val deadline = System.nanoTime() + (timeoutMs * 1_000_000)
        while (System.nanoTime() < deadline) {
            val count =
                GuiActionRunner.execute(
                    object : GuiQuery<Int>() {
                        override fun executeInEDT(): Int = list.model.size
                    },
                )
            if (count >= size) return
            Thread.sleep(10)
        }
        throw AssertionError("Timed out waiting for list size >= $size")
    }
}

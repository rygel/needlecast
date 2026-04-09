package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.git.GitService
import io.github.rygel.needlecast.model.GitStatus
import org.assertj.swing.core.BasicRobot
import org.assertj.swing.core.Robot
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.edt.GuiQuery
import org.assertj.swing.fixture.FrameFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Dimension
import java.nio.file.Path
import javax.swing.JFrame
import javax.swing.JList
import javax.swing.JTextArea

class GitLogPanelUiTest {

    private lateinit var robot: Robot
    private lateinit var fixture: FrameFixture
    private lateinit var panel: GitLogPanel
    private lateinit var list: JList<*>
    private lateinit var diffArea: JTextArea

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

    private fun showInFrame(panel: GitLogPanel, width: Int = 700, height: Int = 500): FrameFixture {
        val frame = GuiActionRunner.execute<JFrame> {
            JFrame("GitLog Test").apply {
                contentPane.add(panel)
                setSize(width, height)
            }
        }
        val fix = FrameFixture(robot, frame)
        fix.show()
        robot.waitForIdle()
        list = robot.finder().findByType(panel, JList::class.java, true)
        diffArea = robot.finder().findByType(panel, JTextArea::class.java, true)
        return fix
    }

    @Test
    fun `large diffs render incrementally without blocking`() {
        val huge = buildString {
            repeat(50_000) { append("line ").append(it).append(" lorem ipsum dolor sit amet\n") }
        }
        val maxDiffChars = 400_000
        val gitService = object : GitService {
            override fun readStatus(dir: String): GitStatus = GitStatus.NotARepo
            override fun log(dir: String, maxEntries: Int): String = "abc123 Commit one\n"
            override fun show(path: String, hash: String): String = huge
        }

        panel = GuiActionRunner.execute<GitLogPanel> { GitLogPanel(gitService) }
        fixture = showInFrame(panel)

        GuiActionRunner.execute { panel.loadProject(tempDir.toString()) }
        waitForListSize(1, 2_000)

        GuiActionRunner.execute { list.selectedIndex = 0 }

        val totalLength = if (huge.length > maxDiffChars) {
            val omitted = huge.length - maxDiffChars
            maxDiffChars + "\n\n[Diff truncated: omitted ${omitted} characters]".length
        } else huge.length
        Thread.sleep(50)
        val partialLength = GuiActionRunner.execute(object : GuiQuery<Int>() {
            override fun executeInEDT(): Int = diffArea.document.length
        })
        org.junit.jupiter.api.Assertions.assertTrue(
            partialLength in 1 until totalLength,
            "Expected incremental rendering; length=$partialLength total=$totalLength"
        )

        waitForDocLength(totalLength, 5_000)
        if (huge.length > maxDiffChars) {
            val text = GuiActionRunner.execute(object : GuiQuery<String>() {
                override fun executeInEDT(): String = diffArea.text
            })
            org.junit.jupiter.api.Assertions.assertTrue(
                text.contains("Diff truncated"),
                "Expected truncation notice in rendered diff"
            )
        }
    }

    private fun waitForListSize(size: Int, timeoutMs: Long) {
        val deadline = System.nanoTime() + (timeoutMs * 1_000_000)
        while (System.nanoTime() < deadline) {
            val count = GuiActionRunner.execute(object : GuiQuery<Int>() {
                override fun executeInEDT(): Int = list.model.size
            })
            if (count >= size) return
            Thread.sleep(10)
        }
        throw AssertionError("Timed out waiting for list size >= $size")
    }

    private fun waitForDocLength(expected: Int, timeoutMs: Long) {
        val deadline = System.nanoTime() + (timeoutMs * 1_000_000)
        while (System.nanoTime() < deadline) {
            val len = GuiActionRunner.execute(object : GuiQuery<Int>() {
                override fun executeInEDT(): Int = diffArea.document.length
            })
            if (len == expected) return
            Thread.sleep(20)
        }
        throw AssertionError("Timed out waiting for diff length $expected")
    }
}

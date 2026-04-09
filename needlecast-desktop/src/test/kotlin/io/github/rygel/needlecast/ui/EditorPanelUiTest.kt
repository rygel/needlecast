package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.config.JsonConfigStore
import io.github.rygel.needlecast.ui.explorer.EditorPanel
import org.assertj.swing.core.BasicRobot
import org.assertj.swing.core.Robot
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.edt.GuiQuery
import org.assertj.swing.fixture.FrameFixture
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import javax.swing.JFrame

class EditorPanelUiTest {

    private lateinit var robot: Robot
    private lateinit var fixture: FrameFixture
    private lateinit var panel: EditorPanel
    private lateinit var editor: RSyntaxTextArea

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

    private fun showInFrame(panel: EditorPanel, width: Int = 700, height: Int = 500): FrameFixture {
        val frame = GuiActionRunner.execute<JFrame> {
            JFrame("Editor Test").apply {
                contentPane.add(panel)
                setSize(width, height)
            }
        }
        val fix = FrameFixture(robot, frame)
        fix.show()
        robot.waitForIdle()
        editor = robot.finder().findByType(panel, RSyntaxTextArea::class.java, true)
        return fix
    }

    @Test
    fun `large files load incrementally without blocking`() {
        val store = JsonConfigStore(tempDir.resolve("config.json"))
        val ctx = AppContext(configStore = store)
        panel = GuiActionRunner.execute<EditorPanel> { EditorPanel(ctx) }
        fixture = showInFrame(panel)

        val file = tempDir.resolve("big.txt").toFile()
        val content = buildString {
            repeat(100_000) { append("line ").append(it).append('\n') }
        }
        file.writeText(content)

        GuiActionRunner.execute { panel.openFile(file) }

        Thread.sleep(50)
        val partialLength = GuiActionRunner.execute(object : GuiQuery<Int>() {
            override fun executeInEDT(): Int = editor.document.length
        })
        val totalLength = content.length
        org.junit.jupiter.api.Assertions.assertTrue(
            partialLength in 1 until totalLength,
            "Expected incremental load; length=$partialLength total=$totalLength"
        )

        waitForDocLength(totalLength, 5_000)
    }

    private fun waitForDocLength(expected: Int, timeoutMs: Long) {
        val deadline = System.nanoTime() + (timeoutMs * 1_000_000)
        while (System.nanoTime() < deadline) {
            val len = GuiActionRunner.execute(object : GuiQuery<Int>() {
                override fun executeInEDT(): Int = editor.document.length
            })
            if (len == expected) return
            Thread.sleep(20)
        }
        throw AssertionError("Timed out waiting for editor length $expected")
    }
}

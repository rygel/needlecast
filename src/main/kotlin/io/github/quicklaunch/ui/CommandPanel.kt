package io.github.quicklaunch.ui

import io.github.quicklaunch.AppContext
import io.github.quicklaunch.model.CommandDescriptor
import io.github.quicklaunch.model.DetectedProject
import io.github.quicklaunch.model.ProcessResult
import io.github.quicklaunch.process.ProcessOutputListener
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JToolBar
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities

class CommandPanel(
    private val ctx: AppContext,
    private val consolePanel: ConsolePanel,
    private val statusBar: StatusBar,
    private val showTitle: Boolean = true,
) : JPanel(BorderLayout()) {

    private val model = DefaultListModel<CommandDescriptor>()
    private val list = JList(model).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        setCellRenderer(CommandCellRenderer())
    }
    private val runButton = JButton("\u25B6  Run").apply { isEnabled = false }
    private val cancelButton = JButton("\u23F9  Cancel").apply { isEnabled = false }

    private var processResult: ProcessResult = ProcessResult.NotStarted

    init {
        if (showTitle) {
            val header = JLabel("Commands").apply {
                border = BorderFactory.createEmptyBorder(4, 6, 2, 6)
                font = font.deriveFont(Font.BOLD)
            }
            add(header, BorderLayout.NORTH)
        }

        list.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selected = list.selectedValue
                runButton.isEnabled = selected?.isSupported == true && processResult !is ProcessResult.Running
            }
        }

        val buttonBar = JToolBar().apply {
            isFloatable = false
            add(runButton)
            add(cancelButton)
        }

        add(JScrollPane(list), BorderLayout.CENTER)
        add(buttonBar, BorderLayout.SOUTH)

        runButton.addActionListener { runSelected() }
        cancelButton.addActionListener { cancelRunning() }
    }

    fun loadProject(project: DetectedProject?) {
        model.clear()
        if (project == null) {
            runButton.isEnabled = false
            return
        }
        project.commands.forEach { model.addElement(it) }
        if (model.size > 0) list.selectedIndex = 0
    }

    private fun runSelected() {
        val cmd = list.selectedValue ?: return
        if (!cmd.isSupported) return

        consolePanel.clear()
        consolePanel.appendLine("> ${cmd.argv.joinToString(" ")}")
        consolePanel.appendLine("")

        statusBar.setRunning(cmd.label)
        runButton.isEnabled = false
        cancelButton.isEnabled = true

        val listener = object : ProcessOutputListener {
            override fun onLine(line: String) {
                SwingUtilities.invokeLater { consolePanel.appendLine(line) }
            }
            override fun onExit(exitCode: Int) {
                SwingUtilities.invokeLater {
                    consolePanel.appendLine("")
                    consolePanel.appendLine("[Process exited with code $exitCode]")
                    statusBar.setFinished(exitCode)
                    processResult = ProcessResult.Finished(exitCode)
                    runButton.isEnabled = list.selectedValue?.isSupported == true
                    cancelButton.isEnabled = false
                }
            }
        }

        val running = ctx.commandRunner.run(cmd, listener)
        processResult = ProcessResult.Running(running)
    }

    private fun cancelRunning() {
        (processResult as? ProcessResult.Running)?.process?.cancel()
        processResult = ProcessResult.NotStarted
        cancelButton.isEnabled = false
        runButton.isEnabled = list.selectedValue?.isSupported == true
        statusBar.setStatus("Cancelled")
    }
}

private class CommandCellRenderer : ListCellRenderer<CommandDescriptor> {
    private val label = JLabel().apply {
        border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
    }

    override fun getListCellRendererComponent(
        list: JList<out CommandDescriptor>,
        value: CommandDescriptor?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        label.text = value?.label ?: ""
        label.toolTipText = if (value?.isSupported == true) value.argv.joinToString(" ")
                            else "This run configuration type is not directly executable"

        val fg = when {
            isSelected -> list.selectionForeground
            value?.isSupported == false -> java.awt.Color.GRAY
            else -> list.foreground
        }
        label.foreground = fg
        label.background = if (isSelected) list.selectionBackground else list.background
        label.isOpaque = true
        return label
    }
}

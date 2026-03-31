package io.github.quicklaunch.ui

import io.github.quicklaunch.AppContext
import io.github.quicklaunch.model.CommandDescriptor
import io.github.quicklaunch.model.CommandHistoryEntry
import io.github.quicklaunch.model.DetectedProject
import io.github.quicklaunch.model.ProcessResult
import io.github.quicklaunch.process.ProcessOutputListener
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JToggleButton
import javax.swing.JToolBar
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities

private const val HISTORY_MAX = 20

class CommandPanel(
    private val ctx: AppContext,
    private val consolePanel: ConsolePanel,
    private val statusBar: StatusBar,
    private val showTitle: Boolean = true,
    /** Returns true when the owning window is focused; used to suppress tray notifications. */
    private val isWindowFocused: () -> Boolean = { true },
) : JPanel(BorderLayout()) {

    private val commandModel = DefaultListModel<CommandDescriptor>()
    private val historyModel = DefaultListModel<CommandHistoryEntry>()

    private val commandList = JList(commandModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        setCellRenderer(CommandCellRenderer())
    }
    private val historyList = JList(historyModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        setCellRenderer(HistoryCellRenderer())
    }

    private val runButton    = JButton("\u25B6  Run").apply    { isEnabled = false }
    private val cancelButton = JButton("\u23F9  Cancel").apply { isEnabled = false }
    private val historyToggle = JToggleButton("\u2713 History").apply { isSelected = false }

    private var processResult: ProcessResult = ProcessResult.NotStarted
    private var currentProjectPath: String? = null

    private val commandScroll = JScrollPane(commandList)
    private val historyScroll = JScrollPane(historyList).apply { isVisible = false }

    init {
        if (showTitle) {
            val header = JLabel("Commands").apply {
                border = BorderFactory.createEmptyBorder(4, 6, 2, 6)
                font = font.deriveFont(Font.BOLD)
            }
            add(header, BorderLayout.NORTH)
        }

        commandList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selected = commandList.selectedValue
                runButton.isEnabled = selected?.isSupported == true && processResult !is ProcessResult.Running
            }
        }

        // Double-click history entry to re-run
        historyList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) rerunHistoryEntry()
            }
        })

        val buttonBar = JToolBar().apply {
            isFloatable = false
            add(runButton)
            add(cancelButton)
            addSeparator()
            add(historyToggle)
        }

        val listPanel = JPanel(BorderLayout()).apply {
            add(commandScroll, BorderLayout.CENTER)
            add(historyScroll, BorderLayout.SOUTH)
        }

        add(listPanel, BorderLayout.CENTER)
        add(buttonBar, BorderLayout.SOUTH)

        runButton.addActionListener    { runSelected() }
        cancelButton.addActionListener { cancelRunning() }

        historyToggle.addActionListener {
            historyScroll.isVisible = historyToggle.isSelected
            revalidate(); repaint()
        }
    }

    fun loadProject(project: DetectedProject?) {
        commandModel.clear()
        historyModel.clear()
        currentProjectPath = project?.directory?.path
        runButton.isEnabled = false

        if (project == null) return

        project.commands.forEach { commandModel.addElement(it) }
        if (commandModel.size > 0) commandList.selectedIndex = 0

        // Load saved history for this project
        ctx.config.commandHistory[project.directory.path]
            ?.forEach { historyModel.addElement(it) }
    }

    private fun runSelected() {
        val cmd = commandList.selectedValue ?: return
        if (!cmd.isSupported) return
        executeCommand(cmd.label, cmd.argv, cmd.workingDirectory)
    }

    private fun rerunHistoryEntry() {
        val entry = historyList.selectedValue ?: return
        executeCommand(entry.label, entry.argv, entry.workingDirectory)
    }

    private fun executeCommand(label: String, argv: List<String>, workingDir: String) {
        consolePanel.clear()
        consolePanel.appendLine("> ${argv.joinToString(" ")}")
        consolePanel.appendLine("")

        statusBar.setRunning(label)
        runButton.isEnabled = false
        cancelButton.isEnabled = true

        val startTime = System.currentTimeMillis()

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
                    runButton.isEnabled = commandList.selectedValue?.isSupported == true
                    cancelButton.isEnabled = false
                    recordHistory(CommandHistoryEntry(label, argv, workingDir, exitCode, startTime))
                    if (!isWindowFocused()) {
                        val msg = if (exitCode == 0) "'$label' finished successfully" else "'$label' failed (exit $exitCode)"
                        val type = if (exitCode == 0) TrayIcon.MessageType.INFO else TrayIcon.MessageType.ERROR
                        TrayNotifier.notify("QuickLaunch", msg, type)
                    }
                }
            }
        }

        val descriptor = CommandDescriptor(label, io.github.quicklaunch.model.BuildTool.MAVEN, argv, workingDir)
        val running = ctx.commandRunner.run(descriptor, listener)
        processResult = ProcessResult.Running(running)
    }

    private fun recordHistory(entry: CommandHistoryEntry) {
        val path = currentProjectPath ?: return
        historyModel.add(0, entry)
        while (historyModel.size > HISTORY_MAX) historyModel.removeElementAt(HISTORY_MAX)

        val updatedHistory = (0 until historyModel.size).map { historyModel.getElementAt(it) }
        val newMap = ctx.config.commandHistory.toMutableMap()
        newMap[path] = updatedHistory
        ctx.updateConfig(ctx.config.copy(commandHistory = newMap))
    }

    private fun cancelRunning() {
        (processResult as? ProcessResult.Running)?.process?.cancel()
        processResult = ProcessResult.NotStarted
        cancelButton.isEnabled = false
        runButton.isEnabled = commandList.selectedValue?.isSupported == true
        statusBar.setStatus("Cancelled")
    }
}

/**
 * Sends a system-tray balloon notification when the OS supports it.
 * Lazily installs a minimal tray icon on first use; no-ops silently if tray is unavailable.
 */
private object TrayNotifier {
    private val trayIcon: TrayIcon? by lazy {
        if (!SystemTray.isSupported()) return@lazy null
        try {
            // 16×16 transparent image — just enough for the OS to accept the icon
            val img = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            val icon = TrayIcon(img, "QuickLaunch")
            SystemTray.getSystemTray().add(icon)
            icon
        } catch (_: Exception) { null }
    }

    fun notify(caption: String, text: String, type: TrayIcon.MessageType) {
        try { trayIcon?.displayMessage(caption, text, type) } catch (_: Exception) {}
    }
}

private class CommandCellRenderer : ListCellRenderer<CommandDescriptor> {
    private val label = JLabel().apply {
        border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
    }

    override fun getListCellRendererComponent(
        list: JList<out CommandDescriptor>, value: CommandDescriptor?,
        index: Int, isSelected: Boolean, cellHasFocus: Boolean,
    ): Component {
        label.text = value?.label ?: ""
        label.toolTipText = if (value?.isSupported == true) value.argv.joinToString(" ")
                            else "This run configuration type is not directly executable"
        label.foreground = when {
            isSelected -> list.selectionForeground
            value?.isSupported == false -> Color.GRAY
            else -> list.foreground
        }
        label.background = if (isSelected) list.selectionBackground else list.background
        label.isOpaque = true
        return label
    }
}

private val timeFmt = SimpleDateFormat("HH:mm")

private class HistoryCellRenderer : ListCellRenderer<CommandHistoryEntry> {
    private val panel     = JPanel(BorderLayout(6, 0)).apply {
        border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
    }
    private val nameLabel = JLabel().apply { font = font.deriveFont(Font.PLAIN, 11f) }
    private val metaLabel = JLabel().apply { font = font.deriveFont(Font.PLAIN,  9f) }

    init { panel.add(nameLabel, BorderLayout.CENTER); panel.add(metaLabel, BorderLayout.EAST) }

    override fun getListCellRendererComponent(
        list: JList<out CommandHistoryEntry>, value: CommandHistoryEntry?,
        index: Int, isSelected: Boolean, cellHasFocus: Boolean,
    ): Component {
        nameLabel.text = value?.label ?: ""
        metaLabel.text = value?.let {
            val time = timeFmt.format(Date(it.ranAt))
            val codeColor = if (it.exitCode == 0) "#4CAF50" else "#F44336"
            "<html><font color='$codeColor'>exit ${it.exitCode}</font> $time</html>"
        } ?: ""
        val bg = if (isSelected) list.selectionBackground else list.background
        nameLabel.foreground = if (isSelected) list.selectionForeground else list.foreground
        panel.background = bg; nameLabel.background = bg; metaLabel.background = bg
        panel.isOpaque = true
        return panel
    }
}

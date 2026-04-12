package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.BuildTool
import io.github.rygel.needlecast.model.CommandDescriptor
import io.github.rygel.needlecast.model.CommandHistoryEntry
import io.github.rygel.needlecast.model.CommandOverride
import io.github.rygel.needlecast.model.DetectedProject
import io.github.rygel.needlecast.model.ProcessResult
import io.github.rygel.needlecast.process.ProcessOutputListener
import io.github.rygel.needlecast.service.CommandHistoryManager
import io.github.rygel.needlecast.service.CommandQueue
import io.github.rygel.needlecast.service.QueuedCommand
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.Window
import java.awt.image.BufferedImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JToggleButton
import javax.swing.JToolBar
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.SwingWorker

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

    private val commandQueue = CommandQueue()
    private val queueModel = DefaultListModel<String>()

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
    private val queueButton  = JButton("\u23ED  Queue").apply  { isEnabled = false; toolTipText = "Add selected command to queue" }
    private val historyToggle = JToggleButton("\u2713 History").apply { isSelected = false }
    private val queueToggle   = JToggleButton("\u23ED Queue").apply   { isSelected = false }

    private val queueList = JList(queueModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        font = Font(Font.MONOSPACED, Font.PLAIN, 10)
        visibleRowCount = 4
    }
    private val clearQueueButton = JButton("Clear Queue").apply {
        toolTipText = "Remove all queued commands"
    }
    private val queuePanel: JPanel = JPanel(BorderLayout(0, 2)).apply {
        border = BorderFactory.createEmptyBorder(2, 0, 0, 0)
        add(JScrollPane(queueList), BorderLayout.CENTER)
        add(clearQueueButton, BorderLayout.SOUTH)
        isVisible = false
    }

    private val historyManager = CommandHistoryManager(ctx)

    private var processResult: ProcessResult = ProcessResult.NotStarted
    private var currentProjectPath: String? = null
    private var currentProjectEnv: Map<String, String> = emptyMap()

    private val commandScroll = JScrollPane(commandList)
    private val historyScroll = JScrollPane(historyList).apply { isVisible = false }

    private val readmeArea = JTextArea().apply {
        isEditable = false
        lineWrap = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 10)
        border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
    }
    private val readmeScroll = JScrollPane(readmeArea).apply {
        preferredSize = Dimension(0, 120)   // ~8 lines at 10pt mono; width is flexible
        isVisible = false
    }

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
                val notRunning = processResult !is ProcessResult.Running
                runButton.isEnabled = selected?.isSupported == true && notRunning
                queueButton.isEnabled = selected?.isSupported == true
            }
        }

        // Double-click command to run it; right-click shows context menu
        commandList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2 && SwingUtilities.isLeftMouseButton(e)) runSelected()
            }
            override fun mousePressed(e: java.awt.event.MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    val idx = commandList.locationToIndex(e.point)
                    if (idx >= 0) commandList.selectedIndex = idx
                    showCommandContextMenu(e)
                }
            }
        })

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
            add(queueButton)
            addSeparator()
            add(historyToggle)
            add(queueToggle)
        }

        val listPanel = JPanel(BorderLayout()).apply {
            add(commandScroll, BorderLayout.CENTER)
            add(historyScroll, BorderLayout.SOUTH)
        }

        val centerPanel = JPanel(BorderLayout()).apply {
            add(listPanel, BorderLayout.CENTER)
            add(readmeScroll, BorderLayout.SOUTH)
        }

        val southPanel = JPanel(BorderLayout()).apply {
            add(buttonBar, BorderLayout.NORTH)
            add(queuePanel, BorderLayout.SOUTH)
        }

        add(centerPanel, BorderLayout.CENTER)
        add(southPanel, BorderLayout.SOUTH)

        runButton.addActionListener    { runSelected() }
        cancelButton.addActionListener { cancelRunning() }
        queueButton.addActionListener  { enqueueSelected() }
        clearQueueButton.addActionListener { clearQueue() }

        historyToggle.addActionListener {
            historyScroll.isVisible = historyToggle.isSelected
            revalidate(); repaint()
        }

        queueToggle.addActionListener {
            queuePanel.isVisible = queueToggle.isSelected
            revalidate(); repaint()
        }
    }

    fun loadProject(project: DetectedProject?) {
        commandModel.clear()
        historyModel.clear()
        currentProjectPath = project?.directory?.path
        currentProjectEnv = project?.directory?.env ?: emptyMap()
        runButton.isEnabled = false
        queueButton.isEnabled = false

        loadReadme(project?.directory?.path)

        if (project == null) return

        val overrides = ctx.config.commandOverrides[project.directory.path] ?: emptyList()
        val commands = applyCommandOverrides(project.commands, overrides)
        commands.forEach { commandModel.addElement(it) }
        if (commandModel.size > 0) commandList.selectedIndex = 0

        // Load saved history for this project
        historyManager.getHistory(project.directory.path)
            .forEach { historyModel.addElement(it) }
    }

    private fun loadReadme(projectPath: String?) {
        readmeScroll.isVisible = false
        readmeArea.text = ""
        if (projectPath == null) return
        object : SwingWorker<String?, Void>() {
            override fun doInBackground(): String? {
                val file = listOf("README.md", "readme.md", "README.txt", "readme.txt")
                    .map { File(projectPath, it) }
                    .firstOrNull { it.exists() && it.isFile } ?: return null
                return file.bufferedReader().useLines { it.take(20).joinToString("\n") }
            }
            override fun done() {
                if (currentProjectPath != projectPath) return  // user switched away
                val preview = try { get() } catch (_: Exception) { return }
                if (preview != null) {
                    readmeArea.text = preview
                    readmeArea.caretPosition = 0
                    readmeScroll.isVisible = true
                    revalidate()
                    repaint()
                }
            }
        }.execute()
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
                    drainQueue()
                }
            }
        }

        val descriptor = CommandDescriptor(label, BuildTool.MAVEN, argv, workingDir, currentProjectEnv)
        val running = ctx.commandRunner.run(descriptor, listener)
        processResult = ProcessResult.Running(running)
    }

    private fun recordHistory(entry: CommandHistoryEntry) {
        val path = currentProjectPath ?: return
        historyManager.record(path, entry)
        // Refresh the list model from the now-updated config (source of truth)
        historyModel.clear()
        historyManager.getHistory(path).forEach { historyModel.addElement(it) }
    }

    private fun cancelRunning() {
        (processResult as? ProcessResult.Running)?.process?.cancel()
        processResult = ProcessResult.NotStarted
        cancelButton.isEnabled = false
        runButton.isEnabled = commandList.selectedValue?.isSupported == true
        statusBar.setStatus("Cancelled")
    }

    private fun enqueueSelected() {
        val cmd = commandList.selectedValue ?: return
        if (!cmd.isSupported) return
        val queued = QueuedCommand(cmd.label, cmd.argv, cmd.workingDirectory)
        commandQueue.enqueue(queued)
        queueModel.addElement(cmd.label)
        // Auto-show the queue panel when something is added
        if (!queueToggle.isSelected) {
            queueToggle.isSelected = true
            queuePanel.isVisible = true
            revalidate(); repaint()
        }
    }

    private fun clearQueue() {
        commandQueue.clear()
        queueModel.clear()
    }

    private fun drainQueue() {
        val next = commandQueue.drain() ?: return
        queueModel.removeElementAt(0)
        executeCommand(next.label, next.argv, next.workingDir)
    }

    private fun showCommandContextMenu(e: java.awt.event.MouseEvent) {
        val cmd = commandList.selectedValue
        val supported = cmd?.isSupported == true
        val notRunning = processResult !is ProcessResult.Running
        val menu = JPopupMenu()
        menu.add(JMenuItem("\u25B6  Run").apply {
            isEnabled = supported && notRunning
            addActionListener { runSelected() }
        })
        menu.add(JMenuItem("\u23ED  Queue").apply {
            isEnabled = supported
            addActionListener { enqueueSelected() }
        })
        menu.addSeparator()
        menu.add(JMenuItem("Edit\u2026").apply {
            isEnabled = cmd != null
            addActionListener { editSelectedCommand() }
        })
        menu.show(commandList, e.x, e.y)
    }

    private fun editSelectedCommand() {
        val idx = commandList.selectedIndex.takeIf { it >= 0 } ?: return
        val original = commandModel.getElementAt(idx)
        val owner = SwingUtilities.getWindowAncestor(this)
        val dialog = EditCommandDialog(owner, original)
        dialog.isVisible = true
        val updated = dialog.result ?: return
        commandModel.set(idx, updated)

        // Persist the override so it survives rescans
        val workDir = currentProjectPath ?: return
        // Resolve the true originalArgv: if this command was already overridden,
        // find the stored override whose .argv matches the current commandModel argv
        val trueOriginalArgv = ctx.config.commandOverrides[workDir]
            ?.firstOrNull { it.argv == original.argv }
            ?.originalArgv
            ?: original.argv
        val newOverride = CommandOverride(
            originalArgv = trueOriginalArgv,
            label = updated.label,
            argv = updated.argv,
        )
        val existing = ctx.config.commandOverrides[workDir]
            ?.filterNot { it.originalArgv == trueOriginalArgv }
            ?: emptyList()
        ctx.updateConfig(
            ctx.config.copy(
                commandOverrides = ctx.config.commandOverrides + (workDir to (existing + newOverride))
            )
        )
    }
}

/**
 * Modal dialog for editing the label and command line of a [CommandDescriptor].
 * The edited command is session-only — scanners will regenerate the original on next project load.
 * [result] is non-null only when the user clicks OK with a non-empty label.
 */
private class EditCommandDialog(owner: Window?, private val cmd: CommandDescriptor) :
    JDialog(owner, "Edit Command", ModalityType.APPLICATION_MODAL) {

    var result: CommandDescriptor? = null
        private set

    private val labelField   = JTextField(cmd.label, 30)
    private val commandField = JTextField(cmd.argv.joinToString(" "), 40)

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        minimumSize = Dimension(480, 160)
        setLocationRelativeTo(owner)

        val grid = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(12, 12, 8, 12)
        }
        val gc = GridBagConstraints().apply { insets = Insets(4, 4, 4, 4) }

        fun row(r: Int, labelText: String, field: Component) {
            gc.gridy = r; gc.gridx = 0; gc.weightx = 0.0
            gc.anchor = GridBagConstraints.WEST; gc.fill = GridBagConstraints.NONE
            grid.add(JLabel(labelText), gc)
            gc.gridx = 1; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL
            grid.add(field, gc)
        }

        row(0, "Label:",   labelField)
        row(1, "Command:", commandField)

        val ok     = JButton("OK").apply     { addActionListener { onOk() } }
        val cancel = JButton("Cancel").apply { addActionListener { dispose() } }
        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 4)).apply {
            add(ok); add(cancel)
        }

        contentPane = JPanel(BorderLayout()).apply {
            add(grid,    BorderLayout.CENTER)
            add(buttons, BorderLayout.SOUTH)
        }
        pack()
        rootPane.defaultButton = ok
    }

    private fun onOk() {
        val label = labelField.text.trim()
        if (label.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Label must not be empty.", "Validation", JOptionPane.WARNING_MESSAGE)
            return
        }
        val argv = commandField.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (argv.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Command must not be empty.", "Validation", JOptionPane.WARNING_MESSAGE)
            return
        }
        result = cmd.copy(label = label, argv = argv)
        dispose()
    }
}

/**
 * Applies stored [overrides] on top of scanner-generated [commands].
 * Each override is matched by [CommandOverride.originalArgv]; unmatched overrides are ignored.
 */
internal fun applyCommandOverrides(
    commands: List<CommandDescriptor>,
    overrides: List<CommandOverride>,
): List<CommandDescriptor> {
    if (overrides.isEmpty()) return commands
    val overrideMap = overrides.associateBy { it.originalArgv } // last-write-wins if duplicates exist (shouldn't happen in normal use)
    return commands.map { cmd ->
        val ov = overrideMap[cmd.argv] ?: return@map cmd
        cmd.copy(label = ov.label, argv = ov.argv)
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
            val img = TrayNotifier::class.java.getResource("/icons/needlecast.png")
                ?.let { javax.imageio.ImageIO.read(it).getScaledInstance(16, 16, java.awt.Image.SCALE_SMOOTH) }
                ?: BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            val icon = TrayIcon(img, "Needlecast")
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

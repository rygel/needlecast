package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.git.ChangedFile
import io.github.rygel.needlecast.git.GitService
import io.github.rygel.needlecast.git.ProcessGitService
import io.github.rygel.needlecast.ui.components.DynamicHelpPopup
import io.github.rygel.needlecast.ui.diff.DiffParser
import io.github.rygel.needlecast.ui.diff.DiffResult
import io.github.rygel.needlecast.ui.diff.DiffStats
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JToggleButton
import javax.swing.KeyStroke
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingWorker

private data class GitCommit(
    val hash: String,
    val subject: String,
)

/**
 * Git panel with three views switched via a toolbar:
 * - Log: read-only commit history (existing behaviour)
 * - Commit: staging checklist + commit message field
 * - Output: streaming text for fetch/push/pull
 */
class GitLogPanel(
    private val gitService: GitService = ProcessGitService(),
    private val ctx: AppContext? = null,
) : JPanel(BorderLayout()) {
    companion object {
        private val logger = LoggerFactory.getLogger(GitLogPanel::class.java)
    }

    // ── Log view ──────────────────────────────────────────────────────────────
    private val logModel = DefaultListModel<GitCommit>()
    private val logList =
        JList(logModel).apply {
            name = "log-list"
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            setCellRenderer(CommitCellRenderer())
            fixedCellHeight = 28
        }
    var onCommitSelected: ((DiffResult) -> Unit)? = null

    // ── Commit view (wired in Task 4) ─────────────────────────────────────────
    private val fileListModel = DefaultListModel<ChangedFile>()
    private val checkedFiles = mutableSetOf<String>()
    private val fileList = JList(fileListModel).apply { name = "changed-files-list" }
    private val commitMessageField =
        JTextField().apply {
            name = "commit-message"
            putClientProperty("JTextField.placeholderText", "Commit message…")
        }
    private val commitButton = JButton("Commit").apply { name = "btn-commit-ok" }
    private val cancelButton = JButton("Cancel").apply { name = "btn-commit-cancel" }

    // ── Output view (wired in Task 5) ─────────────────────────────────────────
    private val outputLabel =
        JLabel("").apply {
            name = "output-label"
            border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
        }
    private val outputArea =
        JTextArea().apply {
            name = "output-area"
            isEditable = false
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
        }
    private val closeButton =
        JButton("Close").apply {
            name = "btn-output-close"
            isEnabled = false
        }

    // ── Toolbar ───────────────────────────────────────────────────────────────
    private val logToggle =
        JToggleButton("Log").apply {
            name = "toggle-log"
            isSelected = true
        }
    private val commitToggle = JToggleButton("Commit").apply { name = "toggle-commit" }
    private val fetchButton = JButton("Fetch").apply { name = "btn-fetch" }
    private val pushButton = JButton("Push").apply { name = "btn-push" }
    private val pullButton = JButton("Pull").apply { name = "btn-pull" }

    private val branchSelector =
        JComboBox<String>().apply {
            name = "branch-selector"
            preferredSize = Dimension(160, 26)
            isFocusable = true
        }
    private var branchChanging = false

    // ── Card layout ───────────────────────────────────────────────────────────
    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout)

    private var currentPath: String? = null
    private var pendingDiffWorker: SwingWorker<*, Void>? = null
    private val maxDiffChars = 400_000

    private val toolbar =
        JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(logToggle)
            add(commitToggle)
            add(fetchButton)
            add(pushButton)
            add(pullButton)
            add(branchSelector)
        }

    init {
        minimumSize = Dimension(0, 0)

        val logCard = JScrollPane(logList).apply { minimumSize = Dimension(0, 0) }

        logList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val commit = logList.selectedValue ?: return@addListSelectionListener
                showCommit(commit.hash)
            }
        }

        cardPanel.add(logCard, "log")
        cardPanel.add(buildCommitCard(), "commit")
        cardPanel.add(buildOutputCard(), "output")

        ButtonGroup().apply {
            add(logToggle)
            add(commitToggle)
        }
        logToggle.addActionListener { cardLayout.show(cardPanel, "log") }
        commitToggle.addActionListener {
            refreshChangedFiles()
            cardLayout.show(cardPanel, "commit")
        }
        fetchButton.addActionListener { runRemoteOp("Fetch") { dir, cb -> gitService.fetchStreaming(dir, cb) } }
        pushButton.addActionListener { runRemoteOp("Push") { dir, cb -> gitService.pushStreaming(dir, cb) } }
        pullButton.addActionListener { runRemoteOp("Pull") { dir, cb -> gitService.pullStreaming(dir, cb) } }

        branchSelector.addActionListener {
            if (branchChanging) return@addActionListener
            val branch = branchSelector.selectedItem as? String ?: return@addActionListener
            val path = currentPath ?: return@addActionListener
            val current = gitService.currentBranch(path)
            if (branch == current) return@addActionListener
            checkoutBranch(path, branch)
        }
        branchSelector.addFocusListener(
            object : FocusListener {
                override fun focusGained(e: FocusEvent?) {
                    refreshBranches()
                }

                override fun focusLost(e: FocusEvent?) {}
            },
        )

        add(toolbar, BorderLayout.NORTH)
        add(cardPanel, BorderLayout.CENTER)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun loadProject(path: String?) {
        currentPath = path
        logModel.clear()
        branchSelector.model = DefaultComboBoxModel()
        if (path == null) {
            return
        }

        logModel.addElement(GitCommit("", "Loading commits\u2026"))

        object : SwingWorker<List<GitCommit>, Void>() {
            override fun doInBackground(): List<GitCommit> =
                gitService
                    .log(path)
                    ?.lines()
                    ?.filter { it.isNotBlank() }
                    ?.mapNotNull { line ->
                        val space = line.indexOf(' ')
                        if (space < 0) null else GitCommit(line.substring(0, space), line.substring(space + 1))
                    }
                    ?: emptyList()

            override fun done() {
                val commits =
                    try {
                        get()
                    } catch (e: Exception) {
                        logger.warn("Failed to load commit history", e)
                        return
                    }
                commits.forEach { logModel.addElement(it) }
            }
        }.execute()

        refreshBranches()

        ctx?.let { appCtx ->
            appCtx.gitAutoSync.fetchIfNeeded(path) { line ->
                javax.swing.SwingUtilities.invokeLater { outputArea.append("$line\n") }
            }
            DynamicHelpPopup(
                appCtx,
                "git-first-open",
                "Fetch/Pull/Push sync with remote. Changes are fetched automatically when you select a project.",
                toolbar,
            ).showIfNotSeen()
        }
    }

    private fun refreshBranches() {
        val path = currentPath ?: return
        object : SwingWorker<Pair<List<String>, String?>, Void>() {
            override fun doInBackground(): Pair<List<String>, String?> {
                val all = gitService.branches(path)
                val current = gitService.currentBranch(path)
                return Pair(all, current)
            }

            override fun done() {
                val (branches, current) =
                    try {
                        get()
                    } catch (e: Exception) {
                        logger.warn("Failed to refresh branches", e)
                        return
                    }
                branchChanging = true
                branchSelector.model = DefaultComboBoxModel(branches.toTypedArray())
                if (current != null) {
                    branchSelector.selectedItem = current
                }
                branchChanging = false
            }
        }.execute()
    }

    private fun checkoutBranch(
        path: String,
        branch: String,
    ) {
        setRemoteButtonsEnabled(false)
        branchSelector.isEnabled = false
        object : SwingWorker<String?, Void>() {
            override fun doInBackground(): String? = gitService.checkout(path, branch)

            override fun done() {
                branchSelector.isEnabled = true
                setRemoteButtonsEnabled(true)
                val error =
                    try {
                        get()
                    } catch (e: Exception) {
                        e.message
                    }
                if (error != null) {
                    JOptionPane.showMessageDialog(
                        this@GitLogPanel,
                        error,
                        "Checkout failed",
                        JOptionPane.WARNING_MESSAGE,
                    )
                    refreshBranches()
                    return
                }
                refreshBranches()
                loadProject(path)
            }
        }.execute()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildCommitCard(): JPanel {
        fileList.setCellRenderer(FileCheckboxRenderer())
        fileList.addMouseListener(
            object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    val index = fileList.locationToIndex(e.point)
                    if (index < 0 || index >= fileListModel.size) return
                    val file = fileListModel.getElementAt(index)
                    if (file.path in checkedFiles) {
                        checkedFiles.remove(file.path)
                    } else {
                        checkedFiles.add(file.path)
                    }
                    fileList.repaint()
                }
            },
        )

        commitButton.addActionListener { onCommitClicked() }
        cancelButton.addActionListener {
            commitMessageField.text = ""
            commitMessageField.border = null
            logToggle.isSelected = true
            cardLayout.show(cardPanel, "log")
        }

        val bottomPanel =
            JPanel(BorderLayout(4, 0)).apply {
                border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
                add(commitMessageField, BorderLayout.CENTER)
                add(
                    JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                        add(commitButton)
                        add(cancelButton)
                    },
                    BorderLayout.EAST,
                )
            }

        return JPanel(BorderLayout()).apply {
            add(JScrollPane(fileList), BorderLayout.CENTER)
            add(bottomPanel, BorderLayout.SOUTH)
        }
    }

    private fun refreshChangedFiles() {
        val path =
            currentPath ?: run {
                fileListModel.clear()
                return
            }
        object : SwingWorker<List<ChangedFile>, Void>() {
            override fun doInBackground(): List<ChangedFile> = gitService.changedFiles(path)

            override fun done() {
                val files =
                    try {
                        get()
                    } catch (e: Exception) {
                        logger.warn("Failed to load changed files", e)
                        return
                    }
                fileListModel.clear()
                checkedFiles.clear()
                files.forEach {
                    fileListModel.addElement(it)
                    checkedFiles.add(it.path) // all checked by default
                }
            }
        }.execute()
    }

    private fun onCommitClicked() {
        val message = commitMessageField.text.trim()
        if (message.isEmpty()) {
            commitMessageField.border = BorderFactory.createLineBorder(Color.RED)
            return
        }
        commitMessageField.border = null
        val path = currentPath ?: return
        val filesToStage =
            (0 until fileListModel.size)
                .map { fileListModel.getElementAt(it) }
                .filter { it.path in checkedFiles }
                .map { it.path }

        commitButton.isEnabled = false
        cancelButton.isEnabled = false

        object : SwingWorker<Unit, Void>() {
            override fun doInBackground() {
                gitService.stage(path, filesToStage)
                gitService.commit(path, message)
            }

            override fun done() {
                commitButton.isEnabled = true
                cancelButton.isEnabled = true
                try {
                    get()
                } catch (e: Exception) {
                    JOptionPane.showMessageDialog(
                        this@GitLogPanel,
                        e.cause?.message ?: e.message,
                        "Commit failed",
                        JOptionPane.ERROR_MESSAGE,
                    )
                    return
                }
                commitMessageField.text = ""
                logToggle.isSelected = true
                cardLayout.show(cardPanel, "log")
                loadProject(path)
            }
        }.execute()
    }

    private fun buildOutputCard(): JPanel {
        closeButton.addActionListener {
            logToggle.isSelected = true
            cardLayout.show(cardPanel, "log")
            loadProject(currentPath)
        }
        return JPanel(BorderLayout()).apply {
            add(outputLabel, BorderLayout.NORTH)
            add(JScrollPane(outputArea), BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.RIGHT)).apply { add(closeButton) }, BorderLayout.SOUTH)
        }
    }

    private fun runRemoteOp(
        label: String,
        op: (String, (String) -> Unit) -> Int,
    ) {
        val path = currentPath ?: return
        outputLabel.text = "$label\u2026"
        outputArea.text = ""
        closeButton.isEnabled = false
        setRemoteButtonsEnabled(false)
        cardLayout.show(cardPanel, "output")

        object : SwingWorker<Int, String>() {
            override fun doInBackground(): Int = op(path) { line -> publish(line) }

            override fun process(chunks: List<String>) {
                chunks.forEach { outputArea.append("$it\n") }
                outputArea.caretPosition = outputArea.document.length
            }

            override fun done() {
                val exitCode =
                    try {
                        get()
                    } catch (e: Exception) {
                        logger.warn("Git streaming operation failed", e)
                        -1
                    }
                if (exitCode == 0) {
                    outputArea.append("\u2713 Done\n")
                    outputLabel.text = "$label \u2014 Done"
                } else {
                    outputArea.append("\u2717 Failed (exit $exitCode)\n")
                    outputLabel.text = "$label \u2014 Failed"
                }
                closeButton.isEnabled = true
                setRemoteButtonsEnabled(true)
            }
        }.execute()
    }

    private fun setRemoteButtonsEnabled(enabled: Boolean) {
        fetchButton.isEnabled = enabled
        pushButton.isEnabled = enabled
        pullButton.isEnabled = enabled
    }

    private fun showCommit(hash: String) {
        val path = currentPath ?: return
        pendingDiffWorker?.cancel(true)
        pendingDiffWorker =
            object : SwingWorker<DiffResult, Void>() {
                override fun doInBackground(): DiffResult {
                    val raw = gitService.show(path, hash) ?: return DiffResult(emptyList(), DiffStats(0, 0))
                    val truncated = if (raw.length > maxDiffChars) raw.take(maxDiffChars) else raw
                    return DiffParser.parse(truncated)
                }

                override fun done() {
                    if (isCancelled) return
                    val result =
                        try {
                            get()
                        } catch (e: Exception) {
                            logger.warn("Failed to load diff for commit", e)
                            return
                        }
                    onCommitSelected?.invoke(result)
                }
            }.also { it.execute() }
    }

    // ── Cell renderer ─────────────────────────────────────────────────────────

    private class CommitCellRenderer : ListCellRenderer<GitCommit> {
        private val panel =
            JPanel(BorderLayout(6, 0)).apply {
                border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
            }
        private val hashLabel =
            JLabel().apply {
                font = Font(Font.MONOSPACED, Font.PLAIN, 10)
                foreground = Color(0x888888)
            }
        private val subjectLabel =
            JLabel().apply {
                font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
            }

        init {
            panel.add(hashLabel, BorderLayout.WEST)
            panel.add(subjectLabel, BorderLayout.CENTER)
        }

        override fun getListCellRendererComponent(
            list: JList<out GitCommit>,
            value: GitCommit?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            hashLabel.text = value?.hash ?: ""
            subjectLabel.text = value?.subject ?: ""
            val bg = if (isSelected) list.selectionBackground else list.background
            panel.background = bg
            panel.isOpaque = true
            subjectLabel.foreground = if (isSelected) list.selectionForeground else list.foreground
            return panel
        }
    }

    private inner class FileCheckboxRenderer : ListCellRenderer<ChangedFile> {
        private val checkBox = JCheckBox().apply { isOpaque = true }

        override fun getListCellRendererComponent(
            list: JList<out ChangedFile>,
            value: ChangedFile?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val file =
                value ?: run {
                    checkBox.text = ""
                    checkBox.isSelected = false
                    return checkBox
                }
            val badge = file.statusCode.firstOrNull { it != ' ' }?.toString() ?: "?"
            checkBox.text = "[$badge] ${file.path}"
            checkBox.isSelected = file.path in checkedFiles
            checkBox.background = if (isSelected) list.selectionBackground else list.background
            checkBox.foreground = statusColor(file.statusCode)
            return checkBox
        }

        private fun statusColor(statusCode: String): Color = gitStatusColor(statusCode)
    }
}

internal fun gitStatusColor(statusCode: String): java.awt.Color =
    when {
        statusCode.any { it == 'M' } -> java.awt.Color(0x4070C0)
        statusCode.any { it == 'A' } -> java.awt.Color(0x40A040)
        statusCode.any { it == 'D' } -> java.awt.Color(0xC04040)
        else -> java.awt.Color(0x888888)
    }

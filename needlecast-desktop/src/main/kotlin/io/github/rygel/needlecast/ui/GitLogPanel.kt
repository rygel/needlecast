package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.git.ChangedFile
import io.github.rygel.needlecast.git.GitService
import io.github.rygel.needlecast.git.ProcessGitService
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JToggleButton
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingWorker

private data class GitCommit(val hash: String, val subject: String)

/**
 * Git panel with three views switched via a toolbar:
 * - Log: read-only commit history (existing behaviour)
 * - Commit: staging checklist + commit message field
 * - Output: streaming text for fetch/push/pull
 */
class GitLogPanel(private val gitService: GitService = ProcessGitService()) : JPanel(BorderLayout()) {

    // ── Log view ──────────────────────────────────────────────────────────────
    private val logModel = DefaultListModel<GitCommit>()
    private val logList = JList(logModel).apply {
        name = "log-list"
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        setCellRenderer(CommitCellRenderer())
        fixedCellHeight = 28
    }
    private val diffArea = JTextArea().apply {
        name = "diff-area"
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 11)
        lineWrap = false
        border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
    }

    // ── Commit view (wired in Task 4) ─────────────────────────────────────────
    private val fileListModel = DefaultListModel<ChangedFile>()
    private val checkedFiles  = mutableSetOf<String>()
    private val fileList = JList(fileListModel).apply { name = "changed-files-list" }
    private val commitMessageField = JTextField().apply {
        name = "commit-message"
        putClientProperty("JTextField.placeholderText", "Commit message…")
    }
    private val commitButton = JButton("Commit").apply { name = "btn-commit-ok" }
    private val cancelButton = JButton("Cancel").apply { name = "btn-commit-cancel" }

    // ── Output view (wired in Task 5) ─────────────────────────────────────────
    private val outputLabel = JLabel("").apply {
        name = "output-label"
        border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
    }
    private val outputArea = JTextArea().apply {
        name = "output-area"
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 11)
        border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
    }
    private val closeButton = JButton("Close").apply { name = "btn-output-close"; isEnabled = false }

    // ── Toolbar ───────────────────────────────────────────────────────────────
    private val logToggle    = JToggleButton("Log").apply    { name = "toggle-log";    isSelected = true }
    private val commitToggle = JToggleButton("Commit").apply { name = "toggle-commit" }
    private val fetchButton  = JButton("Fetch").apply { name = "btn-fetch" }
    private val pushButton   = JButton("Push").apply  { name = "btn-push"  }
    private val pullButton   = JButton("Pull").apply  { name = "btn-pull"  }

    // ── Card layout ───────────────────────────────────────────────────────────
    private val cardLayout = CardLayout()
    private val cardPanel  = JPanel(cardLayout)

    private var currentPath: String? = null
    private var pendingDiffWorker: SwingWorker<String, Void>? = null
    private val maxDiffChars = 400_000

    init {
        minimumSize = Dimension(0, 0)

        // Log card: existing split pane
        val split = JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            JScrollPane(logList).apply { minimumSize = Dimension(0, 0) },
            JScrollPane(diffArea).apply { minimumSize = Dimension(0, 0) },
        ).apply { resizeWeight = 0.4 }

        logList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val commit = logList.selectedValue ?: return@addListSelectionListener
                showCommit(commit.hash)
            }
        }

        cardPanel.add(split,              "log")
        cardPanel.add(buildCommitCard(), "commit")
        cardPanel.add(JPanel(),          "output")   // placeholder — replaced in Task 5

        ButtonGroup().apply { add(logToggle); add(commitToggle) }
        logToggle.addActionListener    { cardLayout.show(cardPanel, "log") }
        commitToggle.addActionListener { refreshChangedFiles(); cardLayout.show(cardPanel, "commit") }
        fetchButton.addActionListener  { }   // wired in Task 5
        pushButton.addActionListener   { }   // wired in Task 5
        pullButton.addActionListener   { }   // wired in Task 5

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(logToggle); add(commitToggle)
            add(fetchButton); add(pushButton); add(pullButton)
        }

        add(toolbar,   BorderLayout.NORTH)
        add(cardPanel, BorderLayout.CENTER)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun loadProject(path: String?) {
        currentPath = path
        logModel.clear()
        TextChunker.cancel(diffArea)
        diffArea.text = if (path == null) "" else "Loading commits\u2026"
        if (path == null) return

        object : SwingWorker<List<GitCommit>, Void>() {
            override fun doInBackground(): List<GitCommit> =
                gitService.log(path)
                    ?.lines()
                    ?.filter { it.isNotBlank() }
                    ?.mapNotNull { line ->
                        val space = line.indexOf(' ')
                        if (space < 0) null else GitCommit(line.substring(0, space), line.substring(space + 1))
                    }
                    ?: emptyList()

            override fun done() {
                val commits = try { get() } catch (_: Exception) { return }
                commits.forEach { logModel.addElement(it) }
                if (logModel.size > 0) {
                    diffArea.text = "Select a commit to view details."
                    diffArea.caretPosition = 0
                } else {
                    diffArea.text = "No commits found."
                }
            }
        }.execute()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildCommitCard(): JPanel {
        fileList.setCellRenderer(FileCheckboxRenderer())
        fileList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                val index = fileList.locationToIndex(e.point)
                if (index < 0 || index >= fileListModel.size) return
                val file = fileListModel.getElementAt(index)
                if (file.path in checkedFiles) checkedFiles.remove(file.path)
                else checkedFiles.add(file.path)
                fileList.repaint()
            }
        })

        commitButton.addActionListener { onCommitClicked() }
        cancelButton.addActionListener {
            commitMessageField.text = ""
            commitMessageField.border = null
            logToggle.isSelected = true
            cardLayout.show(cardPanel, "log")
        }

        val bottomPanel = JPanel(BorderLayout(4, 0)).apply {
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
            add(commitMessageField, BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                add(commitButton); add(cancelButton)
            }, BorderLayout.EAST)
        }

        return JPanel(BorderLayout()).apply {
            add(JScrollPane(fileList), BorderLayout.CENTER)
            add(bottomPanel,           BorderLayout.SOUTH)
        }
    }

    private fun refreshChangedFiles() {
        val path = currentPath ?: run { fileListModel.clear(); return }
        object : SwingWorker<List<ChangedFile>, Void>() {
            override fun doInBackground(): List<ChangedFile> = gitService.changedFiles(path)
            override fun done() {
                val files = try { get() } catch (_: Exception) { return }
                fileListModel.clear()
                checkedFiles.clear()
                files.forEach {
                    fileListModel.addElement(it)
                    checkedFiles.add(it.path)   // all checked by default
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
        val filesToStage = (0 until fileListModel.size)
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
                loadProject(currentPath)
            }
        }.execute()
    }

    private fun showCommit(hash: String) {
        val path = currentPath ?: return
        pendingDiffWorker?.cancel(true)
        pendingDiffWorker = object : SwingWorker<String, Void>() {
            override fun doInBackground(): String =
                gitService.show(path, hash) ?: "Could not load commit $hash"
            override fun done() {
                if (isCancelled) return
                val text = try { get() } catch (_: Exception) { return }
                val rendered = if (text.length > maxDiffChars) {
                    val omitted = text.length - maxDiffChars
                    text.take(maxDiffChars) + "\n\n[Diff truncated: omitted ${omitted} characters]"
                } else text
                TextChunker.setTextChunked(diffArea, rendered) { diffArea.caretPosition = 0 }
            }
        }.also { it.execute() }
    }

    // ── Cell renderer ─────────────────────────────────────────────────────────

    private class CommitCellRenderer : ListCellRenderer<GitCommit> {
        private val panel = JPanel(BorderLayout(6, 0)).apply {
            border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
        }
        private val hashLabel = JLabel().apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 10)
            foreground = Color(0x888888)
        }
        private val subjectLabel = JLabel().apply {
            font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
        }

        init {
            panel.add(hashLabel,    BorderLayout.WEST)
            panel.add(subjectLabel, BorderLayout.CENTER)
        }

        override fun getListCellRendererComponent(
            list: JList<out GitCommit>, value: GitCommit?,
            index: Int, isSelected: Boolean, cellHasFocus: Boolean,
        ): Component {
            hashLabel.text    = value?.hash    ?: ""
            subjectLabel.text = value?.subject ?: ""
            val bg = if (isSelected) list.selectionBackground else list.background
            panel.background        = bg
            panel.isOpaque          = true
            subjectLabel.foreground = if (isSelected) list.selectionForeground else list.foreground
            return panel
        }
    }

    private inner class FileCheckboxRenderer : ListCellRenderer<ChangedFile> {
        private val checkBox = JCheckBox().apply { isOpaque = true }

        override fun getListCellRendererComponent(
            list: JList<out ChangedFile>, value: ChangedFile?,
            index: Int, isSelected: Boolean, cellHasFocus: Boolean,
        ): Component {
            val file = value ?: return checkBox
            val badge = file.statusCode.trim().firstOrNull()?.toString() ?: "?"
            checkBox.text       = "[$badge] ${file.path}"
            checkBox.isSelected = file.path in checkedFiles
            checkBox.background = if (isSelected) list.selectionBackground else list.background
            checkBox.foreground = statusColor(file.statusCode)
            return checkBox
        }

        private fun statusColor(statusCode: String): Color = when {
            statusCode.any { it == 'M' } -> Color(0x4070C0)   // modified — blue
            statusCode.any { it == 'A' } -> Color(0x40A040)   // added — green
            statusCode.any { it == 'D' } -> Color(0xC04040)   // deleted — red
            else                          -> Color(0x888888)   // untracked / other — grey
        }
    }
}

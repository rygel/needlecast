package io.github.quicklaunch.ui

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingWorker

private data class GitCommit(val hash: String, val subject: String)

/**
 * Read-only git log viewer. Shows the last 40 commits in a list; clicking one
 * displays the full `git show` output in the lower pane.
 */
class GitLogPanel : JPanel(BorderLayout()) {

    private val logModel = DefaultListModel<GitCommit>()
    private val logList = JList(logModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        setCellRenderer(CommitCellRenderer())
        fixedCellHeight = 28
    }
    private val diffArea = JTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 11)
        lineWrap = false
        border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
    }

    private var currentPath: String? = null

    init {
        val split = JSplitPane(JSplitPane.VERTICAL_SPLIT,
            JScrollPane(logList),
            JScrollPane(diffArea),
        ).apply { resizeWeight = 0.4 }

        add(split, BorderLayout.CENTER)

        logList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val commit = logList.selectedValue ?: return@addListSelectionListener
                showCommit(commit.hash)
            }
        }
    }

    fun loadProject(path: String?) {
        currentPath = path
        logModel.clear()
        diffArea.text = ""
        if (path == null) return

        object : SwingWorker<List<GitCommit>, Void>() {
            override fun doInBackground(): List<GitCommit> = runGit(path,
                "log", "--oneline", "--no-decorate", "-40"
            )
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
                if (logModel.size > 0) logList.selectedIndex = 0
            }
        }.execute()
    }

    private fun showCommit(hash: String) {
        val path = currentPath ?: return
        object : SwingWorker<String, Void>() {
            override fun doInBackground(): String =
                runGit(path, "show", "--stat", "-p", hash) ?: "Could not load commit $hash"

            override fun done() {
                val text = try { get() } catch (_: Exception) { return }
                diffArea.text = text
                diffArea.caretPosition = 0
            }
        }.execute()
    }

    private fun runGit(dir: String, vararg args: String): String? = try {
        val proc = ProcessBuilder(listOf("git", "-C", dir) + args.toList())
            .redirectErrorStream(true)
            .start()
        val out = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        out.ifBlank { null }
    } catch (_: Exception) { null }

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
            panel.add(hashLabel, BorderLayout.WEST)
            panel.add(subjectLabel, BorderLayout.CENTER)
        }

        override fun getListCellRendererComponent(
            list: JList<out GitCommit>, value: GitCommit?,
            index: Int, isSelected: Boolean, cellHasFocus: Boolean,
        ): Component {
            hashLabel.text = value?.hash ?: ""
            subjectLabel.text = value?.subject ?: ""
            val bg = if (isSelected) list.selectionBackground else list.background
            panel.background = bg
            subjectLabel.foreground = if (isSelected) list.selectionForeground else list.foreground
            panel.isOpaque = true
            return panel
        }
    }
}

package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.model.ProjectDirectory
import io.github.rygel.needlecast.model.ProjectTreeEntry
import io.github.rygel.needlecast.scanner.IS_MAC
import io.github.rygel.needlecast.scanner.IS_WINDOWS
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.SwingWorker
import javax.swing.tree.DefaultMutableTreeNode

internal class ProjectTreeDialogs(
    private val panel: ProjectTreePanelAccess,
) {
    fun addFolder(parentNode: DefaultMutableTreeNode?) {
        val name =
            JOptionPane
                .showInputDialog(panel.component, "Folder name:", "New Folder", JOptionPane.PLAIN_MESSAGE)
                ?.trim() ?: return
        if (name.isBlank()) return
        val node = DefaultMutableTreeNode(ProjectTreeEntry.Folder(name = name))
        val parent = parentNode ?: panel.rootNode
        panel.treeModel.insertNodeInto(node, parent, parent.childCount)
        panel.tree.expandPath(panel.treePath(parent))
        panel.tree.selectionPath = panel.treePath(node)
        panel.persist()
        panel.updateEmptyState()
    }

    fun addProject(parentNode: DefaultMutableTreeNode?) {
        val startDir =
            panel
                .selectedProjectEntry()
                ?.directory
                ?.path
                ?.let { File(it).parentFile }
                ?: File(System.getProperty("user.home"))
        val chooser =
            JFileChooser(startDir).apply {
                dialogTitle = "Select Project Directory"
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            }
        if (chooser.showOpenDialog(panel.component) != JFileChooser.APPROVE_OPTION) return
        val dir = ProjectDirectory(path = chooser.selectedFile.absolutePath)
        val entry = ProjectTreeEntry.Project(directory = dir)
        val node = DefaultMutableTreeNode(entry)
        val parent = parentNode ?: panel.rootNode
        panel.treeModel.insertNodeInto(node, parent, parent.childCount)
        panel.tree.expandPath(panel.treePath(parent))
        panel.tree.selectionPath = panel.treePath(node)
        panel.persist()
        panel.updateEmptyState()
        val missing = panel.updateMissingPath(dir.path)
        if (!missing) panel.scanProject(dir)
        panel.tree.repaint()
    }

    fun renameFolder(
        node: DefaultMutableTreeNode,
        folder: ProjectTreeEntry.Folder,
    ) {
        val name =
            JOptionPane
                .showInputDialog(panel.component, "Folder name:", "Rename", JOptionPane.PLAIN_MESSAGE, null, null, folder.name)
                ?.toString()
                ?.trim() ?: return
        if (name.isBlank()) return
        node.userObject = folder.copy(name = name)
        panel.treeModel.nodeChanged(node)
        panel.persist()
    }

    fun removeNode(node: DefaultMutableTreeNode) {
        val label =
            when (val e = node.userObject) {
                is ProjectTreeEntry.Folder -> "folder '${e.name}'"
                is ProjectTreeEntry.Project -> "project '${e.directory.label()}'"
                else -> "item"
            }
        if (JOptionPane.showConfirmDialog(
                panel.component,
                "Remove $label from the project list?\n(The directory on disk is not affected.)",
                "Remove",
                JOptionPane.OK_CANCEL_OPTION,
            ) != JOptionPane.OK_OPTION
        ) {
            return
        }
        (node.userObject as? ProjectTreeEntry.Project)?.let { panel.missingPaths.remove(it.directory.path) }
        panel.treeModel.removeNodeFromParent(node)
        panel.onProjectSelected(null)
        panel.persist()
    }

    fun deleteProjectFromDisk(
        node: DefaultMutableTreeNode,
        entry: ProjectTreeEntry.Project,
    ) {
        val dir = File(entry.directory.path)
        val name = entry.directory.label()
        object : SwingWorker<Long, Void>() {
            override fun doInBackground(): Long = runCatching { dir.walkTopDown().count().toLong() }.getOrDefault(-1L)

            override fun done() {
                val fileCount =
                    try {
                        get()
                    } catch (_: Exception) {
                        -1
                    }
                val countLine = if (fileCount >= 0) "Contains: $fileCount files/directories\n\n" else ""
                val confirm =
                    JOptionPane.showConfirmDialog(
                        panel.component,
                        "Permanently delete '$name' from disk?\n\n" +
                            "Path: ${dir.absolutePath}\n" +
                            countLine +
                            "This cannot be undone.",
                        "Delete from Disk",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                    )
                if (confirm != JOptionPane.YES_OPTION) return

                Thread {
                    val deleted = dir.deleteRecursively()
                    SwingUtilities.invokeLater {
                        if (deleted) {
                            panel.treeModel.removeNodeFromParent(node)
                            panel.onProjectSelected(null)
                            panel.persist()
                            panel.updateEmptyState()
                        } else {
                            JOptionPane.showMessageDialog(
                                panel.component,
                                "Could not delete '$name'. Some files may be locked or protected.",
                                "Delete Failed",
                                JOptionPane.ERROR_MESSAGE,
                            )
                        }
                    }
                }.start()
            }
        }.execute()
    }

    fun deleteFolderFromDisk(
        node: DefaultMutableTreeNode,
        entry: ProjectTreeEntry.Folder,
    ) {
        val projects = mutableListOf<String>()

        fun collect(n: javax.swing.tree.TreeNode) {
            val obj = (n as? DefaultMutableTreeNode)?.userObject
            if (obj is ProjectTreeEntry.Project) projects.add(obj.directory.path)
            for (i in 0 until n.childCount) collect(n.getChildAt(i))
        }
        collect(node)

        if (projects.isEmpty()) {
            removeNode(node)
            return
        }

        val dirList = projects.joinToString("\n") { "  - $it" }
        val confirm =
            JOptionPane.showConfirmDialog(
                panel.component,
                "Permanently delete folder '${entry.name}' and all its projects from disk?\n\n" +
                    "Directories that will be deleted:\n$dirList\n\n" +
                    "This cannot be undone.",
                "Delete from Disk",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
            )
        if (confirm != JOptionPane.YES_OPTION) return

        Thread {
            val failures = projects.filter { !File(it).deleteRecursively() }
            SwingUtilities.invokeLater {
                if (failures.isEmpty()) {
                    panel.treeModel.removeNodeFromParent(node)
                    panel.onProjectSelected(null)
                    panel.persist()
                    panel.updateEmptyState()
                } else {
                    JOptionPane.showMessageDialog(
                        panel.component,
                        "Could not delete some directories:\n${failures.joinToString("\n") { "  - $it" }}",
                        "Delete Failed",
                        JOptionPane.ERROR_MESSAGE,
                    )
                    panel.treeModel.removeNodeFromParent(node)
                    panel.onProjectSelected(null)
                    panel.persist()
                    panel.updateEmptyState()
                }
            }
        }.start()
    }

    fun editTags(
        node: DefaultMutableTreeNode,
        entry: ProjectTreeEntry.Project,
    ) {
        val current = entry.tags.joinToString(", ")
        val input =
            JOptionPane
                .showInputDialog(panel.component, "Tags (comma-separated):", "Edit Tags", JOptionPane.PLAIN_MESSAGE, null, null, current)
                ?.toString()
                ?.trim() ?: return
        val tags = input.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        node.userObject = entry.copy(tags = tags)
        panel.treeModel.nodeChanged(node)
        panel.persist()
        panel.tree.repaint()
    }

    fun editShellSettings(
        node: DefaultMutableTreeNode,
        entry: ProjectTreeEntry.Project,
    ) {
        val owner = SwingUtilities.getWindowAncestor(panel.component) ?: return
        val dir = entry.directory
        val shellField = JTextField(dir.shellExecutable ?: "", 30)
        val startupField = JTextField(dir.startupCommand ?: "", 30)
        val defaultShell =
            when {
                IS_WINDOWS -> "cmd.exe"
                IS_MAC -> "/bin/zsh"
                else -> "/bin/bash"
            }
        val form =
            JPanel(GridBagLayout()).apply {
                border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
                val gc =
                    GridBagConstraints().apply {
                        insets = Insets(4, 4, 4, 4)
                        anchor = GridBagConstraints.WEST
                    }
                gc.gridx = 0
                gc.gridy = 0
                gc.weightx = 0.0
                gc.fill = GridBagConstraints.NONE
                add(JLabel("Shell:"), gc)
                gc.gridx = 1
                gc.weightx = 1.0
                gc.fill = GridBagConstraints.HORIZONTAL
                add(shellField, gc)
                gc.gridx = 0
                gc.gridy = 1
                gc.weightx = 0.0
                gc.fill = GridBagConstraints.NONE
                add(JLabel("Startup:"), gc)
                gc.gridx = 1
                gc.weightx = 1.0
                gc.fill = GridBagConstraints.HORIZONTAL
                add(startupField, gc)
                gc.gridx = 0
                gc.gridy = 2
                gc.gridwidth = 2
                gc.fill = GridBagConstraints.HORIZONTAL
                add(
                    JLabel(
                        "<html><small>Shell: e.g. <tt>zsh</tt>, <tt>pwsh</tt> \u2014 blank = <tt>$defaultShell</tt><br>" +
                            "Startup: sent on open, e.g. <tt>conda activate ml</tt></small></html>",
                    ),
                    gc,
                )
            }
        if (JOptionPane.showConfirmDialog(
                owner,
                form,
                "Shell Settings \u2014 ${dir.label(panel.ctx.config.privacyModeEnabled)}",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
            ) != JOptionPane.OK_OPTION
        ) {
            return
        }
        node.userObject =
            entry.copy(
                directory =
                    dir.copy(
                        shellExecutable = shellField.text.trim().takeIf { it.isNotEmpty() },
                        startupCommand = startupField.text.trim().takeIf { it.isNotEmpty() },
                    ),
            )
        panel.treeModel.nodeChanged(node)
        panel.persist()
    }

    fun editEnv(
        node: DefaultMutableTreeNode,
        entry: ProjectTreeEntry.Project,
    ) {
        val owner = SwingUtilities.getWindowAncestor(panel.component) ?: return
        EnvEditorDialog(owner, entry.directory.label(panel.ctx.config.privacyModeEnabled), entry.directory.env) { newEnv ->
            node.userObject = entry.copy(directory = entry.directory.copy(env = newEnv))
            panel.treeModel.nodeChanged(node)
            panel.persist()
        }.isVisible = true
    }

    fun editScriptDirs(
        node: DefaultMutableTreeNode,
        entry: ProjectTreeEntry.Project,
    ) {
        val owner = SwingUtilities.getWindowAncestor(panel.component) ?: return
        val dir = entry.directory
        val listModel = DefaultListModel<String>().apply { dir.extraScanDirs.forEach { addElement(it) } }
        val list =
            JList(listModel).apply {
                selectionMode = ListSelectionModel.SINGLE_SELECTION
                visibleRowCount = 6
            }
        val addBtn = JButton("Add\u2026")
        val removeBtn = JButton("Remove").apply { isEnabled = false }

        list.addListSelectionListener { removeBtn.isEnabled = list.selectedIndex >= 0 }

        addBtn.addActionListener {
            val chooser = JFileChooser(dir.path).apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
            if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) return@addActionListener
            val stored = makeRelativeIfPossible(chooser.selectedFile.canonicalPath, dir.path)
            if ((0 until listModel.size).none { listModel.getElementAt(it) == stored }) listModel.addElement(stored)
        }

        removeBtn.addActionListener {
            val i = list.selectedIndex
            if (i >= 0) {
                listModel.remove(i)
                list.clearSelection()
            }
        }

        val form =
            JPanel(BorderLayout(4, 4)).apply {
                border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
                add(
                    JLabel("<html><small>\"scripts/\" and \"bin/\" in the project root are always scanned automatically.</small></html>"),
                    BorderLayout.NORTH,
                )
                add(JScrollPane(list), BorderLayout.CENTER)
                add(
                    JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                        add(addBtn)
                        add(removeBtn)
                    },
                    BorderLayout.SOUTH,
                )
            }

        if (JOptionPane.showConfirmDialog(
                owner,
                form,
                "Script Directories \u2014 ${dir.label(panel.ctx.config.privacyModeEnabled)}",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
            ) != JOptionPane.OK_OPTION
        ) {
            return
        }

        val newDirs = (0 until listModel.size).map { listModel.getElementAt(it) }
        val updated = dir.copy(extraScanDirs = newDirs)
        node.userObject = entry.copy(directory = updated)
        panel.treeModel.nodeChanged(node)
        panel.persist()
        panel.scanProject(updated)
    }

    fun setProjectColor(
        node: DefaultMutableTreeNode,
        entry: ProjectTreeEntry.Project,
        hex: String?,
    ) {
        node.userObject = entry.copy(directory = entry.directory.copy(color = hex))
        panel.treeModel.nodeChanged(node)
        panel.persist()
        panel.tree.repaint()
    }

    fun setFolderColor(
        node: DefaultMutableTreeNode,
        folder: ProjectTreeEntry.Folder,
        hex: String?,
    ) {
        node.userObject = folder.copy(color = hex)
        panel.treeModel.nodeChanged(node)
        panel.persist()
        panel.tree.repaint()
    }
}

/**
 * Returns [absolute] expressed relative to [base] when [absolute] is inside [base],
 * otherwise returns [absolute] unchanged. Output separators are always `/` so the
 * value is portable across platforms.
 */
internal fun makeRelativeIfPossible(
    absolute: String,
    base: String,
): String {
    val rel =
        File(base)
            .toPath()
            .relativize(File(absolute).toPath())
            .toString()
            .replace(File.separatorChar, '/')
    return if (rel.startsWith("..")) absolute else rel
}

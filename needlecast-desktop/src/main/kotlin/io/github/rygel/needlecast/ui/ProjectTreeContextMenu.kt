package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.model.ProjectTreeEntry
import io.github.rygel.needlecast.ui.util.DesktopUtils
import java.awt.Color
import java.io.File
import javax.swing.JCheckBoxMenuItem
import javax.swing.JColorChooser
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.tree.DefaultMutableTreeNode

internal class ProjectTreeContextMenu(
    private val panel: ProjectTreePanelAccess,
    private val dialogs: ProjectTreeDialogs,
) {
    fun showContextMenu(
        node: DefaultMutableTreeNode,
        x: Int,
        y: Int,
    ) {
        val menu = JPopupMenu()
        when (val entry = node.userObject) {
            is ProjectTreeEntry.Folder -> {
                menu.add(JMenuItem("New Subfolder\u2026").apply { addActionListener { dialogs.addFolder(node) } })
                menu.add(JMenuItem("Add Project\u2026").apply { addActionListener { dialogs.addProject(node) } })
                menu.addSeparator()
                menu.add(JMenuItem("Rename\u2026").apply { addActionListener { dialogs.renameFolder(node, entry) } })
                val folderColorPresets =
                    listOf(
                        "Red" to "#E53935",
                        "Orange" to "#F57C00",
                        "Blue" to "#1565C0",
                        "Green" to "#2E7D32",
                        "Purple" to "#6A1B9A",
                        "Teal" to "#00695C",
                    )
                menu.add(
                    buildColorMenu("Color", entry.color, folderColorPresets) { hex ->
                        dialogs.setFolderColor(node, node.userObject as ProjectTreeEntry.Folder, hex)
                    },
                )
                menu.addSeparator()
                menu.add(JMenuItem("Remove").apply { addActionListener { dialogs.removeNode(node) } })
                menu.add(
                    JMenu("Advanced").apply {
                        add(
                            JMenuItem("Delete from disk\u2026").apply {
                                foreground = Color(0xE53935)
                                addActionListener { dialogs.deleteFolderFromDisk(node, entry) }
                            },
                        )
                    },
                )
            }

            is ProjectTreeEntry.Project -> {
                val detected = panel.scanResults[entry.directory.path]
                val isActive = panel.isActivePath(entry.directory.path)
                if (detected != null && !isActive) {
                    menu.add(
                        JMenuItem("Activate Terminal", RemixIcons.icon("ri-play-circle-line", 12)).apply {
                            addActionListener {
                                panel.onActivate(detected)
                                panel.addActivePath(entry.directory.path)
                                panel.tree.repaint()
                            }
                        },
                    )
                }
                if (isActive) {
                    menu.add(
                        JMenuItem("Deactivate Terminal", RemixIcons.icon("ri-stop-line", 12)).apply {
                            addActionListener {
                                if (detected != null) panel.onDeactivate(detected)
                                panel.removeActivePath(entry.directory.path)
                                panel.tree.repaint()
                            }
                        },
                    )
                }
                if (menu.componentCount > 0) menu.addSeparator()
                val dir = File(entry.directory.path)
                if (dir.exists()) {
                    val label = DesktopUtils.openInFileManagerLabel
                    menu.add(
                        JMenuItem(label).apply {
                            addActionListener { openInFileManager(entry.directory.path) }
                        },
                    )
                    menu.addSeparator()
                }
                val topTags = collectTopTags(10)
                menu.add(
                    JMenu("Tags").apply {
                        topTags.forEach { tag ->
                            add(
                                JCheckBoxMenuItem(tag, tag in entry.tags).apply {
                                    addActionListener {
                                        val cur = node.userObject as? ProjectTreeEntry.Project ?: return@addActionListener
                                        val newTags = if (isSelected) cur.tags + tag else cur.tags.filter { it != tag }
                                        node.userObject = cur.copy(tags = newTags)
                                        panel.treeModel.nodeChanged(node)
                                        panel.persist()
                                        panel.tree.repaint()
                                    }
                                },
                            )
                        }
                        if (topTags.isNotEmpty()) addSeparator()
                        add(JMenuItem("Edit\u2026").apply { addActionListener { dialogs.editTags(node, entry) } })
                    },
                )
                menu.add(
                    JCheckBoxMenuItem("Private", entry.directory.isPrivate).apply {
                        toolTipText = "Hide project name and path when Privacy Mode is on"
                        addActionListener {
                            val cur = node.userObject as? ProjectTreeEntry.Project ?: return@addActionListener
                            node.userObject = cur.copy(directory = cur.directory.copy(isPrivate = isSelected))
                            panel.treeModel.nodeChanged(node)
                            panel.persist()
                            panel.tree.repaint()
                        }
                    },
                )
                menu.add(JMenuItem("Shell Settings\u2026").apply { addActionListener { dialogs.editShellSettings(node, entry) } })
                menu.add(JMenuItem("Environment\u2026").apply { addActionListener { dialogs.editEnv(node, entry) } })
                menu.add(JMenuItem("Script Directories\u2026").apply { addActionListener { dialogs.editScriptDirs(node, entry) } })
                menu.addSeparator()
                val colorPresets =
                    listOf(
                        "Red" to "#E53935",
                        "Orange" to "#F57C00",
                        "Blue" to "#1565C0",
                        "Green" to "#2E7D32",
                        "Purple" to "#6A1B9A",
                        "Teal" to "#00695C",
                    )
                menu.add(
                    buildColorMenu("Color", entry.directory.color, colorPresets) { hex ->
                        dialogs.setProjectColor(node, node.userObject as ProjectTreeEntry.Project, hex)
                    },
                )
                menu.addSeparator()
                menu.add(JMenuItem("Remove").apply { addActionListener { dialogs.removeNode(node) } })
                if (dir.exists()) {
                    menu.add(
                        JMenu("Advanced").apply {
                            add(
                                JMenuItem("Delete from disk\u2026").apply {
                                    foreground = Color(0xE53935)
                                    addActionListener { dialogs.deleteProjectFromDisk(node, entry) }
                                },
                            )
                        },
                    )
                }
            }
        }
        if (menu.componentCount > 0) menu.show(panel.tree, x, y)
    }

    fun showRootContextMenu(
        x: Int,
        y: Int,
    ) {
        val menu = JPopupMenu()
        menu.add(JMenuItem("New Folder\u2026").apply { addActionListener { dialogs.addFolder(null) } })
        menu.add(JMenuItem("Add Project\u2026").apply { addActionListener { dialogs.addProject(null) } })
        menu.show(panel.tree, x, y)
    }

    private fun collectTopTags(count: Int): List<String> {
        val freq = mutableMapOf<String, Int>()

        fun walk(node: DefaultMutableTreeNode) {
            val entry = node.userObject
            if (entry is ProjectTreeEntry.Project) {
                entry.tags.forEach { tag -> freq[tag] = (freq[tag] ?: 0) + 1 }
            }
            for (i in 0 until node.childCount) walk(node.getChildAt(i) as DefaultMutableTreeNode)
        }
        walk(panel.rootNode)
        return freq.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(count)
            .map { it.key }
    }

    private fun openInFileManager(path: String) {
        DesktopUtils.openInFileManager(File(path))
    }

    private fun buildColorMenu(
        title: String,
        currentHex: String?,
        presets: List<Pair<String, String>>,
        onSet: (String?) -> Unit,
    ): JMenu =
        JMenu(title).apply {
            presets.forEach { (label, hex) ->
                add(
                    JMenuItem(label, colorSwatchIcon(hex)).apply {
                        addActionListener { onSet(hex) }
                    },
                )
            }
            addSeparator()
            add(
                JMenuItem("Custom\u2026").apply {
                    addActionListener {
                        val init =
                            currentHex?.let {
                                try {
                                    Color.decode(it)
                                } catch (_: Exception) {
                                    null
                                }
                            }
                        val c = JColorChooser.showDialog(panel.component, title, init) ?: return@addActionListener
                        onSet("#%02X%02X%02X".format(c.red, c.green, c.blue))
                    }
                },
            )
            if (currentHex != null) {
                add(JMenuItem("Clear").apply { addActionListener { onSet(null) } })
            }
        }
}

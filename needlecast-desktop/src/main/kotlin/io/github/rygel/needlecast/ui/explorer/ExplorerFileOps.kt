package io.github.rygel.needlecast.ui.explorer

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.ExternalEditor
import io.github.rygel.needlecast.scanner.IS_WINDOWS
import io.github.rygel.needlecast.ui.util.DesktopUtils
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPopupMenu

data class ExplorerCallbacks(
    val navigateTo: (File) -> Unit,
    val navigateUp: () -> Unit,
    val openFileInTab: (File) -> Unit,
    val reloadDirectory: () -> Unit,
    val currentDir: () -> File,
)

class ExplorerFileOps(
    private val ctx: AppContext,
    private val callbacks: ExplorerCallbacks,
    private val parent: JComponent,
) {
    fun showContextMenu(
        entry: FileEntry,
        x: Int,
        y: Int,
        invoker: JComponent,
    ) {
        val menu = JPopupMenu()
        when (entry) {
            is FileEntry.ParentDir -> {
                menu.add(JMenuItem("Go up").apply { addActionListener { callbacks.navigateUp() } })
            }

            is FileEntry.Dir -> {
                menu.add(JMenuItem("Open").apply { addActionListener { callbacks.navigateTo(entry.file) } })
                menu.addSeparator()
                menu.add(JMenuItem("New File\u2026").apply { addActionListener { createFile(entry.file) } })
                menu.add(JMenuItem("New Folder\u2026").apply { addActionListener { createFolder(entry.file) } })
                menu.addSeparator()
                menu.add(JMenuItem("Rename\u2026").apply { addActionListener { renameEntry(entry.file) } })
                menu.add(JMenuItem("Delete").apply { addActionListener { deleteEntry(entry.file) } })
                menu.add(
                    JMenuItem(
                        DesktopUtils.openInFileManagerLabel,
                    ).apply {
                        icon =
                            io.github.rygel.needlecast.ui.RemixIcons
                                .icon("ri-folder-open-line", 12)
                        addActionListener { DesktopUtils.openInFileManager(entry.file) }
                    },
                )
                menu.addSeparator()
                menu.add(copyPathItem(entry.file))
            }

            is FileEntry.RegularFile -> {
                menu.add(
                    JMenuItem("Open in Editor").apply {
                        addActionListener { callbacks.openFileInTab(entry.file) }
                    },
                )
                val editors = ctx.config.externalEditors
                if (editors.isNotEmpty()) {
                    menu.addSeparator()
                    editors.forEach { editor ->
                        menu.add(
                            JMenuItem("Open with ${editor.name}").apply {
                                addActionListener { openWith(entry.file, editor) }
                            },
                        )
                    }
                }
                menu.addSeparator()
                menu.add(JMenuItem("Rename\u2026").apply { addActionListener { renameEntry(entry.file) } })
                menu.add(JMenuItem("Delete").apply { addActionListener { deleteEntry(entry.file) } })
                menu.add(
                    JMenuItem(
                        DesktopUtils.revealInFileManagerLabel,
                    ).apply {
                        addActionListener { DesktopUtils.revealInFileManager(entry.file) }
                    },
                )
                menu.addSeparator()
                menu.add(copyPathItem(entry.file))
            }
        }
        if (entry is FileEntry.ParentDir) {
            menu.addSeparator()
            menu.add(JMenuItem("New File\u2026").apply { addActionListener { createFile(callbacks.currentDir()) } })
            menu.add(JMenuItem("New Folder\u2026").apply { addActionListener { createFolder(callbacks.currentDir()) } })
        }
        menu.show(invoker, x, y)
    }

    fun createFile(inDir: File) {
        val name = JOptionPane.showInputDialog(parent, "File name:", "New File", JOptionPane.PLAIN_MESSAGE) ?: return
        if (name.isBlank()) return
        val file = File(inDir, name.trim())
        try {
            if (!file.createNewFile()) {
                JOptionPane.showMessageDialog(parent, "File already exists.")
                return
            }
            callbacks.reloadDirectory()
            callbacks.openFileInTab(file)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(parent, "Could not create file: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE)
        }
    }

    fun createFolder(inDir: File) {
        val name = JOptionPane.showInputDialog(parent, "Folder name:", "New Folder", JOptionPane.PLAIN_MESSAGE) ?: return
        if (name.isBlank()) return
        val folder = File(inDir, name.trim())
        if (!folder.mkdir()) {
            JOptionPane.showMessageDialog(parent, "Could not create folder.", "Error", JOptionPane.ERROR_MESSAGE)
        } else {
            callbacks.reloadDirectory()
        }
    }

    fun renameEntry(file: File) {
        val newName = JOptionPane.showInputDialog(parent, "Rename to:", file.name) ?: return
        if (newName.isBlank() || newName == file.name) return
        val dest = File(file.parentFile, newName.trim())
        if (!file.renameTo(dest)) {
            JOptionPane.showMessageDialog(parent, "Rename failed.", "Error", JOptionPane.ERROR_MESSAGE)
        } else {
            callbacks.reloadDirectory()
        }
    }

    fun deleteEntry(file: File) {
        val confirm =
            JOptionPane.showConfirmDialog(
                parent,
                "Delete '${file.name}'?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
            )
        if (confirm != JOptionPane.YES_OPTION) return
        if (!file.deleteRecursively()) {
            JOptionPane.showMessageDialog(parent, "Delete failed.", "Error", JOptionPane.ERROR_MESSAGE)
        } else {
            callbacks.reloadDirectory()
        }
    }

    fun copyPath(file: File) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(file.absolutePath), null)
    }

    fun openWith(
        file: File,
        editor: ExternalEditor,
    ) {
        try {
            val cmd =
                if (IS_WINDOWS) {
                    listOf("cmd", "/c", editor.executable, file.absolutePath)
                } else {
                    listOf(editor.executable, file.absolutePath)
                }
            ProcessBuilder(cmd).start()
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                parent,
                "Failed to launch ${editor.name}: ${e.message}",
                "Launch Error",
                JOptionPane.ERROR_MESSAGE,
            )
        }
    }

    private fun copyPathItem(file: File) =
        JMenuItem("Copy Path").apply {
            addActionListener {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(StringSelection(file.absolutePath), null)
            }
        }
}

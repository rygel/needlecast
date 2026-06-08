package io.github.rygel.needlecast.ui.diff

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

class DiffFileTree : JTree() {
    private val rootNode = DefaultMutableTreeNode("Changed Files")
    private val treeModel = DefaultTreeModel(rootNode)

    var onFileSelected: ((index: Int) -> Unit)? = null
    var onFileDoubleClicked: ((filePath: String) -> Unit)? = null

    private var fileDiffs: List<FileDiff> = emptyList()

    init {
        model = treeModel
        isRootVisible = false
        showsRootHandles = false
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        setCellRenderer(FileNodeRenderer())

        addTreeSelectionListener { event ->
            val node = lastSelectedPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
            val index = node.userObject as? Int ?: return@addTreeSelectionListener
            onFileSelected?.invoke(index)
        }

        addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount != 2) return
                    val path = getPathForLocation(e.x, e.y) ?: return
                    val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                    val index = node.userObject as? Int ?: return
                    if (index < fileDiffs.size) {
                        onFileDoubleClicked?.invoke(fileDiffs[index].filePath)
                    }
                }
            },
        )
    }

    fun setFiles(files: List<FileDiff>) {
        fileDiffs = files
        rootNode.removeAllChildren()
        files.forEachIndexed { index, _ ->
            rootNode.add(DefaultMutableTreeNode(index))
        }
        treeModel.reload()
        if (files.isNotEmpty()) {
            setSelectionRow(0)
        }
    }

    fun selectFile(index: Int) {
        if (index < 0 || index >= fileDiffs.size) return
        setSelectionRow(index)
        scrollRowToVisible(index)
    }

    private inner class FileNodeRenderer : DefaultTreeCellRenderer() {
        private val nameLabel = JLabel().apply { font = Font(Font.SANS_SERIF, Font.PLAIN, 11) }
        private val statsLabel = JLabel().apply { font = Font(Font.MONOSPACED, Font.PLAIN, 10) }
        private val panel =
            JPanel(BorderLayout(4, 0)).apply {
                border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
                isOpaque = true
                add(nameLabel, BorderLayout.CENTER)
                add(statsLabel, BorderLayout.EAST)
            }

        override fun getTreeCellRendererComponent(
            tree: JTree,
            value: Any,
            sel: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ): Component {
            val index = (value as? DefaultMutableTreeNode)?.userObject as? Int
            if (index == null || index >= fileDiffs.size) return panel
            val file = fileDiffs[index]

            val slashIdx = file.filePath.lastIndexOf('/')
            nameLabel.text = if (slashIdx >= 0) file.filePath.substring(slashIdx + 1) else file.filePath

            val stats =
                buildString {
                    if (file.additions > 0) append("+${file.additions}")
                    if (file.additions > 0 && file.deletions > 0) append(" ")
                    if (file.deletions > 0) append("-${file.deletions}")
                }
            statsLabel.text = stats

            val selFg = javax.swing.UIManager.getColor("Tree.selectionForeground")
            val selBg = javax.swing.UIManager.getColor("Tree.selectionBackground")
            nameLabel.foreground = if (sel) selFg else tree.foreground
            statsLabel.foreground = if (sel) selFg else Color(0x88, 0x88, 0x88)
            panel.background = if (sel) selBg else tree.background

            return panel
        }
    }
}

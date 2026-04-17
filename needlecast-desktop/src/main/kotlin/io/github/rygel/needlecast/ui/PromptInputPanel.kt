package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.PromptTemplate
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Window
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JMenuItem
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * Compact dockable panel with a categorised prompt tree on the left and an
 * editable text area on the right.
 *
 * - Selecting a prompt leaf loads its body into the text area.
 * - The "+" / "−" toolbar buttons (and right-click context menu) create / delete prompts.
 * - "Send to Terminal" handles {variable} substitution then writes to the active terminal.
 */
class PromptInputPanel(
    private val ctx: AppContext,
    private val sendToTerminal: (String) -> Unit,
    sendButtonLabel: String = "Send to Terminal",
    private val itemLabel: String = "Prompt",
    private val isCommand: Boolean = false,
) : JPanel(BorderLayout()) {

    // ── Tree ──────────────────────────────────────────────────────────────────

    private val rootNode  = DefaultMutableTreeNode("Prompts")
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree      = JTree(treeModel).apply {
        isRootVisible = false
        showsRootHandles = true
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        cellRenderer = PromptTreeCellRenderer()
    }

    // ── Right-side controls ───────────────────────────────────────────────────

    private val textArea = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
    }
    private val sendButton   = JButton(sendButtonLabel)

    // ── Toolbar buttons ───────────────────────────────────────────────────────

    private val newButton = JButton("+").apply {
        isFocusable = false
        margin = Insets(1, 5, 1, 5)
    }
    private val editButton = JButton("\u270F").apply {   // ✏ (pencil)
        isFocusable = false
        isEnabled   = false
        margin = Insets(1, 5, 1, 5)
    }
    private val deleteButton = JButton("\u2212").apply {   // − (minus sign)
        isFocusable = false
        isEnabled   = false
        margin = Insets(1, 5, 1, 5)
    }

    // ─────────────────────────────────────────────────────────────────────────

    init {
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
            isOpaque = false
            add(newButton)
            add(editButton)
            add(deleteButton)
        }
        val leftPanel = JPanel(BorderLayout(0, 2)).apply {
            border = BorderFactory.createEmptyBorder(4, 4, 4, 2)
            add(toolbar,               BorderLayout.NORTH)
            add(JScrollPane(tree),     BorderLayout.CENTER)
        }

        val buttonBar = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 2)).apply {
            isOpaque = false
            add(sendButton)
        }
        val rightPanel = JPanel(BorderLayout(0, 2)).apply {
            border = BorderFactory.createEmptyBorder(4, 2, 4, 4)
            add(JScrollPane(textArea), BorderLayout.CENTER)
            add(buttonBar,             BorderLayout.SOUTH)
        }

        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel).apply {
            dividerLocation    = 200
            resizeWeight       = 0.0
            isContinuousLayout = true
        }
        add(split, BorderLayout.CENTER)

        newButton.toolTipText    = "New $itemLabel"
        editButton.toolTipText   = "Edit selected $itemLabel"
        deleteButton.toolTipText = "Delete selected $itemLabel"

        refreshTree(currentLibrary())
        wireListeners()
    }

    // ── Tree population ───────────────────────────────────────────────────────

    private fun refreshTree(templates: List<PromptTemplate>) {
        // Remember selection so we can restore it after refresh
        val previousId = selectedPrompt()?.id

        rootNode.removeAllChildren()
        val byCategory = templates.groupBy { it.category.ifBlank { "Uncategorized" } }
        byCategory.keys.sortedWith(compareBy { if (it == "Uncategorized") "\uFFFF" else it })
            .forEach { cat ->
                val catNode = DefaultMutableTreeNode(cat)
                byCategory[cat]!!.sortedBy { it.name }.forEach { pt ->
                    catNode.add(DefaultMutableTreeNode(pt))
                }
                rootNode.add(catNode)
            }
        treeModel.reload()

        // Re-expand all category nodes
        for (i in 0 until tree.rowCount) tree.expandRow(i)

        // Restore selection
        if (previousId != null) {
            for (i in 0 until rootNode.childCount) {
                val catNode = rootNode.getChildAt(i) as DefaultMutableTreeNode
                for (j in 0 until catNode.childCount) {
                    val leaf = catNode.getChildAt(j) as DefaultMutableTreeNode
                    if ((leaf.userObject as? PromptTemplate)?.id == previousId) {
                        val path = TreePath(arrayOf(rootNode, catNode, leaf))
                        tree.selectionPath = path
                        tree.scrollPathToVisible(path)
                        return
                    }
                }
            }
        }
    }

    // ── Listener wiring ───────────────────────────────────────────────────────

    private fun wireListeners() {
        tree.addTreeSelectionListener {
            val prompt = selectedPrompt()
            editButton.isEnabled   = prompt != null
            deleteButton.isEnabled = prompt != null
            if (prompt != null) textArea.text = prompt.body
        }

        newButton.addActionListener    { createNewPrompt() }
        editButton.addActionListener   { editSelectedPrompt() }
        deleteButton.addActionListener { deleteSelectedPrompt() }
        sendButton.addActionListener   { doSend() }

        tree.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    val row = tree.getRowForLocation(e.x, e.y)
                    if (row >= 0) tree.setSelectionRow(row)
                    showContextMenu(e)
                }
            }
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    if (selectedPrompt() != null) editSelectedPrompt()
                }
            }
        })
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private fun showContextMenu(e: MouseEvent) {
        val hasSelection = selectedPrompt() != null
        val menu = JPopupMenu()
        menu.add(JMenuItem("New $itemLabel\u2026").apply {
            addActionListener { createNewPrompt() }
        })
        menu.add(JMenuItem("Edit\u2026").apply {
            isEnabled = hasSelection
            addActionListener { editSelectedPrompt() }
        })
        menu.add(JMenuItem("Delete").apply {
            isEnabled = hasSelection
            addActionListener { deleteSelectedPrompt() }
        })
        menu.show(tree, e.x, e.y)
    }

    private fun createNewPrompt() {
        val owner  = SwingUtilities.getWindowAncestor(this)
        val dialog = NewPromptDialog(owner, "New $itemLabel")
        dialog.isVisible = true
        val template = dialog.result ?: return
        ctx.promptLibraryStore.save(template, isCommand)
        refreshTree(currentLibrary())
    }

    private fun editSelectedPrompt() {
        val existing = selectedPrompt() ?: return
        val owner    = SwingUtilities.getWindowAncestor(this)
        val dialog   = NewPromptDialog(owner, "Edit $itemLabel", existing)
        dialog.isVisible = true
        val updated = dialog.result ?: return
        ctx.promptLibraryStore.save(updated, isCommand)
        refreshTree(currentLibrary())
    }

    private fun deleteSelectedPrompt() {
        val prompt  = selectedPrompt() ?: return
        val confirm = JOptionPane.showConfirmDialog(
            this,
            "Delete \"${prompt.name}\"?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE,
        )
        if (confirm != JOptionPane.YES_OPTION) return
        ctx.promptLibraryStore.delete(prompt, isCommand)
        textArea.text = ""
        refreshTree(currentLibrary())
    }

    private fun doSend() {
        val body = textArea.text.takeIf { it.isNotBlank() } ?: return
        val vars = Regex("""\{(\w+)}""").findAll(body)
            .map { it.groupValues[1] }.distinct().toList()
        val resolved = if (vars.isEmpty()) {
            body
        } else {
            val owner = SwingUtilities.getWindowAncestor(this)
            var result: Map<String, String>? = null
            VariableResolutionDialog(owner, vars) { result = it }.isVisible = true
            val substitutions = result ?: return
            var out = body
            substitutions.forEach { (k, v) -> out = out.replace("{$k}", v) }
            out
        }
        sendToTerminal(resolved)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun currentLibrary(): List<PromptTemplate> =
        if (isCommand) ctx.promptLibraryStore.loadCommands() else ctx.promptLibraryStore.loadPrompts()

    private fun selectedPrompt(): PromptTemplate? {
        val node = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode ?: return null
        return node.userObject as? PromptTemplate
    }

    // ── Cell renderer ─────────────────────────────────────────────────────────

    private inner class PromptTreeCellRenderer : DefaultTreeCellRenderer() {
        private val boldFont: Font by lazy { font.deriveFont(Font.BOLD, 12f) }
        private val plainFont: Font by lazy { font.deriveFont(Font.PLAIN, 12f) }

        override fun getTreeCellRendererComponent(
            tree: JTree, value: Any?, sel: Boolean, expanded: Boolean,
            leaf: Boolean, row: Int, hasFocus: Boolean,
        ): Component {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
            val node = value as? DefaultMutableTreeNode ?: return this
            when (val obj = node.userObject) {
                is PromptTemplate -> {
                    text = obj.name
                    font = plainFont
                    icon = leafIcon
                }
                else -> {
                    // Category node — already uses node.toString() via super; make it bold
                    font  = boldFont
                    icon  = if (expanded) openIcon else closedIcon
                }
            }
            return this
        }
    }
}

// ── New-prompt dialog ─────────────────────────────────────────────────────────

/**
 * Lightweight modal dialog for creating a new [PromptTemplate].
 * [result] is non-null only when the user clicks OK with a non-empty name.
 */
private class NewPromptDialog(
    owner: Window?,
    title: String = "New Prompt",
    private val existing: PromptTemplate? = null,
) : JDialog(owner, title, ModalityType.APPLICATION_MODAL) {

    var result: PromptTemplate? = null
        private set

    private val nameField     = JTextField(30)
    private val categoryField = JTextField(30)
    private val bodyArea      = JTextArea(6, 40).apply {
        lineWrap = true
        wrapStyleWord = true
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
    }

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        minimumSize = Dimension(480, 300)
        setLocationRelativeTo(owner)

        // Pre-fill fields when editing an existing prompt
        if (existing != null) {
            nameField.text     = existing.name
            categoryField.text = existing.category
            bodyArea.text      = existing.body
        }

        val grid = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(12, 12, 8, 12)
        }
        val gc = GridBagConstraints().apply { insets = Insets(4, 4, 4, 4) }

        fun row(r: Int, label: String, field: Component, fill: Boolean = false) {
            gc.gridy = r; gc.gridx = 0; gc.weightx = 0.0
            gc.anchor = GridBagConstraints.NORTHWEST; gc.fill = GridBagConstraints.NONE; gc.weighty = 0.0
            grid.add(JLabel(label), gc)
            gc.gridx = 1; gc.weightx = 1.0
            gc.fill   = if (fill) GridBagConstraints.BOTH else GridBagConstraints.HORIZONTAL
            if (fill) gc.weighty = 1.0
            grid.add(field, gc)
        }

        row(0, "Name:",     nameField)
        row(1, "Category:", categoryField)
        row(2, "Body:",     JScrollPane(bodyArea), fill = true)

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
        val name = nameField.text.trim()
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name must not be empty.", "Validation", JOptionPane.WARNING_MESSAGE)
            return
        }
        result = PromptTemplate(
            id       = existing?.id ?: java.util.UUID.randomUUID().toString(),
            name     = name,
            category = categoryField.text.trim(),
            body     = bodyArea.text,
        )
        dispose()
    }
}

package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.model.PromptTemplate
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Window
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Manages a template library (prompts or CLI commands). Opens as a modeless dialog so the user
 * can keep it open while working in the terminal.
 *
 * @param sendButtonLabel Label of the "send to terminal" action button.
 * @param sendToTerminal  Lambda that writes text to the currently-active terminal.
 */
class PromptLibraryDialog(
    owner: Window,
    private val ctx: AppContext,
    private val sendToTerminal: (String) -> Unit,
    title: String = "Prompt Library",
    private val sendButtonLabel: String = "Paste to Terminal",
    private val isCommand: Boolean = false,
) : JDialog(owner, title, ModalityType.MODELESS) {

    // ── List-side state ───────────────────────────────────────────────────
    private val listModel = DefaultListModel<PromptTemplate>()
    private val promptList = JList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        fixedCellHeight = 28
        cellRenderer = PromptListCellRenderer()
    }
    private val searchField = JTextField()

    // ── Form-side fields ──────────────────────────────────────────────────
    private val nameField     = JTextField()
    private val categoryField = JTextField()
    private val descField     = JTextField()
    private val bodyArea      = JTextArea(10, 40).apply {
        lineWrap = true
        wrapStyleWord = true
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
    }

    // ── Action bar ────────────────────────────────────────────────────────
    private val saveButton   = JButton("Save")
    private val pasteButton  = JButton(sendButtonLabel)
    private val newButton    = JButton("New")
    private val deleteButton = JButton("Delete").apply { isEnabled = false }

    /** The template currently loaded in the form; null = new (unsaved) draft. */
    private var editing: PromptTemplate? = null

    init {
        size = Dimension(860, 560)
        minimumSize = Dimension(700, 420)
        defaultCloseOperation = DISPOSE_ON_CLOSE
        setLocationRelativeTo(owner)

        contentPane = buildLayout()
        populateList(currentLibrary())
        wireListeners()
    }

    // ─────────────────────────────────────────────────────────────────────
    // Layout
    // ─────────────────────────────────────────────────────────────────────

    private fun buildLayout(): JPanel {
        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildLeftPanel(), buildRightPanel()).apply {
            dividerLocation = 260
            resizeWeight    = 0.3
        }
        return JPanel(BorderLayout(0, 0)).apply {
            add(split,          BorderLayout.CENTER)
            add(buildActionBar(), BorderLayout.SOUTH)
        }
    }

    private fun buildLeftPanel(): JPanel {
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(newButton)
            add(deleteButton)
        }
        val top = JPanel(BorderLayout(0, 4)).apply {
            border = BorderFactory.createEmptyBorder(0, 0, 4, 0)
            add(JLabel("Search:"), BorderLayout.NORTH)
            add(searchField,       BorderLayout.CENTER)
        }
        return JPanel(BorderLayout(0, 4)).apply {
            border = BorderFactory.createEmptyBorder(8, 8, 8, 4)
            add(top,                     BorderLayout.NORTH)
            add(JScrollPane(promptList), BorderLayout.CENTER)
            add(toolbar,                 BorderLayout.SOUTH)
        }
    }

    private fun buildRightPanel(): JPanel {
        val grid = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(8, 4, 8, 8)
        }
        val gc = GridBagConstraints().apply { insets = Insets(4, 4, 4, 4) }

        fun addRow(row: Int, label: String, field: java.awt.Component, fillH: Boolean = false) {
            gc.gridy = row; gc.gridx = 0; gc.weightx = 0.0
            gc.anchor = GridBagConstraints.NORTHWEST
            gc.fill   = GridBagConstraints.NONE
            gc.weighty = 0.0
            grid.add(JLabel(label), gc)

            gc.gridx = 1; gc.weightx = 1.0
            gc.fill = if (fillH) GridBagConstraints.BOTH else GridBagConstraints.HORIZONTAL
            if (fillH) gc.weighty = 1.0
            grid.add(field, gc)
        }

        addRow(0, "Name:",        nameField)
        addRow(1, "Category:",    categoryField)
        addRow(2, "Description:", descField)
        addRow(3, "Body:",        JScrollPane(bodyArea), fillH = true)

        val hint = JLabel(
            "<html><i>Use <b>{varName}</b> placeholders — you will be prompted to fill them in before pasting.</i></html>"
        ).apply { border = BorderFactory.createEmptyBorder(0, 0, 4, 0) }

        return JPanel(BorderLayout(0, 4)).apply {
            border = BorderFactory.createEmptyBorder(4, 0, 0, 0)
            add(hint, BorderLayout.NORTH)
            add(grid, BorderLayout.CENTER)
        }
    }

    private fun buildActionBar(): JPanel =
        JPanel(FlowLayout(FlowLayout.RIGHT, 8, 6)).apply {
            border = BorderFactory.createMatteBorder(1, 0, 0, 0, Color(0x555555))
            add(pasteButton)
            add(saveButton)
        }

    // ─────────────────────────────────────────────────────────────────────
    // Listeners
    // ─────────────────────────────────────────────────────────────────────

    private fun wireListeners() {
        promptList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val sel = promptList.selectedValue
                deleteButton.isEnabled = sel != null
                loadIntoForm(sel)
            }
        }

        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = applyFilter()
            override fun removeUpdate(e: DocumentEvent) = applyFilter()
            override fun changedUpdate(e: DocumentEvent) {}
        })

        newButton.addActionListener    { startNewDraft() }
        deleteButton.addActionListener { deleteSelected() }
        saveButton.addActionListener   { saveCurrentForm() }
        pasteButton.addActionListener  { pasteToTerminal() }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Actions
    // ─────────────────────────────────────────────────────────────────────

    private fun startNewDraft() {
        editing = null
        promptList.clearSelection()
        clearForm()
        nameField.requestFocusInWindow()
    }

    private fun loadIntoForm(template: PromptTemplate?) {
        editing = template
        if (template == null) { clearForm(); return }
        nameField.text     = template.name
        categoryField.text = template.category
        descField.text     = template.description
        bodyArea.text      = template.body
        bodyArea.caretPosition = 0
    }

    private fun clearForm() {
        nameField.text     = ""
        categoryField.text = ""
        descField.text     = ""
        bodyArea.text      = ""
    }

    private fun saveCurrentForm() {
        val name = nameField.text.trim()
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(
                this, "Name must not be empty.", "Validation", JOptionPane.WARNING_MESSAGE,
            )
            return
        }
        val updated = (editing ?: PromptTemplate()).copy(
            name        = name,
            category    = categoryField.text.trim(),
            description = descField.text.trim(),
            body        = bodyArea.text,
        )
        ctx.promptLibraryStore.save(updated, isCommand)
        editing = updated
        populateList(currentLibrary())
        for (i in 0 until listModel.size) {
            if (listModel.getElementAt(i).id == updated.id) { promptList.selectedIndex = i; break }
        }
    }

    private fun deleteSelected() {
        val sel = promptList.selectedValue ?: return
        val confirm = JOptionPane.showConfirmDialog(
            this, "Delete \"${sel.name}\"?", "Confirm Delete",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
        )
        if (confirm != JOptionPane.YES_OPTION) return
        ctx.promptLibraryStore.delete(sel, isCommand)
        editing = null
        populateList(currentLibrary())
        clearForm()
        deleteButton.isEnabled = false
    }

    private fun pasteToTerminal() {
        val body = bodyArea.text
        if (body.isBlank()) return

        val vars = extractVariables(body)
        val resolvedBody = if (vars.isEmpty()) {
            body
        } else {
            val resolved = resolveVariables(vars) ?: return
            applySubstitutions(body, resolved)
        }
        sendToTerminal(resolvedBody)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Variable substitution
    // ─────────────────────────────────────────────────────────────────────

    /** Returns distinct placeholder names found in [text], e.g. `{projectName}` → `"projectName"`. */
    private fun extractVariables(text: String): List<String> =
        Regex("""\{(\w+)}""").findAll(text).map { it.groupValues[1] }.distinct().toList()

    /**
     * Opens [VariableResolutionDialog] for the given [vars].
     * Returns the resolved map, or null if the user cancelled.
     */
    private fun resolveVariables(vars: List<String>): Map<String, String>? {
        var result: Map<String, String>? = null
        VariableResolutionDialog(this, vars) { result = it }.isVisible = true
        return result
    }

    private fun applySubstitutions(body: String, values: Map<String, String>): String {
        var out = body
        values.forEach { (k, v) -> out = out.replace("{$k}", v) }
        return out
    }

    // ─────────────────────────────────────────────────────────────────────
    // List helpers
    // ─────────────────────────────────────────────────────────────────────

    private fun currentLibrary(): List<PromptTemplate> =
        if (isCommand) ctx.promptLibraryStore.loadCommands() else ctx.promptLibraryStore.loadPrompts()

    private fun applyFilter() {
        val query = searchField.text.trim().lowercase()
        val all = currentLibrary()
        val filtered = if (query.isEmpty()) all
                       else all.filter {
                           it.name.lowercase().contains(query) ||
                           it.category.lowercase().contains(query) ||
                           it.description.lowercase().contains(query)
                       }
        populateList(filtered)
    }

    private fun populateList(templates: List<PromptTemplate>) {
        val previousId = promptList.selectedValue?.id
        listModel.clear()
        templates.forEach { listModel.addElement(it) }
        if (previousId != null) {
            for (i in 0 until listModel.size) {
                if (listModel.getElementAt(i).id == previousId) { promptList.selectedIndex = i; break }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Cell renderer
    // ─────────────────────────────────────────────────────────────────────

    private inner class PromptListCellRenderer : ListCellRenderer<PromptTemplate> {
        private val panel     = JPanel(BorderLayout(4, 0)).apply {
            border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
        }
        private val nameLabel = JLabel().apply { font = font.deriveFont(Font.BOLD, 12f) }
        private val catLabel  = JLabel().apply { font = font.deriveFont(Font.PLAIN, 10f) }

        init {
            panel.add(nameLabel, BorderLayout.CENTER)
            panel.add(catLabel,  BorderLayout.EAST)
        }

        override fun getListCellRendererComponent(
            list: JList<out PromptTemplate>,
            value: PromptTemplate?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): java.awt.Component {
            nameLabel.text = value?.name ?: ""
            catLabel.text  = value?.category?.takeIf { it.isNotEmpty() }?.let { "[$it]" } ?: ""
            val bg = if (isSelected) list.selectionBackground else list.background
            panel.background = bg
            panel.isOpaque   = true
            nameLabel.foreground = if (isSelected) list.selectionForeground else list.foreground
            catLabel.foreground  = if (isSelected) list.selectionForeground else Color(0x888888)
            return panel
        }
    }
}

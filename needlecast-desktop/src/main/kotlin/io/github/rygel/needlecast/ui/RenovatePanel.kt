package io.github.rygel.needlecast.ui

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.rygel.needlecast.process.ProcessExecutor
import io.github.rygel.needlecast.scanner.IS_WINDOWS
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.io.File
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.SwingWorker
import javax.swing.JTextArea
import javax.swing.JCheckBox
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * Dockable panel for running Renovate locally against the active project.
 *
 * Scans for outdated dependencies, shows results in a table with checkboxes,
 * and applies selected updates by replacing version strings in project files.
 */
class RenovatePanel : JPanel(BorderLayout()) {

    /** One row in the updates table. Carries everything needed to apply the update. */
    private data class DepUpdate(
        val manager: String,
        val packageFile: String,
        val depName: String,
        val currentValue: String,
        val newValue: String,
        val updateType: String,
        /** Maven property name, e.g. "jackson.version". Empty for non-shared deps. */
        val sharedVariableName: String,
        /** Byte offset in the file where the version string lives. -1 if unavailable. */
        val fileReplacePosition: Int,
        /** For Dockerfiles: the full string to search/replace (e.g. "maven:3.9-temurin"). */
        val replaceString: String,
        /** The template for generating the new replace string. */
        val autoReplaceTemplate: String,
        /** New digest for pinDigest updates. */
        val newDigest: String,
        var selected: Boolean = true,
    )

    private val statusLabel = JLabel("", SwingConstants.LEFT)
    private val projectLabel = JLabel("No project selected").apply {
        font = font.deriveFont(Font.ITALIC)
    }

    private var currentProjectPath: String? = null
    private var updates = mutableListOf<DepUpdate>()

    private val tableModel = object : AbstractTableModel() {
        private val columns = arrayOf("", "Manager", "Dependency", "Current", "Available", "Type")
        override fun getRowCount() = updates.size
        override fun getColumnCount() = columns.size
        override fun getColumnName(col: Int) = columns[col]
        override fun getColumnClass(col: Int) = if (col == 0) java.lang.Boolean::class.java else String::class.java
        override fun isCellEditable(row: Int, col: Int) = col == 0
        override fun getValueAt(row: Int, col: Int): Any {
            val u = updates[row]
            return when (col) {
                0 -> u.selected
                1 -> u.manager
                2 -> u.depName
                3 -> u.currentValue
                4 -> u.newValue
                5 -> u.updateType
                else -> ""
            }
        }
        override fun setValueAt(value: Any?, row: Int, col: Int) {
            if (col == 0 && value is Boolean) {
                updates[row] = updates[row].copy(selected = value)
                fireTableCellUpdated(row, col)
                updateApplyButton()
            }
        }
    }

    private val table = JTable(tableModel).apply {
        font = Font(monoFont(), Font.PLAIN, 12)
        rowHeight = 22
        autoCreateRowSorter = true
        setDefaultRenderer(Object::class.java, UpdateTypeCellRenderer())
        columnModel.getColumn(0).preferredWidth = 30
        columnModel.getColumn(0).maxWidth = 30
        columnModel.getColumn(1).preferredWidth = 100
        columnModel.getColumn(2).preferredWidth = 280
        columnModel.getColumn(3).preferredWidth = 110
        columnModel.getColumn(4).preferredWidth = 110
        columnModel.getColumn(5).preferredWidth = 70
    }

    private val runButton = JButton("Scan for Updates").apply {
        addActionListener { runLocalScan() }
    }

    private val applyButton = JButton("Apply Selected").apply {
        isEnabled = false
        addActionListener { applySelected() }
    }

    private val selectAllButton = JButton("All").apply {
        toolTipText = "Select all updates"
        addActionListener {
            updates.forEach { it.selected = true }
            tableModel.fireTableDataChanged()
            updateApplyButton()
        }
    }

    private val selectNoneButton = JButton("None").apply {
        toolTipText = "Deselect all updates"
        addActionListener {
            updates.forEach { it.selected = false }
            tableModel.fireTableDataChanged()
            updateApplyButton()
        }
    }

    private val selectPatchButton = JButton("Patch only").apply {
        toolTipText = "Select only patch updates (safest)"
        addActionListener {
            updates.forEach { it.selected = it.updateType == "patch" }
            tableModel.fireTableDataChanged()
            updateApplyButton()
        }
    }

    private val summaryLabel = JLabel(" ").apply {
        border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
    }
    private val defaultSummaryFg: Color = summaryLabel.foreground
    private val logArea = JTextArea().apply {
        isEditable = false
        lineWrap = false
        wrapStyleWord = false
        font = Font(monoFont(), Font.PLAIN, 11)
    }
    private val logScroll = JScrollPane(logArea).apply {
        preferredSize = Dimension(0, 140)
        isVisible = false
    }
    private val showLogsCheck = JCheckBox("Show logs").apply {
        addActionListener {
            logScroll.isVisible = isSelected
            revalidate()
        }
    }
    private val verboseLogsCheck = JCheckBox("Verbose").apply {
        toolTipText = "Include DEBUG output from Renovate"
    }

    init {
        minimumSize = Dimension(0, 0)

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
            add(runButton)
            add(applyButton)
            add(JLabel("  Select:"))
            add(selectAllButton)
            add(selectNoneButton)
            add(selectPatchButton)
            add(showLogsCheck)
            add(verboseLogsCheck)
            add(statusLabel)
        }

        val projectBar = JPanel(FlowLayout(FlowLayout.LEFT, 8, 2)).apply {
            add(JLabel("Project:"))
            add(projectLabel)
        }

        val header = JPanel(BorderLayout()).apply {
            add(projectBar, BorderLayout.NORTH)
            add(toolbar, BorderLayout.SOUTH)
        }

        add(header, BorderLayout.NORTH)
        add(JScrollPane(table), BorderLayout.CENTER)
        add(JPanel(BorderLayout()).apply {
            add(summaryLabel, BorderLayout.NORTH)
            add(logScroll, BorderLayout.CENTER)
        }, BorderLayout.SOUTH)

        checkRenovateStatus()
    }

    /** Called when the active project changes. */
    fun loadProject(path: String?) {
        currentProjectPath = path
        if (path != null) {
            projectLabel.text = path
            projectLabel.font = projectLabel.font.deriveFont(Font.PLAIN)
        } else {
            projectLabel.text = "No project selected"
            projectLabel.font = projectLabel.font.deriveFont(Font.ITALIC)
        }
        updates.clear()
        tableModel.fireTableDataChanged()
        summaryLabel.text = " "
        updateApplyButton()
    }

    private fun updateApplyButton() {
        val count = updates.count { it.selected }
        applyButton.isEnabled = count > 0
        applyButton.text = if (count > 0) "Apply Selected ($count)" else "Apply Selected"
    }

    // ── Scan ─────────────────────────────────────────────────────────────

    private fun runLocalScan() {
        val dir = currentProjectPath
        if (dir == null) {
            summaryLabel.text = "No project selected. Select a project in the tree first."
            return
        }

        val reportFile = File(dir, "target/renovate-report.json")
        reportFile.parentFile?.mkdirs()

        setButtonsEnabled(false)
        updates.clear()
        tableModel.fireTableDataChanged()
        summaryLabel.text = "Scanning\u2026"
        logArea.text = ""

        object : SwingWorker<List<DepUpdate>?, String>() {
            private var scanError: String? = null

            override fun doInBackground(): List<DepUpdate>? {
                val reportPath = reportFile.absolutePath.replace('\\', '/')
                val cmd = "renovate --platform=local --report-type=file --report-path=\"$reportPath\""
                val logLevel = if (verboseLogsCheck.isSelected) "debug" else "info"
                publish("[cmd] $cmd")
                publish("[env] LOG_LEVEL=$logLevel")
                val argv = if (IS_WINDOWS) listOf("cmd", "/c", cmd) else listOf("sh", "-c", cmd)
                val pb = ProcessBuilder(argv).redirectErrorStream(true)
                pb.environment()["PATH"] = System.getenv("PATH") ?: ""
                pb.environment()["LOG_LEVEL"] = logLevel
                pb.directory(File(dir))
                val proc = pb.start()
                proc.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) publish(line)
                }
                val exitCode = proc.waitFor()
                publish("[exit] renovate exited with code $exitCode")
                if (exitCode != 0 || !reportFile.exists()) {
                    scanError = "Renovate failed (exit code $exitCode) — enable logs to see details."
                    return null
                }
                return parseReport(reportFile)
            }

            override fun process(chunks: List<String>) {
                for (line in chunks) {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        logArea.append(trimmed + "\n")
                        logArea.caretPosition = logArea.document.length
                    }
                    if (trimmed.startsWith("INFO:") || trimmed.startsWith("WARN:") || trimmed.startsWith("DEBUG:")) {
                        summaryLabel.text = trimmed.take(120)
                    }
                }
            }

            override fun done() {
                val result = try { get() } catch (e: Exception) {
                    summaryLabel.foreground = Color(0xF44336)
                    summaryLabel.text = "Error: ${e.cause?.message ?: e.message}"
                    setButtonsEnabled(true)
                    return
                }
                if (result == null) {
                    summaryLabel.foreground = Color(0xF44336)
                    summaryLabel.text = scanError ?: "Renovate scan failed."
                    setButtonsEnabled(true)
                    reportFile.delete()
                    return
                }
                summaryLabel.foreground = defaultSummaryFg
                updates.clear()
                updates.addAll(result)
                tableModel.fireTableDataChanged()

                if (updates.isEmpty()) {
                    summaryLabel.text = "All dependencies are up to date."
                } else {
                    val major = updates.count { it.updateType == "major" }
                    val minor = updates.count { it.updateType == "minor" }
                    val patch = updates.count { it.updateType == "patch" }
                    val other = updates.size - major - minor - patch
                    val parts = mutableListOf<String>()
                    if (major > 0) parts += "$major major"
                    if (minor > 0) parts += "$minor minor"
                    if (patch > 0) parts += "$patch patch"
                    if (other > 0) parts += "$other other"
                    summaryLabel.text = "${updates.size} updates available: ${parts.joinToString(", ")}"
                }
                setButtonsEnabled(true)
                updateApplyButton()
                reportFile.delete()
            }
        }.execute()
    }

    // ── Apply ────────────────────────────────────────────────────────────

    private fun applySelected() {
        val dir = currentProjectPath ?: return
        val selected = updates.filter { it.selected }
        if (selected.isEmpty()) return

        val msg = buildString {
            append("<html>Apply ${selected.size} update(s)?<br><br>")
            val majors = selected.filter { it.updateType == "major" }
            if (majors.isNotEmpty()) {
                append("WARNING: ${majors.size} major update(s) may contain breaking changes:<br>")
                majors.forEach {
                    val dep = it.depName.replace("&", "&amp;").replace("<", "&lt;")
                    append("&nbsp;&nbsp;\u2022 $dep ${it.currentValue} \u2192 ${it.newValue}<br>")
                }
                append("<br>")
            }
            val escapedDir = dir.replace("&", "&amp;").replace("<", "&lt;")
            append("Files will be modified in:<br>$escapedDir</html>")
        }
        val confirm = JOptionPane.showConfirmDialog(this, msg, "Apply Updates",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE)
        if (confirm != JOptionPane.OK_OPTION) return

        // Group replacements by file, dedup shared variables
        val replacements = buildReplacements(dir, selected)
        var applied = 0
        var errors = 0

        for ((filePath, fileReplacements) in replacements) {
            try {
                val file = File(filePath)
                var content = file.readText(Charsets.UTF_8)
                for ((oldValue, newValue) in fileReplacements) {
                    val before = content
                    content = content.replace(oldValue, newValue)
                    if (content != before) applied++
                }
                file.writeText(content, Charsets.UTF_8)
            } catch (e: Exception) {
                errors++
            }
        }

        if (errors == 0) {
            summaryLabel.text = "Applied $applied update(s). Run a build to verify."
        } else {
            summaryLabel.text = "Applied $applied update(s), $errors error(s). Check files manually."
        }

        // Remove applied updates from the table
        updates.removeAll { it.selected }
        tableModel.fireTableDataChanged()
        updateApplyButton()
    }

    /**
     * Build a map of file path -> list of (oldString, newString) replacements.
     * Deduplicates shared Maven properties (e.g. jackson.version used by multiple deps).
     */
    private fun buildReplacements(projectDir: String, selected: List<DepUpdate>): Map<String, List<Pair<String, String>>> {
        val result = mutableMapOf<String, MutableList<Pair<String, String>>>()
        val appliedProperties = mutableSetOf<String>() // "file:propertyName" dedup key

        for (update in selected) {
            val filePath = File(projectDir, update.packageFile).absolutePath

            // Dedup shared Maven properties — e.g. jackson-module-kotlin and jackson-databind
            // both point to <jackson.version>2.17.2</jackson.version>
            if (update.sharedVariableName.isNotEmpty()) {
                val key = "${update.packageFile}:${update.sharedVariableName}"
                if (key in appliedProperties) continue
                appliedProperties += key
                // Replace the property value: >currentValue< → >newValue<
                val oldTag = ">${update.currentValue}</${update.sharedVariableName}>"
                val newTag = ">${update.newValue}</${update.sharedVariableName}>"
                result.getOrPut(filePath) { mutableListOf() } += oldTag to newTag
                continue
            }

            // Dockerfile: replace the full image string
            if (update.replaceString.isNotEmpty()) {
                val oldStr = update.replaceString
                val newStr = if (update.autoReplaceTemplate.isNotEmpty() && update.newDigest.isNotEmpty()) {
                    // Pin digest: image:tag@sha256:digest
                    update.autoReplaceTemplate
                        .replace("{{depName}}", update.depName)
                        .replace("{{newValue}}", update.newValue)
                        .replace("{{newDigest}}", update.newDigest)
                } else if (update.newValue != "?" && update.currentValue != update.newValue) {
                    oldStr.replace(update.currentValue, update.newValue)
                } else continue
                result.getOrPut(filePath) { mutableListOf() } += oldStr to newStr
                continue
            }

            // Direct version replacement (non-shared Maven dep, or inline version)
            if (update.currentValue.isNotEmpty() && update.newValue != "?") {
                // For Maven: replace >currentValue</artifactId-related-tag> patterns
                // Use a targeted replacement: >oldVersion< to >newVersion< near the dep
                val oldVer = ">${update.currentValue}<"
                val newVer = ">${update.newValue}<"
                result.getOrPut(filePath) { mutableListOf() } += oldVer to newVer
            }
        }
        return result
    }

    // ── Report parsing ───────────────────────────────────────────────────

    private fun parseReport(reportFile: File): List<DepUpdate> {
        val mapper = ObjectMapper()
        val root = mapper.readTree(reportFile)
        val repo = root.path("repositories").path("local")
        val packageFiles = repo.path("packageFiles")
        if (packageFiles.isMissingNode) return emptyList()

        val result = mutableListOf<DepUpdate>()
        val fields = packageFiles.fields()
        while (fields.hasNext()) {
            val (manager, files) = fields.next()
            for (file in files) {
                val pkgFile = file.path("packageFile").asText("")
                for (dep in file.path("deps")) {
                    val depUpdates = dep.path("updates")
                    if (depUpdates.isEmpty) continue
                    val depName = dep.path("depName").asText("")
                    val currentValue = dep.path("currentValue").asText(
                        dep.path("currentVersion").asText("?")
                    )
                    val sharedVar = dep.path("sharedVariableName").asText("")
                    val frp = dep.path("fileReplacePosition").asInt(-1)
                    val replaceStr = dep.path("replaceString").asText("")
                    val autoReplace = dep.path("autoReplaceStringTemplate").asText("")

                    for (update in depUpdates) {
                        val newValue = update.path("newValue").asText(
                            update.path("newVersion").asText("?")
                        )
                        val updateType = update.path("updateType").asText("?")
                        val newDigest = update.path("newDigest").asText("")
                        result += DepUpdate(
                            manager = manager,
                            packageFile = pkgFile,
                            depName = depName,
                            currentValue = currentValue,
                            newValue = newValue,
                            updateType = updateType,
                            sharedVariableName = sharedVar,
                            fileReplacePosition = frp,
                            replaceString = replaceStr,
                            autoReplaceTemplate = autoReplace,
                            newDigest = newDigest,
                        )
                    }
                }
            }
        }
        val typeOrder = mapOf("major" to 0, "minor" to 1, "patch" to 2)
        result.sortWith(compareBy<DepUpdate> { typeOrder[it.updateType] ?: 3 }.thenBy { it.depName })
        return result
    }

    // ── Status check ─────────────────────────────────────────────────────

    private fun checkRenovateStatus() {
        statusLabel.text = "Checking\u2026"
        object : SwingWorker<Pair<Boolean, String>, Void>() {
            override fun doInBackground(): Pair<Boolean, String> {
                val found = ProcessExecutor.isOnPath("renovate")
                if (!found) return false to ""
                val version = ProcessExecutor.run(listOf("renovate", "--version"), timeoutMs = 5_000L)
                    ?.output?.lines()?.firstOrNull()?.trim() ?: ""
                return true to version
            }

            override fun done() {
                val (found, version) = get()
                if (found) {
                    statusLabel.text = "\u2713 v$version"
                    statusLabel.toolTipText = "renovate --version = $version"
                    statusLabel.foreground = Color(0x4CAF50)
                } else {
                    statusLabel.text = "\u2717 Not installed (install via Settings)"
                    statusLabel.toolTipText = null
                    statusLabel.foreground = Color(0xF44336)
                }
            }
        }.execute()
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        runButton.isEnabled = enabled
        applyButton.isEnabled = enabled && updates.any { it.selected }
        selectAllButton.isEnabled = enabled
        selectNoneButton.isEnabled = enabled
        selectPatchButton.isEnabled = enabled
    }

    /** Colour-codes the update type column. */
    private class UpdateTypeCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, col: Int,
        ): Component {
            val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col)
            if (!isSelected && col == 5) {
                foreground = when (value) {
                    "major" -> Color(0xF44336)
                    "minor" -> Color(0xFFA726)
                    "patch" -> Color(0x4CAF50)
                    else -> table.foreground
                }
                font = font.deriveFont(Font.BOLD)
            } else if (!isSelected) {
                foreground = table.foreground
                font = table.font
            }
            return c
        }
    }

    companion object {
        private fun monoFont(): String {
            val os = System.getProperty("os.name", "").lowercase()
            val available = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toHashSet()
            val preferred = when {
                os.contains("win") -> listOf("Cascadia Mono", "Cascadia Code", "JetBrains Mono", "Consolas")
                os.contains("mac") -> listOf("SF Mono", "Menlo", "JetBrains Mono", "Monaco")
                else -> listOf("JetBrains Mono", "Fira Code", "DejaVu Sans Mono", "Liberation Mono")
            }
            return preferred.firstOrNull { it in available } ?: Font.MONOSPACED
        }
    }
}

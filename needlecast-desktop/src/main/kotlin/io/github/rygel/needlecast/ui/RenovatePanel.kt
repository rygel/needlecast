package io.github.rygel.needlecast.ui

import com.fasterxml.jackson.databind.JsonNode
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
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.SwingWorker
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * Dockable panel for running Renovate locally against the active project.
 *
 * Runs `renovate --platform=local` with a JSON report, then parses the
 * report to show a table of outdated dependencies with current/available
 * versions and update type.
 */
class RenovatePanel : JPanel(BorderLayout()) {

    private data class DepUpdate(
        val manager: String,
        val depName: String,
        val currentVersion: String,
        val newVersion: String,
        val updateType: String,
    )

    private val statusLabel = JLabel("", SwingConstants.LEFT)
    private val projectLabel = JLabel("No project selected").apply {
        font = font.deriveFont(Font.ITALIC)
    }

    private var currentProjectPath: String? = null
    private var updates = mutableListOf<DepUpdate>()

    private val tableModel = object : AbstractTableModel() {
        private val columns = arrayOf("Manager", "Dependency", "Current", "Available", "Type")
        override fun getRowCount() = updates.size
        override fun getColumnCount() = columns.size
        override fun getColumnName(col: Int) = columns[col]
        override fun getValueAt(row: Int, col: Int): Any {
            val u = updates[row]
            return when (col) {
                0 -> u.manager
                1 -> u.depName
                2 -> u.currentVersion
                3 -> u.newVersion
                4 -> u.updateType
                else -> ""
            }
        }
    }

    private val table = JTable(tableModel).apply {
        font = Font(monoFont(), Font.PLAIN, 12)
        rowHeight = 22
        autoCreateRowSorter = true
        setDefaultRenderer(Object::class.java, UpdateTypeCellRenderer())
        columnModel.getColumn(0).preferredWidth = 100
        columnModel.getColumn(1).preferredWidth = 300
        columnModel.getColumn(2).preferredWidth = 120
        columnModel.getColumn(3).preferredWidth = 120
        columnModel.getColumn(4).preferredWidth = 80
    }

    private val runButton = JButton("Scan for Updates").apply {
        addActionListener { runLocalScan() }
    }

    private val summaryLabel = JLabel(" ").apply {
        border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
    }

    init {
        minimumSize = Dimension(0, 0)

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
            add(runButton)
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
        add(summaryLabel, BorderLayout.SOUTH)

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
    }

    private fun runLocalScan() {
        val dir = currentProjectPath
        if (dir == null) {
            summaryLabel.text = "No project selected. Select a project in the tree first."
            return
        }

        val reportFile = File(dir, "target/renovate-report.json")
        reportFile.parentFile?.mkdirs()

        runButton.isEnabled = false
        updates.clear()
        tableModel.fireTableDataChanged()
        summaryLabel.text = "Scanning…"

        object : SwingWorker<List<DepUpdate>, String>() {
            override fun doInBackground(): List<DepUpdate> {
                val reportPath = reportFile.absolutePath.replace('\\', '/')
                val cmd = "renovate --platform=local --report-type=file --report-path=\"$reportPath\""
                val argv = if (IS_WINDOWS) listOf("cmd", "/c", cmd) else listOf("sh", "-c", cmd)
                val pb = ProcessBuilder(argv).redirectErrorStream(true)
                pb.environment()["PATH"] = System.getenv("PATH") ?: ""
                pb.environment()["LOG_LEVEL"] = "info"
                pb.directory(File(dir))
                val proc = pb.start()
                proc.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) publish(line)
                }
                val exitCode = proc.waitFor()
                if (exitCode != 0 || !reportFile.exists()) return emptyList()

                return parseReport(reportFile)
            }

            override fun process(chunks: List<String>) {
                // Show progress in summary label — last meaningful line
                for (line in chunks) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("INFO:") || trimmed.startsWith("WARN:") || trimmed.startsWith("DEBUG:")) {
                        summaryLabel.text = trimmed.take(120)
                    }
                }
            }

            override fun done() {
                val result = try { get() } catch (e: Exception) {
                    summaryLabel.text = "Error: ${e.cause?.message ?: e.message}"
                    runButton.isEnabled = true
                    return
                }
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
                runButton.isEnabled = true
                reportFile.delete()
            }
        }.execute()
    }

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
                for (dep in file.path("deps")) {
                    val depUpdates = dep.path("updates")
                    if (depUpdates.isEmpty) continue
                    val depName = dep.path("depName").asText("")
                    val current = dep.path("currentVersion").asText(
                        dep.path("currentValue").asText("?")
                    )
                    for (update in depUpdates) {
                        val newVer = update.path("newVersion").asText(
                            update.path("newValue").asText("?")
                        )
                        val updateType = update.path("updateType").asText("?")
                        result += DepUpdate(manager, depName, current, newVer, updateType)
                    }
                }
            }
        }
        // Sort: major first, then minor, then patch, then alphabetical
        val typeOrder = mapOf("major" to 0, "minor" to 1, "patch" to 2)
        result.sortWith(compareBy<DepUpdate> { typeOrder[it.updateType] ?: 3 }.thenBy { it.depName })
        return result
    }

    private fun checkRenovateStatus() {
        statusLabel.text = "Checking…"
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
                    statusLabel.foreground = Color(0x4CAF50)
                } else {
                    statusLabel.text = "\u2717 Not installed (install via Settings)"
                    statusLabel.foreground = Color(0xF44336)
                    runButton.isEnabled = false
                }
            }
        }.execute()
    }

    /** Colour-codes the update type column and highlights major updates. */
    private class UpdateTypeCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, col: Int,
        ): Component {
            val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col)
            if (!isSelected && col == 4) {
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

package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.process.ProcessExecutor
import io.github.rygel.needlecast.scanner.IS_WINDOWS
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GraphicsEnvironment
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingConstants
import javax.swing.SwingWorker

/**
 * Dockable panel for running Renovate locally against the active project.
 *
 * Runs `renovate --platform=local` in the project directory — no token needed.
 * Output streams live into the embedded output area.
 */
class RenovatePanel : JPanel(BorderLayout()) {

    private val outputArea = JTextArea().apply {
        isEditable = false
        font = Font(monoFont(), Font.PLAIN, 11)
        lineWrap = true
        wrapStyleWord = false
    }

    private val statusLabel = JLabel("Checking…", SwingConstants.LEFT).apply {
        font = font.deriveFont(Font.BOLD)
    }
    private val versionLabel = JLabel("", SwingConstants.LEFT)
    private val projectLabel = JLabel("No project selected").apply {
        font = font.deriveFont(Font.ITALIC)
    }

    private var currentProjectPath: String? = null

    private val runButton = JButton("Run Renovate").apply {
        toolTipText = "Scan for outdated dependencies in the active project"
        addActionListener { runLocalScan() }
    }

    init {
        minimumSize = Dimension(0, 0)

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
            add(runButton)
            add(statusLabel)
            add(versionLabel)
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
        add(JScrollPane(outputArea).apply {
            border = BorderFactory.createEmptyBorder()
            verticalScrollBar.unitIncrement = 16
        }, BorderLayout.CENTER)

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
    }

    private fun runLocalScan() {
        val dir = currentProjectPath
        if (dir == null) {
            outputArea.text = "No project selected. Select a project in the tree first.\n"
            return
        }
        runButton.isEnabled = false
        outputArea.text = ""
        outputArea.append("$ renovate --platform=local\n")
        outputArea.append("Working directory: $dir\n\n")

        object : SwingWorker<Int, String>() {
            override fun doInBackground(): Int {
                val argv = if (IS_WINDOWS)
                    listOf("cmd", "/c", "renovate --platform=local")
                else
                    listOf("sh", "-c", "renovate --platform=local")
                val pb = ProcessBuilder(argv).redirectErrorStream(true)
                pb.environment()["PATH"] = System.getenv("PATH") ?: ""
                pb.environment()["LOG_LEVEL"] = "info"
                pb.directory(java.io.File(dir))
                val proc = pb.start()
                proc.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) publish(line)
                }
                return proc.waitFor()
            }

            override fun process(chunks: List<String>) {
                for (line in chunks) {
                    outputArea.append("$line\n")
                    outputArea.caretPosition = outputArea.document.length
                }
            }

            override fun done() {
                val exitCode = try { get() } catch (e: Exception) {
                    outputArea.append("\nError: ${e.cause?.message ?: e.message}\n")
                    -1
                }
                if (exitCode == 0) {
                    outputArea.append("\nCompleted successfully.\n")
                } else if (exitCode > 0) {
                    outputArea.append("\nCommand failed (exit code $exitCode).\n")
                }
                outputArea.caretPosition = outputArea.document.length
                runButton.isEnabled = true
            }
        }.execute()
    }

    private fun checkRenovateStatus() {
        statusLabel.text = "Checking…"
        versionLabel.text = ""

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
                    statusLabel.text = "\u2713 Installed"
                    statusLabel.foreground = java.awt.Color(0x4CAF50)
                    versionLabel.text = if (version.isNotEmpty()) "v$version" else ""
                } else {
                    statusLabel.text = "\u2717 Not installed"
                    statusLabel.foreground = java.awt.Color(0xF44336)
                    versionLabel.text = "(install via Settings)"
                    runButton.isEnabled = false
                }
            }
        }.execute()
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

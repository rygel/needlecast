package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.process.ProcessExecutor
import io.github.rygel.needlecast.scanner.IS_WINDOWS
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingConstants
import javax.swing.SwingWorker

/**
 * Dockable panel for running Renovate against the currently selected project.
 *
 * Provides token + repo fields, dry-run toggle, and both local CLI and Docker
 * execution modes. Command output streams live into the embedded output area.
 */
class RenovatePanel : JPanel(BorderLayout()) {

    private val outputArea = JTextArea().apply {
        isEditable = false
        font = Font(monoFont(), Font.PLAIN, 11)
        lineWrap = true
        wrapStyleWord = false
    }

    private val statusLabel = JLabel("Checking…", SwingConstants.CENTER).apply {
        font = font.deriveFont(Font.BOLD)
    }
    private val versionLabel = JLabel("", SwingConstants.CENTER)

    private val envToken = System.getenv("RENOVATE_TOKEN") ?: System.getenv("GITHUB_TOKEN") ?: ""
    private val tokenField = JPasswordField(envToken, 28).apply {
        toolTipText = "GitHub token with Contents R/W and Pull requests R/W"
    }
    private val repoField = JTextField(detectGitRepo() ?: "", 22).apply {
        toolTipText = "GitHub repo (owner/name)"
    }
    private val dryRunCheckbox = javax.swing.JCheckBox("Dry run (no PRs created)", false)

    private val allActionButtons = mutableListOf<JButton>()

    init {
        minimumSize = Dimension(0, 0)

        val statusPanel = JPanel(BorderLayout(0, 2)).apply {
            border = BorderFactory.createTitledBorder("Installation status")
            add(statusLabel, BorderLayout.CENTER)
            add(versionLabel, BorderLayout.SOUTH)
        }

        // ── Run section ──────────────────────────────────────────────────
        val runLocalButton = JButton("Run (local CLI)").apply {
            toolTipText = "Run 'renovate' directly (must be installed)"
            addActionListener { runRenovate(docker = false) }
        }
        allActionButtons.add(runLocalButton)

        val runDockerButton = JButton("Run (Docker)").apply {
            toolTipText = "Run Renovate inside a Docker container"
            addActionListener { runRenovate(docker = true) }
        }
        allActionButtons.add(runDockerButton)

        val runPanel = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createTitledBorder("Run Renovate")
        }
        val gc = GridBagConstraints().apply {
            insets = Insets(3, 6, 3, 6); anchor = GridBagConstraints.WEST; fill = GridBagConstraints.HORIZONTAL
        }
        gc.gridx = 0; gc.gridy = 0; gc.weightx = 0.0; gc.fill = GridBagConstraints.NONE
        runPanel.add(JLabel("Token:"), gc)
        gc.gridx = 1; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL; gc.gridwidth = 2
        runPanel.add(tokenField, gc)

        gc.gridx = 0; gc.gridy = 1; gc.weightx = 0.0; gc.fill = GridBagConstraints.NONE; gc.gridwidth = 1
        runPanel.add(JLabel("Repository:"), gc)
        gc.gridx = 1; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL; gc.gridwidth = 2
        runPanel.add(repoField, gc)

        gc.gridx = 0; gc.gridy = 2; gc.gridwidth = 3; gc.weightx = 0.0; gc.fill = GridBagConstraints.NONE
        runPanel.add(dryRunCheckbox, gc)

        gc.gridy = 3; gc.gridwidth = 3
        runPanel.add(JPanel(FlowLayout(FlowLayout.CENTER, 8, 2)).apply {
            add(runLocalButton); add(runDockerButton)
        }, gc)

        // ── Layout ───────────────────────────────────────────────────────
        val topSection = JPanel(GridBagLayout()).apply {
            val tc = GridBagConstraints().apply {
                gridx = 0; fill = GridBagConstraints.HORIZONTAL; weightx = 1.0; insets = Insets(0, 0, 4, 0)
            }
            tc.gridy = 0; add(JLabel(
                "<html>Renovate keeps your dependencies up to date by opening automated PRs.<br>" +
                "Configure a GitHub token and repository, then run locally or via Docker.</html>"
            ).apply { border = BorderFactory.createEmptyBorder(4, 6, 4, 6) }, tc)
            tc.gridy = 1; add(statusPanel, tc)
            tc.gridy = 2; add(runPanel, tc)
        }

        add(JScrollPane(topSection).apply {
            border = BorderFactory.createEmptyBorder()
            verticalScrollBar.unitIncrement = 16
        }, BorderLayout.NORTH)
        add(JScrollPane(outputArea).apply {
            border = BorderFactory.createTitledBorder("Output")
        }, BorderLayout.CENTER)

        checkRenovateStatus()
    }

    /** Update the repo field when a project is selected. */
    fun loadProject(path: String?) {
        if (path == null) return
        val repo = detectGitRepo(path)
        if (repo != null) repoField.text = repo
    }

    // ── Run logic ────────────────────────────────────────────────────────

    private fun runRenovate(docker: Boolean) {
        val token = String(tokenField.password).trim()
        val repo = repoField.text.trim()
        if (token.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "A GitHub token is required.\nSet RENOVATE_TOKEN / GITHUB_TOKEN or enter one above.",
                "Missing token", JOptionPane.WARNING_MESSAGE)
            return
        }
        if (repo.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Enter the GitHub repository (owner/name).",
                "Missing repository", JOptionPane.WARNING_MESSAGE)
            return
        }
        val dryFlag = if (dryRunCheckbox.isSelected) " --dry-run=full" else ""

        if (docker) {
            val cmd = "docker run --rm" +
                " -e RENOVATE_TOKEN=$token" +
                " -e LOG_LEVEL=info" +
                " ghcr.io/renovatebot/renovate:latest" +
                "$dryFlag --platform=github $repo"
            runCommandStreaming(cmd)
        } else {
            runCommandStreaming("renovate$dryFlag --platform=github $repo",
                env = mapOf("RENOVATE_TOKEN" to token, "LOG_LEVEL" to "info"))
        }
    }

    private fun runCommandStreaming(command: String, env: Map<String, String> = emptyMap()) {
        allActionButtons.forEach { it.isEnabled = false }
        outputArea.text = ""
        outputArea.append("$ $command\n")

        object : SwingWorker<Int, String>() {
            override fun doInBackground(): Int {
                val argv = if (IS_WINDOWS) listOf("cmd", "/c", command) else listOf("sh", "-c", command)
                val pb = ProcessBuilder(argv).redirectErrorStream(true)
                pb.environment()["PATH"] = System.getenv("PATH") ?: ""
                env.forEach { (k, v) -> pb.environment()[k] = v }
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
                allActionButtons.forEach { it.isEnabled = true }
                checkRenovateStatus()
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
                    statusLabel.text = "\u2713  Renovate is installed"
                    statusLabel.foreground = java.awt.Color(0x4CAF50)
                    versionLabel.text = if (version.isNotEmpty()) "version $version" else ""
                } else {
                    statusLabel.text = "\u2717  Renovate not found on PATH"
                    statusLabel.foreground = java.awt.Color(0xF44336)
                    versionLabel.text = "Install via Settings, then use this panel to run it"
                }
            }
        }.execute()
    }

    companion object {
        /** Detect GitHub repo from git remote in a given directory, or cwd if null. */
        fun detectGitRepo(workingDir: String? = null): String? {
            val argv = listOf("git", "remote", "get-url", "origin")
            val result = ProcessExecutor.run(argv, workingDir = workingDir, timeoutMs = 3_000L) ?: return null
            if (result.exitCode != 0) return null
            val url = result.output.trim()
            val sshMatch = Regex("""git@github\.com:(.+?)(?:\.git)?$""").find(url)
            if (sshMatch != null) return sshMatch.groupValues[1]
            val httpsMatch = Regex("""https?://github\.com/(.+?)(?:\.git)?$""").find(url)
            if (httpsMatch != null) return httpsMatch.groupValues[1]
            return null
        }

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

package io.github.rygel.needlecast.ui.settings

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.process.ProcessExecutor
import io.github.rygel.needlecast.scanner.IS_WINDOWS
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingConstants
import javax.swing.SwingWorker

class ApmSettingsPanel(
    private val ctx: AppContext,
    @Suppress("UNUSED_PARAMETER") sendToTerminal: (String) -> Unit = {},
) : JPanel(BorderLayout(0, 8)) {

    private val statusLabel  = JLabel("Checking…", SwingConstants.CENTER).apply { font = font.deriveFont(Font.BOLD) }
    private val versionLabel = JLabel("", SwingConstants.CENTER)

    init {
        border = BorderFactory.createEmptyBorder(12, 14, 12, 14)

        val infoLabel = JLabel(
            "<html>APM (Agent Package Manager) by Microsoft manages AI agent dependencies:<br>" +
            "skills, prompts, instructions, plugins, and MCP servers via an <code>apm.yml</code> manifest.<br>" +
            "Projects with <code>apm.yml</code> are automatically detected and their commands surfaced.</html>"
        ).apply { border = BorderFactory.createEmptyBorder(0, 0, 8, 0) }

        val statusPanel = JPanel(BorderLayout(0, 2)).apply {
            border = BorderFactory.createTitledBorder("Installation status")
            add(statusLabel,  BorderLayout.CENTER)
            add(versionLabel, BorderLayout.SOUTH)
        }

        val outputArea = buildOutputArea()

        val installPanel = JPanel(BorderLayout(0, 4)).apply {
            border = BorderFactory.createTitledBorder("Install via package manager")
        }
        val buttonsPanel   = JPanel(FlowLayout(FlowLayout.CENTER, 8, 4))
        val installButtons = mutableListOf<JButton>()

        buildInstallOptions().forEach { (label, cmd) ->
            val btn = JButton(label).apply {
                toolTipText = cmd
                addActionListener {
                    installButtons.forEach { it.isEnabled = false }
                    runCommandStreaming(cmd, outputArea) {
                        installButtons.forEach { it.isEnabled = true }
                        checkApm()
                    }
                }
            }
            installButtons.add(btn)
            buttonsPanel.add(btn)
        }
        installPanel.add(buttonsPanel, BorderLayout.CENTER)

        val recheckButton = JButton("↻ Recheck").apply { addActionListener { checkApm() } }

        val topSection = JPanel(BorderLayout(0, 8)).apply {
            add(infoLabel, BorderLayout.NORTH)
            add(JPanel(BorderLayout(0, 8)).apply {
                add(statusPanel, BorderLayout.NORTH)
                add(installPanel, BorderLayout.CENTER)
                add(JPanel(FlowLayout(FlowLayout.CENTER)).apply { add(recheckButton) }, BorderLayout.SOUTH)
            }, BorderLayout.CENTER)
        }

        add(topSection, BorderLayout.NORTH)
        add(JScrollPane(outputArea).apply {
            border = BorderFactory.createTitledBorder("Output")
            preferredSize = Dimension(0, 160)
        }, BorderLayout.CENTER)

        checkApm()
    }

    private fun checkApm() {
        statusLabel.text = "Checking…"
        versionLabel.text = ""
        object : SwingWorker<Pair<Boolean, String>, Void>() {
            override fun doInBackground(): Pair<Boolean, String> {
                val found = ProcessExecutor.isOnPath("apm")
                if (!found) return false to ""
                val version = ProcessExecutor.run(listOf("apm", "--version"), timeoutMs = 5_000L)
                    ?.output?.lines()?.firstOrNull()?.trim() ?: ""
                return true to version
            }
            override fun done() {
                val (found, version) = get()
                if (found) {
                    statusLabel.text = "✓  APM is installed"
                    statusLabel.foreground = Color(0x4CAF50)
                    versionLabel.text = if (version.isNotEmpty()) "version $version" else ""
                } else {
                    statusLabel.text = "✗  APM not found on PATH"
                    statusLabel.foreground = Color(0xF44336)
                    versionLabel.text = "Use one of the buttons below to install it"
                }
            }
        }.execute()
    }

    private fun buildInstallOptions(): List<Pair<String, String>> = buildList {
        if (!IS_WINDOWS) add("curl"       to "curl -sSL https://aka.ms/apm-unix | sh")
        if (IS_WINDOWS)  add("PowerShell" to "irm https://aka.ms/apm-windows | iex")
        if (!IS_WINDOWS) add("Homebrew"   to "brew install microsoft/apm/apm")
        add("pip"                         to "pip install apm-cli")
        if (IS_WINDOWS)  add("Scoop"      to "scoop install apm")
    }
}

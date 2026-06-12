package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.scanner.IS_MAC
import io.github.rygel.needlecast.scanner.IS_WINDOWS
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Window
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.KeyStroke

internal class ShellSettingsDialog(
    owner: Window?,
    projectLabel: String,
    currentShell: String?,
    currentStartup: String?,
    private val onSave: (shell: String?, startup: String?) -> Unit,
) : JDialog(owner, "Shell Settings \u2014 $projectLabel", ModalityType.APPLICATION_MODAL) {
    private val shellField = JTextField(currentShell ?: "", 30)
    private val startupField = JTextField(currentStartup ?: "", 30)

    val shellText: String get() = shellField.text
    val startupText: String get() = startupField.text

    private val defaultShell =
        when {
            IS_WINDOWS -> "cmd.exe"
            IS_MAC -> "/bin/zsh"
            else -> "/bin/bash"
        }

    init {
        val form = buildForm()
        add(form, BorderLayout.CENTER)

        val okButton = JButton("OK")
        val cancelButton = JButton("Cancel")
        okButton.addActionListener { handleOk() }
        cancelButton.addActionListener { dispose() }
        add(
            JPanel().apply {
                add(okButton)
                add(cancelButton)
            },
            BorderLayout.SOUTH,
        )

        rootPane.defaultButton = okButton
        pack()
        setLocationRelativeTo(owner)

        rootPane.registerKeyboardAction(
            { dispose() },
            KeyStroke.getKeyStroke("ESCAPE"),
            JComponent.WHEN_IN_FOCUSED_WINDOW,
        )
    }

    private fun buildForm(): JPanel =
        JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            val gc =
                GridBagConstraints().apply {
                    insets = Insets(4, 4, 4, 4)
                    anchor = GridBagConstraints.WEST
                }

            gc.gridx = 0
            gc.gridy = 0
            gc.weightx = 0.0
            gc.fill = GridBagConstraints.NONE
            add(JLabel("Shell:"), gc)
            gc.gridx = 1
            gc.weightx = 1.0
            gc.fill = GridBagConstraints.HORIZONTAL
            add(shellField, gc)

            gc.gridx = 0
            gc.gridy = 1
            gc.weightx = 0.0
            gc.fill = GridBagConstraints.NONE
            add(JLabel("Startup command:"), gc)
            gc.gridx = 1
            gc.weightx = 1.0
            gc.fill = GridBagConstraints.HORIZONTAL
            add(startupField, gc)

            gc.gridx = 0
            gc.gridy = 2
            gc.gridwidth = 2
            gc.fill = GridBagConstraints.HORIZONTAL
            add(
                JLabel(
                    "<html><small>" +
                        "Shell: e.g. <tt>zsh</tt>, <tt>fish</tt>, <tt>powershell</tt> \u2014 " +
                        "blank uses system default (<tt>$defaultShell</tt>)<br>" +
                        "Startup: sent to the shell on open, e.g. <tt>conda activate ml</tt>" +
                        "</small></html>",
                ),
                gc,
            )
        }

    private fun handleOk() {
        val shell = shellField.text.trim().takeIf { it.isNotEmpty() }
        val startup = startupField.text.trim().takeIf { it.isNotEmpty() }
        dispose()
        onSave(shell, startup)
    }

    fun simulateOk() = handleOk()
}

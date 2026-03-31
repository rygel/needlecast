package io.github.quicklaunch.ui

import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel

class StatusBar : JPanel(BorderLayout()) {

    private val label = JLabel(" Ready")

    init {
        border = BorderFactory.createMatteBorder(1, 0, 0, 0, java.awt.Color.GRAY)
        add(label, BorderLayout.WEST)
    }

    fun setStatus(msg: String) {
        label.text = " $msg"
    }

    fun setRunning(commandLabel: String) {
        label.text = " Running: $commandLabel"
    }

    fun setFinished(exitCode: Int) {
        label.text = if (exitCode == 0) " Finished successfully (exit 0)"
                     else " Finished with exit code $exitCode"
    }

    fun setReady() {
        label.text = " Ready"
    }
}

package io.github.rygel.needlecast.ui.terminal

import io.github.rygel.needlecast.ui.RemixIcons
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.charset.Charset
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.SwingConstants
import javax.swing.Timer

internal class ProjectTerminalPane(
    private val path: String,
    private var isDark: Boolean,
    private val extraEnv: Map<String, String> = emptyMap(),
    private val shellExecutable: String? = null,
    private val startupCommand: String? = null,
    initialFg: java.awt.Color? = null,
    initialBg: java.awt.Color? = null,
    initialFontSize: Int = 13,
    initialFontFamily: String? = null,
    initialCharset: Charset = Charsets.UTF_8,
) : JPanel(BorderLayout()) {
    private var customFg: java.awt.Color? = initialFg
    private var customBg: java.awt.Color? = initialBg
    private var currentFontSize: Int = initialFontSize
    private var currentFontFamily: String? = initialFontFamily
    private var currentCharset: Charset = initialCharset

    var onFontSizeChanged: ((Int) -> Unit)? = null
    var onCharsetChanged: ((Charset) -> Unit)? = null

    fun applyTerminalColors(
        fg: java.awt.Color?,
        bg: java.awt.Color?,
    ) {
        customFg = fg
        customBg = bg
        for (i in 0 until realTabCount) {
            (tabs.getComponentAt(i) as? TerminalPanel)?.applyColors(fg, bg)
        }
    }

    fun applyFontSize(size: Int) {
        currentFontSize = size
        for (i in 0 until realTabCount) {
            (tabs.getComponentAt(i) as? TerminalPanel)?.applyFontSize(size)
        }
    }

    fun applyFontFamily(name: String?) {
        currentFontFamily = name
        for (i in 0 until realTabCount) {
            (tabs.getComponentAt(i) as? TerminalPanel)?.applyFontFamily(name)
        }
    }

    internal val tabs = JTabbedPane()
    private var tabCounter = 0
    private var addingTab = false
    private var removingTab = false
    internal val realTabCount: Int get() = tabs.tabCount - 1

    var onStatusChanged: ((AgentStatus) -> Unit)? = null
    private val tabStatuses = mutableMapOf<TerminalPanel, AgentStatus>()

    private fun recomputeStatus() {
        val merged =
            when {
                tabStatuses.values.any { it == AgentStatus.THINKING } -> AgentStatus.THINKING
                tabStatuses.values.any { it == AgentStatus.WAITING } -> AgentStatus.WAITING
                else -> AgentStatus.NONE
            }
        onStatusChanged?.invoke(merged)
    }

    init {
        minimumSize = Dimension(0, 0)
        tabs.minimumSize = Dimension(0, 0)
        val addButton =
            JButton("+").apply {
                toolTipText = "New terminal tab"
                isFocusable = false
                addActionListener { addTerminalTab() }
            }
        val restartButton =
            JButton(RemixIcons.icon("ri-refresh-line", 12)).apply {
                toolTipText = "Restart terminal"
                isFocusable = false
                isBorderPainted = false
                isContentAreaFilled = false
                addActionListener { restartActiveTab() }
            }
        val encodingCombo = JComboBox<String>(ENCODINGS)
        encodingCombo.toolTipText = "Terminal character encoding"
        encodingCombo.isFocusable = false
        encodingCombo.selectedItem = currentCharset.name()
        encodingCombo.addActionListener {
            val selected = encodingCombo.selectedItem as? String ?: return@addActionListener
            val newCharset = tryRun { Charset.forName(selected) } ?: return@addActionListener
            if (newCharset != currentCharset) {
                currentCharset = newCharset
                onCharsetChanged?.invoke(newCharset)
                restartActiveTab()
            }
        }
        val trailingToolbar =
            JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0)).apply {
                isOpaque = false
                add(encodingCombo)
                add(restartButton)
                add(addButton)
            }
        tabs.putClientProperty("JTabbedPane.trailingComponent", trailingToolbar)
        add(tabs, BorderLayout.CENTER)
        tabs.addChangeListener { if (!addingTab && !removingTab && tabs.selectedIndex == realTabCount) addTerminalTab() }
        addTerminalTab()
    }

    fun addTerminalTab() {
        addingTab = true
        tabCounter++
        val terminal =
            TerminalPanel(
                initialDir = path,
                dark = isDark,
                extraEnv = extraEnv,
                shellExecutable = shellExecutable,
                startupCommand = startupCommand,
                initialFg = customFg,
                initialBg = customBg,
                initialFontSize = currentFontSize,
                initialFontFamily = currentFontFamily,
                charset = currentCharset,
            )
        terminal.onStatusChanged = { status ->
            tabStatuses[terminal] = status
            recomputeStatus()
        }
        terminal.onFontSizeChanged = { size ->
            currentFontSize = size
            for (i in 0 until realTabCount) {
                val t = tabs.getComponentAt(i) as? TerminalPanel ?: continue
                if (t !== terminal) t.applyFontSize(size)
            }
            onFontSizeChanged?.invoke(size)
        }
        removePlusTab()
        val title = "Terminal $tabCounter"
        tabs.addTab(title, terminal)
        val idx = tabs.tabCount - 1
        val header =
            TerminalTabHeader(title, canClose = { tabs.tabCount > 1 }) {
                closeTab(terminal)
            }
        tabs.setTabComponentAt(idx, header)
        terminal.onTerminalTitleChanged = { newTitle ->
            val tidx = tabs.indexOfComponent(terminal)
            if (tidx >= 0) {
                tabs.setTitleAt(tidx, newTitle)
                header.setTitle(newTitle)
            }
        }
        terminal.onBell = { header.flashBell() }
        tabs.selectedIndex = idx
        addPlusTab()
        addingTab = false
        terminal.requestFocusInWindow()
    }

    private fun closeTab(terminal: TerminalPanel) {
        val idx = tabs.indexOfComponent(terminal)
        if (idx < 0 || realTabCount <= 1) return
        removingTab = true
        tabs.removeTabAt(idx)
        removingTab = false
        tabStatuses.remove(terminal)
        recomputeStatus()
        terminal.dispose()
    }

    fun closeActiveTab() {
        val idx = tabs.selectedIndex
        if (idx < 0 || idx >= realTabCount) return
        val terminal = tabs.getComponentAt(idx) as? TerminalPanel ?: return
        closeTab(terminal)
    }

    fun nextTab() {
        if (realTabCount <= 1) return
        tabs.selectedIndex = (tabs.selectedIndex + 1) % realTabCount
    }

    fun prevTab() {
        if (realTabCount <= 1) return
        tabs.selectedIndex = (tabs.selectedIndex - 1 + realTabCount) % realTabCount
    }

    fun zoomActive(delta: Int) {
        val idx = tabs.selectedIndex
        if (idx < 0 || idx >= realTabCount) return
        val terminal = tabs.getComponentAt(idx) as? TerminalPanel ?: return
        terminal.changeFontSize(delta)
    }

    fun zoomReset() {
        for (i in 0 until realTabCount) {
            (tabs.getComponentAt(i) as? TerminalPanel)?.applyFontSize(13)
        }
        onFontSizeChanged?.invoke(13)
    }

    private fun restartActiveTab() {
        val idx = tabs.selectedIndex
        if (idx < 0 || idx >= realTabCount) return
        val old = tabs.getComponentAt(idx) as? TerminalPanel ?: return
        val title = tabs.getTitleAt(idx)
        old.dispose()
        val replacement =
            TerminalPanel(
                initialDir = path,
                dark = isDark,
                extraEnv = extraEnv,
                shellExecutable = shellExecutable,
                startupCommand = startupCommand,
                initialFg = customFg,
                initialBg = customBg,
                initialFontSize = currentFontSize,
                initialFontFamily = currentFontFamily,
                charset = currentCharset,
            )
        replacement.onStatusChanged = { status ->
            tabStatuses[replacement] = status
            tabStatuses.remove(old)
            recomputeStatus()
        }
        replacement.onFontSizeChanged = { size ->
            currentFontSize = size
            for (i in 0 until realTabCount) {
                val t = tabs.getComponentAt(i) as? TerminalPanel ?: continue
                if (t !== replacement) t.applyFontSize(size)
            }
            onFontSizeChanged?.invoke(size)
        }
        tabs.setComponentAt(idx, replacement)
        tabs.setTitleAt(idx, title)
        val header =
            TerminalTabHeader(title, canClose = { tabs.tabCount > 1 }) {
                closeTab(replacement)
            }
        tabs.setTabComponentAt(idx, header)
        replacement.onTerminalTitleChanged = { newTitle ->
            val tidx = tabs.indexOfComponent(replacement)
            if (tidx >= 0) {
                tabs.setTitleAt(tidx, newTitle)
                header.setTitle(newTitle)
            }
        }
        replacement.onBell = { header.flashBell() }
        replacement.requestFocusInWindow()
    }

    private fun removePlusTab() {
        if (tabs.tabCount > 0 && (tabs.getTabComponentAt(tabs.tabCount - 1) as? JLabel)?.text == "+") {
            tabs.removeTabAt(tabs.tabCount - 1)
        }
    }

    private fun addPlusTab() {
        val plusLabel =
            JLabel("+", SwingConstants.CENTER).apply {
                preferredSize = Dimension(20, 20)
                isFocusable = false
            }
        tabs.addTab("+", JPanel())
        tabs.setTabComponentAt(tabs.tabCount - 1, plusLabel)
    }

    fun forceStatusOnClaudeTabs(status: AgentStatus) {
        for (i in 0 until realTabCount) {
            val t = tabs.getComponentAt(i) as? TerminalPanel ?: continue
            if (t.isClaudeSession) t.forceStatus(status)
        }
    }

    fun setUseHooksForStatus(enabled: Boolean) {
        for (i in 0 until realTabCount) {
            (tabs.getComponentAt(i) as? TerminalPanel)?.useHooksForStatus = enabled
        }
    }

    fun requestFocusOnActive() {
        if (tabs.selectedIndex < realTabCount) {
            (tabs.selectedComponent as? TerminalPanel)?.requestFocusInWindow()
        }
    }

    fun sendInputToActive(text: String) {
        if (tabs.selectedIndex < realTabCount) {
            (tabs.selectedComponent as? TerminalPanel)?.sendInput(text)
        }
    }

    fun applyTheme(dark: Boolean) {
        isDark = dark
        for (i in 0 until realTabCount) {
            (tabs.getComponentAt(i) as? TerminalPanel)?.applyTheme(dark)
        }
    }

    fun restartAllWithCharset(charset: Charset) {
        currentCharset = charset
        for (i in 0 until realTabCount) {
            val old = tabs.getComponentAt(i) as? TerminalPanel ?: continue
            val title = tabs.getTitleAt(i)
            old.dispose()
            val replacement =
                TerminalPanel(
                    initialDir = path,
                    dark = isDark,
                    extraEnv = extraEnv,
                    shellExecutable = shellExecutable,
                    startupCommand = startupCommand,
                    initialFg = customFg,
                    initialBg = customBg,
                    initialFontSize = currentFontSize,
                    initialFontFamily = currentFontFamily,
                    charset = charset,
                )
            replacement.onStatusChanged = { status ->
                tabStatuses[replacement] = status
                tabStatuses.remove(old)
                recomputeStatus()
            }
            replacement.onFontSizeChanged = { size ->
                currentFontSize = size
                for (j in 0 until realTabCount) {
                    val t = tabs.getComponentAt(j) as? TerminalPanel ?: continue
                    if (t !== replacement) t.applyFontSize(size)
                }
                onFontSizeChanged?.invoke(size)
            }
            tabs.setComponentAt(i, replacement)
            tabs.setTitleAt(i, title)
            val header =
                TerminalTabHeader(title, canClose = { tabs.tabCount > 1 }) {
                    closeTab(replacement)
                }
            tabs.setTabComponentAt(i, header)
            replacement.onTerminalTitleChanged = { newTitle ->
                val tidx = tabs.indexOfComponent(replacement)
                if (tidx >= 0) {
                    tabs.setTitleAt(tidx, newTitle)
                    header.setTitle(newTitle)
                }
            }
        }
    }

    fun dispose() {
        tabStatuses.clear()
        for (i in 0 until realTabCount) {
            (tabs.getComponentAt(i) as? TerminalPanel)?.dispose()
        }
    }
}

private class TerminalTabHeader(
    title: String,
    private val canClose: () -> Boolean,
    onClose: () -> Unit,
) : JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)) {
    private val titleLabel = JLabel(title)
    private var originalTitle: String? = null
    private val bellTimer =
        Timer(500) {
            if (originalTitle != null) {
                titleLabel.text = originalTitle
                originalTitle = null
            }
        }.apply { isRepeats = false }

    init {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        add(titleLabel)
        add(
            JButton(RemixIcons.icon("ri-close-line", 16)).apply {
                toolTipText = "Close tab"
                preferredSize = Dimension(20, 20)
                isFocusable = false
                isBorderPainted = false
                isContentAreaFilled = false
                addActionListener { if (canClose()) onClose() }
            },
        )
    }

    fun setTitle(title: String) {
        titleLabel.text = title
        if (originalTitle != null) originalTitle = title
    }

    fun flashBell() {
        if (bellTimer.isRunning) return
        if (originalTitle == null) originalTitle = titleLabel.text
        titleLabel.text = "\uD83D\uDD14 ${titleLabel.text}"
        bellTimer.restart()
    }
}

internal val ENCODINGS = arrayOf("UTF-8", "ISO-8859-1", "Windows-1252", "US-ASCII", "GBK", "Big5", "Shift_JIS", "EUC-JP", "KOI8-R", "Windows-1251")

private val logger = LoggerFactory.getLogger("io.github.rygel.needlecast.ui.terminal.ProjectTerminalPane")

private inline fun <T> tryRun(block: () -> T): T? =
    try {
        block()
    } catch (e: Exception) {
        logger.debug("tryRun failed", e)
        null
    }

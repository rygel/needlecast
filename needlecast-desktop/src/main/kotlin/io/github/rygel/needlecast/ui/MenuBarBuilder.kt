package io.github.rygel.needlecast.ui

import io.github.rygel.needlecast.AppContext
import io.github.rygel.needlecast.ThemeRegistry
import java.awt.Cursor
import java.awt.Desktop
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import java.net.URI
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import javax.swing.JCheckBoxMenuItem
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

class MenuBarBuilder(
    private val registry: PanelRegistry,
    private val coordinator: PanelCoordinator,
    private val docking: DockingController,
    private val ctx: AppContext,
    private val owner: JFrame,
    private val callbacks: MenuBarBuilder.MenuBarCallbacks,
) {
    data class MenuBarCallbacks(
        val reloadShortcuts: () -> Unit,
        val applyUiFont: () -> Unit,
        val checkForUpdatesManual: () -> Unit,
        val importConfig: () -> Unit,
        val exportConfig: () -> Unit,
        val importWorkspace: () -> Unit,
        val exportWorkspace: () -> Unit,
    )

    private var cliCache: List<Pair<AiCli, Boolean>> = emptyList()
    private var cliCacheReady = false

    fun build(): JMenuBar {
        val i18n = ctx.i18n
        return JMenuBar().apply {
            add(buildFileMenu(i18n))
            add(buildViewMenu(i18n.translate("menu.view")))
            add(buildWindowsMenu())
            add(buildAiMenu())
            add(buildHelpMenu(i18n))
        }
    }

    // ── File menu ──────────────────────────────────────────────────────────────

    private fun buildFileMenu(i18n: io.github.rygel.outerstellar.i18n.I18nService): JMenu {
        val settingsItem =
            JMenuItem(i18n.translate("menu.file.settings")).apply {
                addActionListener {
                    SettingsDialog(
                        owner = owner,
                        ctx = ctx,
                        sendToTerminal = { cmd -> registry.terminalPanel.sendInput(cmd) },
                        callbacks =
                            coordinator.createSettingsCallbacks(
                                reloadShortcuts = callbacks.reloadShortcuts,
                                applyUiFont = callbacks.applyUiFont,
                            ),
                    ).isVisible = true
                }
            }
        val importItem =
            JMenuItem(i18n.translate("menu.file.import")).apply {
                addActionListener { callbacks.importConfig() }
            }
        val exportItem =
            JMenuItem(i18n.translate("menu.file.export")).apply {
                addActionListener { callbacks.exportConfig() }
            }
        val importWorkspaceItem =
            JMenuItem("Import Workspace...").apply {
                addActionListener { callbacks.importWorkspace() }
            }
        val exportWorkspaceItem =
            JMenuItem("Export Workspace...").apply {
                addActionListener { callbacks.exportWorkspace() }
            }
        val importLayoutItem =
            JMenuItem("Import Layout...").apply {
                addActionListener {
                    docking.importLayout(
                        onStatus = { registry.statusBar.setStatus(it) },
                        onError = { msg -> JOptionPane.showMessageDialog(owner, msg, "Import Error", JOptionPane.ERROR_MESSAGE) },
                    )
                }
            }
        val exportLayoutItem =
            JMenuItem("Export Layout...").apply {
                addActionListener {
                    docking.exportLayout(
                        onStatus = { registry.statusBar.setStatus(it) },
                        onError = { msg -> JOptionPane.showMessageDialog(owner, msg, "Export Error", JOptionPane.ERROR_MESSAGE) },
                    )
                }
            }
        val exitItem =
            JMenuItem(i18n.translate("menu.file.exit")).apply {
                addActionListener { owner.dispatchEvent(WindowEvent(owner, WindowEvent.WINDOW_CLOSING)) }
            }
        return JMenu(i18n.translate("menu.file")).apply {
            add(settingsItem)
            addSeparator()
            add(importItem)
            add(exportItem)
            addSeparator()
            add(importWorkspaceItem)
            add(exportWorkspaceItem)
            addSeparator()
            add(importLayoutItem)
            add(exportLayoutItem)
            addSeparator()
            add(exitItem)
        }
    }

    // ── View menu ──────────────────────────────────────────────────────────────

    private fun buildViewMenu(title: String): JMenu {
        val themeItems = mutableListOf<JCheckBoxMenuItem>()

        fun themeItem(
            id: String,
            name: String,
        ) = JCheckBoxMenuItem(name, id == ctx.config.theme).apply {
            addActionListener {
                setTheme(id)
                themeItems.forEach { it.isSelected = false }
                isSelected = true
            }
            themeItems.add(this)
        }

        fun groupSubmenu(
            label: String,
            baseId: String,
            baseName: String,
            group: String,
        ): JMenu =
            JMenu(label).apply {
                add(themeItem(baseId, baseName))
                addSeparator()
                ThemeRegistry.themes.entries
                    .filter { it.value.group == group }
                    .forEach { (id, entry) -> add(themeItem(id, entry.displayName)) }
            }

        fun groupSubmenu(
            label: String,
            group: String,
        ): JMenu =
            JMenu(label).apply {
                ThemeRegistry.themes.entries
                    .filter { it.value.group == group }
                    .forEach { (id, entry) -> add(themeItem(id, entry.displayName)) }
            }

        val showConsoleCb =
            JCheckBoxMenuItem("Show Console", ctx.config.showConsole).apply {
                addActionListener { docking.toggleConsole(isSelected) }
            }
        val showExplorerCb =
            JCheckBoxMenuItem("Show Explorer Tab", ctx.config.showExplorer).apply {
                addActionListener { docking.toggleExplorer(isSelected) }
            }
        val resetLayoutItem =
            JMenuItem("Reset Layout to Default").apply {
                addActionListener { docking.resetLayout { registry.statusBar.setStatus(it) } }
            }

        return JMenu(title).apply {
            add(themeItem("system", ctx.i18n.translate("menu.view.systemTheme")))
            addSeparator()
            add(groupSubmenu("Dark Themes", "dark", ctx.i18n.translate("menu.view.darkTheme"), ThemeRegistry.GROUP_DARK))
            add(groupSubmenu("Light Themes", "light", ctx.i18n.translate("menu.view.lightTheme"), ThemeRegistry.GROUP_LIGHT))
            addSeparator()
            add(showConsoleCb)
            add(showExplorerCb)
            add(
                JCheckBoxMenuItem("Privacy Mode", ctx.config.privacyModeEnabled).apply {
                    toolTipText = "Hide private project names and paths for screenshots"
                    addActionListener {
                        ctx.updateConfig(ctx.config.copy(privacyModeEnabled = isSelected))
                    }
                },
            )
            addSeparator()
            add(
                JCheckBoxMenuItem("Highlight panel on hover  [alpha]", ctx.config.panelHoverHighlight).apply {
                    toolTipText = "Draws a colored border around the panel under the mouse cursor. Experimental."
                    addActionListener {
                        ctx.updateConfig(ctx.config.copy(panelHoverHighlight = isSelected))
                        if (!isSelected) docking.clearPanelHighlight()
                    }
                },
            )
            addSeparator()
            add(resetLayoutItem)
        }
    }

    // ── Windows / Panels menu ──────────────────────────────────────────────────

    private fun buildWindowsMenu(): JMenu {
        val commandsCb =
            JCheckBoxMenuItem("Commands").apply {
                addActionListener { docking.toggleCommands(isSelected) }
            }
        val gitLogCb =
            JCheckBoxMenuItem("Git Log").apply {
                addActionListener { docking.toggleGitLog(isSelected) }
            }
        val diffCb =
            JCheckBoxMenuItem("Diff").apply {
                addActionListener { docking.toggleDiff(isSelected) }
            }
        val searchCb =
            JCheckBoxMenuItem("Search").apply {
                addActionListener { docking.toggleSearch(isSelected) }
            }
        val explorerCb =
            JCheckBoxMenuItem("Explorer").apply {
                addActionListener { docking.toggleExplorer(isSelected) }
            }
        val editorCb =
            JCheckBoxMenuItem("Editor").apply {
                addActionListener { docking.toggleEditor(isSelected) }
            }
        val consoleCb =
            JCheckBoxMenuItem("Output").apply {
                addActionListener { docking.toggleConsole(isSelected) }
            }
        val promptInputCb =
            JCheckBoxMenuItem("Prompt Input").apply {
                addActionListener { docking.togglePromptInput(isSelected) }
            }
        val commandInputCb =
            JCheckBoxMenuItem("Command Input").apply {
                addActionListener { docking.toggleCommandInput(isSelected) }
            }
        val renovateCb =
            JCheckBoxMenuItem("Renovate").apply {
                addActionListener { docking.toggleRenovate(isSelected) }
            }
        val docViewerCb =
            JCheckBoxMenuItem("Doc Viewer").apply {
                addActionListener { docking.toggleDocViewer(isSelected) }
            }

        fun syncState() {
            commandsCb.isSelected = docking.isDocked("commands")
            gitLogCb.isSelected = docking.isDocked("git-log")
            diffCb.isSelected = docking.isDocked("diff-viewer")
            searchCb.isSelected = docking.isDocked("search")
            explorerCb.isSelected = docking.isDocked("explorer")
            editorCb.isSelected = docking.isDocked("editor")
            consoleCb.isSelected = docking.isDocked("console")
            promptInputCb.isSelected = docking.isDocked("prompt-input")
            commandInputCb.isSelected = docking.isDocked("command-input")
            renovateCb.isSelected = docking.isDocked("renovate")
            docViewerCb.isSelected = docking.isDocked("doc-viewer")
        }

        return JMenu("Panels").apply {
            addMenuListener(
                object : javax.swing.event.MenuListener {
                    override fun menuSelected(e: javax.swing.event.MenuEvent) = syncState()

                    override fun menuDeselected(e: javax.swing.event.MenuEvent) {}

                    override fun menuCanceled(e: javax.swing.event.MenuEvent) {}
                },
            )
            add(commandsCb)
            add(gitLogCb)
            add(diffCb)
            add(searchCb)
            add(renovateCb)
            add(docViewerCb)
            addSeparator()
            add(explorerCb)
            add(editorCb)
            addSeparator()
            add(consoleCb)
            add(promptInputCb)
            add(commandInputCb)
        }
    }

    // ── AI Tools menu ──────────────────────────────────────────────────────────

    private fun buildAiMenu(): JMenu {
        val menu = JMenu("AI Tools")

        val promptLibraryItem =
            JMenuItem("Prompt Library...").apply {
                addActionListener {
                    PromptLibraryDialog(
                        owner = owner,
                        ctx = ctx,
                        sendToTerminal = { text -> registry.terminalPanel.sendInput(text) },
                    ).isVisible = true
                }
            }
        val commandLibraryItem =
            JMenuItem("Command Library...").apply {
                addActionListener {
                    PromptLibraryDialog(
                        owner = owner,
                        ctx = ctx,
                        sendToTerminal = { cmd -> registry.terminalPanel.sendInput(cmd) },
                        title = "Command Library",
                        sendButtonLabel = "Run in Terminal",
                        isCommand = true,
                    ).isVisible = true
                }
            }

        refreshCliCache()

        menu.addMenuListener(
            object : javax.swing.event.MenuListener {
                override fun menuSelected(e: javax.swing.event.MenuEvent) {
                    menu.removeAll()
                    menu.add(promptLibraryItem)
                    menu.add(commandLibraryItem)
                    menu.addSeparator()

                    menu.add(
                        JMenuItem("Rescan").apply {
                            icon = RemixIcons.icon("ri-refresh-line", 16)
                            addActionListener {
                                cliCacheReady = false
                                refreshCliCache()
                                menu.doClick()
                            }
                        },
                    )
                    menu.addSeparator()

                    if (!cliCacheReady) {
                        menu.add(JMenuItem("Detecting AI tools\u2026").apply { isEnabled = false })
                        return
                    }

                    val (found, missing) = cliCache.partition { it.second }
                    if (found.isEmpty()) {
                        menu.add(JMenuItem("No AI CLIs detected").apply { isEnabled = false })
                    } else {
                        found.forEach { (cli, _) ->
                            menu.add(
                                JMenuItem(cli.name).apply {
                                    icon = RemixIcons.icon("ri-play-line", 16)
                                    toolTipText = cli.description
                                    font = font.deriveFont(Font.BOLD)
                                    addActionListener { launchCliInTerminal(cli) }
                                },
                            )
                        }
                    }
                    if (missing.isNotEmpty()) {
                        menu.addSeparator()
                        missing.forEach { (cli, _) ->
                            menu.add(
                                JMenuItem("${cli.name}  (not found)").apply {
                                    toolTipText = "Install '${cli.command}' to use it here"
                                    isEnabled = false
                                },
                            )
                        }
                    }
                }

                override fun menuDeselected(e: javax.swing.event.MenuEvent) {}

                override fun menuCanceled(e: javax.swing.event.MenuEvent) {}
            },
        )

        return menu
    }

    private fun refreshCliCache() {
        Thread({
            val cfg = ctx.config
            val builtIn = detectAiClis()
            val custom =
                cfg.customAiClis.map { d ->
                    AiCli(d.name, d.command, d.description) to
                        io.github.rygel.needlecast.process.ProcessExecutor
                            .isOnPath(d.command)
                }
            val all =
                (builtIn + custom).filter { (cli, _) ->
                    cfg.aiCliEnabled[cli.command] != false
                }
            SwingUtilities.invokeLater {
                cliCache = all
                cliCacheReady = true
                registry.terminalPanel.availableCliTools = all.filter { it.second }.map { it.first }
            }
        }, "cli-detector").apply {
            isDaemon = true
            start()
        }
    }

    private fun launchCliInTerminal(cli: AiCli) {
        registry.terminalPanel.sendInput("${cli.command}\n")
        registry.statusBar.setStatus("Launched ${cli.name}")
    }

    // ── Help menu ──────────────────────────────────────────────────────────────

    private fun buildHelpMenu(i18n: io.github.rygel.outerstellar.i18n.I18nService): JMenu {
        val checkUpdateItem =
            JMenuItem("Check for Updates...").apply {
                addActionListener { callbacks.checkForUpdatesManual() }
            }
        val aboutItem =
            JMenuItem(i18n.translate("menu.help.about")).apply {
                addActionListener { showAbout() }
            }
        return JMenu(i18n.translate("menu.help")).apply {
            add(checkUpdateItem)
            addSeparator()
            add(aboutItem)
        }
    }

    private fun showAbout() {
        val version = MainWindow.Companion.currentVersion() ?: "dev"
        val repoUrl = "https://github.com/rygel/needlecast"

        val icon =
            javaClass.getResource("/icons/needlecast.png")?.let {
                ImageIcon(ImageIO.read(it).getScaledInstance(64, 64, java.awt.Image.SCALE_SMOOTH))
            }

        val linkLabel =
            JLabel("<html><a href=''>$repoUrl</a></html>").apply {
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                addMouseListener(
                    object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent) {
                            try {
                                Desktop.getDesktop().browse(URI(repoUrl))
                            } catch (_: Exception) {
                            }
                        }
                    },
                )
            }

        val content =
            JPanel(GridBagLayout()).apply {
                val gbc =
                    GridBagConstraints().apply {
                        gridx = 0
                        gridy = 0
                        insets = Insets(0, 0, 12, 0)
                        anchor = GridBagConstraints.CENTER
                    }
                add(
                    JLabel("Needlecast $version", SwingConstants.CENTER).apply {
                        font = font.deriveFont(Font.BOLD, 16f)
                    },
                    gbc,
                )
                gbc.gridy++
                gbc.insets = Insets(0, 0, 4, 0)
                add(JLabel("A project launcher for developers"), gbc)
                gbc.gridy++
                gbc.insets = Insets(0, 0, 12, 0)
                add(JLabel("by Alexander Brandt"), gbc)
                gbc.gridy++
                gbc.insets = Insets(0, 0, 4, 0)
                add(linkLabel, gbc)
                gbc.gridy++
                gbc.insets = Insets(8, 0, 0, 0)
                add(
                    JLabel(
                        "<html><center>MIT License<br>Java ${System.getProperty("java.version")}</center></html>",
                        SwingConstants.CENTER,
                    ).apply {
                        foreground = java.awt.Color.GRAY
                    },
                    gbc,
                )
            }

        JOptionPane.showMessageDialog(
            owner,
            content,
            "About Needlecast",
            JOptionPane.PLAIN_MESSAGE,
            icon,
        )
    }

    // ── Theme ──────────────────────────────────────────────────────────────────

    private fun setTheme(themeId: String) {
        val dark = ThemeRegistry.apply(themeId)
        callbacks.applyUiFont()
        coordinator.propagateThemeChange(dark)
        ctx.updateConfig(ctx.config.copy(theme = themeId))
        ctx.reloadTheme()
    }
}

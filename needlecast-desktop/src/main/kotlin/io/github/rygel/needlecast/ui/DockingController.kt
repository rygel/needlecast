package io.github.rygel.needlecast.ui

import io.github.andrewauclair.moderndocking.DockableTabPreference
import io.github.andrewauclair.moderndocking.DockingRegion
import io.github.andrewauclair.moderndocking.app.AppState
import io.github.andrewauclair.moderndocking.app.Docking
import io.github.andrewauclair.moderndocking.app.RootDockingPanel
import io.github.andrewauclair.moderndocking.ext.ui.DockingUI
import io.github.andrewauclair.moderndocking.settings.Settings
import io.github.rygel.needlecast.AppContext
import java.awt.AWTEvent
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Insets
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter

class DockingController(
    private val registry: PanelRegistry,
    private val ctx: AppContext,
) {

    private val layoutFile: File = Path.of(
        System.getProperty("user.home"), ".needlecast", "docking-layout.xml"
    ).toFile()

    private var frame: JFrame? = null
    private var highlightedDockable: DockablePanel? = null

    private val panelHoverListener = AWTEventListener { event ->
        if (!ctx.config.panelHoverHighlight) return@AWTEventListener
        if (event !is MouseEvent) return@AWTEventListener
        if (event.id != MouseEvent.MOUSE_MOVED && event.id != MouseEvent.MOUSE_ENTERED) return@AWTEventListener
        val source = event.source as? Component ?: return@AWTEventListener
        val hovered = SwingUtilities.getAncestorOfClass(DockablePanel::class.java, source) as? DockablePanel
        if (hovered !== highlightedDockable) {
            highlightedDockable?.setHoverHighlight(false)
            hovered?.setHoverHighlight(true)
            highlightedDockable = hovered
        }
    }

    fun isEnabled(): Boolean = System.getProperty("needlecast.skipDocking")
        ?.equals("true", ignoreCase = true) != true

    fun initialize(frame: JFrame) {
        this.frame = frame
        Docking.initialize(frame)
        DockingUI.initialize()
        Settings.setActiveHighlighterEnabled(ctx.config.dockingActiveHighlight)
        UIManager.getDefaults()["TabbedPane.contentBorderInsets"] = Insets(0, 0, 0, 0)
        UIManager.getDefaults()["TabbedPane.tabsOverlapBorder"] = true
        registry.allDockables.forEach { Docking.registerDockable(it) }
    }

    fun buildRootPanel(frame: JFrame): Container {
        val rootPanel = RootDockingPanel(frame)
        if (!Docking.getRootPanels().containsKey(frame)) {
            Docking.registerDockingPanel(rootPanel, frame)
        }
        val content = JPanel(BorderLayout())
        content.add(rootPanel, BorderLayout.CENTER)
        content.add(registry.statusBar, BorderLayout.SOUTH)
        return content
    }

    fun buildSimplePanel(): Container {
        val content = JPanel(BorderLayout())
        content.add(registry.statusBar, BorderLayout.SOUTH)
        return content
    }

    // ── Layout ───────────────────────────────────────────────────────────────

    fun restoreLayout() {
        AppState.setPersistFile(layoutFile)
        applyTabPreference()
        val restored = try { AppState.restore() } catch (_: Exception) { false }

        val requiredPanels = listOf(
            registry.terminalDockable, registry.editorDockable, registry.commandsDockable,
            registry.projectTreeDockable, registry.promptInputDockable, registry.commandInputDockable,
            registry.skillsDockable,
        )
        val allPresent = requiredPanels.all { Docking.isDocked(it) }

        if (!restored || !allPresent) {
            registry.allDockables
                .forEach { if (Docking.isDocked(it)) Docking.undock(it) }
            layoutFile.delete()
            applyDefaultLayout()
        }

        AppState.setAutoPersist(true)
    }

    private fun applyTabPreference() {
        Settings.setDefaultTabPreference(
            if (ctx.config.tabsOnTop) DockableTabPreference.TOP_ALWAYS
            else DockableTabPreference.NONE
        )
    }

    fun applyDefaultLayout() {
        applyTabPreference()
        val f = frame ?: return
        Docking.dock(registry.terminalDockable,    f,                          DockingRegion.CENTER)
        Docking.dock(registry.projectTreeDockable,  registry.terminalDockable,  DockingRegion.WEST,   0.15)
        Docking.dock(registry.explorerDockable,     registry.projectTreeDockable, DockingRegion.CENTER)
        Docking.dock(registry.commandsDockable,     registry.terminalDockable,   DockingRegion.EAST,   0.20)
        Docking.dock(registry.gitLogDockable,       registry.commandsDockable,   DockingRegion.CENTER)
        Docking.dock(registry.logViewerDockable,    registry.gitLogDockable,     DockingRegion.CENTER)
        Docking.dock(registry.searchDockable,       registry.logViewerDockable,  DockingRegion.CENTER)
        Docking.dock(registry.docsDockable,         registry.searchDockable,     DockingRegion.CENTER)
        Docking.dock(registry.skillsDockable,       registry.docsDockable,       DockingRegion.CENTER)
        Docking.dock(registry.editorDockable,       registry.terminalDockable,   DockingRegion.CENTER)
        Docking.dock(registry.diffDockable,         registry.commandsDockable,   DockingRegion.SOUTH,  0.55)
        if (ctx.config.showConsole) {
            Docking.dock(registry.consoleDockable,  registry.diffDockable,       DockingRegion.CENTER)
        }
        Docking.dock(registry.promptInputDockable,  registry.terminalDockable,   DockingRegion.SOUTH,  0.90)
        Docking.dock(registry.commandInputDockable,  registry.promptInputDockable, DockingRegion.CENTER)

        SwingUtilities.invokeLater { selectPrimaryTabs() }
    }

    fun resetLayout(onStatus: (String) -> Unit) {
        AppState.setAutoPersist(false)
        registry.allDockables
            .forEach { if (Docking.isDocked(it)) Docking.undock(it) }
        layoutFile.delete()
        applyDefaultLayout()
        AppState.setAutoPersist(true)
        onStatus("Layout reset to default")
    }

    private fun selectPrimaryTabs() {
        selectDockableTab(registry.projectTreeDockable)
        selectDockableTab(registry.terminalDockable)
        selectDockableTab(registry.commandsDockable)
        selectDockableTab(registry.diffDockable)
        selectDockableTab(registry.promptInputDockable)
    }

    private fun selectDockableTab(dockable: DockablePanel) {
        val tabbed = SwingUtilities.getAncestorOfClass(javax.swing.JTabbedPane::class.java, dockable) as? javax.swing.JTabbedPane
            ?: return
        for (i in 0 until tabbed.tabCount) {
            val comp = tabbed.getComponentAt(i)
            if (SwingUtilities.isDescendingFrom(dockable, comp)) {
                tabbed.selectedIndex = i
                return
            }
        }
    }

    fun selectTab(dockableId: String) {
        val dockable = registry.allDockables.find { it.dockableId == dockableId } ?: return
        selectDockableTab(dockable)
    }

    fun isDocked(dockableId: String): Boolean {
        val dockable = registry.allDockables.find { it.dockableId == dockableId } ?: return false
        return Docking.isDocked(dockable)
    }

    // ── View toggles ─────────────────────────────────────────────────────────

    private fun dockTo(
        dockable: DockablePanel,
        anchor: DockablePanel?,
        region: DockingRegion,
        proportion: Double? = null,
    ) {
        if (Docking.isDocked(dockable)) return
        val f = frame ?: return
        if (anchor != null && Docking.isDocked(anchor)) {
            if (proportion != null) Docking.dock(dockable, anchor, region, proportion)
            else Docking.dock(dockable, anchor, region)
        } else {
            Docking.dock(dockable, f, region)
        }
    }

    fun toggleConsole(show: Boolean) {
        if (show && !Docking.isDocked(registry.consoleDockable)) {
            val anchor = when {
                Docking.isDocked(registry.commandsDockable) -> registry.commandsDockable
                Docking.isDocked(registry.explorerDockable) -> registry.explorerDockable
                else                                        -> registry.terminalDockable
            }
            Docking.dock(registry.consoleDockable, anchor, DockingRegion.SOUTH, 0.65)
        } else if (!show && Docking.isDocked(registry.consoleDockable)) {
            Docking.undock(registry.consoleDockable)
        }
        ctx.updateConfig(ctx.config.copy(showConsole = show))
    }

    fun toggleExplorer(show: Boolean) {
        if (show && !Docking.isDocked(registry.explorerDockable)) {
            val f = frame
            if (Docking.isDocked(registry.terminalDockable))
                Docking.dock(registry.explorerDockable, registry.terminalDockable, DockingRegion.EAST, 0.35)
            else if (f != null)
                Docking.dock(registry.explorerDockable, f, DockingRegion.EAST, 0.35)
        } else if (!show && Docking.isDocked(registry.explorerDockable)) {
            Docking.undock(registry.explorerDockable)
        }
        ctx.updateConfig(ctx.config.copy(showExplorer = show))
    }

    fun toggleCommands(show: Boolean) {
        if (show && !Docking.isDocked(registry.commandsDockable)) {
            dockTo(registry.commandsDockable, registry.terminalDockable, DockingRegion.EAST, 0.28)
        } else if (!show && Docking.isDocked(registry.commandsDockable)) {
            Docking.undock(registry.commandsDockable)
        }
    }

    fun toggleGitLog(show: Boolean) {
        if (show && !Docking.isDocked(registry.gitLogDockable)) {
            if (Docking.isDocked(registry.commandsDockable)) Docking.dock(registry.gitLogDockable, registry.commandsDockable, DockingRegion.CENTER)
            else dockTo(registry.gitLogDockable, registry.terminalDockable, DockingRegion.EAST, 0.28)
        } else if (!show && Docking.isDocked(registry.gitLogDockable)) {
            Docking.undock(registry.gitLogDockable)
        }
    }

    fun toggleDiff(show: Boolean) {
        if (show && !Docking.isDocked(registry.diffDockable)) {
            val anchor = when {
                Docking.isDocked(registry.consoleDockable) -> registry.consoleDockable
                Docking.isDocked(registry.commandsDockable) -> registry.commandsDockable
                else -> registry.terminalDockable
            }
            Docking.dock(registry.diffDockable, anchor, DockingRegion.SOUTH, 0.55)
        } else if (!show && Docking.isDocked(registry.diffDockable)) {
            Docking.undock(registry.diffDockable)
        }
    }

    fun toggleSearch(show: Boolean) {
        if (show && !Docking.isDocked(registry.searchDockable)) {
            if (Docking.isDocked(registry.commandsDockable)) Docking.dock(registry.searchDockable, registry.commandsDockable, DockingRegion.CENTER)
            else dockTo(registry.searchDockable, registry.terminalDockable, DockingRegion.EAST, 0.28)
        } else if (!show && Docking.isDocked(registry.searchDockable)) {
            Docking.undock(registry.searchDockable)
        }
    }

    fun toggleEditor(show: Boolean) {
        if (show && !Docking.isDocked(registry.editorDockable)) {
            dockTo(registry.editorDockable, registry.terminalDockable, DockingRegion.CENTER)
        } else if (!show && Docking.isDocked(registry.editorDockable)) {
            Docking.undock(registry.editorDockable)
        }
    }

    fun togglePromptInput(show: Boolean) {
        if (show && !Docking.isDocked(registry.promptInputDockable)) {
            dockTo(registry.promptInputDockable, registry.terminalDockable, DockingRegion.SOUTH, 0.78)
        } else if (!show && Docking.isDocked(registry.promptInputDockable)) {
            Docking.undock(registry.promptInputDockable)
        }
    }

    fun toggleCommandInput(show: Boolean) {
        if (show && !Docking.isDocked(registry.commandInputDockable)) {
            val anchor = if (Docking.isDocked(registry.promptInputDockable)) registry.promptInputDockable else registry.terminalDockable
            dockTo(registry.commandInputDockable, anchor, DockingRegion.SOUTH, 0.78)
        } else if (!show && Docking.isDocked(registry.commandInputDockable)) {
            Docking.undock(registry.commandInputDockable)
        }
    }

    fun toggleRenovate(show: Boolean) {
        if (show && !Docking.isDocked(registry.renovateDockable)) {
            if (Docking.isDocked(registry.commandsDockable)) Docking.dock(registry.renovateDockable, registry.commandsDockable, DockingRegion.CENTER)
            else dockTo(registry.renovateDockable, registry.terminalDockable, DockingRegion.EAST, 0.28)
        } else if (!show && Docking.isDocked(registry.renovateDockable)) {
            Docking.undock(registry.renovateDockable)
        }
    }

    fun toggleDocViewer(show: Boolean) {
        if (show && !Docking.isDocked(registry.docViewerDockable)) {
            if (Docking.isDocked(registry.docsDockable)) Docking.dock(registry.docViewerDockable, registry.docsDockable, DockingRegion.CENTER)
            else dockTo(registry.docViewerDockable, registry.terminalDockable, DockingRegion.EAST, 0.28)
        } else if (!show && Docking.isDocked(registry.docViewerDockable)) {
            Docking.undock(registry.docViewerDockable)
        }
    }

    // ── Layout import/export ─────────────────────────────────────────────────

    fun importLayout(onStatus: (String) -> Unit, onError: (String) -> Unit) {
        val chooser = JFileChooser(File(System.getProperty("user.home"))).apply {
            dialogTitle = "Import Layout"
            fileFilter = FileNameExtensionFilter("Needlecast layout (*.needlecast-layout)", "needlecast-layout")
        }
        val f = frame
        if (f == null || chooser.showOpenDialog(f) != JFileChooser.APPROVE_OPTION) return
        try {
            Files.copy(chooser.selectedFile.toPath(), layoutFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            AppState.setAutoPersist(false)
            registry.allDockables.forEach { if (Docking.isDocked(it)) Docking.undock(it) }
            applyDefaultLayout()
            AppState.restore()
            AppState.setAutoPersist(true)
            onStatus("Layout imported")
        } catch (e: Exception) {
            onError("Failed to import layout: ${e.message}")
        }
    }

    fun exportLayout(onStatus: (String) -> Unit, onError: (String) -> Unit) {
        if (!layoutFile.exists()) {
            onError("No saved layout found. Arrange your panels first, then export.")
            return
        }
        val chooser = JFileChooser(File(System.getProperty("user.home"))).apply {
            dialogTitle = "Export Layout"
            fileFilter = FileNameExtensionFilter("Needlecast layout (*.needlecast-layout)", "needlecast-layout")
            selectedFile = File("layout.needlecast-layout")
        }
        val f = frame
        if (f == null || chooser.showSaveDialog(f) != JFileChooser.APPROVE_OPTION) return
        val selected = chooser.selectedFile
        val target = if (selected.name.endsWith(".needlecast-layout")) selected else File("${selected.absolutePath}.needlecast-layout")
        try {
            Files.copy(layoutFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            onStatus("Layout exported to ${target.name}")
        } catch (e: Exception) {
            onError("Failed to export layout: ${e.message}")
        }
    }

    // ── Panel hover highlight ────────────────────────────────────────────────

    fun installHoverHighlighter() {
        Toolkit.getDefaultToolkit().addAWTEventListener(
            panelHoverListener,
            AWTEvent.MOUSE_MOTION_EVENT_MASK or AWTEvent.MOUSE_EVENT_MASK,
        )
    }

    fun clearPanelHighlight() {
        highlightedDockable?.setHoverHighlight(false)
        highlightedDockable = null
    }

    fun dispose() {
        if (!isEnabled()) return
        try {
            registry.allDockables.forEach { dockable ->
                if (Docking.isDockableRegistered(dockable.dockableId)) {
                    Docking.deregisterDockable(dockable)
                }
            }
            frame?.let { Docking.deregisterDockingPanel(it) }
            Docking.uninitialize()
        } catch (_: Exception) {
        }
    }
}

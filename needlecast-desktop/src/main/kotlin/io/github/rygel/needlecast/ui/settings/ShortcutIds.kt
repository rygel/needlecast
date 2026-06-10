package io.github.rygel.needlecast.ui.settings

object ShortcutIds {
    data class Definition(
        val id: String,
        val defaultKey: String,
        val label: String,
    )

    val all: List<Definition> =
        listOf(
            Definition("rescan", "F5", "Rescan projects"),
            Definition("activate-terminal", "ctrl T", "Activate terminal"),
            Definition("focus-projects", "ctrl 1", "Focus project list"),
            Definition("focus-explorer", "ctrl 2", "Focus file explorer"),
            Definition("focus-terminal", "ctrl 3", "Focus terminal"),
            Definition("focus-commands", "ctrl 4", "Focus commands"),
            Definition("project-switcher", "ctrl P", "Global project switcher"),
            Definition("focus-search", "ctrl shift F", "Find in files"),
            Definition("new-terminal-tab", "ctrl shift T", "New terminal tab"),
            Definition("close-terminal-tab", "ctrl W", "Close terminal tab"),
            Definition("next-terminal-tab", "ctrl TAB", "Next terminal tab"),
            Definition("prev-terminal-tab", "ctrl shift TAB", "Previous terminal tab"),
            Definition("zoom-in", "ctrl EQUALS", "Zoom terminal in"),
            Definition("zoom-out", "ctrl MINUS", "Zoom terminal out"),
            Definition("zoom-reset", "ctrl 0", "Reset terminal zoom"),
            Definition("toggle-sidebar", "ctrl B", "Toggle sidebar"),
        )

    val defaults: LinkedHashMap<String, String> =
        linkedMapOf(*all.map { it.id to it.defaultKey }.toTypedArray())

    val labels: Map<String, String> = all.associate { it.id to it.label }
}

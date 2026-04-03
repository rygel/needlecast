package io.github.rygel.needlecast.ui.terminal

enum class AgentStatus {
    /** No terminal active for this project. */
    NONE,
    /** Terminal alive but no output received in the last second — agent is awaiting input. */
    WAITING,
    /** Output is actively flowing — agent is processing. */
    THINKING,
}

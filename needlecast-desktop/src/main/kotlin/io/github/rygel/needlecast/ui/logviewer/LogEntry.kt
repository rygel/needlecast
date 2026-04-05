package io.github.rygel.needlecast.ui.logviewer

enum class LogLevel(val label: String, val colorHex: Int) {
    ERROR("ERROR", 0xF44336),
    WARN("WARN",   0xFFA726),
    INFO("INFO",   0x000000),   // uses default foreground
    DEBUG("DEBUG", 0x888888),
    TRACE("TRACE", 0x888888),
    UNKNOWN("",    0x000000),
}

data class LogEntry(
    val timestamp: String?,
    val thread: String?,
    val level: LogLevel,
    val logger: String?,
    val message: String,
    var stackTrace: String? = null,
    val raw: String,
    val lineNumber: Int = 0,
)

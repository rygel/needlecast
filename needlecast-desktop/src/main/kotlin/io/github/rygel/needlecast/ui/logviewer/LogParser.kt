package io.github.rygel.needlecast.ui.logviewer

/**
 * Parses raw log file lines into structured [LogEntry] objects.
 *
 * Supports:
 * - Logback: `HH:mm:ss.SSS [thread] LEVEL logger - message`
 * - Log4j2:  `YYYY-MM-DD HH:mm:ss,SSS LEVEL [thread] logger - message`
 * - JSON lines: `{"timestamp":..., "level":..., "message":...}`
 * - Plain text: keyword detection for level, rest is message
 *
 * Stack trace lines (starting with whitespace, `at `, `Caused by:`, `...`) are
 * grouped with the preceding log entry.
 */
object LogParser {

    private val LOGBACK = Regex(
        """^(\d{2}:\d{2}:\d{2}[.,]\d{3})\s+\[([^\]]+)]\s+(ERROR|WARN|INFO|DEBUG|TRACE)\s+(\S+)\s+-\s+(.*)$"""
    )
    private val LOG4J2 = Regex(
        """^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}[.,]\d{3})\s+(ERROR|WARN|INFO|DEBUG|TRACE)\s+\[([^\]]+)]\s+(\S+)\s+-\s+(.*)$"""
    )
    private val STACK_TRACE_LINE = Regex("""^\s+(at\s|\.{3}\s|\.\.\.\s).*|^Caused by:.*|^\s+\.\.\.\s*\d+\s+more""")
    private val JSON_OBJECT = Regex("""^\s*\{.*}$""")

    fun parse(lines: List<String>): List<LogEntry> {
        val entries = mutableListOf<LogEntry>()
        var lineNum = 0
        for (line in lines) {
            lineNum++
            if (line.isBlank()) continue

            // Stack trace continuation → append to previous entry
            if (entries.isNotEmpty() && STACK_TRACE_LINE.matches(line)) {
                val prev = entries.last()
                prev.stackTrace = (prev.stackTrace?.plus("\n") ?: "") + line
                continue
            }

            val entry = parseLogback(line, lineNum)
                ?: parseLog4j2(line, lineNum)
                ?: parseJson(line, lineNum)
                ?: parsePlainText(line, lineNum)
            entries.add(entry)
        }
        return entries
    }

    private fun parseLogback(line: String, lineNum: Int): LogEntry? {
        val m = LOGBACK.matchEntire(line) ?: return null
        return LogEntry(
            timestamp = m.groupValues[1],
            thread = m.groupValues[2],
            level = parseLevel(m.groupValues[3]),
            logger = m.groupValues[4],
            message = m.groupValues[5],
            raw = line,
            lineNumber = lineNum,
        )
    }

    private fun parseLog4j2(line: String, lineNum: Int): LogEntry? {
        val m = LOG4J2.matchEntire(line) ?: return null
        return LogEntry(
            timestamp = m.groupValues[1],
            thread = m.groupValues[3],
            level = parseLevel(m.groupValues[2]),
            logger = m.groupValues[4],
            message = m.groupValues[5],
            raw = line,
            lineNumber = lineNum,
        )
    }

    private fun parseJson(line: String, lineNum: Int): LogEntry? {
        if (!JSON_OBJECT.matches(line)) return null
        return try {
            val obj = com.fasterxml.jackson.databind.ObjectMapper().readTree(line)
            val level = obj.path("level").asText("").uppercase()
            val message = obj.path("message").asText(obj.path("msg").asText(""))
            if (message.isEmpty()) return null
            LogEntry(
                timestamp = obj.path("timestamp").asText(obj.path("@timestamp").asText(null)),
                thread = obj.path("thread_name").asText(obj.path("thread").asText(null)),
                level = parseLevel(level),
                logger = obj.path("logger_name").asText(obj.path("logger").asText(null)),
                message = message,
                raw = line,
                lineNumber = lineNum,
            )
        } catch (_: Exception) { null }
    }

    private fun parsePlainText(line: String, lineNum: Int): LogEntry {
        val upper = line.uppercase()
        val level = when {
            "ERROR" in upper || "FATAL" in upper -> LogLevel.ERROR
            "WARN" in upper                      -> LogLevel.WARN
            "DEBUG" in upper                      -> LogLevel.DEBUG
            "TRACE" in upper                      -> LogLevel.TRACE
            else                                  -> LogLevel.UNKNOWN
        }
        return LogEntry(
            timestamp = null, thread = null, level = level, logger = null,
            message = line, raw = line, lineNumber = lineNum,
        )
    }

    private fun parseLevel(s: String): LogLevel = when (s.uppercase()) {
        "ERROR", "FATAL" -> LogLevel.ERROR
        "WARN", "WARNING" -> LogLevel.WARN
        "INFO"  -> LogLevel.INFO
        "DEBUG" -> LogLevel.DEBUG
        "TRACE" -> LogLevel.TRACE
        else    -> LogLevel.UNKNOWN
    }
}

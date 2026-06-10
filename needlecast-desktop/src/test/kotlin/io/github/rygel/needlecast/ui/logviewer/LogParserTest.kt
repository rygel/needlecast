package io.github.rygel.needlecast.ui.logviewer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LogParserTest {

    @Test
    fun `parses Logback format`() {
        val line = "14:23:45.123 [main] INFO  io.github.rygel.needlecast.App - Application started"
        val entries = LogParser.parse(listOf(line))
        assertEquals(1, entries.size)
        val e = entries[0]
        assertEquals("14:23:45.123", e.timestamp)
        assertEquals("main", e.thread)
        assertEquals(LogLevel.INFO, e.level)
        assertEquals("io.github.rygel.needlecast.App", e.logger)
        assertEquals("Application started", e.message)
        assertEquals(1, e.lineNumber)
    }

    @Test
    fun `parses Log4j2 format`() {
        val line = "2026-06-10 14:23:45,123 INFO  [main] io.github.rygel.needlecast.App - Ready"
        val entries = LogParser.parse(listOf(line))
        assertEquals(1, entries.size)
        val e = entries[0]
        assertEquals("2026-06-10 14:23:45,123", e.timestamp)
        assertEquals("main", e.thread)
        assertEquals(LogLevel.INFO, e.level)
        assertEquals("io.github.rygel.needlecast.App", e.logger)
        assertEquals("Ready", e.message)
    }

    @Test
    fun `parses JSON format`() {
        val line = """{"timestamp":"2026-06-10T14:23:45","level":"ERROR","logger_name":"app","message":"failed","thread_name":"worker-1"}"""
        val entries = LogParser.parse(listOf(line))
        assertEquals(1, entries.size)
        val e = entries[0]
        assertEquals(LogLevel.ERROR, e.level)
        assertEquals("failed", e.message)
        assertEquals("app", e.logger)
        assertEquals("worker-1", e.thread)
    }

    @Test
    fun `groups stack trace lines with preceding entry`() {
        val lines = listOf(
            "14:23:45.123 [main] ERROR app.Main - Something broke",
            "    at com.example.Service.doWork(Service.java:42)",
            "    at com.example.App.run(App.java:10)",
            "Caused by: java.lang.NullPointerException",
            "    at com.example.Util.process(Util.java:99)",
        )
        val entries = LogParser.parse(lines)
        assertEquals(1, entries.size)
        assertNotNull(entries[0].stackTrace)
        assertTrue(entries[0].stackTrace!!.contains("at com.example.Service.doWork"))
        assertTrue(entries[0].stackTrace!!.contains("Caused by:"))
    }

    @Test
    fun `parses plain text with level keywords`() {
        val entries = LogParser.parse(listOf("Something went ERROR wrong"))
        assertEquals(1, entries.size)
        assertEquals(LogLevel.ERROR, entries[0].level)
        assertEquals("Something went ERROR wrong", entries[0].message)
    }

    @Test
    fun `skips blank lines`() {
        val lines = listOf(
            "14:23:45.123 [main] INFO  app - hello",
            "",
            "14:23:46.000 [main] INFO  app - world",
        )
        val entries = LogParser.parse(lines)
        assertEquals(2, entries.size)
    }

    @Test
    fun `assigns correct line numbers`() {
        val lines = listOf(
            "",
            "14:23:45.123 [main] INFO  app - first",
            "",
            "14:23:46.000 [main] WARN  app - second",
        )
        val entries = LogParser.parse(lines)
        assertEquals(2, entries[0].lineNumber)
        assertEquals(4, entries[1].lineNumber)
    }

    @Test
    fun `handles empty input`() {
        val entries = LogParser.parse(emptyList())
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `parses WARN and TRACE levels`() {
        val warn = "14:23:45.123 [main] WARN  app - caution"
        val trace = "14:23:45.123 [main] TRACE app - detail"
        val entries = LogParser.parse(listOf(warn, trace))
        assertEquals(LogLevel.WARN, entries[0].level)
        assertEquals(LogLevel.TRACE, entries[1].level)
    }

    @Test
    fun `unknown level falls back to UNKNOWN`() {
        val entries = LogParser.parse(listOf("Just some random text here"))
        assertEquals(1, entries.size)
        assertEquals(LogLevel.UNKNOWN, entries[0].level)
    }
}

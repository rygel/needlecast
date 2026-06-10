package io.github.rygel.needlecast.ui.terminal

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ClaudeUsageServiceTest {

    private val service = ClaudeUsageService { }

    @Test
    fun `parseResponse with complete JSON`() {
        val json = """{
            "five_hour": {"utilization": 0.75, "resets_at": "2026-06-10T15:00:00Z"},
            "seven_day": {"utilization": 0.42, "resets_at": "2026-06-17T00:00:00Z"},
            "seven_day_sonnet": {"utilization": 0.30},
            "seven_day_opus": {"utilization": 0.12}
        }"""
        val result = parseResponse(json)
        assertNotNull(result)
        assertEquals(0.75, result!!.fiveHourPercent!!, 0.001)
        assertEquals("2026-06-10T15:00:00Z", result.fiveHourResetsAt)
        assertEquals(0.42, result.sevenDayPercent!!, 0.001)
        assertEquals("2026-06-17T00:00:00Z", result.sevenDayResetsAt)
        assertEquals(0.30, result.sevenDaySonnetPercent!!, 0.001)
        assertEquals(0.12, result.sevenDayOpusPercent!!, 0.001)
    }

    @Test
    fun `parseResponse with missing fields returns nulls`() {
        val json = """{"five_hour": {}}"""
        val result = parseResponse(json)
        assertNotNull(result)
        assertNull(result!!.fiveHourPercent)
        assertNull(result.fiveHourResetsAt)
        assertNull(result.sevenDayPercent)
    }

    @Test
    fun `parseResponse with error payload returns null`() {
        val json = """{"error": "unauthorized"}"""
        val result = parseResponse(json)
        assertNull(result)
    }

    @Test
    fun `parseResponse with empty object`() {
        val json = "{}"
        val result = parseResponse(json)
        assertNotNull(result)
        assertNull(result!!.fiveHourPercent)
        assertNull(result.sevenDayPercent)
    }

    @Test
    fun `parseResponse with invalid JSON returns null`() {
        val result = parseResponse("not json")
        assertNull(result)
    }

    @Test
    fun `parseResponse with zero utilization`() {
        val json = """{
            "five_hour": {"utilization": 0.0},
            "seven_day": {"utilization": 0.0}
        }"""
        val result = parseResponse(json)
        assertNotNull(result)
        assertEquals(0.0, result!!.fiveHourPercent!!, 0.001)
        assertEquals(0.0, result.sevenDayPercent!!, 0.001)
    }

    @Test
    fun `parseResponse with high utilization`() {
        val json = """{
            "five_hour": {"utilization": 0.99},
            "seven_day": {"utilization": 0.95}
        }"""
        val result = parseResponse(json)
        assertNotNull(result)
        assertEquals(0.99, result!!.fiveHourPercent!!, 0.001)
        assertEquals(0.95, result.sevenDayPercent!!, 0.001)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseResponse(body: String): ClaudeUsageData? {
        val method = ClaudeUsageService::class.java.getDeclaredMethod("parseResponse", String::class.java)
        method.isAccessible = true
        return method.invoke(service, body) as? ClaudeUsageData?
    }
}

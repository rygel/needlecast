package io.github.rygel.needlecast.git

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class GitAutoSyncTest {

    @Test
    fun `shouldFetch returns true when never fetched`() {
        val sync = GitAutoSync(intervalMinutes = 5)
        assertTrue(sync.shouldFetch("/some/project"))
    }

    @Test
    fun `shouldFetch returns false when fetched recently`() {
        val sync = GitAutoSync(intervalMinutes = 5)
        sync.recordFetch("/some/project")
        assertFalse(sync.shouldFetch("/some/project"))
    }

    @Test
    fun `shouldFetch returns true when interval elapsed`() {
        val sync = GitAutoSync(intervalMinutes = 5)
        sync.recordFetch("/some/project", Instant.now().minus(6, ChronoUnit.MINUTES))
        assertTrue(sync.shouldFetch("/some/project"))
    }

    @Test
    fun `shouldFetch is per-project`() {
        val sync = GitAutoSync(intervalMinutes = 5)
        sync.recordFetch("/project/a")
        assertFalse(sync.shouldFetch("/project/a"))
        assertTrue(sync.shouldFetch("/project/b"))
    }

    @Test
    fun `recordFetch updates timestamp`() {
        val sync = GitAutoSync(intervalMinutes = 5)
        sync.recordFetch("/some/project", Instant.now().minus(6, ChronoUnit.MINUTES))
        assertTrue(sync.shouldFetch("/some/project"))
        sync.recordFetch("/some/project")
        assertFalse(sync.shouldFetch("/some/project"))
    }
}

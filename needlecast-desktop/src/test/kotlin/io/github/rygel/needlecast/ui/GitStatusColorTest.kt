package io.github.rygel.needlecast.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.Color

class GitStatusColorTest {
    @Test
    fun `modified status returns blue`() {
        assertEquals(Color(0x4070C0), gitStatusColor(" M"))
    }

    @Test
    fun `staged modified returns blue`() {
        assertEquals(Color(0x4070C0), gitStatusColor("M "))
    }

    @Test
    fun `both modified returns blue`() {
        assertEquals(Color(0x4070C0), gitStatusColor("MM"))
    }

    @Test
    fun `added status returns green`() {
        assertEquals(Color(0x40A040), gitStatusColor("A "))
    }

    @Test
    fun `deleted status returns red`() {
        assertEquals(Color(0xC04040), gitStatusColor(" D"))
    }

    @Test
    fun `staged deleted returns red`() {
        assertEquals(Color(0xC04040), gitStatusColor("D "))
    }

    @Test
    fun `untracked returns grey`() {
        assertEquals(Color(0x888888), gitStatusColor("??"))
    }

    @Test
    fun `renamed returns grey`() {
        assertEquals(Color(0x888888), gitStatusColor("R "))
    }

    @Test
    fun `copied with modification returns blue`() {
        assertEquals(Color(0x4070C0), gitStatusColor("CM"))
    }

    @Test
    fun `modified takes priority over added`() {
        assertEquals(Color(0x4070C0), gitStatusColor("AM"))
    }
}

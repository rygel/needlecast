package io.github.rygel.needlecast.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.ConnectException
import java.net.UnknownHostException
import java.net.http.HttpConnectTimeoutException
import javax.net.ssl.SSLHandshakeException

class UpdateCheckErrorsTest {
    @Test
    fun `classifies http connect timeout as network timeout`() {
        val error =
            HttpConnectTimeoutException("HTTP connect timed out").apply {
                initCause(ConnectException("HTTP connect timed out"))
            }

        val details = UpdateCheckErrors.details(error)

        assertEquals("network_timeout", details.category)
        assertEquals(ConnectException::class.java, details.root::class.java)
        assertTrue(details.userMessage.contains("timed out"))
    }

    @Test
    fun `classifies plain connect exception as network connect failure`() {
        val details = UpdateCheckErrors.details(ConnectException("Connection refused"))

        assertEquals("network_connect", details.category)
        assertTrue(details.userMessage.contains("Could not connect"))
    }

    @Test
    fun `classifies unknown host as dns failure`() {
        val details = UpdateCheckErrors.details(UnknownHostException("github.com"))

        assertEquals("dns_unresolved_host", details.category)
        assertTrue(details.userMessage.contains("resolve"))
    }

    @Test
    fun `classifies ssl handshake failures separately from connect failures`() {
        val details = UpdateCheckErrors.details(SSLHandshakeException("PKIX path building failed"))

        assertEquals("tls_handshake", details.category)
        assertTrue(details.userMessage.contains("secure connection"))
    }

    @Test
    fun `sanitizes multiline log fields`() {
        assertEquals("one two three", UpdateCheckErrors.sanitizeLogField("one\ntwo\tthree"))
    }
}

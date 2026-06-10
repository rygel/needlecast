package io.github.rygel.needlecast.ui

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpTimeoutException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

internal object UpdateCheckErrors {
    data class Details(
        val category: String,
        val root: Throwable,
        val userMessage: String,
    )

    fun details(error: Throwable): Details {
        val root = rootCause(error)
        val category = classify(error)
        return Details(
            category = category,
            root = root,
            userMessage = userMessage(category),
        )
    }

    fun rootCause(error: Throwable): Throwable {
        var current = error
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return current
    }

    fun classify(error: Throwable): String =
        when {
            error.hasCause<SSLHandshakeException>() -> {
                "tls_handshake"
            }

            error.hasCause<SSLException>() -> {
                "tls_ssl"
            }

            error.hasCause<UnknownHostException>() -> {
                "dns_unresolved_host"
            }

            error.hasCause<HttpConnectTimeoutException>() ||
                error.hasCause<HttpTimeoutException>() ||
                error.hasCause<SocketTimeoutException>() -> {
                "network_timeout"
            }

            error.hasCause<ConnectException>() -> {
                val rootMessage = rootCause(error).message.orEmpty().lowercase()
                if (rootMessage.contains("timed out") || rootMessage.contains("timeout")) {
                    "network_timeout"
                } else {
                    "network_connect"
                }
            }

            else -> {
                val message = error.fullMessage().lowercase()
                when {
                    message.contains("pkix") || message.contains("certification path") -> "tls_cert_path"
                    message.contains("certificate") -> "tls_certificate"
                    else -> "unknown"
                }
            }
        }

    fun sanitizeLogField(value: String?): String {
        if (value.isNullOrBlank()) return "-"
        return value.replace(Regex("[\\r\\n\\t]+"), " ").trim()
    }

    private fun userMessage(category: String): String =
        when (category) {
            "network_timeout" -> {
                "Could not reach the update server before the connection timed out. Check your network, VPN, proxy, or firewall."
            }

            "network_connect" -> {
                "Could not connect to the update server. Check your network, VPN, proxy, or firewall."
            }

            "dns_unresolved_host" -> {
                "Could not resolve the update server. Check your DNS or network connection."
            }

            "tls_handshake",
            "tls_ssl",
            "tls_cert_path",
            "tls_certificate",
            -> {
                "The secure connection to the update server failed. This may be caused by a proxy or certificate trust issue."
            }

            else -> {
                "Could not check for updates. See the log for details."
            }
        }

    private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is T) return true
            current = current.cause?.takeIf { it !== current }
        }
        return false
    }

    private fun Throwable.fullMessage(): String {
        val parts = mutableListOf<String>()
        var current: Throwable? = this
        while (current != null) {
            parts.add(current.message.orEmpty())
            current = current.cause?.takeIf { it !== current }
        }
        return parts.joinToString(" ")
    }
}

package com.waytun.app.vpn.xray

import android.net.InetAddresses
import android.util.Log
import com.waytun.app.core.parser.SingBoxLink
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val TAG = "ServerResolver"
private const val RESOLVE_TIMEOUT_MS = 4_000L

/**
 * Resolves the proxy server's hostname once, with the system resolver and *before* the tunnel exists, and
 * hands sing-box the IP instead. Left to itself sing-box resolves the host at every dial through a plain-UDP
 * query to 1.1.1.1 outside the tunnel - on networks that throttle or drop that, the lookup takes up to ten
 * seconds (or fails) and every connection, plus every app's DNS (which rides the proxy), waits for it: the
 * "nothing opens for a minute after connecting" symptom.
 *
 * The original hostname is kept where it matters (TLS server name, HTTP Host / WebSocket host), so the
 * handshake looks exactly as it would have.
 */
object ServerAddressResolver {

    private val executor = Executors.newCachedThreadPool { runnable -> Thread(runnable, "server-resolve").apply { isDaemon = true } }

    fun resolve(link: SingBoxLink): SingBoxLink {
        val host = link.server
        if (host.isBlank() || InetAddresses.isNumericAddress(host)) return link
        val address = lookup(host) ?: return link
        Log.d(TAG, "resolved server host to ${address.hostAddress?.let { "an address" }} (${if (address is Inet4Address) "IPv4" else "IPv6"})")
        return link.copy(
            server = address.hostAddress ?: return link,
            // Everything that used to be implied by the server name must now be said explicitly.
            sni = link.sni ?: host.takeIf { link.security != "none" || link.protocol.isQuicBased() },
            wsHost = link.wsHost ?: host,
            httpUpgradeHost = link.httpUpgradeHost ?: host
        )
    }

    private fun lookup(host: String): InetAddress? {
        val future = executor.submit(Callable { InetAddress.getAllByName(host).toList() })
        return try {
            val all = future.get(RESOLVE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            all.firstOrNull { it is Inet4Address } ?: all.firstOrNull()
        } catch (e: Exception) {
            future.cancel(true)
            Log.w(TAG, "system lookup failed or timed out (${e.javaClass.simpleName}); leaving the hostname to sing-box")
            null
        }
    }

    private fun com.waytun.app.core.parser.TunnelProtocol.isQuicBased() = when (this) {
        com.waytun.app.core.parser.TunnelProtocol.HYSTERIA2,
        com.waytun.app.core.parser.TunnelProtocol.HYSTERIA,
        com.waytun.app.core.parser.TunnelProtocol.TUIC -> true
        else -> false
    }
}

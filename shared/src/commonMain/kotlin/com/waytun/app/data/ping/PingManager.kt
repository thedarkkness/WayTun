package com.waytun.app.data.ping

import com.waytun.app.core.parser.SingBoxLinkParser
import com.waytun.app.core.parser.TunnelProtocol
import com.waytun.app.core.time.nowMillis
import com.waytun.app.data.tunnel.TunnelRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** [millis] is null when the server didn't answer; [udpOnly] marks QUIC-based protocols, where a TCP probe says nothing. */
data class PingResult(val millis: Long?, val udpOnly: Boolean, val measuredAtMillis: Long)

/** One TCP handshake to [host]:[port]; a refused connection still counts as an answer. Null on timeout or failure. */
interface TcpProbe {
    suspend fun roundTripMillis(host: String, port: Int, timeoutMs: Int): Long?
}

private data class Endpoint(val host: String, val port: Int, val quic: Boolean)

private const val CONNECT_TIMEOUT_MS = 3_000
private const val MAX_PARALLEL = 8
private const val FRESH_FOR_MS = 60_000L

/**
 * Measures the round trip to each tunnel's server with a TCP handshake; on Android over the *underlying* network,
 * explicitly bypassing any active VPN, so the number is the real distance to the server rather than the path through
 * whatever tunnel happens to be up. A refused connection still counts: the RST proves the host answered, and that's
 * the only thing a TCP probe can learn about a server that only listens on UDP.
 */
class PingManager(
    private val tunnelRepository: TunnelRepository,
    private val probe: TcpProbe
) {
    private val _results = MutableStateFlow<Map<String, PingResult>>(emptyMap())
    val results: StateFlow<Map<String, PingResult>> = _results.asStateFlow()

    private val endpoints = MutableStateFlow<Map<String, Endpoint>>(emptyMap())
    private val permits = Semaphore(MAX_PARALLEL)

    /** Pings [ids]; entries measured within the last minute are skipped unless [force]. */
    suspend fun measure(ids: Collection<String>, force: Boolean = false) = coroutineScope {
        val now = nowMillis()
        val current = _results.value
        ids.filter { force || current[it]?.let { r -> now - r.measuredAtMillis < FRESH_FOR_MS } != true }
            .map { id -> async { permits.withPermit { measureOne(id) } } }
            .awaitAll()
        Unit
    }

    fun forget(ids: Collection<String>) {
        endpoints.update { it - ids.toSet() }
        _results.update { it - ids.toSet() }
    }

    private suspend fun measureOne(id: String) {
        val endpoint = endpoints.value[id] ?: resolveEndpoint(id)?.also { e -> endpoints.update { it + (id to e) } } ?: return
        val millis = probe.roundTripMillis(endpoint.host, endpoint.port, CONNECT_TIMEOUT_MS)
        _results.update { it + (id to PingResult(millis, endpoint.quic && millis == null, nowMillis())) }
    }

    private suspend fun resolveEndpoint(id: String): Endpoint? {
        val raw = tunnelRepository.getDecryptedConfigText(id).getOrNull() ?: return null
        SingBoxLinkParser.parse(raw)?.let { link ->
            val quic = link.protocol in setOf(TunnelProtocol.HYSTERIA2, TunnelProtocol.HYSTERIA, TunnelProtocol.TUIC) ||
                link.network.equals("quic", ignoreCase = true)
            return Endpoint(link.server, link.port, quic)
        }
        // WireGuard / AmneziaWG: "Endpoint = host:port" (IPv6 as [addr]:port)
        val line = raw.lineSequence().firstOrNull { it.trim().startsWith("Endpoint", ignoreCase = true) } ?: return null
        val value = line.substringAfter('=').trim()
        val host = if (value.startsWith("[")) value.substringAfter('[').substringBefore(']') else value.substringBeforeLast(':')
        val port = value.substringAfterLast(':').toIntOrNull() ?: return null
        return Endpoint(host, port, quic = true) // WireGuard is UDP-only
    }
}

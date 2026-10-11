package com.waytun.app.data.ping

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.waytun.app.core.parser.SingBoxLinkParser
import com.waytun.app.core.parser.TunnelProtocol
import com.waytun.app.data.tunnel.TunnelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** [millis] is null when the server didn't answer; [udpOnly] marks QUIC-based protocols, where a TCP probe says nothing. */
data class PingResult(val millis: Long?, val udpOnly: Boolean, val measuredAtMillis: Long)

private data class Endpoint(val host: String, val port: Int, val quic: Boolean)

private const val CONNECT_TIMEOUT_MS = 3_000
private const val MAX_PARALLEL = 8
private const val FRESH_FOR_MS = 60_000L

/**
 * Measures the round trip to each tunnel's server with a TCP handshake over the *underlying*
 * network - explicitly bypassing any active VPN, so the number is the real distance to the server
 * rather than the path through whatever tunnel happens to be up. A refused connection still counts:
 * the RST proves the host answered, and that's the only thing a TCP probe can learn about a server
 * that only listens on UDP.
 */
@Singleton
class PingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tunnelRepository: TunnelRepository
) {
    private val _results = MutableStateFlow<Map<String, PingResult>>(emptyMap())
    val results: StateFlow<Map<String, PingResult>> = _results.asStateFlow()

    private val endpoints = ConcurrentHashMap<String, Endpoint?>()
    private val permits = Semaphore(MAX_PARALLEL)

    /** Pings [ids]; entries measured within the last minute are skipped unless [force]. */
    suspend fun measure(ids: Collection<String>, force: Boolean = false) = coroutineScope {
        val now = System.currentTimeMillis()
        val current = _results.value
        ids.filter { force || current[it]?.let { r -> now - r.measuredAtMillis < FRESH_FOR_MS } != true }
            .map { id -> async(Dispatchers.IO) { permits.withPermit { measureOne(id) } } }
            .awaitAll()
        Unit
    }

    fun forget(ids: Collection<String>) {
        ids.forEach { endpoints.remove(it) }
        _results.update { it - ids.toSet() }
    }

    private suspend fun measureOne(id: String) {
        val endpoint = endpoints.getOrPut(id) { resolveEndpoint(id) } ?: return
        val millis = withContext(Dispatchers.IO) { tcpRoundTrip(endpoint) }
        _results.update { it + (id to PingResult(millis, endpoint.quic && millis == null, System.currentTimeMillis())) }
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

    private fun tcpRoundTrip(endpoint: Endpoint): Long? {
        val network = underlyingNetwork()
        return try {
            val addresses = (network?.getAllByName(endpoint.host) ?: java.net.InetAddress.getAllByName(endpoint.host)).toList()
            val address = addresses.firstOrNull { it is java.net.Inet4Address } ?: addresses.firstOrNull() ?: return null
            Socket().use { socket ->
                network?.bindSocket(socket)
                val start = System.nanoTime()
                try {
                    socket.connect(InetSocketAddress(address, endpoint.port), CONNECT_TIMEOUT_MS)
                } catch (e: ConnectException) {
                    // RST: the host is there and answered; just not on TCP.
                }
                (System.nanoTime() - start) / 1_000_000
            }
        } catch (e: SocketTimeoutException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun underlyingNetwork(): Network? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        return cm.allNetworks.firstOrNull { network ->
            val caps = cm.getNetworkCapabilities(network)
            caps != null && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
}

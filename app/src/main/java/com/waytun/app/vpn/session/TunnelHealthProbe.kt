package com.waytun.app.vpn.session

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tells whether traffic really flows *through* the tunnel right now.
 *
 * A bare TCP connect is useless for this: gVisor/tun2socks-style TUN stacks
 * completes the handshake locally before dialing upstream, so it "succeeds" even when the proxy
 * behind it is dead. The probes therefore send a request and require an actual response. They run
 * from this app's own sockets, which are routed into the tunnel like any other app's.
 */
object TunnelHealthProbe {

    private const val OVERALL_TIMEOUT_MS = 6_000L
    private const val SOCKET_TIMEOUT_MS = 5_000

    /** True if at least one probe got a real answer through the tunnel. */
    suspend fun isHealthy(): Boolean = coroutineScope {
        val verdict = CompletableDeferred<Boolean>()
        listOf("1.1.1.1", "1.0.0.1").forEach { host ->
            launch(Dispatchers.IO) { if (rawHttp(host)) verdict.complete(true) }
        }
        launch(Dispatchers.IO) { if (generate204()) verdict.complete(true) }
        launch { delay(OVERALL_TIMEOUT_MS); verdict.complete(false) }
        verdict.await().also { coroutineContext.cancelChildren() }
    }

    private fun rawHttp(host: String): Boolean = runCatching {
        Socket().use { socket ->
            socket.soTimeout = SOCKET_TIMEOUT_MS
            socket.connect(InetSocketAddress(host, 80), SOCKET_TIMEOUT_MS)
            socket.getOutputStream().apply {
                write("HEAD / HTTP/1.0\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray())
                flush()
            }
            val first = ByteArray(5)
            socket.getInputStream().read(first) >= 5 && String(first).startsWith("HTTP/")
        }
    }.getOrDefault(false)

    private fun generate204(): Boolean = runCatching {
        val connection = URL("https://www.gstatic.com/generate_204").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = SOCKET_TIMEOUT_MS
            connection.readTimeout = SOCKET_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.responseCode in 200..399
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(false)

    /** Whether any non-VPN network with internet access exists (reconnecting is pointless without one). */
    suspend fun hasUnderlyingNetwork(context: Context): Boolean = withContext(Dispatchers.Default) {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return@withContext true
        cm.allNetworks.any { network ->
            val caps = cm.getNetworkCapabilities(network)
            caps != null &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
}

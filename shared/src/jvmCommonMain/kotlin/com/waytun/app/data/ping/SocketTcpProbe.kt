package com.waytun.app.data.ping

import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where pings go: on Android the non-VPN network; elsewhere the default route. */
interface UnderlyingNetwork {
    fun resolve(host: String): List<InetAddress>
    fun bind(socket: Socket)

    object Default : UnderlyingNetwork {
        override fun resolve(host: String): List<InetAddress> = InetAddress.getAllByName(host).toList()
        override fun bind(socket: Socket) = Unit
    }
}

class SocketTcpProbe(private val network: UnderlyingNetwork = UnderlyingNetwork.Default) : TcpProbe {
    override suspend fun roundTripMillis(host: String, port: Int, timeoutMs: Int): Long? = withContext(Dispatchers.IO) {
        try {
            val addresses = network.resolve(host)
            val address = addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull() ?: return@withContext null
            Socket().use { socket ->
                network.bind(socket)
                val start = System.nanoTime()
                try {
                    socket.connect(InetSocketAddress(address, port), timeoutMs)
                } catch (e: ConnectException) {
                    // RST: the host is there and answered; just not on TCP.
                }
                (System.nanoTime() - start) / 1_000_000
            }
        } catch (e: Exception) {
            null
        }
    }
}

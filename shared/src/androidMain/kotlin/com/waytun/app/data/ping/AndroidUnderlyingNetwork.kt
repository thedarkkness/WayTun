package com.waytun.app.data.ping

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.InetAddress
import java.net.Socket

/** The first non-VPN network with internet access, so pings bypass an active tunnel. */
class AndroidUnderlyingNetwork(private val context: Context) : UnderlyingNetwork {
    override fun resolve(host: String): List<InetAddress> =
        (network()?.getAllByName(host) ?: InetAddress.getAllByName(host)).toList()

    override fun bind(socket: Socket) {
        network()?.bindSocket(socket)
    }

    private fun network(): Network? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        return cm.allNetworks.firstOrNull { network ->
            val caps = cm.getNetworkCapabilities(network)
            caps != null && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
}

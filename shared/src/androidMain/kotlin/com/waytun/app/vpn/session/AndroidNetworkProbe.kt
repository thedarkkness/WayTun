package com.waytun.app.vpn.session

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidNetworkProbe(private val context: Context) : NetworkProbe {

    override suspend fun hasUnderlyingNetwork(): Boolean = withContext(Dispatchers.Default) {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return@withContext true
        cm.allNetworks.any { network ->
            val caps = cm.getNetworkCapabilities(network)
            caps != null &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    override suspend fun tunnelIsHealthy(): Boolean = TunnelHealthProbe.isHealthy()
}

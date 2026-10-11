package com.waytun.app.service

import android.content.Intent
import android.os.IBinder
import com.waytun.app.data.tunnel.TunnelRepository
import com.waytun.app.notification.VpnNotificationFactory
import com.waytun.app.vpn.WireGuardVpnBackend
import com.waytun.app.vpn.remote.ACTION_OBSERVE_STATE
import com.wireguard.android.backend.GoBackend
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The WireGuard VPN service, running in its own `:wireguard` process. GoBackend hardcodes a
 * reference to its own nested [GoBackend.VpnService] class when it needs to start one itself; by
 * subclassing it and always starting *this* class ourselves first (see VpnServiceRouting),
 * GoBackend finds its internal "is a VpnService already running" future already completed and
 * never tries to start the base class directly (which isn't declared in the manifest).
 */
@AndroidEntryPoint
class WayTunVpnService : GoBackend.VpnService() {

    @Inject lateinit var vpnBackend: WireGuardVpnBackend
    @Inject lateinit var tunnelRepository: TunnelRepository
    @Inject lateinit var notificationFactory: VpnNotificationFactory

    private lateinit var runtime: VpnServiceRuntime

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == ACTION_OBSERVE_STATE) runtime.binder else super.onBind(intent)

    override fun onCreate() {
        super.onCreate()
        runtime = VpnServiceRuntime(this, vpnBackend, tunnelRepository, notificationFactory, ACTION_CONNECT, ACTION_DISCONNECT)
        runtime.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runtime.onStartCommand(intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onRevoke() {
        runtime.onRevoke()
        super.onRevoke()
    }

    override fun onDestroy() {
        runtime.onDestroy()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CONNECT = "com.waytun.app.action.CONNECT"
        const val ACTION_DISCONNECT = "com.waytun.app.action.DISCONNECT"
    }
}

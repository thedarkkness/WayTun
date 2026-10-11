package com.waytun.app.service

import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import com.waytun.app.data.tunnel.TunnelRepository
import com.waytun.app.notification.VpnNotificationFactory
import com.waytun.app.vpn.remote.ACTION_OBSERVE_STATE
import com.waytun.app.vpn.xray.XrayVpnBackend
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** The Xray-core VPN service, running in its own `:xray` process (see [WayTunSingBoxVpnService]). */
@AndroidEntryPoint
class WayTunXrayVpnService : VpnService() {

    @Inject lateinit var vpnBackend: XrayVpnBackend
    @Inject lateinit var tunnelRepository: TunnelRepository
    @Inject lateinit var notificationFactory: VpnNotificationFactory

    private lateinit var runtime: VpnServiceRuntime

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == ACTION_OBSERVE_STATE) runtime.binder else super.onBind(intent)

    override fun onCreate() {
        super.onCreate()
        vpnBackend.attachService(this)
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
        vpnBackend.detachService(this)
        super.onDestroy()
    }

    companion object {
        const val ACTION_CONNECT = "com.waytun.app.action.XRAY_CONNECT"
        const val ACTION_DISCONNECT = "com.waytun.app.action.XRAY_DISCONNECT"
    }
}

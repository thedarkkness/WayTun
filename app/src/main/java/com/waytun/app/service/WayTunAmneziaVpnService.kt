package com.waytun.app.service

import android.content.Intent
import android.os.IBinder
import com.waytun.app.data.tunnel.TunnelRepository
import com.waytun.app.notification.VpnNotificationFactory
import com.waytun.app.vpn.AmneziaVpnBackend
import com.waytun.app.vpn.remote.ACTION_OBSERVE_STATE
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.amnezia.awg.backend.GoBackend

/**
 * AmneziaWG's counterpart of [WayTunVpnService], running in its own `:amneziawg` process. The
 * AmneziaWG library has the exact same "hardcoded reference to its own nested VpnService class"
 * design as the WireGuard library (see [WayTunVpnService]'s doc), so it needs its own subclass and
 * its own always-start-us-first sequencing, independent of the WireGuard service/backend.
 */
@AndroidEntryPoint
class WayTunAmneziaVpnService : GoBackend.VpnService() {

    @Inject lateinit var vpnBackend: AmneziaVpnBackend
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
        const val ACTION_CONNECT = "com.waytun.app.action.AMNEZIA_CONNECT"
        const val ACTION_DISCONNECT = "com.waytun.app.action.AMNEZIA_DISCONNECT"
    }
}

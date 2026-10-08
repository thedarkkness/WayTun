package com.waytun.app.service

import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import com.waytun.app.R
import com.waytun.app.data.tunnel.TunnelRepository
import com.waytun.app.notification.VpnNotificationFactory
import com.waytun.app.vpn.ActiveTunnel
import com.waytun.app.vpn.AmneziaBackend
import com.waytun.app.vpn.TunnelState
import com.waytun.app.vpn.VpnBackend
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.amnezia.awg.backend.GoBackend

/**
 * AmneziaWG's counterpart of [WayTunVpnService]. The AmneziaWG library has the exact same
 * "hardcoded reference to its own nested VpnService class" design as the WireGuard library (see
 * [WayTunVpnService]'s doc), so it needs its own subclass and its own always-start-us-first
 * sequencing, independent of the WireGuard service/backend.
 */
@AndroidEntryPoint
class WayTunAmneziaVpnService : GoBackend.VpnService() {

    @Inject
    @AmneziaBackend
    lateinit var vpnBackend: VpnBackend

    @Inject lateinit var tunnelRepository: TunnelRepository
    @Inject lateinit var notificationFactory: VpnNotificationFactory

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeTunnelName: String = ""

    override fun onCreate() {
        super.onCreate()
        notificationFactory.ensureChannel()
        startForeground(
            VpnNotificationFactory.NOTIFICATION_ID,
            notificationFactory.build(getString(R.string.app_name), TunnelState.Connecting),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
        )
        vpnBackend.state.drop(1).onEach { state ->
            updateNotification(state)
            if (state is TunnelState.Disconnected) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }.launchIn(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val tunnelId = intent.getStringExtra(EXTRA_TUNNEL_ID)
                activeTunnelName = intent.getStringExtra(EXTRA_TUNNEL_NAME).orEmpty()
                if (tunnelId != null) connect(tunnelId)
            }
            ACTION_DISCONNECT -> disconnect()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun connect(tunnelId: String) {
        serviceScope.launch {
            val rawConfig = tunnelRepository.getDecryptedConfigText(tunnelId).getOrNull()
            if (rawConfig == null) {
                updateNotification(TunnelState.Error(getString(R.string.vpn_error_unknown)))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }
            vpnBackend.start(ActiveTunnel(tunnelId, activeTunnelName, rawConfig))
        }
    }

    private fun disconnect() {
        serviceScope.launch { vpnBackend.stop() }
    }

    private fun updateNotification(state: TunnelState) {
        val title = activeTunnelName.ifEmpty { getString(R.string.app_name) }
        val notification = notificationFactory.build(title, state)
        getSystemService(NotificationManager::class.java)
            ?.notify(VpnNotificationFactory.NOTIFICATION_ID, notification)
    }

    override fun onRevoke() {
        serviceScope.launch { vpnBackend.stop() }
        super.onRevoke()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CONNECT = "com.waytun.app.action.AMNEZIA_CONNECT"
        const val ACTION_DISCONNECT = "com.waytun.app.action.AMNEZIA_DISCONNECT"
        const val EXTRA_TUNNEL_ID = "extra_tunnel_id"
        const val EXTRA_TUNNEL_NAME = "extra_tunnel_name"
    }
}

package com.waytun.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.waytun.app.core.obfuscation.ObfuscationProfile
import com.waytun.app.core.obfuscation.ObfuscationSettings
import com.waytun.app.data.tunnel.TunnelRepository
import com.waytun.app.notification.TunnelNotificationController
import com.waytun.app.notification.VpnNotificationFactory
import com.waytun.app.vpn.TunnelState
import com.waytun.app.vpn.VpnBackend
import com.waytun.app.vpn.remote.VpnStatePublisher
import com.waytun.app.vpn.session.AndroidNetworkProbe
import com.waytun.app.vpn.session.TunnelSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

private const val ERROR_LINGER_MS = 20_000L

/** Intent extras shared by all three VPN services. */
object VpnServiceExtras {
    const val EXTRA_TUNNEL_ID = "extra_tunnel_id"
    const val EXTRA_TUNNEL_NAME = "extra_tunnel_name"
    const val EXTRA_OBFUSCATION_SETTINGS = "extra_obfuscation_settings"
    const val EXTRA_OBFUSCATION_PROFILE = "extra_obfuscation_profile"
}

/**
 * Everything the VPN services (WireGuard, AmneziaWG, Xray) have in common: the
 * foreground notification, the state bridge to the UI process, and the [TunnelSession] that keeps
 * the tunnel alive. Each service only supplies its base class, its backend and its action names.
 */
class VpnServiceRuntime(
    private val service: Service,
    private val backend: VpnBackend,
    private val tunnelRepository: TunnelRepository,
    notificationFactory: VpnNotificationFactory,
    private val connectAction: String,
    private val disconnectAction: String
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tunnelName = ""
    private var stopJob: Job? = null

    private val notification = TunnelNotificationController(service, notificationFactory, scope, disconnectAction, backend::stats)

    private val session = TunnelSession(
        scope = scope,
        backend = backend,
        loadConfig = { id -> tunnelRepository.getDecryptedConfigText(id).getOrNull() },
        listener = object : TunnelSession.Listener {
            override fun onReconnectStatus(attempt: Int?) = notification.setReconnectAttempt(attempt)

            override fun onFinished() {
                stopJob?.cancel()
                if (backend.state.value is TunnelState.Error) {
                    // Stay up briefly in the Error state so the UI can show why, then leave the error
                    // behind as an ordinary (dismissible) notification.
                    stopJob = scope.launch {
                        delay(ERROR_LINGER_MS)
                        service.stopForeground(Service.STOP_FOREGROUND_DETACH)
                        service.stopSelf()
                    }
                } else {
                    service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
                    service.stopSelf()
                }
            }
        },
        network = AndroidNetworkProbe(service)
    )

    private val publisher = VpnStatePublisher(scope, backend, session.reconnectingTunnelId)

    val binder: IBinder get() = publisher.binder

    fun onCreate() {
        notification.startForeground()
        backend.state.drop(1).onEach { notification.onState(it) }.launchIn(scope)
    }

    fun onStartCommand(intent: Intent?) {
        when (intent?.action) {
            connectAction -> {
                stopJob?.cancel()
                val tunnelId = intent.getStringExtra(VpnServiceExtras.EXTRA_TUNNEL_ID) ?: return
                tunnelName = intent.getStringExtra(VpnServiceExtras.EXTRA_TUNNEL_NAME).orEmpty()
                notification.setTunnelName(tunnelName)
                val settings = ObfuscationSettings.fromJson(intent.getStringExtra(VpnServiceExtras.EXTRA_OBFUSCATION_SETTINGS))
                val profile = ObfuscationProfile.fromJson(intent.getStringExtra(VpnServiceExtras.EXTRA_OBFUSCATION_PROFILE))
                session.begin(TunnelSession.Request(tunnelId, tunnelName, settings, profile))
            }
            disconnectAction -> session.userStop()
        }
    }

    /** Another app took over the VPN (or the user revoked it): stop, and above all don't fight back. */
    fun onRevoke() = session.userStop()

    fun onDestroy() {
        notification.stop()
        session.release()
        scope.cancel()
    }
}

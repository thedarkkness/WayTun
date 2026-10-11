package com.waytun.app.vpn.remote

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import com.waytun.app.vpn.TunnelState
import com.waytun.app.vpn.TunnelStats
import com.waytun.app.vpn.VpnBackend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI-process mirror of a backend that actually runs inside [serviceClass]'s own process (see
 * [RemoteVpnState] for why). Read-only: tunnels are started/stopped via the service's
 * CONNECT/DISCONNECT intents, so [start]/[stop] are never meant to be called on this side.
 *
 * Binds without BIND_AUTO_CREATE, so it never spins the service up by itself - it just attaches
 * whenever the service is running (including one that outlived a previous UI process) and falls
 * back to Disconnected when it goes away.
 */
class RemoteVpnBackend(
    context: Context,
    private val serviceClass: Class<*>
) : VpnBackend {

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow<TunnelState>(TunnelState.Disconnected)
    override val state: StateFlow<TunnelState> = _state.asStateFlow()

    private val _activeTunnelId = MutableStateFlow<String?>(null)
    override val activeTunnelId: StateFlow<String?> = _activeTunnelId.asStateFlow()

    private val replies = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            if (message.what == MSG_STATE) {
                _state.value = message.data.tunnelState()
                _activeTunnelId.value = message.data.tunnelId()
            }
            true
        }
    )

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            try {
                Messenger(binder).send(Message.obtain(null, MSG_REGISTER).apply { replyTo = replies })
            } catch (e: RemoteException) {
                reset()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) = reset()

        override fun onBindingDied(name: ComponentName) {
            reset()
            appContext.unbindService(this)
            bind()
        }
    }

    init {
        bind()
    }

    private fun bind() {
        appContext.bindService(
            Intent(appContext, serviceClass).setAction(ACTION_OBSERVE_STATE),
            connection,
            0
        )
    }

    private fun reset() {
        _state.value = TunnelState.Disconnected
        _activeTunnelId.value = null
    }

    override suspend fun start(tunnel: com.waytun.app.vpn.ActiveTunnel): Result<Unit> =
        Result.failure(UnsupportedOperationException("Start via the VPN service's CONNECT intent"))

    override suspend fun stop(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Stop via the VPN service's DISCONNECT intent"))

    override fun stats(): TunnelStats? = null
}

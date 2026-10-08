package com.waytun.app.vpn

import android.content.Context
import com.waytun.app.R
import com.waytun.app.core.parser.TunnelConfigParser
import com.waytun.app.core.parser.TunnelParseResult
import com.wireguard.android.backend.BackendException
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Interface name used for the GoBackend/TUN handle; unrelated to the user-visible tunnel name. */
private const val TUNNEL_INTERFACE_NAME = "waytun0"

@Singleton
class WireGuardVpnBackend @Inject constructor(
    @param:ApplicationContext private val context: Context
) : VpnBackend {

    private val goBackend by lazy { GoBackend(context) }

    private val _state = MutableStateFlow<TunnelState>(TunnelState.Disconnected)
    override val state: StateFlow<TunnelState> = _state.asStateFlow()

    private val _activeTunnelId = MutableStateFlow<String?>(null)
    override val activeTunnelId: StateFlow<String?> = _activeTunnelId.asStateFlow()

    @Volatile
    private var runningHandle: BackendTunnelHandle? = null

    override suspend fun start(tunnel: ActiveTunnel): Result<Unit> = withContext(Dispatchers.IO) {
        _state.value = TunnelState.Connecting
        when (val parsed = TunnelConfigParser.parse(tunnel.rawConfigText)) {
            is TunnelParseResult.WireGuardSuccess -> {
                try {
                    val handle = BackendTunnelHandle(TUNNEL_INTERFACE_NAME, ::onBackendStateChanged)
                    goBackend.setState(handle, Tunnel.State.UP, parsed.config)
                    runningHandle = handle
                    _activeTunnelId.value = tunnel.id
                    Result.success(Unit)
                } catch (e: BackendException) {
                    failWith(backendErrorMessage(e))
                    Result.failure(e)
                } catch (e: Exception) {
                    failWith(e.message ?: context.getString(R.string.vpn_error_unknown))
                    Result.failure(e)
                }
            }
            else -> {
                val message = context.getString(R.string.vpn_error_invalid_config)
                failWith(message)
                Result.failure(IllegalStateException(message))
            }
        }
    }

    override suspend fun stop(): Result<Unit> = withContext(Dispatchers.IO) {
        val handle = runningHandle
        if (handle == null) {
            _state.value = TunnelState.Disconnected
            _activeTunnelId.value = null
            return@withContext Result.success(Unit)
        }
        _state.value = TunnelState.Disconnecting
        runCatching { goBackend.setState(handle, Tunnel.State.DOWN, null); Unit }
            .onSuccess {
                runningHandle = null
                _activeTunnelId.value = null
                _state.value = TunnelState.Disconnected
            }
            .onFailure { e -> failWith(e.message ?: context.getString(R.string.vpn_error_unknown)) }
    }

    override fun stats(): TunnelStats? {
        val handle = runningHandle ?: return null
        return try {
            val statistics = goBackend.getStatistics(handle)
            var rx = 0L
            var tx = 0L
            var latestHandshake: Long? = null
            for (key in statistics.peers()) {
                val peerStats = statistics.peer(key) ?: continue
                rx += peerStats.rxBytes()
                tx += peerStats.txBytes()
                if (peerStats.latestHandshakeEpochMillis() > 0) {
                    latestHandshake = maxOf(latestHandshake ?: 0L, peerStats.latestHandshakeEpochMillis())
                }
            }
            TunnelStats(rx, tx, latestHandshake)
        } catch (e: Exception) {
            null
        }
    }

    private fun failWith(message: String) {
        runningHandle = null
        _activeTunnelId.value = null
        _state.value = TunnelState.Error(message)
    }

    private fun onBackendStateChanged(newState: Tunnel.State) {
        _state.value = when (newState) {
            Tunnel.State.UP -> TunnelState.Connected(System.currentTimeMillis())
            Tunnel.State.DOWN -> TunnelState.Disconnected
            Tunnel.State.TOGGLE -> _state.value
        }
    }

    private fun backendErrorMessage(e: BackendException): String = when (e.reason) {
        BackendException.Reason.VPN_NOT_AUTHORIZED -> context.getString(R.string.vpn_error_not_authorized)
        BackendException.Reason.UNABLE_TO_START_VPN -> context.getString(R.string.vpn_error_unable_to_start)
        BackendException.Reason.TUN_CREATION_ERROR -> context.getString(R.string.vpn_error_tun_creation)
        BackendException.Reason.DNS_RESOLUTION_FAILURE -> context.getString(R.string.vpn_error_dns_resolution)
        BackendException.Reason.TUNNEL_MISSING_CONFIG -> context.getString(R.string.vpn_error_missing_config)
        else -> context.getString(R.string.vpn_error_unknown)
    }

    private class BackendTunnelHandle(
        private val tunnelName: String,
        private val onStateChanged: (Tunnel.State) -> Unit
    ) : Tunnel {
        override fun getName(): String = tunnelName
        override fun onStateChange(newState: Tunnel.State) = onStateChanged(newState)
    }
}

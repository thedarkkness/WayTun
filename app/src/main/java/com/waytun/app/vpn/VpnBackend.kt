package com.waytun.app.vpn

import kotlinx.coroutines.flow.StateFlow

sealed interface TunnelState {
    data object Disconnected : TunnelState
    data object Connecting : TunnelState
    data class Connected(val sinceEpochMillis: Long) : TunnelState
    data object Disconnecting : TunnelState
    data class Error(val message: String) : TunnelState
}

data class TunnelStats(
    val rxBytes: Long,
    val txBytes: Long,
    val latestHandshakeEpochMillis: Long?
)

/** Everything needed to bring a tunnel up; [rawConfigText] is the decrypted original .conf text. */
data class ActiveTunnel(
    val id: String,
    val displayName: String,
    val rawConfigText: String
)

/**
 * Protocol-agnostic control surface for a VPN tunnel. [WireGuardVpnBackend] is the Stage 2
 * implementation (backed by [com.wireguard.android.backend.GoBackend]); an AmneziaWG
 * implementation will be added in Stage 4 behind this same interface.
 */
interface VpnBackend {
    val state: StateFlow<TunnelState>
    val activeTunnelId: StateFlow<String?>

    suspend fun start(tunnel: ActiveTunnel): Result<Unit>
    suspend fun stop(): Result<Unit>
    fun stats(): TunnelStats?
}

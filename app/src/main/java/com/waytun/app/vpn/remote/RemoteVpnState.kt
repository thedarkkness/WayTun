package com.waytun.app.vpn.remote

import android.os.Bundle
import com.waytun.app.vpn.TunnelState

/**
 * Wire format for mirroring a [com.waytun.app.vpn.VpnBackend]'s state from a VPN service's own
 * process into the UI process. Each Go-based backend (wireguard-go, amneziawg-go, Xray) embeds
 * its own Go runtime, and two of those in one process crash it ("unknown caller pc" / "found
 * pointer to free object") even after one has been stopped, so every VPN service runs in its own
 * process and the UI only ever sees state through this bridge.
 */
internal const val ACTION_OBSERVE_STATE = "com.waytun.app.action.OBSERVE_STATE"
internal const val MSG_REGISTER = 1
internal const val MSG_STATE = 2

private const val KEY_KIND = "kind"
private const val KEY_SINCE = "since"
private const val KEY_MESSAGE = "message"
private const val KEY_TUNNEL_ID = "tunnel_id"

private const val KIND_DISCONNECTED = "disconnected"
private const val KIND_CONNECTING = "connecting"
private const val KIND_CONNECTED = "connected"
private const val KIND_DISCONNECTING = "disconnecting"
private const val KIND_ERROR = "error"

internal fun stateBundle(state: TunnelState, tunnelId: String?): Bundle = Bundle().apply {
    putString(KEY_TUNNEL_ID, tunnelId)
    when (state) {
        TunnelState.Disconnected -> putString(KEY_KIND, KIND_DISCONNECTED)
        TunnelState.Connecting -> putString(KEY_KIND, KIND_CONNECTING)
        is TunnelState.Connected -> {
            putString(KEY_KIND, KIND_CONNECTED)
            putLong(KEY_SINCE, state.sinceEpochMillis)
        }
        TunnelState.Disconnecting -> putString(KEY_KIND, KIND_DISCONNECTING)
        is TunnelState.Error -> {
            putString(KEY_KIND, KIND_ERROR)
            putString(KEY_MESSAGE, state.message)
        }
    }
}

internal fun Bundle.tunnelState(): TunnelState = when (getString(KEY_KIND)) {
    KIND_CONNECTING -> TunnelState.Connecting
    KIND_CONNECTED -> TunnelState.Connected(getLong(KEY_SINCE))
    KIND_DISCONNECTING -> TunnelState.Disconnecting
    KIND_ERROR -> TunnelState.Error(getString(KEY_MESSAGE).orEmpty())
    else -> TunnelState.Disconnected
}

internal fun Bundle.tunnelId(): String? = getString(KEY_TUNNEL_ID)

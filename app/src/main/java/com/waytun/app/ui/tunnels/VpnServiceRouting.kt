package com.waytun.app.ui.tunnels

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.waytun.app.core.parser.TunnelProtocol
import com.waytun.app.service.WayTunAmneziaVpnService
import com.waytun.app.service.WayTunVpnService
import com.waytun.app.vpn.VpnBackend

/** Which VPN service component owns a given protocol's tunnels. */
internal fun serviceClassFor(protocol: TunnelProtocol): Class<*> =
    if (protocol == TunnelProtocol.WIREGUARD) WayTunVpnService::class.java else WayTunAmneziaVpnService::class.java

private fun connectActionFor(protocol: TunnelProtocol): String =
    if (protocol == TunnelProtocol.WIREGUARD) WayTunVpnService.ACTION_CONNECT else WayTunAmneziaVpnService.ACTION_CONNECT

private fun disconnectActionFor(protocol: TunnelProtocol): String =
    if (protocol == TunnelProtocol.WIREGUARD) WayTunVpnService.ACTION_DISCONNECT else WayTunAmneziaVpnService.ACTION_DISCONNECT

internal fun startConnect(context: Context, protocol: TunnelProtocol, tunnelId: String, tunnelName: String) {
    val intent = Intent(context, serviceClassFor(protocol))
        .setAction(connectActionFor(protocol))
        .putExtra(WayTunVpnService.EXTRA_TUNNEL_ID, tunnelId)
        .putExtra(WayTunVpnService.EXTRA_TUNNEL_NAME, tunnelName)
    ContextCompat.startForegroundService(context, intent)
}

internal fun sendDisconnect(context: Context, protocol: TunnelProtocol) {
    context.startService(Intent(context, serviceClassFor(protocol)).setAction(disconnectActionFor(protocol)))
}

internal fun sendDisconnectIfActive(context: Context, backend: VpnBackend, protocol: TunnelProtocol, tunnelId: String) {
    if (backend.activeTunnelId.value == tunnelId) {
        sendDisconnect(context, protocol)
    }
}

package com.waytun.app.ui.tunnels

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.waytun.app.core.obfuscation.ObfuscationProfile
import com.waytun.app.core.obfuscation.ObfuscationSettings
import com.waytun.app.service.VpnServiceExtras
import com.waytun.app.service.WayTunAmneziaVpnService
import com.waytun.app.service.WayTunVpnService
import com.waytun.app.service.WayTunXrayVpnService
import com.waytun.app.vpn.Engine

private fun serviceClassFor(engine: Engine): Class<*> = when (engine) {
    Engine.WIREGUARD -> WayTunVpnService::class.java
    Engine.AMNEZIA -> WayTunAmneziaVpnService::class.java
    Engine.XRAY -> WayTunXrayVpnService::class.java
}

private fun connectActionFor(engine: Engine): String = when (engine) {
    Engine.WIREGUARD -> WayTunVpnService.ACTION_CONNECT
    Engine.AMNEZIA -> WayTunAmneziaVpnService.ACTION_CONNECT
    Engine.XRAY -> WayTunXrayVpnService.ACTION_CONNECT
}

private fun disconnectActionFor(engine: Engine): String = when (engine) {
    Engine.WIREGUARD -> WayTunVpnService.ACTION_DISCONNECT
    Engine.AMNEZIA -> WayTunAmneziaVpnService.ACTION_DISCONNECT
    Engine.XRAY -> WayTunXrayVpnService.ACTION_DISCONNECT
}

internal fun startConnect(
    context: Context,
    engine: Engine,
    tunnelId: String,
    tunnelName: String,
    obfuscation: ObfuscationSettings,
    profile: ObfuscationProfile?
) {
    val intent = Intent(context, serviceClassFor(engine))
        .setAction(connectActionFor(engine))
        .putExtra(VpnServiceExtras.EXTRA_TUNNEL_ID, tunnelId)
        .putExtra(VpnServiceExtras.EXTRA_TUNNEL_NAME, tunnelName)
        .putExtra(VpnServiceExtras.EXTRA_OBFUSCATION_SETTINGS, obfuscation.toJson())
        .putExtra(VpnServiceExtras.EXTRA_OBFUSCATION_PROFILE, profile?.toJson())
    ContextCompat.startForegroundService(context, intent)
}

internal fun sendDisconnect(context: Context, engine: Engine) {
    context.startService(Intent(context, serviceClassFor(engine)).setAction(disconnectActionFor(engine)))
}

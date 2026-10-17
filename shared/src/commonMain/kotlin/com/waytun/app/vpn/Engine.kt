package com.waytun.app.vpn

import com.waytun.app.core.parser.TunnelProtocol

/** The independent VPN cores. Each runs in its own service/process; see vpn/remote. */
enum class Engine(val representativeProtocol: TunnelProtocol) {
    WIREGUARD(TunnelProtocol.WIREGUARD),
    AMNEZIA(TunnelProtocol.AMNEZIA_WG),
    XRAY(TunnelProtocol.VLESS)
}

fun TunnelProtocol.engine(): Engine = when (this) {
    TunnelProtocol.WIREGUARD -> Engine.WIREGUARD
    TunnelProtocol.AMNEZIA_WG -> Engine.AMNEZIA
    else -> Engine.XRAY
}

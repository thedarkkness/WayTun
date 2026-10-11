package com.waytun.app.core.parser

/** Short human label for a tunnel's stack, shown under its name: "VLESS / TCP / REALITY", "WireGuard / UDP", ... */
object TunnelDescriptor {

    fun describe(rawConfig: String): String? {
        SingBoxLinkParser.parse(rawConfig)?.let { return describe(it) }
        return when (TunnelConfigParser.parse(rawConfig)) {
            is TunnelParseResult.WireGuardSuccess -> "WireGuard / UDP"
            is TunnelParseResult.AmneziaSuccess -> "AmneziaWG / UDP"
            else -> null
        }
    }

    fun describe(link: SingBoxLink): String = describeStack(link) + if (link.fromJson) " | JSON" else ""

    private fun describeStack(link: SingBoxLink): String {
        val security = when (link.security) {
            "reality" -> "REALITY"
            "tls" -> "TLS"
            else -> null
        }
        val transport = link.network.uppercase().let { if (it == "RAW") "TCP" else it }
        return when (link.protocol) {
            TunnelProtocol.VLESS -> listOfNotNull("VLESS", transport, security)
            TunnelProtocol.VMESS -> listOfNotNull("VMess", transport, security)
            TunnelProtocol.TROJAN -> listOfNotNull("Trojan", transport, security)
            TunnelProtocol.SHADOWSOCKS -> listOfNotNull("Shadowsocks", link.method, link.plugin)
            TunnelProtocol.HYSTERIA2 -> listOfNotNull("Hysteria2", "QUIC", link.obfsType)
            TunnelProtocol.HYSTERIA -> listOfNotNull("Hysteria", "QUIC")
            TunnelProtocol.TUIC -> listOfNotNull("TUIC", "QUIC")
            TunnelProtocol.WIREGUARD, TunnelProtocol.AMNEZIA_WG -> listOfNotNull(link.protocol.name)
        }.joinToString(" / ")
    }
}

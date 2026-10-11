package com.waytun.app.vpn.xray

import com.waytun.app.core.obfuscation.ObfuscationProfile
import com.waytun.app.core.parser.SingBoxLink
import com.waytun.app.core.parser.TunnelProtocol
import org.json.JSONArray
import org.json.JSONObject

private const val TUN_NAME = "waytun0"
private const val TUN_MTU = 1400

/**
 * Builds the Xray-core JSON for one tunnel: a `tun` inbound (the VpnService descriptor is handed to Xray
 * out of band), the proxy outbound derived from the share link, and routing that sends DNS to Xray's own
 * resolver (cached, answered through the proxy) and optionally rejects UDP/443.
 */
object XrayConfigBuilder {

    class Unsupported(message: String) : IllegalArgumentException(message)

    val tunMtu: Int get() = TUN_MTU

    fun build(link: SingBoxLink, obfuscation: ObfuscationProfile?, blockQuic: Boolean, logFile: String?): String {
        val root = JSONObject()
        // Debug builds only: Xray writes its own log to a file we can read back (it has no logcat output).
        root.put("log", JSONObject().put("loglevel", if (logFile != null) "debug" else "warning").apply { logFile?.let { put("error", it) } })
        root.put(
            "dns",
            JSONObject()
                // TCP, not plain UDP: UDP can't ride every proxy (VLESS+Vision carries no UDP DNS), TCP rides all of them.
                .put("servers", JSONArray().put("tcp://1.1.1.1").put("tcp://8.8.8.8"))
                .put("queryStrategy", "UseIP")
                .put("disableFallback", false)
        )
        root.put(
            "inbounds",
            JSONArray().put(
                JSONObject()
                    .put("tag", "tun")
                    .put("protocol", "tun")
                    .put("settings", JSONObject().put("name", TUN_NAME).put("mtu", TUN_MTU))
                    // Sniffing hands the *domain* to the proxy server to resolve, so a poisoned local
                    // answer can't send a connection to the wrong address.
                    .put("sniffing", JSONObject().put("enabled", true).put("destOverride", JSONArray().put("http").put("tls")))
            )
        )
        val outbounds = JSONArray()
            .put(proxyOutbound(link, obfuscation))
            .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
            .put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
            .put(JSONObject().put("tag", "dns-out").put("protocol", "dns"))
        root.put("outbounds", outbounds)

        // Apps' DNS goes to a public resolver *through the tunnel* (the VPN advertises it, see XrayVpnBackend), like
        // any other UDP/TCP flow - no resolver of our own in the middle.
        val rules = JSONArray()
        if (blockQuic) {
            rules.put(JSONObject().put("type", "field").put("network", "udp").put("port", "443").put("outboundTag", "block"))
        }
        rules.put(JSONObject().put("type", "field").put("protocol", JSONArray().put("bittorrent")).put("outboundTag", "block"))
        root.put("routing", JSONObject().put("domainStrategy", "AsIs").put("rules", rules))
        return root.toString()
    }

    /**
     * Runs a panel-provided Xray config ([entryJson]) as is: its outbounds, routing and DNS stay exactly as the panel
     * wrote them (e.g. `.ru` direct); only the inbounds are replaced by our TUN, the log goes to our file, UDP/443 is
     * optionally blocked and the proxy server's hostname is replaced by [resolvedServer] (SNI/host stay the name).
     */
    fun fromJson(entryJson: String, resolvedServer: String?, originalHost: String?, blockQuic: Boolean, logFile: String?): String {
        val root = JSONObject(entryJson)
        root.put("log", JSONObject().put("loglevel", if (logFile != null) "debug" else "warning").apply { logFile?.let { put("error", it) } })
        root.remove("remarks")
        root.put(
            "inbounds",
            JSONArray().put(
                JSONObject().put("tag", "tun").put("protocol", "tun")
                    .put("settings", JSONObject().put("name", TUN_NAME).put("mtu", TUN_MTU))
                    .put("sniffing", JSONObject().put("enabled", true).put("destOverride", JSONArray().put("http").put("tls")))
            )
        )
        val outbounds = root.optJSONArray("outbounds") ?: JSONArray().also { root.put("outbounds", it) }
        val tags = (0 until outbounds.length()).mapNotNull { outbounds.optJSONObject(it)?.optString("tag") }
        if ("direct" !in tags) outbounds.put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
        if ("block" !in tags) outbounds.put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
        if (resolvedServer != null && originalHost != null) {
            (0 until outbounds.length()).mapNotNull { outbounds.optJSONObject(it) }.firstOrNull { it.optString("tag") == "proxy" }
                ?.let { pinServerAddress(it, originalHost, resolvedServer) }
        }
        val routing = root.optJSONObject("routing") ?: JSONObject().also { root.put("routing", it) }
        if (blockQuic) {
            val rules = routing.optJSONArray("rules") ?: JSONArray().also { routing.put("rules", it) }
            val withBlock = JSONArray().put(JSONObject().put("type", "field").put("network", "udp").put("port", "443").put("outboundTag", "block"))
            for (i in 0 until rules.length()) withBlock.put(rules.get(i))
            routing.put("rules", withBlock)
        }
        return root.toString()
    }

    /** Swaps the hostname for its IP in the server address, keeping the name where TLS/HTTP still need it. */
    private fun pinServerAddress(outbound: JSONObject, host: String, ip: String) {
        val settings = outbound.optJSONObject("settings")
        settings?.optJSONArray("vnext")?.let { for (i in 0 until it.length()) it.optJSONObject(i)?.takeIf { v -> v.optString("address") == host }?.put("address", ip) }
        settings?.optJSONArray("servers")?.let { for (i in 0 until it.length()) it.optJSONObject(i)?.takeIf { v -> v.optString("address") == host }?.put("address", ip) }
        if (settings?.optString("address") == host) settings.put("address", ip)
        val stream = outbound.optJSONObject("streamSettings") ?: return
        stream.optJSONObject("tlsSettings")?.let { if (!it.has("serverName")) it.put("serverName", host) }
        for (key in listOf("xhttpSettings", "splithttpSettings", "wsSettings", "httpupgradeSettings")) {
            stream.optJSONObject(key)?.let { if (!it.has("host") && key != "wsSettings") it.put("host", host) }
        }
    }

    private fun proxyOutbound(link: SingBoxLink, obfuscation: ObfuscationProfile?): JSONObject {
        val outbound = JSONObject().put("tag", "proxy")
        when (link.protocol) {
            TunnelProtocol.VLESS -> {
                outbound.put("protocol", "vless")
                outbound.put(
                    "settings",
                    JSONObject().put(
                        "vnext",
                        JSONArray().put(
                            JSONObject().put("address", link.server).put("port", link.port).put(
                                "users",
                                JSONArray().put(
                                    JSONObject().put("id", link.uuidOrPassword)
                                        .put("encryption", link.encryption?.takeIf { it.isNotBlank() } ?: "none")
                                        .apply { link.flow?.takeIf { it.isNotBlank() }?.let { put("flow", it) } }
                                )
                            )
                        )
                    )
                )
                outbound.put("streamSettings", streamSettings(link, obfuscation))
            }
            TunnelProtocol.VMESS -> {
                outbound.put("protocol", "vmess")
                outbound.put(
                    "settings",
                    JSONObject().put(
                        "vnext",
                        JSONArray().put(
                            JSONObject().put("address", link.server).put("port", link.port).put(
                                "users",
                                JSONArray().put(
                                    JSONObject().put("id", link.uuidOrPassword)
                                        .put("alterId", link.alterId)
                                        .put("security", link.encryption ?: "auto")
                                )
                            )
                        )
                    )
                )
                outbound.put("streamSettings", streamSettings(link, obfuscation))
            }
            TunnelProtocol.TROJAN -> {
                outbound.put("protocol", "trojan")
                outbound.put(
                    "settings",
                    JSONObject().put(
                        "servers",
                        JSONArray().put(JSONObject().put("address", link.server).put("port", link.port).put("password", link.uuidOrPassword))
                    )
                )
                outbound.put("streamSettings", streamSettings(link, obfuscation))
            }
            TunnelProtocol.SHADOWSOCKS -> {
                if (link.plugin != null) throw Unsupported("Shadowsocks plugins (${link.plugin}) aren't supported by Xray")
                outbound.put("protocol", "shadowsocks")
                outbound.put(
                    "settings",
                    JSONObject().put(
                        "servers",
                        JSONArray().put(
                            JSONObject().put("address", link.server).put("port", link.port)
                                .put("method", link.method ?: throw Unsupported("Shadowsocks link without a cipher"))
                                .put("password", link.uuidOrPassword)
                        )
                    )
                )
            }
            TunnelProtocol.HYSTERIA2 -> {
                outbound.put("protocol", "hysteria")
                outbound.put("settings", JSONObject().put("address", link.server).put("port", link.port).put("version", 2))
                outbound.put("streamSettings", hysteria2Stream(link, obfuscation))
            }
            else -> throw Unsupported("${link.protocol} isn't an Xray protocol")
        }
        return outbound
    }

    private fun streamSettings(link: SingBoxLink, obfuscation: ObfuscationProfile?): JSONObject {
        val network = when (val n = link.network.lowercase()) {
            "tcp", "raw" -> "raw"
            "splithttp" -> "xhttp"
            "h2", "http" -> throw Unsupported("The HTTP/2 transport was removed from Xray; this server needs XHTTP")
            "quic", "kcp", "mkcp" -> throw Unsupported("The $n transport isn't supported")
            else -> n
        }
        val stream = JSONObject().put("network", network)
        when (network) {
            "ws" -> stream.put(
                "wsSettings",
                JSONObject().put("path", link.wsPath ?: "/").apply {
                    val headers = JSONObject()
                    link.wsHost?.let { headers.put("Host", it) }
                    obfuscation?.userAgent?.let { headers.put("User-Agent", it) }
                    if (headers.length() > 0) put("headers", headers)
                }
            )
            "grpc" -> stream.put(
                "grpcSettings",
                JSONObject().put("serviceName", link.grpcServiceName ?: "")
                    .apply { link.wsHost?.let { put("authority", it) } }
            )
            "httpupgrade" -> stream.put(
                "httpupgradeSettings",
                JSONObject().put("path", link.httpUpgradePath ?: "/").apply { link.httpUpgradeHost?.let { put("host", it) } }
            )
            "xhttp" -> stream.put(
                "xhttpSettings",
                JSONObject().put("path", link.wsPath ?: "/").put("mode", link.xhttpMode ?: "auto").apply {
                    (link.wsHost)?.let { put("host", it) }
                    link.xhttpExtra?.let { extra -> runCatching { JSONObject(extra) }.getOrNull()?.let { put("extra", it) } }
                }
            )
        }
        when (link.security) {
            "tls" -> {
                stream.put("security", "tls")
                stream.put(
                    "tlsSettings",
                    JSONObject().apply {
                        (link.sni ?: link.wsHost)?.let { put("serverName", it) }
                        (obfuscation?.fingerprint ?: link.fingerprint)?.let { put("fingerprint", it) }
                        if (link.alpn.isNotEmpty()) put("alpn", JSONArray(link.alpn))
                        applyCertChecks(link)
                    }
                )
            }
            "reality" -> {
                stream.put("security", "reality")
                stream.put(
                    "realitySettings",
                    JSONObject()
                        .put("serverName", link.sni ?: "")
                        // REALITY is picky about its ClientHello shape: keep the fingerprint the panel chose
                        // (rotating it made handshakes slow or stall in testing).
                        .put("fingerprint", link.fingerprint ?: "chrome")
                        .put("publicKey", link.realityPublicKey ?: "")
                        .put("shortId", link.realityShortId ?: "")
                        .apply { link.spiderX?.let { put("spiderX", it) } }
                )
            }
        }
        return stream
    }

    /** Xray has no "insecure" switch any more: a server is verified normally, or against a pinned certificate / name. */
    private fun JSONObject.applyCertChecks(link: SingBoxLink) {
        link.pinnedCertSha256?.let { put("pinnedPeerCertSha256", it) }
        link.verifyCertByName?.let { put("verifyPeerCertByName", it) }
    }

    private fun hysteria2Stream(link: SingBoxLink, obfuscation: ObfuscationProfile?): JSONObject {
        val tls = JSONObject().apply {
            link.sni?.let { put("serverName", it) }
            put("alpn", JSONArray(link.alpn.ifEmpty { listOf("h3") }))
            (obfuscation?.fingerprint ?: link.fingerprint)?.let { put("fingerprint", it) }
            applyCertChecks(link)
        }
        val finalMask = link.finalMask?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        if (!finalMask.has("quicParams")) finalMask.put("quicParams", JSONObject().put("congestion", "bbr"))
        if (link.obfsType.equals("salamander", ignoreCase = true) && link.obfsPassword != null) {
            val udp = finalMask.optJSONArray("udp") ?: JSONArray().also { finalMask.put("udp", it) }
            udp.put(JSONObject().put("type", "salamander").put("settings", JSONObject().put("password", link.obfsPassword)))
        }
        if (link.serverPorts.isNotEmpty()) throw Unsupported("Hysteria2 port hopping isn't supported yet")
        return JSONObject()
            .put("network", "hysteria")
            .put("hysteriaSettings", JSONObject().put("version", 2).put("auth", link.uuidOrPassword))
            .put("security", "tls")
            .put("tlsSettings", tls)
            .put("finalmask", finalMask)
    }
}

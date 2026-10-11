package com.waytun.app.core.parser

import android.util.Base64
import java.net.URLDecoder
import org.json.JSONObject

/** A parsed share link for any protocol sing-box runs, independent of sing-box's own JSON config format. */
data class SingBoxLink(
    val protocol: TunnelProtocol,
    val remark: String,
    val server: String,
    val port: Int,
    /** VLESS/VMess/TUIC UUID, or the Trojan/Shadowsocks/Hysteria2 password. */
    val uuidOrPassword: String,
    val encryption: String? = null,
    val alterId: Int = 0,
    val flow: String? = null,
    val network: String = "tcp",
    val security: String = "none",
    val sni: String? = null,
    val fingerprint: String? = null,
    val alpn: List<String> = emptyList(),
    val allowInsecure: Boolean = false,
    val realityPublicKey: String? = null,
    val realityShortId: String? = null,
    val wsPath: String? = null,
    val wsHost: String? = null,
    val grpcServiceName: String? = null,
    val httpUpgradePath: String? = null,
    val httpUpgradeHost: String? = null,
    /** TUIC's password (its [uuidOrPassword] is the UUID). */
    val password: String? = null,
    /** Shadowsocks cipher. */
    val method: String? = null,
    /** Shadowsocks SIP003 plugin ("obfs-local", "v2ray-plugin") and its options. */
    val plugin: String? = null,
    val pluginOptions: String? = null,
    /** Hysteria2 obfuscation ("salamander") and its password. */
    val obfsType: String? = null,
    val obfsPassword: String? = null,
    /** Hysteria2 port hopping, in sing-box's "start:end" form. */
    val serverPorts: List<String> = emptyList(),
    /** Hysteria v1 bandwidth (Mbps) and its legacy obfuscation string. */
    val upMbps: Int? = null,
    val downMbps: Int? = null,
    val legacyObfs: String? = null,
    /** TUIC tuning. */
    val congestionControl: String? = null,
    val udpRelayMode: String? = null,
    /** Came from a JSON (Xray/sing-box) subscription rather than a share link. */
    val fromJson: Boolean = false,
    /** XHTTP (Xray only): `mode` (auto/packet-up/stream-up/stream-one) and the raw `extra` JSON object. */
    val xhttpMode: String? = null,
    val xhttpExtra: String? = null,
    /** REALITY spiderX (`spx`). */
    val spiderX: String? = null,
    /** Xray "finalmask" JSON from `fm=` (QUIC congestion/params, UDP masks such as salamander). */
    val finalMask: String? = null,
    /** Xray TLS verification options: pinned certificate SHA-256 list (`pcs`) and name check (`vcn`). */
    val pinnedCertSha256: String? = null,
    val verifyCertByName: String? = null
)

/**
 * Parses the de-facto standard share-link formats used across the V2Ray/Xray/sing-box client
 * ecosystem (vless://, vmess://, trojan://, ss://, hysteria2:// / hy2://, tuic://), so pasting or
 * scanning a link from any such app or panel just works.
 */
object SingBoxLinkParser {

    private val SCHEMES = listOf("vless://", "vmess://", "trojan://", "ss://", "hysteria2://", "hy2://")

    fun canParse(rawText: String): Boolean {
        val trimmed = rawText.trim()
        if (trimmed.startsWith("{")) return JsonSubscription.linkOf(trimmed) != null
        return SCHEMES.any { trimmed.startsWith(it, ignoreCase = true) }
    }

    fun parse(rawText: String): SingBoxLink? {
        val trimmed = rawText.trim()
        // A whole Xray JSON config (what Happ-style subscriptions deliver): describe it by its proxy server.
        if (trimmed.startsWith("{")) return JsonSubscription.linkOf(trimmed)?.let { parse(it) }
        val scheme = trimmed.substringBefore("://").lowercase()
        val fromJson = Regex("[?&]${JsonSubscription.SOURCE_PARAM}=${JsonSubscription.SOURCE_VALUE}(&|#|$)").containsMatchIn(trimmed) ||
            (scheme == "vmess" && decodeBase64(trimmed.substringAfter("://").trim())?.contains("\"${JsonSubscription.SOURCE_PARAM}\":\"${JsonSubscription.SOURCE_VALUE}\"") == true)
        return try {
            when (scheme) {
                "vless" -> parseVlessOrTrojan(trimmed, TunnelProtocol.VLESS)
                "trojan" -> parseVlessOrTrojan(trimmed, TunnelProtocol.TROJAN)
                "vmess" -> parseVmess(trimmed)
                "ss" -> parseShadowsocks(trimmed)
                "hysteria2", "hy2" -> parseHysteria2(trimmed)
                else -> null
            }?.let { if (fromJson) it.copy(fromJson = true) else it }
        } catch (e: Exception) {
            android.util.Log.w("SingBoxLinkParser", "parse failed for $scheme://: ${e.javaClass.simpleName}")
            null
        }
    }

    /** VLESS and Trojan share the same `scheme://id@host:port?query#remark` shape. */
    private fun parseVlessOrTrojan(rawText: String, protocol: TunnelProtocol): SingBoxLink? {
        val uri = RawUri.parse(rawText) ?: return null
        val userInfo = uri.userInfo?.let(::percentDecode) ?: return null
        val params = uri.params
        return SingBoxLink(
            protocol = protocol,
            remark = uri.remark,
            server = uri.host,
            port = uri.port ?: 443,
            uuidOrPassword = userInfo,
            flow = params["flow"]?.ifBlank { null },
            network = params["type"]?.ifBlank { null } ?: "tcp",
            // Trojan is TLS by definition; links usually only say so when it's something else.
            security = params["security"]?.ifBlank { null }
                ?: if (protocol == TunnelProtocol.TROJAN) "tls" else "none",
            sni = (params["sni"] ?: params["peer"])?.ifBlank { null },
            fingerprint = params["fp"]?.ifBlank { null },
            alpn = params["alpn"].splitList(),
            allowInsecure = (params["allowInsecure"] ?: params["insecure"]).isTrue(),
            realityPublicKey = params["pbk"]?.ifBlank { null },
            realityShortId = params["sid"]?.ifBlank { null },
            wsPath = params["path"]?.ifBlank { null },
            wsHost = params["host"]?.ifBlank { null },
            grpcServiceName = params["serviceName"]?.ifBlank { null },
            httpUpgradePath = params["path"]?.ifBlank { null },
            httpUpgradeHost = params["host"]?.ifBlank { null },
            xhttpMode = params["mode"]?.ifBlank { null },
            xhttpExtra = params["extra"]?.ifBlank { null },
            spiderX = params["spx"]?.ifBlank { null },
            finalMask = params["fm"]?.ifBlank { null },
            pinnedCertSha256 = params["pcs"]?.ifBlank { null },
            verifyCertByName = params["vcn"]?.ifBlank { null }
        )
    }

    /** VMess links are `vmess://<base64 JSON>`, unlike VLESS/Trojan's plain query-string form. */
    private fun parseVmess(rawText: String): SingBoxLink? {
        val payload = rawText.substringAfter("://").trim()
        val json = JSONObject(decodeBase64(payload) ?: return null)
        val host = json.optString("add").ifBlank { return null }
        val tls = json.optString("tls")
        return SingBoxLink(
            protocol = TunnelProtocol.VMESS,
            remark = json.optString("ps").ifBlank { host },
            server = host,
            port = json.optString("port").toIntOrNull() ?: 443,
            uuidOrPassword = json.optString("id"),
            encryption = json.optString("scy", "auto").ifBlank { "auto" },
            alterId = json.optString("aid", "0").toIntOrNull() ?: 0,
            network = json.optString("net", "tcp").ifBlank { "tcp" },
            security = if (tls.equals("tls", ignoreCase = true)) "tls" else "none",
            sni = json.optString("sni").ifBlank { null },
            fingerprint = json.optString("fp").ifBlank { null },
            alpn = json.optString("alpn").splitList(),
            allowInsecure = json.optString("allowInsecure").isTrue(),
            wsPath = json.optString("path").ifBlank { null },
            wsHost = json.optString("host").ifBlank { null },
            grpcServiceName = json.optString("path").ifBlank { null },
            httpUpgradePath = json.optString("path").ifBlank { null },
            httpUpgradeHost = json.optString("host").ifBlank { null }
        )
    }

    /**
     * SIP002 (`ss://base64(method:password)@host:port/?plugin=...#tag`, or a percent-encoded
     * plain `method:password` userinfo as used for 2022 ciphers) and the legacy
     * `ss://base64(method:password@host:port)#tag` form.
     */
    private fun parseShadowsocks(rawText: String): SingBoxLink? {
        var uri = RawUri.parse(rawText) ?: return null
        val credentials: String
        if (uri.userInfo != null) {
            val plain = percentDecode(uri.userInfo!!)
            credentials = if (plain.contains(':')) plain else decodeBase64(uri.userInfo!!) ?: return null
        } else {
            val legacy = decodeBase64(uri.authority) ?: return null
            credentials = legacy.substringBeforeLast('@')
            uri = RawUri.parse("ss://${legacy.substringAfterLast('@')}") ?: return null
        }
        val method = credentials.substringBefore(':').ifBlank { return null }
        val password = credentials.substringAfter(':', "")
        val pluginSpec = uri.params["plugin"]?.ifBlank { null }
        val pluginName = pluginSpec?.substringBefore(';')?.let { if (it == "simple-obfs") "obfs-local" else it }
        return SingBoxLink(
            protocol = TunnelProtocol.SHADOWSOCKS,
            remark = uri.remark,
            server = uri.host,
            port = uri.port ?: return null,
            uuidOrPassword = password,
            method = method,
            plugin = pluginName,
            pluginOptions = pluginSpec?.substringAfter(';', "")?.ifBlank { null }
        )
    }

    /** `hysteria2://auth@host:port[,port-range...]/?sni=&insecure=1&obfs=salamander&obfs-password=&mport=#tag` */
    private fun parseHysteria2(rawText: String): SingBoxLink? {
        val uri = RawUri.parse(rawText) ?: return null
        val params = uri.params
        // Port hopping comes either in the authority ("443,20000-30000") or as `mport`.
        val portSpec = params["mport"]?.ifBlank { null } ?: uri.portText
        val ranges = portSpec?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val hopping = ranges.size > 1 || ranges.any { it.contains('-') } || params["mport"] != null
        val firstPort = ranges.firstOrNull()?.substringBefore('-')?.toIntOrNull() ?: uri.port ?: 443
        return SingBoxLink(
            protocol = TunnelProtocol.HYSTERIA2,
            remark = uri.remark,
            server = uri.host,
            port = firstPort,
            uuidOrPassword = uri.userInfo?.let(::percentDecode).orEmpty(),
            security = "tls",
            sni = (params["sni"] ?: params["peer"])?.ifBlank { null },
            alpn = params["alpn"].splitList(),
            allowInsecure = (params["insecure"] ?: params["allowInsecure"] ?: params["allow_insecure"]).isTrue(),
            obfsType = params["obfs"]?.ifBlank { null },
            obfsPassword = (params["obfs-password"] ?: params["obfs_password"] ?: params["obfsPassword"])?.ifBlank { null },
            finalMask = params["fm"]?.ifBlank { null },
            pinnedCertSha256 = params["pcs"]?.ifBlank { null },
            verifyCertByName = params["vcn"]?.ifBlank { null },
            serverPorts = if (hopping) {
                ranges.map { range -> if (range.contains('-')) range.replace('-', ':') else "$range:$range" }
            } else {
                emptyList()
            }
        )
    }

    /** `hysteria://host:port?auth=&peer=&insecure=1&upmbps=&downmbps=&alpn=&obfsParam=&mport=#tag` (Hysteria v1). */
    private fun parseHysteria(rawText: String): SingBoxLink? {
        val uri = RawUri.parse(rawText) ?: return null
        val params = uri.params
        val portSpec = params["mport"]?.ifBlank { null } ?: uri.portText
        val ranges = portSpec?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val hopping = ranges.size > 1 || ranges.any { it.contains('-') } || params["mport"] != null
        return SingBoxLink(
            protocol = TunnelProtocol.HYSTERIA,
            remark = uri.remark,
            server = uri.host,
            port = ranges.firstOrNull()?.substringBefore('-')?.toIntOrNull() ?: uri.port ?: 443,
            uuidOrPassword = (params["auth"] ?: params["auth_str"] ?: uri.userInfo?.let(::percentDecode)).orEmpty(),
            security = "tls",
            sni = (params["peer"] ?: params["sni"])?.ifBlank { null },
            alpn = params["alpn"].splitList(),
            allowInsecure = params["insecure"].isTrue(),
            upMbps = params["upmbps"]?.toIntOrNull(),
            downMbps = params["downmbps"]?.toIntOrNull(),
            legacyObfs = (params["obfsParam"] ?: params["obfs"])?.ifBlank { null },
            serverPorts = if (hopping) {
                ranges.map { range -> if (range.contains('-')) range.replace('-', ':') else "$range:$range" }
            } else {
                emptyList()
            }
        )
    }

    /** `tuic://uuid:password@host:port?congestion_control=bbr&udp_relay_mode=native&alpn=h3&sni=&allow_insecure=1#tag` */
    private fun parseTuic(rawText: String): SingBoxLink? {
        val uri = RawUri.parse(rawText) ?: return null
        val userInfo = uri.userInfo?.let(::percentDecode) ?: return null
        val params = uri.params
        return SingBoxLink(
            protocol = TunnelProtocol.TUIC,
            remark = uri.remark,
            server = uri.host,
            port = uri.port ?: 443,
            uuidOrPassword = userInfo.substringBefore(':'),
            password = userInfo.substringAfter(':', "").ifEmpty { null },
            security = "tls",
            sni = params["sni"]?.ifBlank { null },
            alpn = params["alpn"].splitList().ifEmpty { listOf("h3") },
            allowInsecure = (params["allow_insecure"] ?: params["insecure"] ?: params["allowInsecure"]).isTrue(),
            congestionControl = (params["congestion_control"] ?: params["congestion-control"])?.ifBlank { null },
            udpRelayMode = (params["udp_relay_mode"] ?: params["udp-relay-mode"])?.ifBlank { null }
        )
    }

    /**
     * Minimal `scheme://[userinfo@]host[:port][/path][?query][#fragment]` splitter. java.net.URI
     * rejects things real links contain (port ranges, raw `+`/`/` in base64 userinfo, emoji).
     */
    private class RawUri(
        val authority: String,
        val userInfo: String?,
        val host: String,
        val portText: String?,
        val params: Map<String, String>,
        fragment: String?
    ) {
        val port: Int? get() = portText?.toIntOrNull()
        val remark: String = fragment?.let(::percentDecode).orEmpty().ifBlank { host }

        companion object {
            fun parse(raw: String): RawUri? {
                val rest = raw.substringAfter("://", "").ifEmpty { return null }
                val fragment = rest.substringAfter('#', "").ifEmpty { null }
                val withoutFragment = rest.substringBefore('#')
                val query = withoutFragment.substringAfter('?', "")
                val beforeQuery = withoutFragment.substringBefore('?')
                // Userinfo (passwords/base64 keys) may contain a raw '/', so the host starts after
                // the *last* '@' and only the part after it is cut at the first '/'.
                val userInfo = if (beforeQuery.contains('@')) beforeQuery.substringBeforeLast('@') else null
                val hostPort = beforeQuery.substringAfterLast('@').substringBefore('/')
                val authority = if (userInfo != null) "$userInfo@$hostPort" else hostPort
                val (host, portText) = if (hostPort.startsWith("[")) {
                    hostPort.substringAfter('[').substringBefore(']') to hostPort.substringAfter("]:", "")
                } else if (hostPort.count { it == ':' } == 1) {
                    hostPort.substringBefore(':') to hostPort.substringAfter(':')
                } else {
                    hostPort to ""
                }
                if (host.isBlank()) return null
                return RawUri(authority, userInfo, host, portText.ifEmpty { null }, parseQuery(query), fragment)
            }
        }
    }

    private fun parseQuery(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) return emptyMap()
        return rawQuery.split("&").mapNotNull { part ->
            val index = part.indexOf('=')
            if (index == -1) null else percentDecode(part.substring(0, index)) to percentDecode(part.substring(index + 1))
        }.toMap()
    }

    /** Percent-decoding that keeps a literal `+` (URLDecoder would turn it into a space, corrupting base64 passwords). */
    private fun percentDecode(value: String): String = try {
        URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
    } catch (e: Exception) {
        value
    }

    private fun decodeBase64(value: String): String? {
        val compact = value.trim().filterNot { it.isWhitespace() }
        val padded = compact.padEnd(compact.length + (4 - compact.length % 4) % 4, '=')
        for (flags in intArrayOf(Base64.DEFAULT, Base64.URL_SAFE)) {
            runCatching { return String(Base64.decode(padded, flags), Charsets.UTF_8) }
        }
        return null
    }

    private fun String?.splitList(): List<String> =
        this?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    private fun String?.isTrue(): Boolean = this == "1" || this.equals("true", ignoreCase = true)
}

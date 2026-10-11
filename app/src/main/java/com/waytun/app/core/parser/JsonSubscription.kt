package com.waytun.app.core.parser

import android.util.Base64
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * Happ-style JSON subscriptions: an array of full Xray configs (one per server, named by
 * `remarks`), a single Xray config, or a sing-box config with `outbounds`. Each proxy outbound is
 * converted to the equivalent share link, so everything downstream (storage, labels, the sing-box
 * config builder) keeps working on one format. Links are tagged `src=json` so the UI can mark them.
 */
object JsonSubscription {

    const val SOURCE_PARAM = "src"
    const val SOURCE_VALUE = "json"

    fun looksLikeJson(text: String): Boolean {
        val head = text.trimStart()
        return head.startsWith("[") || head.startsWith("{")
    }

    /** How many top-level entries (servers) the JSON document holds, supported or not. */
    fun countEntries(text: String): Int = when (val root = runCatching { JSONTokenerValue.parse(text.trim()) }.getOrNull()) {
        is JSONArray -> root.length()
        is JSONObject -> 1
        else -> 0
    }

    /**
     * The supported entries of a JSON subscription, each as its own complete JSON text. The whole config is
     * kept (not just the server): routing rules, DNS and transport extras written by the panel are part of it.
     */
    fun entries(text: String): List<String> {
        val root: Any = runCatching { JSONTokenerValue.parse(text.trim()) }.getOrNull() ?: return emptyList()
        val configs = when (root) {
            is JSONArray -> (0 until root.length()).mapNotNull { root.optJSONObject(it) }
            is JSONObject -> listOf(root)
            else -> emptyList()
        }
        return configs.filter { linkOf(it) != null }.map { it.toString() }
    }

    /** Share link (tagged `src=json`) describing the proxy server of one JSON entry, or null if unsupported. */
    fun linkOf(entryJson: String): String? =
        runCatching { JSONObject(entryJson) }.getOrNull()?.let(::linkOf)

    /** Share links for every supported outbound found; empty if [text] isn't such a JSON document. */
    fun toLinks(text: String): List<String> = entries(text).mapNotNull(::linkOf)

    private fun linkOf(config: JSONObject): String? {
        val remarks = config.optString("remarks").ifBlank { config.optString("tag").ifBlank { "" } }
        val outbounds = config.optJSONArray("outbounds") ?: return null
        return (0 until outbounds.length()).mapNotNull { outbounds.optJSONObject(it) }
            .firstNotNullOfOrNull { outbound -> fromXray(outbound, remarks) ?: fromSingBox(outbound, remarks) }
    }

    // ---- Xray ------------------------------------------------------------------------------

    private fun fromXray(outbound: JSONObject, remarks: String): String? {
        val protocol = outbound.optString("protocol").lowercase()
        val settings = outbound.optJSONObject("settings") ?: return null
        val stream = outbound.optJSONObject("streamSettings")
        val network = stream?.optString("network")?.ifBlank { null }?.lowercase() ?: "tcp"
        val security = stream?.optString("security")?.ifBlank { null }?.lowercase() ?: "none"
        val name = remarks.ifBlank { outbound.optString("tag").ifBlank { protocol } }

        val params = linkedMapOf<String, String>()
        if (protocol == "vless" || protocol == "vmess" || protocol == "trojan") {
            params["type"] = when (network) {
                "h2", "http" -> "http"
                "raw" -> "tcp"
                "splithttp" -> "xhttp"
                else -> network
            }
            if (network in setOf("kcp", "mkcp")) return null
            params["security"] = security
            stream?.optJSONObject("tlsSettings")?.takeIf { security == "tls" }?.let { tls ->
                tls.optString("serverName").ifBlank { null }?.let { params["sni"] = it }
                tls.optString("fingerprint").ifBlank { null }?.let { params["fp"] = it }
                tls.optJSONArray("alpn")?.let { a -> params["alpn"] = (0 until a.length()).joinToString(",") { a.optString(it) } }
                if (tls.optBoolean("allowInsecure")) params["allowInsecure"] = "1"
                tls.optString("pinnedPeerCertSha256").ifBlank { null }?.let { params["pcs"] = it }
                tls.optString("verifyPeerCertByName").ifBlank { null }?.let { params["vcn"] = it }
            }
            stream?.optJSONObject("realitySettings")?.takeIf { security == "reality" }?.let { r ->
                r.optString("serverName").ifBlank { null }?.let { params["sni"] = it }
                params["fp"] = r.optString("fingerprint").ifBlank { "chrome" }
                r.optString("publicKey").ifBlank { null }?.let { params["pbk"] = it }
                r.optString("shortId").ifBlank { null }?.let { params["sid"] = it }
                r.optString("spiderX").ifBlank { null }?.let { params["spx"] = it }
            }
            when (network) {
                "ws" -> stream?.optJSONObject("wsSettings")?.let { ws ->
                    ws.optString("path").ifBlank { null }?.let { params["path"] = it }
                    (ws.optJSONObject("headers")?.optString("Host") ?: ws.optString("host")).ifBlank { null }?.let { params["host"] = it }
                }
                "grpc" -> stream?.optJSONObject("grpcSettings")?.optString("serviceName")?.ifBlank { null }?.let { params["serviceName"] = it }
                "httpupgrade" -> stream?.optJSONObject("httpupgradeSettings")?.let { hu ->
                    hu.optString("path").ifBlank { null }?.let { params["path"] = it }
                    hu.optString("host").ifBlank { null }?.let { params["host"] = it }
                }
                "xhttp", "splithttp" -> (stream?.optJSONObject("xhttpSettings") ?: stream?.optJSONObject("splithttpSettings"))?.let { x ->
                    x.optString("path").ifBlank { null }?.let { params["path"] = it }
                    x.optString("host").ifBlank { null }?.let { params["host"] = it }
                    x.optString("mode").ifBlank { null }?.let { params["mode"] = it }
                    x.optJSONObject("extra")?.let { params["extra"] = it.toString() }
                }
                "h2", "http" -> stream?.optJSONObject("httpSettings")?.let { h ->
                    h.optString("path").ifBlank { null }?.let { params["path"] = it }
                    h.optJSONArray("host")?.optString(0)?.ifBlank { null }?.let { params["host"] = it }
                }
            }
        }

        return when (protocol) {
            "vless" -> {
                val server = settings.optJSONArray("vnext")?.optJSONObject(0) ?: return null
                val user = server.optJSONArray("users")?.optJSONObject(0) ?: return null
                user.optString("flow").ifBlank { null }?.let { params["flow"] = it }
                params["encryption"] = user.optString("encryption").ifBlank { "none" }
                link("vless", user.optString("id"), server.optString("address"), server.optInt("port", 443), params, name)
            }
            "vmess" -> {
                val server = settings.optJSONArray("vnext")?.optJSONObject(0) ?: return null
                val user = server.optJSONArray("users")?.optJSONObject(0) ?: return null
                val json = JSONObject()
                    .put("v", "2").put("ps", name).put("add", server.optString("address"))
                    .put("port", server.optInt("port", 443).toString()).put("id", user.optString("id"))
                    .put("aid", user.optInt("alterId", 0).toString()).put("scy", user.optString("security").ifBlank { "auto" })
                    .put("net", params["type"] ?: "tcp").put("tls", if (security == "tls") "tls" else "")
                    .put("sni", params["sni"] ?: "").put("fp", params["fp"] ?: "").put("alpn", params["alpn"] ?: "")
                    .put("path", params["path"] ?: params["serviceName"] ?: "").put("host", params["host"] ?: "")
                    .put(SOURCE_PARAM, SOURCE_VALUE)
                "vmess://" + Base64.encodeToString(json.toString().toByteArray(), Base64.NO_WRAP)
            }
            "trojan" -> {
                val server = settings.optJSONArray("servers")?.optJSONObject(0) ?: return null
                link("trojan", server.optString("password"), server.optString("address"), server.optInt("port", 443), params, name)
            }
            "shadowsocks" -> {
                val server = settings.optJSONArray("servers")?.optJSONObject(0) ?: return null
                ssLink(server.optString("method"), server.optString("password"), server.optString("address"), server.optInt("port"), name)
            }
            "hysteria" -> {
                // Xray >= 26 runs Hysteria 2 as `protocol: hysteria` + `version: 2`.
                if (settings.optInt("version", 2) != 2) return null
                val auth = stream?.optJSONObject("hysteriaSettings")?.optString("auth").orEmpty()
                val tls = stream?.optJSONObject("tlsSettings")
                val q = linkedMapOf<String, String>()
                tls?.optString("serverName")?.ifBlank { null }?.let { q["sni"] = it }
                if (tls?.optBoolean("allowInsecure") == true) q["insecure"] = "1"
                link("hysteria2", auth, settings.optString("address"), settings.optInt("port", 443), q, name)
            }
            else -> null
        }
    }

    // ---- sing-box --------------------------------------------------------------------------

    private fun fromSingBox(outbound: JSONObject, remarks: String): String? {
        val type = outbound.optString("type").lowercase()
        if (type.isEmpty()) return null
        val server = outbound.optString("server").ifBlank { return null }
        val port = outbound.optInt("server_port", 443)
        val name = remarks.ifBlank { outbound.optString("tag").ifBlank { type } }
        val params = linkedMapOf<String, String>()

        val tls = outbound.optJSONObject("tls")?.takeIf { it.optBoolean("enabled") }
        val reality = tls?.optJSONObject("reality")?.takeIf { it.optBoolean("enabled") }
        params["security"] = when {
            reality != null -> "reality"
            tls != null -> "tls"
            else -> "none"
        }
        tls?.optString("server_name")?.ifBlank { null }?.let { params["sni"] = it }
        tls?.optJSONObject("utls")?.optString("fingerprint")?.ifBlank { null }?.let { params["fp"] = it }
        tls?.optJSONArray("alpn")?.let { a -> params["alpn"] = (0 until a.length()).joinToString(",") { a.optString(it) } }
        if (tls?.optBoolean("insecure") == true) params["allowInsecure"] = "1"
        reality?.optString("public_key")?.ifBlank { null }?.let { params["pbk"] = it }
        reality?.optString("short_id")?.ifBlank { null }?.let { params["sid"] = it }

        val transport = outbound.optJSONObject("transport")
        params["type"] = when (val t = transport?.optString("type")?.lowercase().orEmpty()) {
            "" -> "tcp"
            else -> t
        }
        transport?.optString("path")?.ifBlank { null }?.let { params["path"] = it }
        transport?.optString("service_name")?.ifBlank { null }?.let { params["serviceName"] = it }
        (transport?.optJSONObject("headers")?.optString("Host") ?: transport?.optString("host"))?.ifBlank { null }?.let { params["host"] = it }

        return when (type) {
            "vless" -> {
                outbound.optString("flow").ifBlank { null }?.let { params["flow"] = it }
                params["encryption"] = "none"
                link("vless", outbound.optString("uuid"), server, port, params, name)
            }
            "vmess" -> {
                val json = JSONObject()
                    .put("v", "2").put("ps", name).put("add", server).put("port", port.toString())
                    .put("id", outbound.optString("uuid")).put("aid", outbound.optInt("alter_id", 0).toString())
                    .put("scy", outbound.optString("security").ifBlank { "auto" })
                    .put("net", params["type"] ?: "tcp").put("tls", if (tls != null) "tls" else "")
                    .put("sni", params["sni"] ?: "").put("fp", params["fp"] ?: "").put("alpn", params["alpn"] ?: "")
                    .put("path", params["path"] ?: params["serviceName"] ?: "").put("host", params["host"] ?: "")
                    .put(SOURCE_PARAM, SOURCE_VALUE)
                "vmess://" + Base64.encodeToString(json.toString().toByteArray(), Base64.NO_WRAP)
            }
            "trojan" -> link("trojan", outbound.optString("password"), server, port, params, name)
            "shadowsocks" -> ssLink(outbound.optString("method"), outbound.optString("password"), server, port, name)
            "hysteria2" -> {
                val q = linkedMapOf<String, String>()
                params["sni"]?.let { q["sni"] = it }
                params["alpn"]?.let { q["alpn"] = it }
                if (params["allowInsecure"] == "1") q["insecure"] = "1"
                outbound.optJSONObject("obfs")?.let { o ->
                    o.optString("type").ifBlank { null }?.let { q["obfs"] = it }
                    o.optString("password").ifBlank { null }?.let { q["obfs-password"] = it }
                }
                link("hysteria2", outbound.optString("password"), server, port, q, name)
            }
            "tuic" -> {
                val q = linkedMapOf<String, String>()
                params["sni"]?.let { q["sni"] = it }
                params["alpn"]?.let { q["alpn"] = it }
                if (params["allowInsecure"] == "1") q["allow_insecure"] = "1"
                outbound.optString("congestion_control").ifBlank { null }?.let { q["congestion_control"] = it }
                outbound.optString("udp_relay_mode").ifBlank { null }?.let { q["udp_relay_mode"] = it }
                link("tuic", outbound.optString("uuid") + ":" + outbound.optString("password"), server, port, q, name)
            }
            else -> null
        }
    }

    // ---- link building ---------------------------------------------------------------------

    private fun link(scheme: String, userInfo: String, host: String, port: Int, params: Map<String, String>, name: String): String? {
        if (host.isBlank()) return null
        val query = (params + (SOURCE_PARAM to SOURCE_VALUE)).entries.joinToString("&") { (k, v) -> "$k=${enc(v)}" }
        val hostPart = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
        return "$scheme://${enc(userInfo)}@$hostPart:$port?$query#${enc(name)}"
    }

    private fun ssLink(method: String, password: String, host: String, port: Int, name: String): String? {
        if (host.isBlank() || method.isBlank()) return null
        val hostPart = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
        return "ss://${enc("$method:$password")}@$hostPart:$port?$SOURCE_PARAM=$SOURCE_VALUE#${enc(name)}"
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

/** `org.json` has no "parse any JSON value" entry point; this picks array vs object by first character. */
private object JSONTokenerValue {
    fun parse(text: String): Any = if (text.startsWith("[")) JSONArray(text) else JSONObject(text)
}

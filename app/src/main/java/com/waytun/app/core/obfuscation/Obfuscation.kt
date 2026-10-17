package com.waytun.app.core.obfuscation

import org.json.JSONObject
import kotlin.random.Random

enum class ObfuscationLevel { LIGHT, MEDIUM, STRONG }

/**
 * What the user chose in Settings. Everything here is *client-side only*: none of it needs to be
 * matched by the server, so it can be changed freely and per connection. (AmneziaWG's S1/S2/H1-H4,
 * Hysteria2's salamander etc. must be configured on both ends, so they stay in the tunnel config.)
 */
data class ObfuscationSettings(
    val enabled: Boolean = false,
    val level: ObfuscationLevel = ObfuscationLevel.LIGHT,
    /** Split the TLS ClientHello across packets/records at random points (defeats SNI matching by DPI). */
    val tlsFragmentation: Boolean = true,
    /** Rotate the browser TLS fingerprint (uTLS) between connections. */
    val fingerprintRotation: Boolean = true,
    /** Randomize the QUIC Initial packet size for Hysteria/TUIC. */
    val quicPacketSize: Boolean = true,
    /** Rotate the User-Agent of WebSocket/HTTPUpgrade/HTTP transports. */
    val headerRotation: Boolean = true,
    /** Send AmneziaWG-style junk packets before the handshake; also for plain WireGuard tunnels. */
    val wireguardJunk: Boolean = true,
    /** Regenerate the values every [ROTATE_INTERVAL_MINUTES] minutes. */
    val autoRotate: Boolean = true,
    /** Reconnect with fresh values when the tunnel breaks (failure, drop, dead connection). */
    val autoReconnect: Boolean = true,
    /** Also reconnect when the periodic rotation produces new values (briefly interrupts connections). */
    val applyRotationLive: Boolean = true,
    /**
     * Reject UDP/443 so YouTube and browsers fall back from QUIC to TCP. HTTP/3 inside a QUIC tunnel
     * (Hysteria2/TUIC) is QUIC-in-QUIC: it works but stutters badly, while plain HTTPS over the tunnel is smooth.
     */
    val blockQuic: Boolean = false
) {
    fun toJson(): String = JSONObject()
        .put("enabled", enabled).put("level", level.name)
        .put("tlsFragmentation", tlsFragmentation).put("fingerprintRotation", fingerprintRotation)
        .put("quicPacketSize", quicPacketSize).put("headerRotation", headerRotation)
        .put("wireguardJunk", wireguardJunk).put("autoRotate", autoRotate)
        .put("autoReconnect", autoReconnect).put("applyRotationLive", applyRotationLive)
        .put("blockQuic", blockQuic)
        .toString()

    companion object {
        const val ROTATE_INTERVAL_MINUTES = 10L

        fun fromJson(json: String?): ObfuscationSettings {
            if (json.isNullOrBlank()) return ObfuscationSettings()
            return runCatching {
                val o = JSONObject(json)
                val d = ObfuscationSettings()
                ObfuscationSettings(
                    enabled = o.optBoolean("enabled", d.enabled),
                    level = runCatching { ObfuscationLevel.valueOf(o.optString("level")) }.getOrDefault(d.level),
                    tlsFragmentation = o.optBoolean("tlsFragmentation", d.tlsFragmentation),
                    fingerprintRotation = o.optBoolean("fingerprintRotation", d.fingerprintRotation),
                    quicPacketSize = o.optBoolean("quicPacketSize", d.quicPacketSize),
                    headerRotation = o.optBoolean("headerRotation", d.headerRotation),
                    wireguardJunk = o.optBoolean("wireguardJunk", d.wireguardJunk),
                    autoRotate = o.optBoolean("autoRotate", d.autoRotate),
                    autoReconnect = o.optBoolean("autoReconnect", d.autoReconnect),
                    applyRotationLive = o.optBoolean("applyRotationLive", d.applyRotationLive),
                    blockQuic = o.optBoolean("blockQuic", d.blockQuic)
                )
            }.getOrDefault(ObfuscationSettings())
        }
    }
}

/** One concrete set of randomized values, applied to a connection when it is (re)started. */
data class ObfuscationProfile(
    val generatedAtMillis: Long,
    val fragmentPackets: Boolean,
    val fragmentRecords: Boolean,
    val fragmentDelayMs: Int,
    /** A uTLS fingerprint name, or null to leave the tunnel's own choice alone. */
    val fingerprint: String?,
    /** QUIC Initial packet size, or null to leave the default. */
    val quicInitialPacketSize: Int?,
    val userAgent: String?,
    val awgJunkCount: Int?,
    val awgJunkMin: Int?,
    val awgJunkMax: Int?,
    /** AmneziaWG "I1" decoy first packet in its tag syntax, or null. */
    val awgDecoyPacket: String?
) {
    /** Compact one-line summary for logs (contains no tunnel data). */
    fun summary(): String =
        "frag=$fragmentPackets rec=$fragmentRecords delay=${fragmentDelayMs}ms fp=$fingerprint quic=$quicInitialPacketSize " +
            "awg=$awgJunkCount/$awgJunkMin/$awgJunkMax decoy=${awgDecoyPacket != null}"

    fun toJson(): String = JSONObject()
        .put("generatedAt", generatedAtMillis)
        .put("fragmentPackets", fragmentPackets).put("fragmentRecords", fragmentRecords)
        .put("fragmentDelayMs", fragmentDelayMs)
        .put("fingerprint", fingerprint ?: JSONObject.NULL)
        .put("quicInitialPacketSize", quicInitialPacketSize ?: JSONObject.NULL)
        .put("userAgent", userAgent ?: JSONObject.NULL)
        .put("awgJunkCount", awgJunkCount ?: JSONObject.NULL)
        .put("awgJunkMin", awgJunkMin ?: JSONObject.NULL)
        .put("awgJunkMax", awgJunkMax ?: JSONObject.NULL)
        .put("awgDecoyPacket", awgDecoyPacket ?: JSONObject.NULL)
        .toString()

    companion object {
        fun fromJson(json: String?): ObfuscationProfile? {
            if (json.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(json)
                fun str(k: String) = if (o.isNull(k)) null else o.getString(k)
                fun int(k: String) = if (o.isNull(k)) null else o.getInt(k)
                ObfuscationProfile(
                    generatedAtMillis = o.getLong("generatedAt"),
                    fragmentPackets = o.getBoolean("fragmentPackets"),
                    fragmentRecords = o.getBoolean("fragmentRecords"),
                    fragmentDelayMs = o.getInt("fragmentDelayMs"),
                    fingerprint = str("fingerprint"),
                    quicInitialPacketSize = int("quicInitialPacketSize"),
                    userAgent = str("userAgent"),
                    awgJunkCount = int("awgJunkCount"),
                    awgJunkMin = int("awgJunkMin"),
                    awgJunkMax = int("awgJunkMax"),
                    awgDecoyPacket = str("awgDecoyPacket")
                )
            }.getOrNull()
        }
    }
}

object ObfuscationGenerator {

    // No "android": its uTLS preset has no X25519 key share, which REALITY needs ("nil ecdheKey").
    private val fingerprints = listOf("chrome", "firefox", "safari", "edge", "ios")

    private val userAgents = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.4 Safari/605.1.15",
        "Mozilla/5.0 (X11; Linux x86_64; rv:138.0) Gecko/20100101 Firefox/138.0",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.4 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36 Edg/135.0.0.0"
    )

    /** Fresh random values for [settings]; [random] is injectable for tests. */
    fun generate(settings: ObfuscationSettings, random: Random = Random.Default, now: Long = System.currentTimeMillis()): ObfuscationProfile {
        val level = settings.level
        val fragment = settings.tlsFragmentation
        // Light: packet split only. Medium/Strong: sometimes also split the TLS record itself.
        val records = fragment && level != ObfuscationLevel.LIGHT && random.nextInt(100) < if (level == ObfuscationLevel.STRONG) 80 else 50
        // Kept short: the delay is paid once per fragment, so hundreds of ms stretch a handshake to seconds
        // and make REALITY/TLS servers drop the connection.
        val delay = when (level) {
            ObfuscationLevel.LIGHT -> random.nextInt(8, 25)
            ObfuscationLevel.MEDIUM -> random.nextInt(12, 40)
            ObfuscationLevel.STRONG -> random.nextInt(20, 60)
        }
        val (jcRange, jminRange, jmaxRange) = when (level) {
            ObfuscationLevel.LIGHT -> Triple(2..4, 20..40, 60..110)
            ObfuscationLevel.MEDIUM -> Triple(4..8, 30..60, 90..220)
            ObfuscationLevel.STRONG -> Triple(8..16, 50..100, 200..520)
        }
        val jmin = random.nextInt(jminRange.first, jminRange.last + 1)
        val jmax = maxOf(random.nextInt(jmaxRange.first, jmaxRange.last + 1), jmin + 16)
        return ObfuscationProfile(
            generatedAtMillis = now,
            fragmentPackets = fragment,
            fragmentRecords = records,
            fragmentDelayMs = delay,
            fingerprint = fingerprints.random(random).takeIf { settings.fingerprintRotation },
            // 1200 is QUIC's minimum; staying near the 1252 default keeps the packet under the path MTU of mobile networks.
            quicInitialPacketSize = random.nextInt(1200, 1281).takeIf { settings.quicPacketSize },
            userAgent = userAgents.random(random).takeIf { settings.headerRotation },
            awgJunkCount = random.nextInt(jcRange.first, jcRange.last + 1).takeIf { settings.wireguardJunk },
            awgJunkMin = jmin.takeIf { settings.wireguardJunk },
            awgJunkMax = jmax.takeIf { settings.wireguardJunk },
            awgDecoyPacket = decoyPacket(random).takeIf { settings.wireguardJunk && level != ObfuscationLevel.LIGHT }
        )
    }

    /**
     * A decoy first packet shaped like a QUIC v1 long-header Initial (flags, version 1, 8-byte DCID
     * with random bytes, then random payload): in AmneziaWG's "I1" tag syntax, sent once before the
     * real handshake and ignored by the server.
     */
    private fun decoyPacket(random: Random): String {
        val payload = random.nextInt(700, 901)
        return "<b 0xc00000000108><r 8><b 0x00><r 2><b 0x44><r 1><r $payload>"
    }
}

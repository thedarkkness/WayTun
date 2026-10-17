package com.waytun.app.golden

import com.waytun.app.core.obfuscation.ObfuscationLevel
import com.waytun.app.core.obfuscation.ObfuscationProfile
import com.waytun.app.core.obfuscation.ObfuscationSettings

internal data class LinkGolden(
    val input: String,
    val canParse: Boolean,
    val parsed: String?,
    val describe: String?,
    val xrayPlain: String?,
    val xrayObfuscated: String?
)

internal data class SubscriptionGolden(
    val text: String,
    val extract: List<String>,
    val countAll: Int,
    val looksLikeJson: Boolean,
    val countEntries: Int,
    val entries: List<String>,
    val toLinks: List<String>
)

internal data class JsonEntryGolden(val entry: String, val linkOf: String?, val host: String?, val plain: String?, val pinned: String?)

internal data class ProfileGolden(val settingsIndex: Int, val seed: Int, val now: Long, val profile: String, val json: String)

/** The inputs GoldenCapture used that aren't stored in [GoldenData] itself. */
internal object GoldenInputs {
    const val XRAY_LOG = "/data/user/0/com.waytun.app/files/xray-debug.log"
    const val PINNED_IP = "203.0.113.7"
    const val PINNED_LOG = "/data/x.log"

    val settings = listOf(
        ObfuscationSettings(),
        ObfuscationSettings(enabled = true, level = ObfuscationLevel.STRONG, blockQuic = true),
        ObfuscationSettings(enabled = true, level = ObfuscationLevel.MEDIUM, tlsFragmentation = false, fingerprintRotation = false, wireguardJunk = false),
    )

    val profile = ObfuscationProfile(
        generatedAtMillis = 1_700_000_000_000L, fragmentPackets = true, fragmentRecords = false, fragmentDelayMs = 17,
        fingerprint = "firefox", quicInitialPacketSize = 1250, userAgent = "Mozilla/5.0 (X11; Linux x86_64; rv:138.0) Gecko/20100101 Firefox/138.0",
        awgJunkCount = 3, awgJunkMin = 25, awgJunkMax = 90, awgDecoyPacket = "<b 0xc00000000108><r 8><b 0x00><r 2><b 0x44><r 1><r 800>"
    )
}

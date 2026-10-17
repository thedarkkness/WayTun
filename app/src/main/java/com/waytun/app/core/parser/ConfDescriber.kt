package com.waytun.app.core.parser

/** Labels wg-quick/awg-quick `.conf` text with the WireGuard/AmneziaWG libraries the Android app bundles. */
object ConfDescriber {
    fun describe(rawConfig: String): String? = when (TunnelConfigParser.parse(rawConfig)) {
        is TunnelParseResult.WireGuardSuccess -> "WireGuard / UDP"
        is TunnelParseResult.AmneziaSuccess -> "AmneziaWG / UDP"
        else -> null
    }
}

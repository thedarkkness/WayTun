package com.waytun.app.core.parser

import java.io.BufferedReader
import java.io.IOException
import java.io.StringReader
import java.util.Locale
import com.wireguard.config.BadConfigException as WgBadConfigException
import com.wireguard.config.Config as WgConfig
import org.amnezia.awg.config.BadConfigException as AwgBadConfigException
import org.amnezia.awg.config.Config as AwgConfig

enum class ConfigErrorKind { MISSING, INVALID, STRUCTURE }

/** [fieldName] is the exact wg-quick/awg-quick attribute name (e.g. "PrivateKey", "JunkPacketCount"); blank for file-level errors. */
data class ConfigFieldError(val fieldName: String, val kind: ConfigErrorKind)

/** Result of validating and parsing a raw .conf text supplied by the user. */
sealed interface TunnelParseResult {
    data class WireGuardSuccess(val config: WgConfig) : TunnelParseResult
    data class AmneziaSuccess(val config: AwgConfig) : TunnelParseResult

    /** A well-formed `key = value` config with a semantically invalid field (bad key, bad endpoint, ...). */
    data class Invalid(val error: ConfigFieldError) : TunnelParseResult

    /** The text isn't line-structured .conf content at all (e.g. missing `[Interface]`, garbage input). */
    data class SyntaxError(val detail: String) : TunnelParseResult
}

/**
 * Parses wg-quick/awg-quick style `.conf` text. WireGuard configs are parsed with the upstream
 * WireGuard library ([WgConfig]); AmneziaWG configs (detected by their Jc/Jmin/Jmax/S1/S2/H1-H4
 * obfuscation parameters) are parsed with the AmneziaWG library ([AwgConfig], a superset grammar
 * of the same `[Interface]`/`[Peer]` format), so validation semantics match each protocol's own
 * reference client.
 */
object TunnelConfigParser {

    private val AMNEZIA_WG_MARKER_KEYS = setOf(
        "jc", "jmin", "jmax", "s1", "s2", "h1", "h2", "h3", "h4"
    )
    private val ATTRIBUTE_LINE = Regex("""(\w+)\s*=\s*(.+)""")

    fun parse(rawText: String): TunnelParseResult =
        if (containsAmneziaWgMarkers(rawText)) parseAmnezia(rawText) else parseWireGuard(rawText)

    private fun parseWireGuard(rawText: String): TunnelParseResult = try {
        TunnelParseResult.WireGuardSuccess(WgConfig.parse(BufferedReader(StringReader(rawText))))
    } catch (e: WgBadConfigException) {
        TunnelParseResult.Invalid(ConfigFieldError(e.location.getName(), e.reason.toKind()))
    } catch (e: IOException) {
        TunnelParseResult.SyntaxError(e.message ?: "IO error while reading configuration")
    }

    private fun parseAmnezia(rawText: String): TunnelParseResult = try {
        TunnelParseResult.AmneziaSuccess(AwgConfig.parse(BufferedReader(StringReader(rawText))))
    } catch (e: AwgBadConfigException) {
        TunnelParseResult.Invalid(ConfigFieldError(e.location.getName(), e.reason.toKind()))
    } catch (e: IOException) {
        TunnelParseResult.SyntaxError(e.message ?: "IO error while reading configuration")
    }

    private fun WgBadConfigException.Reason.toKind(): ConfigErrorKind = when (this) {
        WgBadConfigException.Reason.MISSING_ATTRIBUTE, WgBadConfigException.Reason.MISSING_SECTION -> ConfigErrorKind.MISSING
        WgBadConfigException.Reason.INVALID_KEY, WgBadConfigException.Reason.INVALID_NUMBER,
        WgBadConfigException.Reason.INVALID_VALUE -> ConfigErrorKind.INVALID
        WgBadConfigException.Reason.SYNTAX_ERROR, WgBadConfigException.Reason.UNKNOWN_ATTRIBUTE,
        WgBadConfigException.Reason.UNKNOWN_SECTION -> ConfigErrorKind.STRUCTURE
    }

    private fun AwgBadConfigException.Reason.toKind(): ConfigErrorKind = when (this) {
        AwgBadConfigException.Reason.MISSING_ATTRIBUTE, AwgBadConfigException.Reason.MISSING_SECTION -> ConfigErrorKind.MISSING
        AwgBadConfigException.Reason.INVALID_KEY, AwgBadConfigException.Reason.INVALID_NUMBER,
        AwgBadConfigException.Reason.INVALID_VALUE -> ConfigErrorKind.INVALID
        AwgBadConfigException.Reason.SYNTAX_ERROR, AwgBadConfigException.Reason.UNKNOWN_ATTRIBUTE,
        AwgBadConfigException.Reason.UNKNOWN_SECTION -> ConfigErrorKind.STRUCTURE
    }

    /** Minimal, library-independent `key = value` scan, just to pick which parser to hand the text to. */
    private fun containsAmneziaWgMarkers(rawText: String): Boolean {
        var inInterfaceSection = false
        for (rawLine in rawText.lineSequence()) {
            val commentIndex = rawLine.indexOf('#')
            val line = (if (commentIndex != -1) rawLine.substring(0, commentIndex) else rawLine).trim()
            if (line.isEmpty()) continue
            if (line.startsWith("[")) {
                inInterfaceSection = line.equals("[Interface]", ignoreCase = true)
                continue
            }
            if (!inInterfaceSection) continue
            val key = ATTRIBUTE_LINE.matchEntire(line)?.groupValues?.get(1) ?: continue
            if (key.lowercase(Locale.ROOT) in AMNEZIA_WG_MARKER_KEYS) return true
        }
        return false
    }
}

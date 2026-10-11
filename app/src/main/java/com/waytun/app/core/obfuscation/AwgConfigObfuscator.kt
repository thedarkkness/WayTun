package com.waytun.app.core.obfuscation

/**
 * Writes an [ObfuscationProfile]'s junk-packet values into a WireGuard/AmneziaWG config text.
 *
 * Only the client-side-only keys are touched: Jc/Jmin/Jmax (junk packets sent before the
 * handshake) and, if the config doesn't set its own, I1 (a decoy first packet). S1-S4/H1-H4 must
 * match the server and are never changed. With those left at their defaults an AmneziaWG client is
 * wire-compatible with a plain WireGuard server, which just drops the junk - that's what lets the
 * same obfuscation apply to ordinary WireGuard tunnels.
 */
object AwgConfigObfuscator {

    private val managedKeys = setOf("jc", "jmin", "jmax")

    fun apply(rawConfig: String, profile: ObfuscationProfile): String {
        val count = profile.awgJunkCount ?: return rawConfig
        val min = profile.awgJunkMin ?: return rawConfig
        val max = profile.awgJunkMax ?: return rawConfig

        val lines = rawConfig.lines()
        val interfaceIndex = lines.indexOfFirst { it.trim().equals("[Interface]", ignoreCase = true) }
        if (interfaceIndex < 0) return rawConfig
        var sectionEnd = lines.size
        for (i in interfaceIndex + 1 until lines.size) {
            if (lines[i].trim().startsWith("[")) { sectionEnd = i; break }
        }

        fun keyOf(line: String) = line.substringBefore('=', "").trim().lowercase()
        val sectionKeys = (interfaceIndex + 1 until sectionEnd).map { keyOf(lines[it]) }.toSet()
        val inserted = buildList {
            add("Jc = $count")
            add("Jmin = $min")
            add("Jmax = $max")
            if (profile.awgDecoyPacket != null && "i1" !in sectionKeys) add("I1 = ${profile.awgDecoyPacket}")
        }

        return buildList {
            addAll(lines.subList(0, interfaceIndex + 1))
            addAll(inserted)
            for (i in interfaceIndex + 1 until sectionEnd) {
                if (keyOf(lines[i]) !in managedKeys) add(lines[i])
            }
            addAll(lines.subList(sectionEnd, lines.size))
        }.joinToString("\n")
    }
}

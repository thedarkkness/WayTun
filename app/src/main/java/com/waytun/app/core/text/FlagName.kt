package com.waytun.app.core.text

/** A tunnel name split into its leading flag emoji (if any) and the rest: "🇫🇮 Финляндия #1" -> ("🇫🇮", "Финляндия #1"). */
data class FlagName(val flag: String?, val title: String) {
    companion object {
        private const val REGIONAL_INDICATOR_FIRST = 0x1F1E6
        private const val REGIONAL_INDICATOR_LAST = 0x1F1FF

        fun parse(name: String): FlagName {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return FlagName(null, name)
            val first = trimmed.codePointAt(0)
            val firstLen = Character.charCount(first)
            if (first in REGIONAL_INDICATOR_FIRST..REGIONAL_INDICATOR_LAST && trimmed.length > firstLen) {
                val second = trimmed.codePointAt(firstLen)
                if (second in REGIONAL_INDICATOR_FIRST..REGIONAL_INDICATOR_LAST) {
                    val end = firstLen + Character.charCount(second)
                    val rest = trimmed.substring(end).trimStart(' ', '|', '-', '·', '_').ifBlank { trimmed.substring(0, end) }
                    return FlagName(trimmed.substring(0, end), rest)
                }
            }
            return FlagName(null, trimmed)
        }
    }
}

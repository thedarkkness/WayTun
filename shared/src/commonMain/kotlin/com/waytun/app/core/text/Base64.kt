package com.waytun.app.core.text

import kotlin.io.encoding.Base64 as KotlinBase64

/**
 * The subset of android.util.Base64 the parsers use, with Android's exact decoding behaviour: characters outside
 * the alphabet (whitespace, but also '-'/'_' in standard mode) are skipped rather than rejected, padding is
 * optional, and only a truncated final group or a misplaced '=' is an error. Subscription links in the wild depend
 * on that leniency, and the stored links were produced under it.
 */
object Base64 {
    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val URL_SAFE = 8

    private const val SKIP = -1
    private const val EQUALS = -2

    private val decodeStandard = table("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/")
    private val decodeUrlSafe = table("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_")

    private fun table(alphabet: String): IntArray = IntArray(256) { SKIP }.also { t ->
        alphabet.forEachIndexed { i, c -> t[c.code] = i }
        t['='.code] = EQUALS
    }

    /** @throws IllegalArgumentException "bad base-64", like Android. */
    fun decode(value: String, flags: Int): ByteArray = decode(value.encodeToByteArray(), flags)

    fun decode(input: ByteArray, flags: Int): ByteArray {
        val alphabet = if (flags and URL_SAFE != 0) decodeUrlSafe else decodeStandard
        val output = ByteArray(input.size * 3 / 4 + 3)
        var op = 0
        var state = 0
        var value = 0
        for (byte in input) {
            val d = alphabet[byte.toInt() and 0xff]
            when (state) {
                0 -> if (d >= 0) { value = d; state++ } else if (d != SKIP) bad()
                1 -> if (d >= 0) { value = (value shl 6) or d; state++ } else if (d != SKIP) bad()
                2 -> when {
                    d >= 0 -> { value = (value shl 6) or d; state++ }
                    d == EQUALS -> { output[op++] = (value shr 4).toByte(); state = 4 }
                    d != SKIP -> bad()
                }
                3 -> when {
                    d >= 0 -> {
                        value = (value shl 6) or d
                        output[op + 2] = value.toByte()
                        output[op + 1] = (value shr 8).toByte()
                        output[op] = (value shr 16).toByte()
                        op += 3
                        state = 0
                    }
                    d == EQUALS -> {
                        output[op + 1] = (value shr 2).toByte()
                        output[op] = (value shr 10).toByte()
                        op += 2
                        state = 5
                    }
                    d != SKIP -> bad()
                }
                4 -> if (d == EQUALS) state++ else if (d != SKIP) bad()
                5 -> if (d != SKIP) bad()
            }
        }
        when (state) {
            1, 4 -> bad()
            2 -> output[op++] = (value shr 4).toByte()
            3 -> {
                output[op++] = (value shr 10).toByte()
                output[op++] = (value shr 2).toByte()
            }
        }
        return output.copyOf(op)
    }

    /** Only the single-line forms are used (NO_WRAP, optionally URL_SAFE / NO_PADDING). */
    fun encodeToString(input: ByteArray, flags: Int): String {
        val codec = if (flags and URL_SAFE != 0) KotlinBase64.UrlSafe else KotlinBase64.Default
        val encoded = codec.encode(input)
        return if (flags and NO_PADDING != 0) encoded.trimEnd('=') else encoded
    }

    private fun bad(): Nothing = throw IllegalArgumentException("bad base-64")
}

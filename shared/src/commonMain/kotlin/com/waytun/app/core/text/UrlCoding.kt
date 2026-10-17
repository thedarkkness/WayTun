package com.waytun.app.core.text

/** java.net.URLEncoder / URLDecoder ("UTF-8") behaviour for code that has to run off the JVM. */
object UrlCoding {

    private const val HEX = "0123456789ABCDEF"

    /** application/x-www-form-urlencoded: letters, digits and `.-*_` stay, space becomes '+', the rest %XX. */
    fun encode(value: String): String {
        val out = StringBuilder()
        for (byte in value.encodeToByteArray()) {
            val b = byte.toInt() and 0xff
            val c = b.toChar()
            when {
                c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '.' || c == '-' || c == '*' || c == '_' -> out.append(c)
                c == ' ' -> out.append('+')
                else -> out.append('%').append(HEX[b shr 4]).append(HEX[b and 0xf])
            }
        }
        return out.toString()
    }

    /** '+' becomes a space, %XX runs are decoded as UTF-8; @throws IllegalArgumentException on a broken escape. */
    fun decode(value: String): String {
        if (value.indexOf('%') == -1 && value.indexOf('+') == -1) return value
        val out = StringBuilder()
        var i = 0
        while (i < value.length) {
            when (val c = value[i]) {
                '+' -> { out.append(' '); i++ }
                '%' -> {
                    val bytes = ArrayList<Byte>()
                    while (i < value.length && value[i] == '%') {
                        require(i + 2 < value.length) { "Incomplete trailing escape (%) pattern" }
                        val hi = value[i + 1].digitToIntOrNull(16)
                        val lo = value[i + 2].digitToIntOrNull(16)
                        require(hi != null && lo != null) { "Illegal hex characters in escape (%) pattern" }
                        bytes.add(((hi shl 4) or lo).toByte())
                        i += 3
                    }
                    out.append(bytes.toByteArray().decodeToString())
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }
}

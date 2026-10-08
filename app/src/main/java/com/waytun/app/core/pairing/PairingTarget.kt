package com.waytun.app.core.pairing

import android.util.Base64

/**
 * Encodes where/how to send a tunnel config to a device that just displayed a "receive via QR"
 * code (typically a camera-less device like an Android TV). The QR text format is:
 * `waytun-pair:v1:<ipv4>:<port>:<base64 32-byte AES key>`.
 */
data class PairingTarget(val ip: String, val port: Int, val keyBytes: ByteArray) {

    fun toQrText(): String = "$PREFIX$ip:$port:${Base64.encodeToString(keyBytes, Base64.NO_WRAP)}"

    companion object {
        private const val PREFIX = "waytun-pair:v1:"

        fun parse(text: String): PairingTarget? {
            if (!text.startsWith(PREFIX)) return null
            val parts = text.removePrefix(PREFIX).split(":")
            if (parts.size != 3) return null
            val ip = parts[0]
            val port = parts[1].toIntOrNull() ?: return null
            val key = try {
                Base64.decode(parts[2], Base64.NO_WRAP)
            } catch (e: IllegalArgumentException) {
                return null
            }
            if (key.size != PAIRING_KEY_SIZE_BYTES) return null
            return PairingTarget(ip, port, key)
        }
    }
}

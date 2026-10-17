package com.waytun.app.core.pairing

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH_BITS = 128
const val PAIRING_KEY_SIZE_BYTES = 32

/**
 * One-time AES-256-GCM encryption for the local-network device-to-device tunnel transfer.
 * The key lives only in memory for the duration of one QR-pairing session - it is never
 * persisted and is independent of the per-tunnel Keystore encryption used for storage.
 */
object PairingCrypto {

    fun generateKey(): ByteArray = ByteArray(PAIRING_KEY_SIZE_BYTES).also { SecureRandom().nextBytes(it) }

    /** Returns (iv, ciphertext). */
    fun encrypt(keyBytes: ByteArray, plainText: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"))
        val ciphertext = cipher.doFinal(plainText)
        return cipher.iv to ciphertext
    }

    fun decrypt(keyBytes: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }
}

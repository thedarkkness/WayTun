package com.waytun.app.core.crypto

/** Ciphertext and IV produced by [ConfigCipher.encrypt]; both must be persisted together. */
data class EncryptedConfig(
    val ciphertext: ByteArray,
    val iv: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedConfig) return false
        return ciphertext.contentEquals(other.ciphertext) && iv.contentEquals(other.iv)
    }

    override fun hashCode(): Int = 31 * ciphertext.contentHashCode() + iv.contentHashCode()
}

/** Thrown when a config blob cannot be encrypted or decrypted (e.g. the Keystore key is unavailable). */
class TunnelCryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Encrypts/decrypts tunnel configuration text so that private keys never touch disk in the clear.
 * Implementations must use a key that never leaves the Android Keystore.
 */
interface ConfigCipher {
    fun encrypt(plainText: ByteArray): EncryptedConfig
    fun decrypt(encrypted: EncryptedConfig): ByteArray
}

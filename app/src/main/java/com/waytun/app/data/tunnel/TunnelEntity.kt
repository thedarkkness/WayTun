package com.waytun.app.data.tunnel

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted tunnel metadata. [encryptedConfig] is the AES-GCM ciphertext of the original .conf
 * text (private keys included); it is unreadable without the Keystore key, so storing it in Room
 * never exposes key material in the clear.
 */
@Entity(tableName = "tunnels")
data class TunnelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val protocol: String,
    val sortOrder: Int,
    val createdAtEpochMillis: Long,
    val encryptedConfig: ByteArray,
    val configIv: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TunnelEntity) return false
        return id == other.id &&
            name == other.name &&
            protocol == other.protocol &&
            sortOrder == other.sortOrder &&
            createdAtEpochMillis == other.createdAtEpochMillis &&
            encryptedConfig.contentEquals(other.encryptedConfig) &&
            configIv.contentEquals(other.configIv)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + protocol.hashCode()
        result = 31 * result + sortOrder
        result = 31 * result + createdAtEpochMillis.hashCode()
        result = 31 * result + encryptedConfig.contentHashCode()
        result = 31 * result + configIv.contentHashCode()
        return result
    }
}

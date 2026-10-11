package com.waytun.app.data.subscription

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A subscription URL and the panel metadata from its last successful fetch. The URL embeds the
 * user's access token, so like tunnel configs it's only stored AES-GCM encrypted.
 */
@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val encryptedUrl: ByteArray,
    val urlIv: ByteArray,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val uploadBytes: Long?,
    val downloadBytes: Long?,
    val totalBytes: Long?,
    val expireEpochSeconds: Long?,
    val updateIntervalHours: Int?,
    val announce: String? = null,
    val supportUrl: String? = null,
    val webPageUrl: String? = null,
    val collapsed: Boolean = false,
    val description: String? = null,
    val announceUrl: String? = null,
    val supportEmail: String? = null,
    @ColumnInfo(defaultValue = "1") val autoUpdate: Boolean = true,
    /** The user's own interval; null = whatever the panel advertises. */
    val userIntervalHours: Int? = null,
    @ColumnInfo(defaultValue = "0") val updateOnOpen: Boolean = false,
    @ColumnInfo(defaultValue = "1") val pingOnOpen: Boolean = true,
    @ColumnInfo(defaultValue = "1") val sendHwid: Boolean = true,
    @ColumnInfo(defaultValue = "15") val timeoutSeconds: Int = 15,
    /** Custom User-Agent; null = the app's default. */
    val userAgent: String? = null,
    @ColumnInfo(defaultValue = "0") val sortMode: Int = 0,
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SubscriptionEntity) return false
        return id == other.id &&
            name == other.name &&
            encryptedUrl.contentEquals(other.encryptedUrl) &&
            urlIv.contentEquals(other.urlIv) &&
            createdAtEpochMillis == other.createdAtEpochMillis &&
            updatedAtEpochMillis == other.updatedAtEpochMillis &&
            uploadBytes == other.uploadBytes &&
            downloadBytes == other.downloadBytes &&
            totalBytes == other.totalBytes &&
            expireEpochSeconds == other.expireEpochSeconds &&
            updateIntervalHours == other.updateIntervalHours &&
            announce == other.announce &&
            supportUrl == other.supportUrl &&
            webPageUrl == other.webPageUrl &&
            collapsed == other.collapsed &&
            description == other.description &&
            announceUrl == other.announceUrl &&
            supportEmail == other.supportEmail &&
            autoUpdate == other.autoUpdate &&
            userIntervalHours == other.userIntervalHours &&
            updateOnOpen == other.updateOnOpen &&
            pingOnOpen == other.pingOnOpen &&
            sendHwid == other.sendHwid &&
            timeoutSeconds == other.timeoutSeconds &&
            userAgent == other.userAgent &&
            sortMode == other.sortMode &&
            pinned == other.pinned
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + encryptedUrl.contentHashCode()
        result = 31 * result + updatedAtEpochMillis.hashCode()
        return result
    }
}

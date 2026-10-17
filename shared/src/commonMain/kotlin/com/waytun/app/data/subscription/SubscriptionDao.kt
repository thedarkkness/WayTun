package com.waytun.app.data.subscription

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SubscriptionEntity)

    @Query("SELECT * FROM subscriptions ORDER BY createdAtEpochMillis ASC")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions ORDER BY createdAtEpochMillis ASC")
    suspend fun getAll(): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SubscriptionEntity?

    @Query(
        "UPDATE subscriptions SET name = :name, updatedAtEpochMillis = :updatedAt, uploadBytes = :upload, " +
            "downloadBytes = :download, totalBytes = :total, expireEpochSeconds = :expire, " +
            "updateIntervalHours = :intervalHours, announce = :announce, supportUrl = :supportUrl, " +
            "webPageUrl = :webPageUrl, description = :description, announceUrl = :announceUrl, " +
            "supportEmail = :supportEmail, botLink = :botLink WHERE id = :id"
    )
    suspend fun updateMetadata(
        id: String,
        name: String,
        updatedAt: Long,
        upload: Long?,
        download: Long?,
        total: Long?,
        expire: Long?,
        intervalHours: Int?,
        announce: String?,
        supportUrl: String?,
        webPageUrl: String?,
        description: String?,
        announceUrl: String?,
        supportEmail: String?,
        botLink: String?
    )

    @Query("SELECT * FROM subscriptions WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<SubscriptionEntity?>

    @Query(
        "UPDATE subscriptions SET autoUpdate = :autoUpdate, userIntervalHours = :intervalHours, " +
            "updateOnOpen = :updateOnOpen, pingOnOpen = :pingOnOpen, sendHwid = :sendHwid, " +
            "timeoutSeconds = :timeoutSeconds, userAgent = :userAgent, sortMode = :sortMode WHERE id = :id"
    )
    suspend fun updateSettings(
        id: String,
        autoUpdate: Boolean,
        intervalHours: Int?,
        updateOnOpen: Boolean,
        pingOnOpen: Boolean,
        sendHwid: Boolean,
        timeoutSeconds: Int,
        userAgent: String?,
        sortMode: Int
    )

    @Query("UPDATE subscriptions SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE subscriptions SET collapsed = :collapsed WHERE id = :id")
    suspend fun setCollapsed(id: String, collapsed: Boolean)

    @Query("UPDATE subscriptions SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteById(id: String)
}

package com.waytun.app.data.tunnel

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TunnelDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TunnelEntity)

    @Query("SELECT * FROM tunnels ORDER BY sortOrder ASC, createdAtEpochMillis ASC")
    fun observeAll(): Flow<List<TunnelEntity>>

    @Query("SELECT * FROM tunnels WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TunnelEntity?

    @Query("SELECT COUNT(*) FROM tunnels")
    suspend fun count(): Int

    @Query("DELETE FROM tunnels WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM tunnels WHERE subscriptionId = :subscriptionId")
    suspend fun getBySubscription(subscriptionId: String): List<TunnelEntity>

    @Query("UPDATE tunnels SET name = :name, sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateNameAndOrder(id: String, name: String, sortOrder: Int)

    @Query("UPDATE tunnels SET details = :details WHERE id = :id")
    suspend fun updateDetails(id: String, details: String?)

    @Query("SELECT * FROM tunnels WHERE details IS NULL")
    suspend fun getWithoutDetails(): List<TunnelEntity>

    @Query("DELETE FROM tunnels WHERE subscriptionId = :subscriptionId")
    suspend fun deleteBySubscription(subscriptionId: String)

    @Query(
        "UPDATE tunnels SET name = :name, protocol = :protocol, encryptedConfig = :encryptedConfig, " +
            "configIv = :configIv, details = :details WHERE id = :id"
    )
    suspend fun update(id: String, name: String, protocol: String, encryptedConfig: ByteArray, configIv: ByteArray, details: String?)
}

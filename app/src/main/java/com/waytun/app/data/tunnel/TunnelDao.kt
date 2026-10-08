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

    @Query(
        "UPDATE tunnels SET name = :name, protocol = :protocol, encryptedConfig = :encryptedConfig, " +
            "configIv = :configIv WHERE id = :id"
    )
    suspend fun update(id: String, name: String, protocol: String, encryptedConfig: ByteArray, configIv: ByteArray)
}

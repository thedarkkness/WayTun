package com.waytun.app.data.tunnel

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TunnelEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tunnelDao(): TunnelDao
}

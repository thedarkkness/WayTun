package com.waytun.app.di

import android.content.Context
import androidx.room.Room
import com.waytun.app.data.tunnel.AppDatabase
import com.waytun.app.data.tunnel.TunnelDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "waytun.db").build()

    @Provides
    fun provideTunnelDao(database: AppDatabase): TunnelDao = database.tunnelDao()
}

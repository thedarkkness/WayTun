package com.waytun.app.di

import android.content.Context
import androidx.room.Room
import com.waytun.app.data.subscription.SubscriptionDao
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
        Room.databaseBuilder(context, AppDatabase::class.java, "waytun.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6)
            // The VPN services run in their own processes and open this database too.
            .enableMultiInstanceInvalidation()
            .build()

    @Provides
    fun provideTunnelDao(database: AppDatabase): TunnelDao = database.tunnelDao()

    @Provides
    fun provideSubscriptionDao(database: AppDatabase): SubscriptionDao = database.subscriptionDao()
}

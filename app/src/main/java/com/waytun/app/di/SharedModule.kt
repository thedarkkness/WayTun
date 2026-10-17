package com.waytun.app.di

import android.content.Context
import com.waytun.app.core.crypto.ConfigCipher
import com.waytun.app.core.device.DeviceIdentity
import com.waytun.app.core.net.SharedHttp
import com.waytun.app.core.obfuscation.ObfuscationManager
import com.waytun.app.core.prefs.SharedPreferencesStore
import com.waytun.app.data.subscription.SubscriptionClient
import com.waytun.app.data.subscription.SubscriptionDao
import com.waytun.app.data.subscription.SubscriptionRepository
import com.waytun.app.data.tunnel.AppDatabase
import com.waytun.app.data.tunnel.TunnelDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The shared module's classes carry no DI annotations; Android wires them here. */
@Module
@InstallIn(SingletonComponent::class)
object SharedModule {

    @Provides
    @Singleton
    fun provideSubscriptionClient(@ApplicationContext context: Context): SubscriptionClient =
        SubscriptionClient(SharedHttp.client) { DeviceIdentity.subscriptionHeaders(context) }

    @Provides
    @Singleton
    fun provideSubscriptionRepository(
        database: AppDatabase,
        subscriptionDao: SubscriptionDao,
        tunnelDao: TunnelDao,
        cipher: ConfigCipher,
        client: SubscriptionClient
    ): SubscriptionRepository = SubscriptionRepository(database, subscriptionDao, tunnelDao, cipher, client)

    @Provides
    @Singleton
    fun provideObfuscationManager(@ApplicationContext context: Context): ObfuscationManager =
        ObfuscationManager(SharedPreferencesStore(context, "obfuscation"))
}

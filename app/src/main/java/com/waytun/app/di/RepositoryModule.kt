package com.waytun.app.di

import com.waytun.app.core.crypto.ConfigCipher
import com.waytun.app.core.parser.ConfDescriber
import com.waytun.app.data.tunnel.TunnelDao
import com.waytun.app.data.tunnel.TunnelRepository
import com.waytun.app.data.tunnel.TunnelRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideTunnelRepository(dao: TunnelDao, cipher: ConfigCipher): TunnelRepository =
        TunnelRepositoryImpl(dao, cipher, ConfDescriber::describe)
}

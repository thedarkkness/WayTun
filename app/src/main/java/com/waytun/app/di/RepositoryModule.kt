package com.waytun.app.di

import com.waytun.app.data.tunnel.TunnelRepository
import com.waytun.app.data.tunnel.TunnelRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindTunnelRepository(impl: TunnelRepositoryImpl): TunnelRepository
}

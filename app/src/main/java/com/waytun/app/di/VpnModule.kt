package com.waytun.app.di

import com.waytun.app.vpn.AmneziaBackend
import com.waytun.app.vpn.AmneziaVpnBackend
import com.waytun.app.vpn.VpnBackend
import com.waytun.app.vpn.WireGuardBackend
import com.waytun.app.vpn.WireGuardVpnBackend
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VpnModule {
    @Binds
    @Singleton
    @WireGuardBackend
    abstract fun bindWireGuardBackend(impl: WireGuardVpnBackend): VpnBackend

    @Binds
    @Singleton
    @AmneziaBackend
    abstract fun bindAmneziaBackend(impl: AmneziaVpnBackend): VpnBackend
}

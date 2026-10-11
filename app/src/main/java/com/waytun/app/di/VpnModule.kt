package com.waytun.app.di

import android.content.Context
import com.waytun.app.service.WayTunAmneziaVpnService
import com.waytun.app.service.WayTunVpnService
import com.waytun.app.service.WayTunXrayVpnService
import com.waytun.app.vpn.AmneziaBackend
import com.waytun.app.vpn.VpnBackend
import com.waytun.app.vpn.WireGuardBackend
import com.waytun.app.vpn.XrayBackend
import com.waytun.app.vpn.remote.RemoteVpnBackend
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The qualified [VpnBackend]s are what the UI observes: read-only mirrors of the real backends,
 * which live in each VPN service's own process (see RemoteVpnState.kt). The services inject the
 * concrete backend classes directly instead.
 */
@Module
@InstallIn(SingletonComponent::class)
object VpnModule {
    @Provides
    @Singleton
    @WireGuardBackend
    fun provideWireGuardBackend(@ApplicationContext context: Context): VpnBackend =
        RemoteVpnBackend(context, WayTunVpnService::class.java)

    @Provides
    @Singleton
    @AmneziaBackend
    fun provideAmneziaBackend(@ApplicationContext context: Context): VpnBackend =
        RemoteVpnBackend(context, WayTunAmneziaVpnService::class.java)

    @Provides
    @Singleton
    @XrayBackend
    fun provideXrayBackend(@ApplicationContext context: Context): VpnBackend =
        RemoteVpnBackend(context, WayTunXrayVpnService::class.java)
}

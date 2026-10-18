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
import com.waytun.app.data.UiPrefs
import com.waytun.app.data.ping.AndroidUnderlyingNetwork
import com.waytun.app.data.ping.PingManager
import com.waytun.app.data.ping.SocketTcpProbe
import com.waytun.app.data.tunnel.TunnelRepository
import com.waytun.app.ui.AndroidPlatformServices
import com.waytun.app.ui.AppContainer
import com.waytun.app.ui.tunnels.AndroidVpnLauncher
import com.waytun.app.vpn.AmneziaBackend
import com.waytun.app.vpn.Engine
import com.waytun.app.vpn.VpnBackend
import com.waytun.app.vpn.VpnBackends
import com.waytun.app.vpn.WireGuardBackend
import com.waytun.app.vpn.XrayBackend
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
    fun provideUiPrefs(@ApplicationContext context: Context): UiPrefs = UiPrefs(SharedPreferencesStore(context, UiPrefs.STORE_NAME))

    @Provides
    @Singleton
    fun providePingManager(@ApplicationContext context: Context, tunnels: TunnelRepository): PingManager =
        PingManager(tunnels, SocketTcpProbe(AndroidUnderlyingNetwork(context)))

    @Provides
    @Singleton
    fun provideVpnBackends(
        @ApplicationContext context: Context,
        @WireGuardBackend wireGuard: VpnBackend,
        @AmneziaBackend amnezia: VpnBackend,
        @XrayBackend xray: VpnBackend
    ): VpnBackends = VpnBackends(
        mapOf(Engine.WIREGUARD to wireGuard, Engine.AMNEZIA to amnezia, Engine.XRAY to xray),
        AndroidVpnLauncher(context)
    )

    @Provides
    @Singleton
    fun provideObfuscationManager(@ApplicationContext context: Context): ObfuscationManager =
        ObfuscationManager(SharedPreferencesStore(context, "obfuscation"))

    /** What the shared (Compose Multiplatform) screens get instead of Hilt: the same singletons. */
    @Provides
    @Singleton
    fun provideAppContainer(
        tunnels: TunnelRepository,
        subscriptions: SubscriptionRepository,
        backends: VpnBackends,
        obfuscation: ObfuscationManager,
        pings: PingManager,
        uiPrefs: UiPrefs,
        platform: AndroidPlatformServices
    ): AppContainer = AppContainer(tunnels, subscriptions, backends, obfuscation, pings, uiPrefs, platform)
}

package com.waytun.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.waytun.app.core.obfuscation.ObfuscationManager
import com.waytun.app.data.UiPrefs
import com.waytun.app.data.ping.PingManager
import com.waytun.app.data.subscription.SubscriptionRepository
import com.waytun.app.data.tunnel.TunnelRepository
import com.waytun.app.ui.tunnels.ObfuscationReapplier
import com.waytun.app.update.ReleaseInfo
import com.waytun.app.vpn.VpnBackends

/** Everything the shared screens need, assembled once per platform (Android: from Hilt; desktop: by hand). */
class AppContainer(
    val tunnelRepository: TunnelRepository,
    val subscriptionRepository: SubscriptionRepository,
    val backends: VpnBackends,
    val obfuscation: ObfuscationManager,
    val pingManager: PingManager,
    val uiPrefs: UiPrefs,
    val platform: PlatformServices
) {
    val reapplier: ObfuscationReapplier by lazy { ObfuscationReapplier(backends, tunnelRepository, obfuscation) }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer is not provided") }

/** What only the platform knows or can do. */
interface PlatformServices {
    val appVersionName: String
    /** Not an official signed release (debug build). */
    val isBetaBuild: Boolean
    /** The HWID panels see (Android's ANDROID_ID). */
    val deviceId: String
    val defaultUserAgent: String

    /** "auto", "en" or "ru". */
    fun currentLanguage(): String
    fun setLanguage(tag: String)

    /** Null where the app can't update itself in place yet. */
    val updater: AppUpdater?

    /** Sends [text] to a device showing a "receive" QR code, end-to-end encrypted with its one-time key. */
    suspend fun sendToDevice(target: com.waytun.app.core.pairing.PairingTarget, text: String): Result<Unit>

    /** wg-quick / awg-quick `.conf` text, checked with the WireGuard/AmneziaWG implementation the platform runs. */
    fun checkConf(rawText: String): ConfCheck
}

/** Result of checking a `.conf`; [summary] feeds the import preview. */
sealed interface ConfCheck {
    data class Valid(val amnezia: Boolean, val summary: ConfSummary) : ConfCheck
    /** [field] is the wg-quick attribute name, blank for file-level errors. */
    data class Invalid(val field: String, val kind: com.waytun.app.core.parser.ConfigErrorKind) : ConfCheck
    data class SyntaxError(val detail: String) : ConfCheck
    /** This platform can't run WireGuard/AmneziaWG tunnels yet. */
    data object Unsupported : ConfCheck
}

data class ConfSummary(
    val addresses: List<String>,
    val dns: List<String>,
    val endpoint: String?,
    val allowedIps: List<String>,
    val mtu: Int?,
    /** "Jc 4 · Jmin 40 · ..." for AmneziaWG, null for plain WireGuard. */
    val obfuscation: String?
)

/** Downloads and installs a newer release of this app. */
interface AppUpdater {
    /** This platform's installer among the release assets, as (url, file name). */
    fun assetFor(release: ReleaseInfo): Pair<String, String>?
    /** Downloads to a local file and returns its path; [onProgress] gets a percentage when the size is known. */
    suspend fun download(url: String, fileName: String, onProgress: (Int?) -> Unit): Result<String>
    suspend fun install(path: String): Result<Unit>
}

package com.waytun.app.ui

import android.app.Activity
import android.content.Context
import com.waytun.app.BuildConfig
import com.waytun.app.core.device.DeviceIdentity
import com.waytun.app.core.language.AppLanguage
import com.waytun.app.core.parser.TunnelConfigParser
import com.waytun.app.core.parser.TunnelParseResult
import com.waytun.app.update.ApkDownloader
import com.waytun.app.update.ApkInstaller
import com.waytun.app.update.DownloadProgress
import com.waytun.app.update.ReleaseInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android side of [PlatformServices]. It lives as long as the app (ViewModels keep it), so it holds the application
 * context and only a weak reference to the visible activity, which a language switch needs.
 */
@Singleton
class AndroidPlatformServices @Inject constructor(
    @ApplicationContext private val context: Context
) : PlatformServices {

    private var activity = WeakReference<Activity>(null)

    fun attach(activity: Activity) {
        this.activity = WeakReference(activity)
    }

    fun detach(activity: Activity) {
        if (this.activity.get() === activity) this.activity = WeakReference(null)
    }

    override val appVersionName: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"

    override val isBetaBuild: Boolean = BuildConfig.DEBUG

    override val deviceId: String by lazy { DeviceIdentity.hwid(context) }

    override val defaultUserAgent: String by lazy { DeviceIdentity.userAgent(context) }

    override fun currentLanguage(): String = AppLanguage.current(context)

    override fun setLanguage(tag: String) {
        activity.get()?.let { AppLanguage.set(it, tag) }
    }

    override val updater: AppUpdater = object : AppUpdater {
        override fun assetFor(release: ReleaseInfo): Pair<String, String>? {
            val url = release.apkDownloadUrl ?: return null
            val name = release.apkFileName ?: return null
            return url to name
        }

        override suspend fun download(url: String, fileName: String, onProgress: (Int?) -> Unit): Result<String> =
            withContext(Dispatchers.IO) {
                var result: Result<String> = Result.failure(IllegalStateException("Download interrupted"))
                ApkDownloader.download(context, url, fileName) { progress ->
                    when (progress) {
                        is DownloadProgress.InProgress -> onProgress(progress.percent)
                        is DownloadProgress.Done -> result = Result.success(progress.file.path)
                        is DownloadProgress.Failed -> result = Result.failure(Exception(progress.message))
                    }
                }
                result
            }

        override suspend fun install(path: String): Result<Unit> =
            withContext(Dispatchers.IO) { ApkInstaller.install(context, File(path)) }
    }

    override fun checkConf(rawText: String): ConfCheck = when (val result = TunnelConfigParser.parseConf(rawText)) {
        is TunnelParseResult.WireGuardSuccess -> {
            val iface = result.config.getInterface()
            val peer = result.config.getPeers().firstOrNull()
            ConfCheck.Valid(
                amnezia = false,
                summary = ConfSummary(
                    addresses = iface.getAddresses().map { it.toString() },
                    dns = iface.getDnsServers().map { it.hostAddress.orEmpty() } + iface.getDnsSearchDomains(),
                    endpoint = peer?.getEndpoint()?.let { if (it.isPresent) it.get().toString() else null },
                    allowedIps = peer?.getAllowedIps()?.map { it.toString() }.orEmpty(),
                    mtu = if (iface.getMtu().isPresent) iface.getMtu().get() else null,
                    obfuscation = null
                )
            )
        }
        is TunnelParseResult.AmneziaSuccess -> {
            val iface = result.config.getInterface()
            val peer = result.config.getPeers().firstOrNull()
            val obfuscation = buildList {
                if (iface.getJunkPacketCount().isPresent) add("Jc=${iface.getJunkPacketCount().get()}")
                if (iface.getJunkPacketMinSize().isPresent) add("Jmin=${iface.getJunkPacketMinSize().get()}")
                if (iface.getJunkPacketMaxSize().isPresent) add("Jmax=${iface.getJunkPacketMaxSize().get()}")
                if (iface.getInitPacketJunkSize().isPresent) add("S1=${iface.getInitPacketJunkSize().get()}")
                if (iface.getResponsePacketJunkSize().isPresent) add("S2=${iface.getResponsePacketJunkSize().get()}")
                if (iface.getInitPacketMagicHeader().isPresent) add("H1")
                if (iface.getResponsePacketMagicHeader().isPresent) add("H2")
                if (iface.getUnderloadPacketMagicHeader().isPresent) add("H3")
                if (iface.getTransportPacketMagicHeader().isPresent) add("H4")
            }
            ConfCheck.Valid(
                amnezia = true,
                summary = ConfSummary(
                    addresses = iface.getAddresses().map { it.toString() },
                    dns = iface.getDnsServers().map { it.hostAddress.orEmpty() } + iface.getDnsSearchDomains(),
                    endpoint = peer?.getEndpoint()?.let { if (it.isPresent) it.get().toString() else null },
                    allowedIps = peer?.getAllowedIps()?.map { it.toString() }.orEmpty(),
                    mtu = if (iface.getMtu().isPresent) iface.getMtu().get() else null,
                    obfuscation = obfuscation.joinToString(", ").ifBlank { null }
                )
            )
        }
        is TunnelParseResult.Invalid -> ConfCheck.Invalid(result.error.fieldName, result.error.kind)
        is TunnelParseResult.SyntaxError -> ConfCheck.SyntaxError(result.detail)
        is TunnelParseResult.SingBoxSuccess -> ConfCheck.SyntaxError("Share link")
    }
}

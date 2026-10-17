package com.waytun.app.data.subscription

import com.waytun.app.core.crypto.ConfigCipher
import com.waytun.app.core.log.AppLog
import com.waytun.app.core.crypto.EncryptedConfig
import com.waytun.app.core.parser.ShareLinkList
import com.waytun.app.core.parser.SingBoxLinkParser
import com.waytun.app.core.parser.TunnelDescriptor
import com.waytun.app.data.tunnel.AppDatabase
import com.waytun.app.data.tunnel.inTransaction
import com.waytun.app.core.log.PlatformLog
import com.waytun.app.core.time.nowMillis
import kotlin.uuid.Uuid
import kotlinx.coroutines.IO
import com.waytun.app.data.tunnel.TunnelDao
import com.waytun.app.data.tunnel.TunnelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class SubscriptionSummary(
    val id: String,
    val name: String,
    val usage: SubscriptionUsage?,
    val updatedAtEpochMillis: Long,
    val updateIntervalHours: Int?,
    val announce: String?,
    val supportUrl: String?,
    val webPageUrl: String?,
    val collapsed: Boolean,
    val description: String? = null,
    val announceUrl: String? = null,
    val supportEmail: String? = null,
    val botLink: String? = null,
    val settings: SubscriptionSettings = SubscriptionSettings()
)

/** What a subscription's settings screen edits (the same set Happ offers, minus connect-on-open). */
data class SubscriptionSettings(
    val autoUpdate: Boolean = true,
    val userIntervalHours: Int? = null,
    val updateOnOpen: Boolean = false,
    val pingOnOpen: Boolean = true,
    val sendHwid: Boolean = true,
    val timeoutSeconds: Int = 15,
    val userAgent: String? = null,
    val sortMode: Int = 0,
    val pinned: Boolean = false
)

data class SubscriptionSettingsState(
    val id: String,
    val name: String,
    val panelIntervalHours: Int?,
    val settings: SubscriptionSettings
)

private fun SubscriptionEntity.settings() = SubscriptionSettings(
    autoUpdate, userIntervalHours, updateOnOpen, pingOnOpen, sendHwid, timeoutSeconds, userAgent, sortMode, pinned
)

private fun SubscriptionEntity.fetchOptions() = FetchOptions(userAgent, sendHwid, timeoutSeconds)

data class SubscriptionSyncResult(val imported: Int, val skipped: Int)

class SubscriptionEmptyException : Exception("The subscription has no supported connections")

/** When the panel doesn't advertise `profile-update-interval` at all. */
private const val DEFAULT_UPDATE_INTERVAL_HOURS = 1
private const val MILLIS_PER_HOUR = 3_600_000L

class SubscriptionRepository(
    private val database: AppDatabase,
    private val subscriptionDao: SubscriptionDao,
    private val tunnelDao: TunnelDao,
    private val cipher: ConfigCipher,
    private val client: SubscriptionClient
) {

    fun observeSubscriptions(): Flow<List<SubscriptionSummary>> =
        subscriptionDao.observeAll().map { list ->
            list.map { entity ->
                SubscriptionSummary(
                    id = entity.id,
                    name = entity.name,
                    usage = SubscriptionUsage(
                        entity.uploadBytes, entity.downloadBytes, entity.totalBytes, entity.expireEpochSeconds
                    ).takeIf { it.hasAnyValue() },
                    updatedAtEpochMillis = entity.updatedAtEpochMillis,
                    updateIntervalHours = entity.updateIntervalHours,
                    announce = entity.announce,
                    supportUrl = entity.supportUrl,
                    webPageUrl = entity.webPageUrl,
                    collapsed = entity.collapsed,
                    description = entity.description,
                    announceUrl = entity.announceUrl,
                    supportEmail = entity.supportEmail,
                    botLink = entity.botLink,
                    settings = entity.settings()
                )
            }
        }

    fun observeSettings(id: String): Flow<SubscriptionSettingsState?> =
        subscriptionDao.observeById(id).map { e ->
            e?.let { SubscriptionSettingsState(it.id, it.name, it.updateIntervalHours, it.settings()) }
        }

    suspend fun getSettings(id: String): SubscriptionSettings? = withContext(Dispatchers.IO) {
        subscriptionDao.getById(id)?.settings()
    }

    suspend fun updateSettings(id: String, s: SubscriptionSettings) = withContext(Dispatchers.IO) {
        subscriptionDao.updateSettings(
            id, s.autoUpdate, s.userIntervalHours, s.updateOnOpen, s.pingOnOpen, s.sendHwid,
            s.timeoutSeconds.coerceIn(5, 15), s.userAgent?.trim()?.ifEmpty { null }, s.sortMode
        )
    }

    suspend fun setPinned(id: String, pinned: Boolean) = withContext(Dispatchers.IO) {
        subscriptionDao.setPinned(id, pinned)
    }

    /** Subscriptions whose keys should not be pinged just because the app was opened. */
    suspend fun idsWithoutPingOnOpen(): Set<String> = withContext(Dispatchers.IO) {
        subscriptionDao.getAll().filter { !it.pingOnOpen }.map { it.id }.toSet()
    }

    suspend fun add(url: String): Result<SubscriptionSyncResult> = addInternal(url).also { result ->
        result.onSuccess { AppLog.log(AppLog.SUBSCRIPTION, "added a subscription: ${it.imported} keys, ${it.skipped} skipped") }
            .onFailure { AppLog.log(AppLog.SUBSCRIPTION, "adding a subscription failed: ${it::class.simpleName}: ${it.message}") }
    }

    private suspend fun addInternal(url: String): Result<SubscriptionSyncResult> = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.fetch(url)
            val links = ShareLinkList.extract(response.body)
            if (links.isEmpty()) throw SubscriptionEmptyException()
            val encryptedUrl = cipher.encrypt(url.encodeToByteArray())
            val now = nowMillis()
            val id = Uuid.random().toString()
            database.inTransaction {
                subscriptionDao.insert(
                    SubscriptionEntity(
                        id = id,
                        name = response.title ?: hostOf(url),
                        encryptedUrl = encryptedUrl.ciphertext,
                        urlIv = encryptedUrl.iv,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                        uploadBytes = response.usage?.uploadBytes,
                        downloadBytes = response.usage?.downloadBytes,
                        totalBytes = response.usage?.totalBytes,
                        expireEpochSeconds = response.usage?.expireEpochSeconds,
                        updateIntervalHours = response.updateIntervalHours,
                        announce = response.announce,
                        supportUrl = response.supportUrl,
                        webPageUrl = response.webPageUrl,
                        description = response.description,
                        announceUrl = response.announceUrl,
                        supportEmail = response.supportEmail,
                        botLink = response.botLink
                    )
                )
                syncTunnels(id, links, keepTunnelIds = emptySet())
            }.let { imported -> SubscriptionSyncResult(imported, (ShareLinkList.countAllLinks(response.body) - imported).coerceAtLeast(0)) }
        }
    }

    /**
     * Re-downloads [id] and replaces its tunnels: unchanged links keep their tunnel (and id), new
     * ones are added, gone ones removed - except [keepTunnelIds] (the currently running tunnel),
     * so a refresh never pulls the rug from under an active connection.
     */
    suspend fun refresh(id: String, keepTunnelIds: Set<String>): Result<SubscriptionSyncResult> {
        val name = subscriptionDao.getById(id)?.name ?: "subscription"
        return refreshInternal(id, keepTunnelIds).also { result ->
            result.onSuccess { AppLog.log(AppLog.SUBSCRIPTION, "updated '$name': ${it.imported} keys, ${it.skipped} skipped") }
                .onFailure { AppLog.log(AppLog.SUBSCRIPTION, "update of '$name' failed: ${it::class.simpleName}: ${it.message}") }
        }
    }

    private suspend fun refreshInternal(id: String, keepTunnelIds: Set<String>): Result<SubscriptionSyncResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val entity = subscriptionDao.getById(id) ?: error("Subscription not found")
                val url = cipher.decrypt(EncryptedConfig(entity.encryptedUrl, entity.urlIv)).decodeToString()
                val response = client.fetch(url, entity.fetchOptions())
                val links = ShareLinkList.extract(response.body)
                if (links.isEmpty()) throw SubscriptionEmptyException()
                val imported = database.inTransaction {
                    subscriptionDao.updateMetadata(
                        id = id,
                        name = response.title ?: entity.name,
                        updatedAt = nowMillis(),
                        upload = response.usage?.uploadBytes,
                        download = response.usage?.downloadBytes,
                        total = response.usage?.totalBytes,
                        expire = response.usage?.expireEpochSeconds,
                        intervalHours = response.updateIntervalHours ?: entity.updateIntervalHours,
                        announce = response.announce,
                        supportUrl = response.supportUrl,
                        webPageUrl = response.webPageUrl,
                        description = response.description,
                        announceUrl = response.announceUrl,
                        supportEmail = response.supportEmail,
                        botLink = response.botLink
                    )
                    syncTunnels(id, links, keepTunnelIds)
                }
                SubscriptionSyncResult(imported, (ShareLinkList.countAllLinks(response.body) - imported).coerceAtLeast(0))
            }
        }

    /**
     * Refreshes every subscription whose last update is older than its own panel-given interval (and, right after the
     * app opens, those set to refresh on every launch); failures are left for the next try. The interval is whatever
     * the panel's `profile-update-interval` header says - the app never offers its own value to override it with.
     */
    suspend fun refreshStale(keepTunnelIds: Set<String>, onOpen: Boolean = false) {
        val now = nowMillis()
        subscriptionDao.getAll()
            .filter { entity ->
                (onOpen && entity.updateOnOpen) ||
                    (entity.autoUpdate && now - entity.updatedAtEpochMillis >=
                        (entity.updateIntervalHours ?: DEFAULT_UPDATE_INTERVAL_HOURS) * MILLIS_PER_HOUR)
            }
            .forEach { refresh(it.id, keepTunnelIds) }
    }

    suspend fun setCollapsed(id: String, collapsed: Boolean) = withContext(Dispatchers.IO) {
        subscriptionDao.setCollapsed(id, collapsed)
    }

    suspend fun rename(id: String, name: String) = withContext(Dispatchers.IO) {
        if (name.isNotBlank()) subscriptionDao.rename(id, name.trim())
    }

    suspend fun getUrl(id: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val entity = subscriptionDao.getById(id) ?: error("Subscription not found")
            cipher.decrypt(EncryptedConfig(entity.encryptedUrl, entity.urlIv)).decodeToString()
        }
    }

    suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            database.inTransaction {
                tunnelDao.deleteBySubscription(id)
                subscriptionDao.deleteById(id)
            }
        }
    }

    /** Must run inside a transaction. Returns how many links became tunnels. */
    private suspend fun syncTunnels(subscriptionId: String, links: List<String>, keepTunnelIds: Set<String>): Int {
        val existing = tunnelDao.getBySubscription(subscriptionId)
        val existingByConfig = existing.groupBy { entity ->
            cipher.decrypt(EncryptedConfig(entity.encryptedConfig, entity.configIv)).decodeToString()
        }.mapValues { (_, entities) -> entities.toMutableList() }
        val kept = mutableSetOf<String>()
        var imported = 0
        links.forEachIndexed { index, link ->
            val parsed = SingBoxLinkParser.parse(link)
            if (parsed == null) {
                PlatformLog.d("Subscription", "skipped entry #$index: scheme=${link.substringBefore("://", "?")} length=${link.length}")
                return@forEachIndexed
            }
            val reused = existingByConfig[link]?.removeFirstOrNull()
            if (reused != null) {
                tunnelDao.updateNameAndOrder(reused.id, parsed.remark, index)
                kept += reused.id
            } else {
                val encrypted = cipher.encrypt(link.encodeToByteArray())
                tunnelDao.insert(
                    TunnelEntity(
                        id = Uuid.random().toString(),
                        name = parsed.remark,
                        protocol = parsed.protocol.name,
                        sortOrder = index,
                        createdAtEpochMillis = nowMillis(),
                        encryptedConfig = encrypted.ciphertext,
                        configIv = encrypted.iv,
                        subscriptionId = subscriptionId,
                        details = TunnelDescriptor.describe(parsed)
                    )
                )
            }
            imported++
        }
        existing.filter { it.id !in kept && it.id !in keepTunnelIds }.forEach { tunnelDao.deleteById(it.id) }
        return imported
    }

    /** java.net.URI(url).host, or the whole url when there is none. */
    private fun hostOf(url: String): String {
        val authority = url.substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@').let { if (it.startsWith("[")) it.substringBefore(']') + "]" else it.substringBefore(':') }
        return host.ifEmpty { url }
    }

    private fun SubscriptionUsage.hasAnyValue() =
        uploadBytes != null || downloadBytes != null || totalBytes != null || expireEpochSeconds != null

    companion object {
        fun isSubscriptionUrl(text: String): Boolean {
            val trimmed = text.trim()
            if (trimmed.contains('\n') || trimmed.contains(' ')) return false
            return trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)
        }
    }
}

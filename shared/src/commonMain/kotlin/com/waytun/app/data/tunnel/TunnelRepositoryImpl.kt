package com.waytun.app.data.tunnel

import com.waytun.app.core.crypto.ConfigCipher
import com.waytun.app.core.crypto.EncryptedConfig
import com.waytun.app.core.parser.TunnelDescriptor
import com.waytun.app.core.parser.TunnelProtocol
import com.waytun.app.core.time.nowMillis
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [describeConf] labels wg-quick/awg-quick `.conf` text (see [TunnelDescriptor.describe]); it comes from the platform
 * because parsing those formats is done by the WireGuard/AmneziaWG libraries each platform bundles.
 */
class TunnelRepositoryImpl(
    private val dao: TunnelDao,
    private val cipher: ConfigCipher,
    private val describeConf: (String) -> String?
) : TunnelRepository {

    override fun observeTunnels(): Flow<List<TunnelSummary>> =
        dao.observeAll().map { entities ->
            entities.map { entity ->
                TunnelSummary(
                    id = entity.id,
                    name = entity.name,
                    protocol = TunnelProtocol.valueOf(entity.protocol),
                    createdAtEpochMillis = entity.createdAtEpochMillis,
                    subscriptionId = entity.subscriptionId,
                    details = entity.details
                )
            }
        }

    override suspend fun importTunnel(
        name: String,
        protocol: TunnelProtocol,
        rawConfigText: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val encrypted = cipher.encrypt(rawConfigText.encodeToByteArray())
            val id = Uuid.random().toString()
            dao.insert(
                TunnelEntity(
                    id = id,
                    name = name,
                    protocol = protocol.name,
                    sortOrder = dao.count(),
                    createdAtEpochMillis = nowMillis(),
                    encryptedConfig = encrypted.ciphertext,
                    configIv = encrypted.iv,
                    details = TunnelDescriptor.describe(rawConfigText, describeConf)
                )
            )
            id
        }
    }

    override suspend fun getDecryptedConfigText(id: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val entity = dao.getById(id) ?: error("Tunnel not found")
                val decrypted = cipher.decrypt(EncryptedConfig(entity.encryptedConfig, entity.configIv))
                decrypted.decodeToString()
            }
        }

    override suspend fun getTunnel(id: String): Result<TunnelDetails> =
        withContext(Dispatchers.IO) {
            runCatching {
                val entity = dao.getById(id) ?: error("Tunnel not found")
                val decrypted = cipher.decrypt(EncryptedConfig(entity.encryptedConfig, entity.configIv))
                TunnelDetails(
                    id = entity.id,
                    name = entity.name,
                    protocol = TunnelProtocol.valueOf(entity.protocol),
                    rawConfigText = decrypted.decodeToString()
                )
            }
        }

    override suspend fun updateTunnel(
        id: String,
        name: String,
        protocol: TunnelProtocol,
        rawConfigText: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val encrypted = cipher.encrypt(rawConfigText.encodeToByteArray())
            dao.update(id, name, protocol.name, encrypted.ciphertext, encrypted.iv, TunnelDescriptor.describe(rawConfigText, describeConf))
        }
    }

    override suspend fun deleteTunnel(id: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { dao.deleteById(id) }
        }

    override suspend fun backfillDetails() = withContext(Dispatchers.IO) {
        dao.getWithoutDetails().forEach { entity ->
            runCatching {
                val raw = cipher.decrypt(EncryptedConfig(entity.encryptedConfig, entity.configIv)).decodeToString()
                // Empty string (not null) for configs that don't parse, so they aren't retried forever.
                dao.updateDetails(entity.id, TunnelDescriptor.describe(raw, describeConf) ?: "")
            }
        }
    }
}

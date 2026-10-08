package com.waytun.app.data.tunnel

import com.waytun.app.core.crypto.ConfigCipher
import com.waytun.app.core.crypto.EncryptedConfig
import com.waytun.app.core.parser.TunnelProtocol
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TunnelRepositoryImpl @Inject constructor(
    private val dao: TunnelDao,
    private val cipher: ConfigCipher
) : TunnelRepository {

    override fun observeTunnels(): Flow<List<TunnelSummary>> =
        dao.observeAll().map { entities ->
            entities.map { entity ->
                TunnelSummary(
                    id = entity.id,
                    name = entity.name,
                    protocol = TunnelProtocol.valueOf(entity.protocol),
                    createdAtEpochMillis = entity.createdAtEpochMillis
                )
            }
        }

    override suspend fun importTunnel(
        name: String,
        protocol: TunnelProtocol,
        rawConfigText: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val encrypted = cipher.encrypt(rawConfigText.toByteArray(StandardCharsets.UTF_8))
            val id = UUID.randomUUID().toString()
            dao.insert(
                TunnelEntity(
                    id = id,
                    name = name,
                    protocol = protocol.name,
                    sortOrder = dao.count(),
                    createdAtEpochMillis = System.currentTimeMillis(),
                    encryptedConfig = encrypted.ciphertext,
                    configIv = encrypted.iv
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
                String(decrypted, StandardCharsets.UTF_8)
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
                    rawConfigText = String(decrypted, StandardCharsets.UTF_8)
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
            val encrypted = cipher.encrypt(rawConfigText.toByteArray(StandardCharsets.UTF_8))
            dao.update(id, name, protocol.name, encrypted.ciphertext, encrypted.iv)
        }
    }

    override suspend fun deleteTunnel(id: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { dao.deleteById(id) }
        }
}

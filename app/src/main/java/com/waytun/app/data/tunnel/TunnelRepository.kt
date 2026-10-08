package com.waytun.app.data.tunnel

import com.waytun.app.core.parser.TunnelProtocol
import kotlinx.coroutines.flow.Flow

data class TunnelSummary(
    val id: String,
    val name: String,
    val protocol: TunnelProtocol,
    val createdAtEpochMillis: Long
)

data class TunnelDetails(
    val id: String,
    val name: String,
    val protocol: TunnelProtocol,
    val rawConfigText: String
)

interface TunnelRepository {
    fun observeTunnels(): Flow<List<TunnelSummary>>

    /** Encrypts [rawConfigText] and stores it as a new tunnel; returns the generated tunnel id. */
    suspend fun importTunnel(name: String, protocol: TunnelProtocol, rawConfigText: String): Result<String>

    /** Decrypts and returns the original .conf text for [id], e.g. to start the tunnel. */
    suspend fun getDecryptedConfigText(id: String): Result<String>

    /** Decrypts metadata + config text together, for the tunnel detail/edit screen. */
    suspend fun getTunnel(id: String): Result<TunnelDetails>

    /** Re-encrypts and overwrites an existing tunnel's name/protocol/config in place. */
    suspend fun updateTunnel(id: String, name: String, protocol: TunnelProtocol, rawConfigText: String): Result<Unit>

    suspend fun deleteTunnel(id: String): Result<Unit>
}

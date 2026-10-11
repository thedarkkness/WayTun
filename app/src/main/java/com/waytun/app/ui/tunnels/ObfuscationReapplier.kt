package com.waytun.app.ui.tunnels

import android.content.Context
import com.waytun.app.core.log.AppLog
import com.waytun.app.core.obfuscation.ObfuscationManager
import com.waytun.app.data.tunnel.TunnelRepository
import com.waytun.app.vpn.TunnelState
import com.waytun.app.vpn.VpnBackends
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DEBOUNCE_MS = 900L
private const val SAME_ENGINE_RESTART_GAP_MS = 400L

/**
 * Obfuscation settings used to reach only the *next* connection, so a tunnel that was already up kept its old packets
 * (and, if the new values mattered, simply stopped working until the VPN was switched off and on by hand). After a
 * change this brings the running tunnel back up with a freshly generated profile built from the new settings.
 * Changes made in quick succession (several switches in a row) cause a single restart.
 */
@Singleton
class ObfuscationReapplier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val backends: VpnBackends,
    private val tunnelRepository: TunnelRepository,
    private val obfuscation: ObfuscationManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    fun requestReapply() {
        job?.cancel()
        job = scope.launch {
            delay(DEBOUNCE_MS)
            val tunnelId = backends.activeTunnelIds().firstOrNull() ?: return@launch
            val details = tunnelRepository.getTunnel(tunnelId).getOrNull() ?: return@launch
            val settings = obfuscation.settings.value
            val engine = backends.runtimeEngine(details.protocol, settings)
            // Every running engine goes fully down first, exactly like a normal switch, so two Go runtimes never overlap.
            val running = backends.all.filter { (_, backend) -> backend.state.value !is TunnelState.Disconnected }.map { it.first }
            AppLog.log(AppLog.CONNECTION, "obfuscation settings changed: reconnecting '${details.name}' with new values")
            running.forEach { sendDisconnect(context, it) }
            if (running.isNotEmpty()) {
                backends.awaitStopped(running)
                delay(SAME_ENGINE_RESTART_GAP_MS)
            }
            startConnect(context, engine, tunnelId, details.name, settings, obfuscation.profileForConnect())
        }
    }
}

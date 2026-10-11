package com.waytun.app.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import com.waytun.app.R
import com.waytun.app.vpn.TunnelState
import com.waytun.app.vpn.TunnelStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val STATS_POLL_INTERVAL_MS = 1_500L

/**
 * The foreground notification of one VPN service: tunnel name as the title, status + live traffic
 * as the text, a Disconnect action, and an Android 16 Live Update chip while active. Shared by all
 * three VPN services so every protocol behaves the same.
 */
class TunnelNotificationController(
    private val service: Service,
    private val factory: VpnNotificationFactory,
    private val scope: CoroutineScope,
    disconnectAction: String,
    private val stats: () -> TunnelStats?
) {
    private var tunnelName = ""
    private var state: TunnelState = TunnelState.Connecting
    private var rate: TrafficRate? = null
    private var reconnectAttempt: Int? = null
    private var statsJob: Job? = null

    private val disconnectIntent = PendingIntent.getService(
        service,
        0,
        Intent(service, service.javaClass).setAction(disconnectAction),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun startForeground() {
        factory.ensureChannel()
        service.startForeground(
            VpnNotificationFactory.NOTIFICATION_ID,
            build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
        )
    }

    fun setTunnelName(name: String) {
        tunnelName = name
        post()
    }

    fun setReconnectAttempt(attempt: Int?) {
        reconnectAttempt = attempt
        post()
    }

    fun onState(newState: TunnelState) {
        state = newState
        rate = null
        if (newState is TunnelState.Connected) startStatsPolling() else stopStatsPolling()
        post()
    }

    fun stop() = stopStatsPolling()

    private fun build() = factory.build(
        tunnelName.ifEmpty { service.getString(R.string.app_name) },
        state,
        rate,
        disconnectIntent,
        reconnectAttempt
    )

    private fun post() {
        service.getSystemService(NotificationManager::class.java)
            ?.notify(VpnNotificationFactory.NOTIFICATION_ID, build())
    }

    private fun startStatsPolling() {
        if (statsJob?.isActive == true) return
        statsJob = scope.launch {
            var previous: TunnelStats? = null
            var previousAtMillis = System.currentTimeMillis()
            while (isActive) {
                delay(STATS_POLL_INTERVAL_MS)
                val current = stats()
                val nowMillis = System.currentTimeMillis()
                val prev = previous
                if (current != null && prev != null) {
                    val elapsedSeconds = (nowMillis - previousAtMillis) / 1000.0
                    if (elapsedSeconds > 0) {
                        rate = TrafficRate(
                            ((current.rxBytes - prev.rxBytes) / elapsedSeconds).toLong().coerceAtLeast(0),
                            ((current.txBytes - prev.txBytes) / elapsedSeconds).toLong().coerceAtLeast(0)
                        )
                        post()
                    }
                }
                previous = current
                previousAtMillis = nowMillis
            }
        }
    }

    private fun stopStatsPolling() {
        statsJob?.cancel()
        statsJob = null
    }
}

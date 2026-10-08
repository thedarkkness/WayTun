package com.waytun.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.waytun.app.MainActivity
import com.waytun.app.R
import com.waytun.app.vpn.TunnelState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Instantaneous throughput, in bytes/sec, computed from two [com.waytun.app.vpn.TunnelStats] samples. */
data class TrafficRate(val downBytesPerSec: Long, val upBytesPerSec: Long)

@Singleton
class VpnNotificationFactory @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_vpn_status_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_vpn_status_description)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(tunnelName: String, state: TunnelState, trafficRate: TrafficRate? = null): Notification {
        val statusText = when (state) {
            TunnelState.Connecting -> context.getString(R.string.vpn_status_connecting)
            is TunnelState.Connected -> context.getString(R.string.vpn_status_connected)
            TunnelState.Disconnecting -> context.getString(R.string.vpn_status_disconnecting)
            TunnelState.Disconnected -> context.getString(R.string.vpn_status_disconnected)
            is TunnelState.Error -> state.message
        }
        val isOngoing = state is TunnelState.Connecting ||
            state is TunnelState.Connected ||
            state is TunnelState.Disconnecting

        val contentText = if (state is TunnelState.Connected && trafficRate != null) {
            context.getString(
                R.string.notification_traffic_format,
                statusText,
                formatRate(trafficRate.downBytesPerSec),
                formatRate(trafficRate.upBytesPerSec)
            )
        } else {
            statusText
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            ),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(tunnelName)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification_vpn)
            .setOngoing(isOngoing)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun formatRate(bytesPerSec: Long): String {
        val bps = bytesPerSec.coerceAtLeast(0)
        return when {
            bps < 1024 -> "$bps B/s"
            bps < 1024 * 1024 -> String.format(Locale.US, "%.0f KB/s", bps / 1024.0)
            else -> String.format(Locale.US, "%.1f MB/s", bps / (1024.0 * 1024.0))
        }
    }

    companion object {
        const val CHANNEL_ID = "vpn_status"
        const val NOTIFICATION_ID = 1001
    }
}

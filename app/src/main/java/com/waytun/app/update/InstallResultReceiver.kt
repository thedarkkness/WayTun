package com.waytun.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

/** Receives the status of a [PackageInstaller] session started by [ApkInstaller]. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                confirmIntent?.let { runCatching { context.startActivity(it) } }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Toast.makeText(context, message ?: "Install failed", Toast.LENGTH_LONG).show()
            }
        }
    }
}

package com.waytun.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File

/**
 * Installs an APK via the [PackageInstaller] session API instead of an `ACTION_VIEW` intent.
 * `ACTION_VIEW` depends on a Package Installer activity being resolvable for the APK mime type,
 * which some Android TV builds don't register, crashing with an ActivityNotFoundException. The
 * session API has no such dependency: the system handles any needed confirmation UI itself via
 * the status [PendingIntent], so this works the same way on phones and TVs.
 */
object ApkInstaller {

    fun install(context: Context, file: File): Result<Unit> = runCatching {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val sessionId = packageInstaller.createSession(params)
        packageInstaller.openSession(sessionId).use { session ->
            file.inputStream().use { input ->
                session.openWrite(file.name, 0, file.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val statusIntent = Intent(context, InstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, statusIntent, flags)
            session.commit(pendingIntent.intentSender)
        }
    }
}

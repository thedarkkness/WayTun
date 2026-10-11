package com.waytun.app.core.device

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * What subscription panels (Remnawave, Marzban, 3x-ui...) expect from a client to count and
 * limit devices - the same set of headers Happ sends. [hwid] is Android's per-app-signing-key
 * ANDROID_ID: stable across reinstalls and app updates, distinct per device/user, and needs no
 * permission.
 */
object DeviceIdentity {

    @SuppressLint("HardwareIds")
    fun hwid(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()

    const val OS_NAME = "Android"

    val osVersion: String get() = Build.VERSION.RELEASE

    val model: String get() = Build.MODEL

    fun userAgent(context: Context): String {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0"
        return "WayTun/$version (Android ${Build.VERSION.RELEASE}; ${Build.MODEL})"
    }

    fun subscriptionHeaders(context: Context): Map<String, String> = mapOf(
        "User-Agent" to userAgent(context),
        "x-hwid" to hwid(context),
        "x-device-os" to OS_NAME,
        "x-ver-os" to osVersion,
        "x-device-model" to model
    )
}

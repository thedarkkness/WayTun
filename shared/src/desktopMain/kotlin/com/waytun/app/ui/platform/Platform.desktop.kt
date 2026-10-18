package com.waytun.app.ui.platform

import androidx.compose.runtime.Composable
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

actual fun relativeTimeSpan(epochMillis: Long, nowMillis: Long, minResolutionMillis: Long): String {
    val ru = Locale.getDefault().language == "ru"
    val minutes = (nowMillis - epochMillis).coerceAtLeast(0) / 60_000
    return when {
        minutes < 1 -> if (ru) "только что" else "just now"
        minutes < 60 -> if (ru) "$minutes мин. назад" else "$minutes min. ago"
        minutes < 24 * 60 -> (minutes / 60).let { if (ru) "$it ч. назад" else "$it hr. ago" }
        else -> (minutes / (24 * 60)).let { if (ru) "$it дн. назад" else "$it days ago" }
    }
}

actual fun formatDateSkeleton(epochMillis: Long, skeleton: String): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(epochMillis))

actual val operatingSystemName: String get() = "${System.getProperty("os.name")} ${System.getProperty("os.version")}".trim()

actual fun formatDecimal(value: Double, maxFractionDigits: Int): String =
    NumberFormat.getNumberInstance().apply { maximumFractionDigits = maxFractionDigits }.format(value)

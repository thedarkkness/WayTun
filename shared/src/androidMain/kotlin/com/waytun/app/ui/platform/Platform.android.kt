package com.waytun.app.ui.platform

import android.icu.text.SimpleDateFormat
import android.os.Build
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

actual fun relativeTimeSpan(epochMillis: Long, nowMillis: Long, minResolutionMillis: Long): String =
    DateUtils.getRelativeTimeSpanString(epochMillis, nowMillis, minResolutionMillis, DateUtils.FORMAT_ABBREV_RELATIVE).toString()

actual fun formatDateSkeleton(epochMillis: Long, skeleton: String): String =
    SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton), Locale.getDefault()).format(Date(epochMillis))

actual val operatingSystemName: String get() = "Android ${Build.VERSION.RELEASE}"

actual fun formatDecimal(value: Double, maxFractionDigits: Int): String =
    NumberFormat.getNumberInstance().apply { maximumFractionDigits = maxFractionDigits }.format(value)

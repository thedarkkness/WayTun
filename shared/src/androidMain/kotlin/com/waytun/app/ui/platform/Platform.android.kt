package com.waytun.app.ui.platform

import android.icu.text.SimpleDateFormat
import android.os.Build
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.waytun.app.core.qr.QrCodeGenerator
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

actual fun relativeTimeSpan(epochMillis: Long, nowMillis: Long, minResolutionMillis: Long, abbreviated: Boolean): String =
    if (abbreviated) {
        DateUtils.getRelativeTimeSpanString(epochMillis, nowMillis, minResolutionMillis, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
    } else {
        DateUtils.getRelativeTimeSpanString(epochMillis, nowMillis, minResolutionMillis).toString()
    }

actual fun formatDateSkeleton(epochMillis: Long, skeleton: String): String =
    SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton), Locale.getDefault()).format(Date(epochMillis))

actual val operatingSystemName: String get() = "Android ${Build.VERSION.RELEASE}"

actual fun formatDecimal(value: Double, maxFractionDigits: Int): String =
    NumberFormat.getNumberInstance().apply { maximumFractionDigits = maxFractionDigits }.format(value)

actual fun qrCodeImage(text: String, sizePx: Int): ImageBitmap? = QrCodeGenerator.generate(text, sizePx)?.asImageBitmap()

@Composable
actual fun rememberClipboardWriter(label: String): (String) -> Unit {
    val context = LocalContext.current
    return remember(context, label) {
        { text -> context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text)) }
    }
}

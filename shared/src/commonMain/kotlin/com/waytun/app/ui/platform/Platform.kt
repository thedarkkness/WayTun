package com.waytun.app.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/** System back (gesture/button) handling; desktop has none, so screens keep their own back arrow. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/**
 * "5 min. ago" ([abbreviated]) or "5 minutes ago" in the current language at [minResolutionMillis] granularity,
 * like Android's DateUtils.getRelativeTimeSpanString.
 */
expect fun relativeTimeSpan(epochMillis: Long, nowMillis: Long, minResolutionMillis: Long, abbreviated: Boolean = true): String

/** A date in the current locale for a pattern skeleton such as "d MMM yyyy". */
expect fun formatDateSkeleton(epochMillis: Long, skeleton: String): String

/** "Android 16", "Windows 11", "Linux 6.8" - shown in About. */
expect val operatingSystemName: String

/** "1.9 ГБ": [units] are the localized B/KB/MB/GB/TB names (Res.array.byte_units). */
fun formatBytes(bytes: Long, units: List<String>): String {
    var value = bytes.coerceAtLeast(0).toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return "${formatDecimal(value, if (unit > 0 && value < 10) 1 else 0)} ${units[unit]}"
}

/** The number with up to [maxFractionDigits] digits and the locale's decimal separator. */
expect fun formatDecimal(value: Double, maxFractionDigits: Int): String

/** A black-on-white QR code of [text], or null when it doesn't fit even at the lowest error correction. */
expect fun qrCodeImage(text: String, sizePx: Int = 800): ImageBitmap?

/** Puts plain text on the system clipboard. */
@Composable
expect fun rememberClipboardWriter(label: String = "config"): (String) -> Unit

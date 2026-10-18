package com.waytun.app.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

actual fun relativeTimeSpan(epochMillis: Long, nowMillis: Long, minResolutionMillis: Long, abbreviated: Boolean): String {
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

actual fun qrCodeImage(text: String, sizePx: Int): ImageBitmap? {
    val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 1)
    val matrix = try {
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    } catch (e: WriterException) {
        return null
    }
    val image = BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_RGB)
    for (x in 0 until sizePx) for (y in 0 until sizePx) image.setRGB(x, y, if (matrix.get(x, y)) 0x000000 else 0xFFFFFF)
    return image.toComposeImageBitmap()
}

@Composable
actual fun rememberClipboardWriter(label: String): (String) -> Unit = remember {
    { text -> Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
}

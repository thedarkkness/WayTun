package com.waytun.app.ui.tunnels

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.waytun.app.res.Res
import com.waytun.app.res.action_continue
import com.waytun.app.res.action_not_now
import com.waytun.app.res.action_scan_qr
import com.waytun.app.res.battery_rationale_message
import com.waytun.app.res.battery_rationale_title
import com.waytun.app.res.notification_permission_rationale_message
import com.waytun.app.res.notification_permission_rationale_title
import com.waytun.app.ui.components.GlassAlertDialog
import com.waytun.app.ui.pairing.ReceiveViaNetworkScreen
import com.waytun.app.ui.scanner.QrScannerScreen
import com.waytun.app.ui.tv.isTelevision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The main screen's Android side: VPN consent, notification/battery prompts, the document picker, the camera and the TV receive screen. */
@Composable
fun rememberAndroidTunnelListHost(): TunnelListHost {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isTv = remember { isTelevision(context) }

    var showNotificationRationale by remember { mutableStateOf(false) }
    var showBatteryRationale by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var showReceiveOverlay by rememberSaveable { mutableStateOf(false) }
    val callbacks = remember { HostCallbacks() }

    val vpnConsentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val granted = callbacks.vpnGranted
        val denied = callbacks.vpnDenied
        callbacks.vpnGranted = null
        callbacks.vpnDenied = null
        if (result.resultCode == Activity.RESULT_OK && granted != null) granted() else denied?.invoke()
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Connecting proceeds either way; this only affects whether the status notice is visible. */ }
    val batteryExemptionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* Connecting proceeds either way; this only affects whether the OS can kill the tunnel in the background. */ }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showQrScanner = true else callbacks.takeQr()?.onDenied?.invoke()
    }
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) { readTextOrNull(context, uri) }
            val suggestedName = if (text.isNullOrBlank()) "" else {
                queryDisplayName(context, uri)?.removeSuffix(".conf")?.removeSuffix(".CONF").orEmpty()
            }
            callbacks.results?.onFilePicked?.invoke(suggestedName, text)
        }
    }

    return remember(context) {
        object : TunnelListHost {
            override val isTv: Boolean = isTv
            override val scanQrLabel: StringResource? = if (isTv) null else Res.string.action_scan_qr
            override val canReceiveFromDevice: Boolean = true

            override fun clipboardText(): String {
                val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                return clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
            }

            override fun openUrl(url: String) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }

            override fun onBeforeConnect() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    showNotificationRationale = true
                } else if (!isTv) {
                    // The OS (and on many phones, the manufacturer's own battery manager on top of it) can kill a
                    // backgrounded VPN service to save power, silently dropping the tunnel until it's reopened by hand.
                    // Asking once to be exempted is what keeps a connection up unattended, the way users expect.
                    val powerManager = context.getSystemService(PowerManager::class.java)
                    if (powerManager?.isIgnoringBatteryOptimizations(context.packageName) == false) {
                        showBatteryRationale = true
                    }
                }
            }

            override fun requestVpnPermission(onGranted: () -> Unit, onDenied: () -> Unit) {
                val prepareIntent = VpnService.prepare(context)
                if (prepareIntent != null) {
                    callbacks.vpnGranted = onGranted
                    callbacks.vpnDenied = onDenied
                    vpnConsentLauncher.launch(prepareIntent)
                } else {
                    onGranted()
                }
            }

            override fun pickConfigFile() {
                filePickerLauncher.launch(arrayOf("*/*"))
            }

            override fun scanQr(onResult: (String) -> Unit, onDismiss: () -> Unit, onDenied: () -> Unit) {
                callbacks.qr = QrCallbacks(onResult, onDismiss, onDenied)
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    showQrScanner = true
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }

            override fun receiveFromDevice() {
                showReceiveOverlay = true
            }

            @Composable
            override fun Overlays(results: TunnelListResults): Boolean {
                // Plain assignment during composition: it must be in place before a pending activity result is delivered.
                callbacks.results = results
                if (showNotificationRationale) {
                    GlassAlertDialog(
                        onDismissRequest = { showNotificationRationale = false },
                        title = { Text(stringResource(Res.string.notification_permission_rationale_title)) },
                        text = { Text(stringResource(Res.string.notification_permission_rationale_message)) },
                        confirmButton = {
                            TextButton(onClick = {
                                showNotificationRationale = false
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }) { Text(stringResource(Res.string.action_continue)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showNotificationRationale = false }) {
                                Text(stringResource(Res.string.action_not_now))
                            }
                        }
                    )
                }

                if (showBatteryRationale) {
                    GlassAlertDialog(
                        onDismissRequest = { showBatteryRationale = false },
                        title = { Text(stringResource(Res.string.battery_rationale_title)) },
                        text = { Text(stringResource(Res.string.battery_rationale_message)) },
                        confirmButton = {
                            TextButton(onClick = {
                                showBatteryRationale = false
                                runCatching {
                                    batteryExemptionLauncher.launch(
                                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                                    )
                                }
                            }) { Text(stringResource(Res.string.action_continue)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showBatteryRationale = false }) {
                                Text(stringResource(Res.string.action_not_now))
                            }
                        }
                    )
                }

                BackHandler(enabled = showQrScanner) { showQrScanner = false; callbacks.takeQr()?.onDismiss?.invoke() }
                BackHandler(enabled = showReceiveOverlay) { showReceiveOverlay = false }

                if (showQrScanner) {
                    QrScannerScreen(
                        onResult = { text ->
                            showQrScanner = false
                            callbacks.takeQr()?.onResult?.invoke(text)
                        },
                        onClose = { showQrScanner = false; callbacks.takeQr()?.onDismiss?.invoke() }
                    )
                    return true
                }

                if (showReceiveOverlay) {
                    ReceiveViaNetworkScreen(
                        onReceived = { text ->
                            showReceiveOverlay = false
                            results.onReceived(text)
                        },
                        onClose = { showReceiveOverlay = false }
                    )
                    return true
                }
                return false
            }
        }
    }
}

private class QrCallbacks(val onResult: (String) -> Unit, val onDismiss: () -> Unit, val onDenied: () -> Unit)

/** The pending callbacks of whatever the host is waiting on (a system dialog, the scanner, a picker). */
private class HostCallbacks {
    var vpnGranted: (() -> Unit)? = null
    var vpnDenied: (() -> Unit)? = null
    var qr: QrCallbacks? = null
    var results: TunnelListResults? = null

    fun takeQr(): QrCallbacks? = qr.also { qr = null }
}

private fun readTextOrNull(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
    }.getOrNull()

private fun queryDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }

package com.waytun.app.ui.tunnels

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource

/** What the main screen asks of the platform it runs on: permissions, pickers, the camera, the clipboard. */
interface TunnelListHost {
    /** A TV: no touchscreen or camera, only a remote. */
    val isTv: Boolean

    /** The "scan QR" menu label (a camera on Android, a QR image on desktop), or null when there is no way to read one. */
    val scanQrLabel: StringResource?

    /** Whether this device can show a "receive from phone" code. */
    val canReceiveFromDevice: Boolean

    fun clipboardText(): String

    fun openUrl(url: String)

    /** Called when the power button starts a connection: one-time prompts that don't block it (Android: notifications, battery). */
    fun onBeforeConnect()

    /** Whatever the OS needs before a tunnel may come up (Android: the VPN consent dialog). */
    fun requestVpnPermission(onGranted: () -> Unit, onDenied: () -> Unit)

    /** Lets the user pick a config file; the result arrives through [TunnelListResults.onFilePicked]. */
    fun pickConfigFile()

    /** Reads a QR code; [onDismiss] when the user closed it, [onDenied] when it can't be opened (no camera permission). */
    fun scanQr(onResult: (String) -> Unit, onDismiss: () -> Unit, onDenied: () -> Unit)

    /** Shows this device's "receive from phone" screen; the text arrives through [TunnelListResults.onReceived]. */
    fun receiveFromDevice()

    /**
     * The host's own dialogs and full-screen overlays; returns true while a full-screen one covers the list.
     * [results] is handed over on every composition, so a result that outlives the screen (an activity recreated while
     * the file picker was open) still reaches the current one.
     */
    @Composable
    fun Overlays(results: TunnelListResults): Boolean
}

class TunnelListResults(
    /** The file name without `.conf` and its text (null when unreadable). */
    val onFilePicked: (suggestedName: String, text: String?) -> Unit,
    val onReceived: (String) -> Unit
)

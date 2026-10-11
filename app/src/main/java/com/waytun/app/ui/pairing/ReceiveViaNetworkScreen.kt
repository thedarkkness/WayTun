package com.waytun.app.ui.pairing

import androidx.compose.foundation.Image
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.waytun.app.ui.icons.AppIcons
import com.waytun.app.ui.tv.tvFocusBorder
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.waytun.app.R
import com.waytun.app.core.pairing.PairingCrypto
import com.waytun.app.core.pairing.PairingTarget
import com.waytun.app.core.qr.QrCodeGenerator
import java.io.DataInputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val LISTEN_TIMEOUT_MS = 120_000L

private sealed interface ReceiveState {
    data object Preparing : ReceiveState
    data class WaitingForSender(val qrText: String) : ReceiveState
    data object TimedOut : ReceiveState
    data class Error(val message: String) : ReceiveState
}

/** Shown on a camera-less device (e.g. Android TV): displays a QR another WayTun device can
 * scan to push one of its tunnels here over the local network. */
@Composable
fun ReceiveViaNetworkScreen(
    onReceived: (rawConfigText: String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<ReceiveState>(ReceiveState.Preparing) }
    var attempt by remember { mutableStateOf(0) }

    DisposableEffect(attempt) {
        state = ReceiveState.Preparing
        val job = Job()
        val scope = CoroutineScope(Dispatchers.IO + job)
        var serverSocket: ServerSocket? = null

        scope.launch {
            val localIp = findLocalIpv4Address()
            if (localIp == null) {
                state = ReceiveState.Error(context.getString(R.string.pairing_no_network))
                return@launch
            }
            val key = PairingCrypto.generateKey()
            val server = try {
                ServerSocket(0).apply { soTimeout = LISTEN_TIMEOUT_MS.toInt() }
            } catch (e: Exception) {
                state = ReceiveState.Error(e.message ?: context.getString(R.string.pairing_error_generic))
                return@launch
            }
            serverSocket = server
            val target = PairingTarget(localIp, server.localPort, key)
            state = ReceiveState.WaitingForSender(target.toQrText())

            val received = withTimeoutOrNull(LISTEN_TIMEOUT_MS) {
                try {
                    server.accept().use { socket ->
                        val input = DataInputStream(socket.getInputStream())
                        val length = input.readInt()
                        val iv = ByteArray(12)
                        input.readFully(iv)
                        val cipherBytes = ByteArray(length - iv.size)
                        input.readFully(cipherBytes)
                        PairingCrypto.decrypt(key, iv, cipherBytes)
                    }
                } catch (e: Exception) {
                    null
                }
            }

            if (received != null) {
                withContext(Dispatchers.Main) { onReceived(String(received, Charsets.UTF_8)) }
            } else {
                state = ReceiveState.TimedOut
            }
        }

        onDispose {
            job.cancel()
            runCatching { serverSocket?.close() }
        }
    }

    val config = LocalConfiguration.current
    val landscape = config.screenWidthDp > config.screenHeightDp
    // Fit the code to the screen height (a phone held sideways is far shorter than a TV).
    val qrSize = if (landscape) (config.screenHeightDp - 130).coerceIn(150, 300).dp else 190.dp
    // Own palette on purpose (deep teal/blue, mint + amber accents): this screen is the first thing seen
    // on a TV, and it should look distinct from the violet phone UI in both light and dark mode.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF042B33), Color(0xFF0A3A55), Color(0xFF16215F))))
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        when (val s = state) {
            ReceiveState.Preparing -> CircularProgressIndicator(color = ReceiveMint)

            is ReceiveState.WaitingForSender -> {
                val qrBitmap = remember(s.qrText) { QrCodeGenerator.generate(s.qrText, 720) }
                val qr: @Composable () -> Unit = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(28.dp))
                                .background(Color.White)
                                .border(4.dp, ReceiveMint, RoundedCornerShape(28.dp))
                                .padding(14.dp)
                        ) {
                            if (qrBitmap != null) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(qrSize)
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        WaitingPulse()
                    }
                }
                val steps: @Composable () -> Unit = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(ReceiveMint.copy(alpha = 0.2f))
                        ) { Icon(AppIcons.Tv, null, tint = ReceiveMint, modifier = Modifier.size(30.dp)) }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            stringResource(R.string.pairing_receive_title),
                            style = if (landscape) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(if (landscape) 18.dp else 10.dp))
                    listOf(
                        R.string.pairing_step_1 to ReceiveMint,
                        R.string.pairing_step_2 to ReceiveAmber,
                        R.string.pairing_step_3 to ReceiveCyan
                    ).forEachIndexed { index, (step, accent) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = if (landscape) 5.dp else 3.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                                .padding(if (landscape) 14.dp else 10.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(32.dp).clip(CircleShape).background(accent)
                            ) {
                                Text("${index + 1}", color = Color(0xFF05222A), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(stringResource(step), style = if (landscape) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium, color = Color(0xFFEAF6FA))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    CloseButton(onClose)
                }
                if (landscape) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                        qr()
                        Column(modifier = Modifier.widthIn(max = 520.dp).verticalScroll(rememberScrollState())) { steps() }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.verticalScroll(rememberScrollState())) {
                        qr()
                        Spacer(Modifier.height(14.dp))
                        Column { steps() }
                    }
                }
            }

            ReceiveState.TimedOut -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.pairing_timeout), style = MaterialTheme.typography.titleLarge, color = Color.White)
                Spacer(Modifier.height(18.dp))
                RetryButton { attempt++ }
                CloseButton(onClose)
            }

            is ReceiveState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.message, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(Modifier.height(18.dp))
                RetryButton { attempt++ }
                CloseButton(onClose)
            }
        }
    }
}

private val ReceiveMint = Color(0xFF3EE6B0)
private val ReceiveAmber = Color(0xFFFFC857)
private val ReceiveCyan = Color(0xFF5CD6FF)

/** A softly pulsing dot + "waiting for the phone": shows the TV is alive and listening. */
@Composable
private fun WaitingPulse() {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(ReceiveMint.copy(alpha = pulse)))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.pairing_waiting_phone), style = MaterialTheme.typography.titleMedium, color = Color(0xFFCDEFF7))
    }
}

@Composable
private fun RetryButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = ReceiveMint, contentColor = Color(0xFF05222A)),
        modifier = Modifier.tvFocusBorder(RoundedCornerShape(50))
    ) { Text(stringResource(R.string.pairing_retry), fontWeight = FontWeight.Bold) }
}

@Composable
private fun CloseButton(onClose: () -> Unit) {
    OutlinedButton(
        onClick = onClose,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.5f)),
        modifier = Modifier.padding(top = 8.dp).tvFocusBorder(RoundedCornerShape(50))
    ) { Text(stringResource(R.string.action_cancel)) }
}

/**
 * Picks the device's real LAN address, preferring Ethernet then Wi-Fi. TV boxes in particular can
 * expose several interfaces (docker-style virtual NICs, VPN/tun interfaces from this very app,
 * link-local addresses before DHCP finishes); picking the wrong one produces a QR the sender can
 * never actually reach.
 */
private fun findLocalIpv4Address(): String? = try {
    NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { iface ->
            iface.isUp && !iface.isLoopback && !iface.isVirtual &&
                iface.name.lowercase().let { name ->
                    !name.startsWith("tun") && !name.startsWith("wg") && !name.startsWith("ppp") &&
                        !name.startsWith("rmnet") && !name.startsWith("docker") && !name.startsWith("ap")
                }
        }
        .sortedByDescending { iface ->
            when {
                iface.name.lowercase().startsWith("eth") -> 2
                iface.name.lowercase().startsWith("wlan") -> 1
                else -> 0
            }
        }
        .flatMap { it.inetAddresses.asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
        ?.hostAddress
} catch (e: Exception) {
    null
}

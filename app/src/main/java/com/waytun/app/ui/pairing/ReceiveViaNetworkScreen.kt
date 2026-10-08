package com.waytun.app.ui.pairing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
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

    DisposableEffect(Unit) {
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
                ServerSocket(0)
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

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (val s = state) {
                ReceiveState.Preparing -> CircularProgressIndicator()

                is ReceiveState.WaitingForSender -> {
                    val qrBitmap = remember(s.qrText) { QrCodeGenerator.generate(s.qrText, 600) }
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(280.dp)
                        )
                    }
                    Text(
                        stringResource(R.string.pairing_waiting_for_sender),
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                ReceiveState.TimedOut -> Text(stringResource(R.string.pairing_timeout))
                is ReceiveState.Error -> Text(s.message)
            }
            TextButton(onClick = onClose, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

private fun findLocalIpv4Address(): String? = try {
    NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress }
        ?.hostAddress
} catch (e: Exception) {
    null
}

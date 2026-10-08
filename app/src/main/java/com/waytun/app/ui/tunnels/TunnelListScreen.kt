package com.waytun.app.ui.tunnels

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waytun.app.R
import com.waytun.app.core.pairing.PairingTarget
import com.waytun.app.core.parser.TunnelProtocol
import com.waytun.app.ui.pairing.ReceiveViaNetworkScreen
import com.waytun.app.ui.scanner.QrScannerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TunnelListScreen(
    onTunnelClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: TunnelListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingConnectTunnelId by remember { mutableStateOf<String?>(null) }
    var showNotificationRationale by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TunnelListItem?>(null) }
    var editedImportName by remember(pendingImport) { mutableStateOf(pendingImport?.suggestedName.orEmpty()) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var showReceiveOverlay by remember { mutableStateOf(false) }
    var pendingPairingTarget by remember { mutableStateOf<PairingTarget?>(null) }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is TunnelListEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    val vpnConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val tunnelId = pendingConnectTunnelId
        pendingConnectTunnelId = null
        if (result.resultCode == Activity.RESULT_OK && tunnelId != null) {
            viewModel.onToggleTunnel(tunnelId)
        } else {
            viewModel.onVpnPermissionDenied()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Connecting proceeds either way; this only affects whether the status notice is visible. */ }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showQrScanner = true
        } else {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.camera_permission_denied)) }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) { readTextOrNull(context, uri) }
            if (text.isNullOrBlank()) {
                viewModel.onImportFileReadFailed()
            } else {
                val suggestedName = queryDisplayName(context, uri)
                    ?.removeSuffix(".conf")
                    ?.removeSuffix(".CONF")
                    .orEmpty()
                viewModel.onConfigPicked(suggestedName, text)
            }
        }
    }

    fun beginConnect(tunnelId: String) {
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            pendingConnectTunnelId = tunnelId
            vpnConsentLauncher.launch(prepareIntent)
        } else {
            viewModel.onToggleTunnel(tunnelId)
        }
    }

    fun onToggleRequested(tunnel: TunnelListItem) {
        if (tunnel.isActive) {
            viewModel.onToggleTunnel(tunnel.id)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            showNotificationRationale = true
        }
        beginConnect(tunnel.id)
    }

    if (showNotificationRationale) {
        AlertDialog(
            onDismissRequest = { showNotificationRationale = false },
            title = { Text(stringResource(R.string.notification_permission_rationale_title)) },
            text = { Text(stringResource(R.string.notification_permission_rationale_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationRationale = false
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationRationale = false }) {
                    Text(stringResource(R.string.action_not_now))
                }
            }
        )
    }

    pendingImport?.let { preview ->
        AlertDialog(
            onDismissRequest = { viewModel.onCancelImport() },
            title = { Text(stringResource(R.string.import_preview_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editedImportName,
                        onValueChange = { editedImportName = it },
                        label = { Text(stringResource(R.string.import_preview_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    PreviewRow(stringResource(R.string.import_preview_protocol_label), protocolDisplayName(preview.protocol))
                    PreviewRow(stringResource(R.string.import_preview_address_label), preview.addresses)
                    PreviewRow(stringResource(R.string.import_preview_dns_label), preview.dns)
                    PreviewRow(stringResource(R.string.import_preview_endpoint_label), preview.endpoint)
                    PreviewRow(stringResource(R.string.import_preview_allowed_ips_label), preview.allowedIps)
                    preview.mtu?.let { PreviewRow(stringResource(R.string.import_preview_mtu_label), it) }
                    preview.obfuscation?.let { PreviewRow(stringResource(R.string.import_preview_obfuscation_label), it) }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onConfirmImport(editedImportName) }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onCancelImport() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    pendingDelete?.let { tunnel ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_tunnel_title)) },
            text = { Text(stringResource(R.string.delete_tunnel_message, tunnel.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDeleteTunnel(tunnel.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    pendingPairingTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingPairingTarget = null },
            title = { Text(stringResource(R.string.pairing_select_tunnel_title)) },
            text = {
                Column {
                    uiState.tunnels.forEach { tunnel ->
                        TextButton(
                            onClick = {
                                viewModel.sendTunnelOverNetwork(tunnel.id, target)
                                pendingPairingTarget = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(tunnel.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pendingPairingTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showQrScanner) {
        QrScannerScreen(
            onResult = { text ->
                showQrScanner = false
                val pairing = PairingTarget.parse(text)
                if (pairing != null) {
                    pendingPairingTarget = pairing
                } else {
                    viewModel.onConfigPicked("", text)
                }
            },
            onClose = { showQrScanner = false }
        )
        return
    }

    if (showReceiveOverlay) {
        val defaultName = stringResource(R.string.pairing_default_name)
        ReceiveViaNetworkScreen(
            onReceived = { text ->
                showReceiveOverlay = false
                viewModel.onConfigPicked(defaultName, text)
            },
            onClose = { showReceiveOverlay = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { showImportMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_add_tunnel)
                    )
                }
                DropdownMenu(
                    expanded = showImportMenu,
                    onDismissRequest = { showImportMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_import_file)) },
                        onClick = {
                            showImportMenu = false
                            filePickerLauncher.launch(arrayOf("*/*"))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_scan_qr)) },
                        onClick = {
                            showImportMenu = false
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                showQrScanner = true
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_receive_via_network)) },
                        onClick = {
                            showImportMenu = false
                            showReceiveOverlay = true
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        if (uiState.tunnels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.tunnel_list_empty))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(uiState.tunnels, key = { it.id }) { tunnel ->
                    ListItem(
                        headlineContent = { Text(tunnel.name) },
                        supportingContent = { Text(protocolDisplayName(tunnel.protocol)) },
                        trailingContent = {
                            Switch(
                                checked = tunnel.isActive,
                                enabled = !tunnel.isBusy,
                                onCheckedChange = { onToggleRequested(tunnel) }
                            )
                        },
                        modifier = Modifier.combinedClickable(
                            onClick = { onTunnelClick(tunnel.id) },
                            onLongClick = { pendingDelete = tunnel }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewRow(label: String, value: String) {
    Text("$label: $value")
}

private fun protocolDisplayName(protocol: TunnelProtocol): String = when (protocol) {
    TunnelProtocol.WIREGUARD -> "WireGuard"
    TunnelProtocol.AMNEZIA_WG -> "AmneziaWG"
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

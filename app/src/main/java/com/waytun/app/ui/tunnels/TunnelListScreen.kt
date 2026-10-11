package com.waytun.app.ui.tunnels

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import com.waytun.app.ui.components.ScrollToTopButton
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.waytun.app.ui.components.GlassAlertDialog
import com.waytun.app.ui.components.GlassButton
import com.waytun.app.ui.components.smoothScrollToTop
import com.waytun.app.ui.components.GlassDropdownMenu
import com.waytun.app.ui.components.GlassIconButton
import com.waytun.app.ui.components.GlassScaffold
import com.waytun.app.ui.components.GlassStyle
import com.waytun.app.ui.components.StatusHud
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waytun.app.R
import com.waytun.app.core.pairing.PairingTarget
import com.waytun.app.core.parser.TunnelProtocol
import com.waytun.app.data.subscription.SubscriptionRepository
import com.waytun.app.ui.pairing.ReceiveViaNetworkScreen
import com.waytun.app.ui.scanner.QrScannerScreen
import com.waytun.app.ui.components.WayTunSnackbarHost
import com.waytun.app.ui.components.showWayTun
import com.waytun.app.ui.icons.AppIcons
import com.waytun.app.ui.tv.isTelevision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TunnelListScreen(
    onTunnelClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onSubscriptionSettings: (String) -> Unit,
    viewModel: TunnelListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val speed by viewModel.speed.collectAsStateWithLifecycle()
    val hud by viewModel.hud.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val isSendingTunnel by viewModel.isSendingTunnel.collectAsStateWithLifecycle()
    val isAddingSubscription by viewModel.isAddingSubscription.collectAsStateWithLifecycle()
    val pendingInsecureSubscription by viewModel.pendingInsecureSubscription.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val showScrollTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 600 } }

    var pendingConnectTunnelId by remember { mutableStateOf<String?>(null) }
    var showNotificationRationale by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TunnelListItem?>(null) }
    var editedImportName by remember(pendingImport) { mutableStateOf(pendingImport?.suggestedName.orEmpty()) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var showReceiveOverlay by rememberSaveable { mutableStateOf(false) }
    val isTv = remember { isTelevision(context) }
    var pendingPairingTarget by remember { mutableStateOf<PairingTarget?>(null) }
    // Set while the QR scanner is open for "send this subscription to a TV".
    var sendSubscriptionId by remember { mutableStateOf<String?>(null) }
    var showAddSubscription by remember { mutableStateOf(false) }
    var subscriptionUrl by remember { mutableStateOf("") }
    var pendingSubscriptionDelete by remember { mutableStateOf<SubscriptionHeader?>(null) }
    var pendingSubscriptionRename by remember { mutableStateOf<SubscriptionHeader?>(null) }
    var renameText by remember { mutableStateOf("") }

    // A TV has no keyboard worth the name: when it has nothing yet, go straight to "add from phone".
    LaunchedEffect(isTv) {
        if (isTv) {
            kotlinx.coroutines.delay(1_500)
            if (viewModel.uiState.value.sections.isEmpty()) showReceiveOverlay = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is TunnelListEvent.Message -> snackbarHostState.showWayTun(event.text, event.isError)
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
            scope.launch { snackbarHostState.showWayTun(context.getString(R.string.camera_permission_denied), isError = true) }
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

    fun openAddSubscription() {
        // Pre-fill from the clipboard when it already holds a subscription link.
        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
        val text = clip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
        subscriptionUrl = if (SubscriptionRepository.isSubscriptionUrl(text)) text else ""
        showAddSubscription = true
    }

    if (showNotificationRationale) {
        GlassAlertDialog(
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
        GlassAlertDialog(
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
        GlassAlertDialog(
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
        GlassAlertDialog(
            onDismissRequest = { pendingPairingTarget = null },
            title = { Text(stringResource(R.string.pairing_select_tunnel_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    uiState.sections.mapNotNull { it.subscription }.forEach { subscription ->
                        ListItem(
                            headlineContent = { Text(subscription.name) },
                            supportingContent = { Text(stringResource(R.string.pairing_whole_subscription)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.sendSubscriptionOverNetwork(subscription.id, target)
                                    pendingPairingTarget = null
                                }
                        )
                    }
                    uiState.tunnels.filter { tunnel -> uiState.sections.none { s -> s.subscription != null && s.tunnels.any { it.id == tunnel.id } } }.forEach { tunnel ->
                        ListItem(
                            headlineContent = { Text(tunnel.name) },
                            supportingContent = { Text(protocolDisplayName(tunnel.protocol)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.sendTunnelOverNetwork(tunnel.id, target)
                                    pendingPairingTarget = null
                                }
                        )
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

    if (showAddSubscription) {
        GlassAlertDialog(
            onDismissRequest = { showAddSubscription = false },
            title = { Text(stringResource(R.string.action_add_subscription)) },
            text = {
                OutlinedTextField(
                    value = subscriptionUrl,
                    onValueChange = { subscriptionUrl = it },
                    label = { Text(stringResource(R.string.subscription_url_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAddSubscription = false
                    viewModel.onAddSubscription(subscriptionUrl)
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddSubscription = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (pendingInsecureSubscription != null) {
        GlassAlertDialog(
            onDismissRequest = { viewModel.onDismissInsecureSubscription() },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text(stringResource(R.string.subscription_insecure_title)) },
            text = { Text(stringResource(R.string.subscription_insecure_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onConfirmInsecureSubscription() }) {
                    Text(stringResource(R.string.action_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDismissInsecureSubscription() }) {
                    Text(stringResource(R.string.action_no))
                }
            }
        )
    }

    pendingSubscriptionRename?.let { subscription ->
        GlassAlertDialog(
            onDismissRequest = { pendingSubscriptionRename = null },
            title = { Text(stringResource(R.string.subscription_rename)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onRenameSubscription(subscription.id, renameText.trim())
                    pendingSubscriptionRename = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingSubscriptionRename = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    pendingSubscriptionDelete?.let { subscription ->
        GlassAlertDialog(
            onDismissRequest = { pendingSubscriptionDelete = null },
            title = { Text(stringResource(R.string.delete_subscription_title)) },
            text = { Text(stringResource(R.string.delete_subscription_message, subscription.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDeleteSubscription(subscription.id)
                    pendingSubscriptionDelete = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingSubscriptionDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (isSendingTunnel || isAddingSubscription) {
        GlassAlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator()
                    Text(
                        stringResource(if (isAddingSubscription) R.string.subscription_loading else R.string.pairing_sending),
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        )
    }

    BackHandler(enabled = showQrScanner) { showQrScanner = false; sendSubscriptionId = null }
    BackHandler(enabled = showReceiveOverlay) { showReceiveOverlay = false }

    if (showQrScanner) {
        QrScannerScreen(
            onResult = { text ->
                showQrScanner = false
                val pairing = PairingTarget.parse(text)
                val subscriptionToSend = sendSubscriptionId
                sendSubscriptionId = null
                when {
                    pairing != null && subscriptionToSend != null -> viewModel.sendSubscriptionOverNetwork(subscriptionToSend, pairing)
                    pairing != null -> pendingPairingTarget = pairing
                    SubscriptionRepository.isSubscriptionUrl(text) -> viewModel.onAddSubscription(text)
                    else -> viewModel.onTextImported("", text)
                }
            },
            onClose = { showQrScanner = false; sendSubscriptionId = null }
        )
        return
    }

    if (showReceiveOverlay) {
        val defaultName = stringResource(R.string.pairing_default_name)
        ReceiveViaNetworkScreen(
            onReceived = { text ->
                showReceiveOverlay = false
                if (SubscriptionRepository.isSubscriptionUrl(text)) viewModel.onAddSubscription(text.trim())
                else viewModel.onConfigPicked(defaultName, text)
            },
            onClose = { showReceiveOverlay = false }
        )
        return
    }

    fun onPowerRequested() {
        val hero = uiState.hero
        if (hero.phase != HeroPhase.IDLE) {
            viewModel.onPowerClick()
            return
        }
        val selected = uiState.tunnels.firstOrNull { it.isSelected } ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            showNotificationRationale = true
        }
        beginConnect(selected.id)
    }

    val linkCopied = stringResource(R.string.subscription_link_copied)

    Box(Modifier.fillMaxSize()) {
    GlassScaffold(
        title = {
            SparkleTitle(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                onLongPress = { showReceiveOverlay = true }
            )
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScrollToTopButton(visible = showScrollTop, onClick = { scope.launch { listState.smoothScrollToTop() } })
            Box {
                GlassIconButton(
                    onClick = { showImportMenu = true },
                    accent = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_add_tunnel)
                    )
                }
                GlassDropdownMenu(
                    expanded = showImportMenu,
                    onDismissRequest = { showImportMenu = false }
                ) {
                    if (isTv) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.pairing_receive_title)) },
                            leadingIcon = { Icon(AppIcons.Tv, null, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                showImportMenu = false
                                showReceiveOverlay = true
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_paste_clipboard)) },
                        onClick = {
                            showImportMenu = false
                            val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                            val text = clip?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)?.coerceToText(context)?.toString()
                            viewModel.onTextImported("", text.orEmpty())
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_add_subscription)) },
                        onClick = {
                            showImportMenu = false
                            openAddSubscription()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_import_file)) },
                        onClick = {
                            showImportMenu = false
                            filePickerLauncher.launch(arrayOf("*/*"))
                        }
                    )
                    if (!isTv) DropdownMenuItem(
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
                }
            }
            }
        }
    ) { innerPadding ->
        WayTunSnackbarHost(snackbarHostState)
        // On wide screens (tablets, TVs, landscape) the content stays a readable column in the middle.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxHeight(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "hero") {
                HeroCard(hero = uiState.hero, speed = speed, onPower = ::onPowerRequested, onSpeedTest = viewModel::onSpeedTest)
            }
            if (uiState.sections.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        onAddSubscription = ::openAddSubscription,
                        onPaste = {
                            val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                            val text = clip?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)?.coerceToText(context)?.toString()
                            viewModel.onTextImported("", text.orEmpty())
                        }
                    )
                }
            }
            uiState.sections.forEach { section ->
                val subscription = section.subscription
                if (subscription == null) {
                    items(section.tunnels, key = { it.id }) { tunnel ->
                        TunnelRow(
                            tunnel = tunnel,
                            onSelect = { viewModel.onSelectTunnel(tunnel.id) },
                            onPing = { viewModel.pingAll(listOf(tunnel.id)) },
                            onInfo = { onTunnelClick(tunnel.id) },
                            onLongClick = { pendingDelete = tunnel },
                            modifier = Modifier.animateItem()
                        )
                    }
                } else {
                    item(key = "subscription-${subscription.id}") {
                        SubscriptionCard(
                            subscription = subscription,
                            tunnelCount = section.tunnels.size,
                            onToggleCollapsed = { viewModel.onToggleSubscriptionCollapsed(subscription.id) },
                            onRefresh = { viewModel.onRefreshSubscription(subscription.id) },
                            onPingAll = { viewModel.pingAll(section.tunnels.map { it.id }) },
                            onSendToTv = if (isTv) null else {
                                {
                                sendSubscriptionId = subscription.id
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    showQrScanner = true
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                                }
                            },
                            onOpenSettings = { onSubscriptionSettings(subscription.id) },
                            onTogglePin = { viewModel.onTogglePinned(subscription.id) },
                            onRename = {
                                renameText = subscription.name
                                pendingSubscriptionRename = subscription
                            },
                            onCopyLink = {
                                scope.launch {
                                    viewModel.subscriptionUrl(subscription.id)?.let { url ->
                                        context.getSystemService(ClipboardManager::class.java)
                                            ?.setPrimaryClip(ClipData.newPlainText("subscription", url))
                                        snackbarHostState.showWayTun(linkCopied)
                                    }
                                }
                            },
                            onDelete = { pendingSubscriptionDelete = subscription },
                            onOpenUrl = { url ->
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            },
                            modifier = Modifier.animateItem()
                        ) {
                            StaggeredRows(section.tunnels) { tunnel ->
                                TunnelRow(
                                    tunnel = tunnel,
                                    onSelect = { viewModel.onSelectTunnel(tunnel.id) },
                                    onPing = { viewModel.pingAll(listOf(tunnel.id)) },
                                    onInfo = { onTunnelClick(tunnel.id) },
                                    onLongClick = { pendingDelete = tunnel },
                                    style = GlassStyle.Inset
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
        StatusHud(hud)
    }
}

/**
 * Shows the first few rows at once and brings the rest in a few per frame. Composing a long subscription in one go made
 * the first frame after opening the screen (or coming back to it) stall; the rows below the fold appear a moment
 * later without anyone noticing, and the animation stays smooth.
 */
@Composable
private fun StaggeredRows(tunnels: List<TunnelListItem>, row: @Composable (TunnelListItem) -> Unit) {
    var shown by remember { mutableIntStateOf(FIRST_ROWS) }
    LaunchedEffect(tunnels.size) {
        while (shown < tunnels.size) {
            withFrameNanos { }
            shown = (shown + ROWS_PER_FRAME).coerceAtMost(tunnels.size)
        }
    }
    tunnels.take(shown.coerceAtMost(tunnels.size)).forEach { row(it) }
}

private const val FIRST_ROWS = 4
private const val ROWS_PER_FRAME = 3

@Composable
private fun EmptyState(onAddSubscription: () -> Unit, onPaste: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 8.dp)
    ) {
        Text(
            stringResource(R.string.empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Text(
            stringResource(R.string.empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )
        GlassButton(onClick = onAddSubscription, primary = true, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_add_subscription))
        }
        GlassButton(onClick = onPaste, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(stringResource(R.string.action_paste_clipboard))
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
    TunnelProtocol.VLESS -> "VLESS"
    TunnelProtocol.VMESS -> "VMess"
    TunnelProtocol.TROJAN -> "Trojan"
    TunnelProtocol.SHADOWSOCKS -> "Shadowsocks"
    TunnelProtocol.HYSTERIA2 -> "Hysteria2"
    TunnelProtocol.HYSTERIA -> "Hysteria"
    TunnelProtocol.TUIC -> "TUIC"
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

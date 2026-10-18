package com.waytun.app.ui.tunnels

import com.waytun.app.res.Res
import com.waytun.app.res.action_back
import com.waytun.app.res.action_cancel
import com.waytun.app.res.action_copy
import com.waytun.app.res.action_delete
import com.waytun.app.res.action_save
import com.waytun.app.res.action_show_qr
import com.waytun.app.res.app_name
import com.waytun.app.res.config_copied
import com.waytun.app.res.delete_tunnel_message
import com.waytun.app.res.delete_tunnel_title
import com.waytun.app.res.detail_label_config
import com.waytun.app.res.detail_label_name
import com.waytun.app.res.detail_load_failed
import com.waytun.app.res.json_copied
import com.waytun.app.res.qr_too_large
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import com.waytun.app.ui.components.GlassAlertDialog
import com.waytun.app.ui.components.GlassIconButton
import com.waytun.app.ui.components.JsonLinesCard
import com.waytun.app.ui.components.GlassScaffold
import com.waytun.app.ui.components.GlassTextField
import com.waytun.app.ui.icons.AppIcons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.waytun.app.ui.components.WayTunSnackbarHost
import com.waytun.app.ui.components.showWayTun
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.waytun.app.ui.AppContainer
import com.waytun.app.ui.LocalAppContainer
import com.waytun.app.ui.resolve
import com.waytun.app.ui.platform.PlatformBackHandler
import com.waytun.app.ui.platform.qrCodeImage
import com.waytun.app.ui.platform.rememberClipboardWriter
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunnelDetailScreen(
    tunnelId: String,
    onBack: () -> Unit,
    container: AppContainer = LocalAppContainer.current,
    viewModel: TunnelDetailViewModel = viewModel(key = tunnelId) { TunnelDetailViewModel(tunnelId, container) }
) {
    // A back gesture closes the screen exactly like the arrow does (same slide), not with the system's shrinking preview.
    PlatformBackHandler(onBack = onBack)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val pageScroll = rememberScrollState()
    val copyToClipboard = rememberClipboardWriter()
    val scope = rememberCoroutineScope()
    val jsonCopied = stringResource(Res.string.json_copied)
    val configCopied = stringResource(Res.string.config_copied)
    val isJson = uiState.configText.trimStart().startsWith("{")
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is TunnelDetailEvent.Message -> snackbarHostState.showWayTun(event.text.resolve(), event.isError)
                TunnelDetailEvent.Saved -> onBack()
                TunnelDetailEvent.Deleted -> onBack()
            }
        }
    }

    if (showDeleteConfirm) {
        GlassAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(Res.string.delete_tunnel_title)) },
            text = { Text(stringResource(Res.string.delete_tunnel_message, uiState.name)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.onDelete()
                }) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }

    if (showQrDialog) {
        val qrBitmap = remember(uiState.configText) { qrCodeImage(uiState.configText) }
        GlassAlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = { Text(uiState.name) },
            text = {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap,
                        contentDescription = uiState.name,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(stringResource(Res.string.qr_too_large))
                }
            },
            confirmButton = {
                TextButton(onClick = { showQrDialog = false }) { Text(stringResource(Res.string.action_back)) }
            }
        )
    }

    GlassScaffold(
        title = { Text(uiState.name.ifBlank { stringResource(Res.string.app_name) }, maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
            }
        },
        actions = {
            // A subscription's JSON key has no use for a QR (it is far too big and the panel owns it) or for a
            // delete button (the next update brings it back), so both only appear for hand-imported configs.
            if (!isJson && !uiState.isLoading && !uiState.loadFailed) {
                IconButton(onClick = {
                    copyToClipboard(uiState.configText)
                    scope.launch { snackbarHostState.showWayTun(configCopied) }
                }) {
                    Icon(AppIcons.ContentCopy, contentDescription = stringResource(Res.string.action_copy))
                }
                IconButton(onClick = { showQrDialog = true }) {
                    Icon(
                        AppIcons.QrCode,
                        contentDescription = stringResource(Res.string.action_show_qr)
                    )
                }
            }
            if (!isJson) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.action_delete))
                }
            }
        },
        floatingActionButton = {
            if (!uiState.isLoading && !uiState.loadFailed) {
                GlassIconButton(
                    onClick = { viewModel.onSave() },
                    size = 56.dp,
                    accent = MaterialTheme.colorScheme.primary
                ) { Icon(Icons.Default.Check, contentDescription = stringResource(Res.string.action_save)) }
            }
        }
    ) { innerPadding ->
        WayTunSnackbarHost(snackbarHostState)
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            uiState.loadFailed -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { Text(stringResource(Res.string.detail_load_failed)) }

            isJson -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 16.dp, end = 16.dp,
                        top = innerPadding.calculateTopPadding() + 12.dp,
                        bottom = innerPadding.calculateBottomPadding() + 84.dp
                    )
            ) {
                GlassTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onNameChanged,
                    label = stringResource(Res.string.detail_label_name),
                    singleLine = true
                )
                // A panel-provided Xray config as readable colored JSON; the card fills the rest of the screen.
                JsonLinesCard(
                    lines = uiState.jsonLines,
                    onCopy = {
                        copyToClipboard(uiState.jsonLines.joinToString("\n"))
                        scope.launch { snackbarHostState.showWayTun(jsonCopied) }
                    },
                    modifier = Modifier.padding(top = 14.dp).weight(1f)
                )
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(pageScroll)
                    .padding(
                        start = 16.dp, end = 16.dp,
                        top = innerPadding.calculateTopPadding() + 12.dp,
                        bottom = innerPadding.calculateBottomPadding() + 16.dp
                    )
            ) {
                GlassTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onNameChanged,
                    label = stringResource(Res.string.detail_label_name),
                    singleLine = true
                )
                GlassTextField(
                    value = uiState.configText,
                    onValueChange = viewModel::onConfigTextChanged,
                    label = stringResource(Res.string.detail_label_config),
                    minLines = 8,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

package com.waytun.app.ui.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waytun.app.R
import com.waytun.app.core.log.AppLog
import com.waytun.app.core.log.LogFileInfo
import com.waytun.app.ui.components.GlassCard
import com.waytun.app.ui.components.GlassScaffold
import com.waytun.app.ui.components.WayTunSnackbarHost
import com.waytun.app.ui.components.showWayTun
import com.waytun.app.ui.icons.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The journal screen: the list of logs, and one log opened for reading, copying or clearing. */
@Composable
fun LogsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val files by produceState(emptyList<LogFileInfo>(), reload, open) {
        value = withContext(Dispatchers.IO) { AppLog.files(context) }
    }
    val snackbar = remember { SnackbarHostState() }
    val copiedText = stringResource(R.string.logs_copied)

    // Back closes an open log first, then the screen - the arrow and the gesture behave the same.
    val goBack = { if (open != null) open = null else onBack() }
    BackHandler(onBack = goBack)

    val text by produceState("", open, reload) {
        val name = open
        value = if (name == null) "" else withContext(Dispatchers.IO) { AppLog.read(context, name) }
    }

    GlassScaffold(
        title = { Text(open?.let { titleOf(it) } ?: stringResource(R.string.logs_title), maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = goBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        },
        actions = {
            if (open != null) {
                IconButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("log", text))
                    scope.launch { snackbar.showWayTun(copiedText) }
                }) { Icon(AppIcons.ContentCopy, contentDescription = stringResource(R.string.action_copy)) }
                IconButton(onClick = {
                    open?.let { name ->
                        scope.launch {
                            withContext(Dispatchers.IO) { AppLog.clear(context, name) }
                            reload++
                        }
                    }
                }) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.logs_clear)) }
            }
        }
    ) { padding ->
        WayTunSnackbarHost(snackbar)
        AnimatedContent(
            targetState = open,
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(220))) togetherWith fadeOut(tween(140))
                } else {
                    fadeIn(tween(220)) togetherWith (slideOutHorizontally(tween(260)) { it / 4 } + fadeOut(tween(160)))
                }
            },
            label = "logs"
        ) { name ->
            if (name == null) {
                LogList(files, padding.calculateTopPadding(), padding.calculateBottomPadding()) { open = it }
            } else {
                LogViewer(text, padding.calculateTopPadding(), padding.calculateBottomPadding())
            }
        }
    }
}

@Composable
private fun titleOf(name: String): String = stringResource(
    when (name) {
        AppLog.SUBSCRIPTION -> R.string.logs_subscription
        AppLog.CONNECTION -> R.string.logs_connection
        else -> R.string.logs_xray_debug
    }
)

@Composable
private fun LogList(files: List<LogFileInfo>, top: androidx.compose.ui.unit.Dp, bottom: androidx.compose.ui.unit.Dp, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = top + 8.dp, bottom = bottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            files.forEach { file ->
                GlassCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(file.name) }.padding(horizontal = 18.dp, vertical = 16.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(titleOf(file.name), style = MaterialTheme.typography.titleMedium)
                            Text(
                                Formatter.formatShortFileSize(context, file.bytes) + if (file.modifiedMillis > 0) " · " +
                                    DateUtils.getRelativeTimeSpanString(file.modifiedMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS) else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.graphicsLayer { rotationZ = -90f }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogViewer(text: String, top: androidx.compose.ui.unit.Dp, bottom: androidx.compose.ui.unit.Dp) {
    val scroll = rememberScrollState()
    // A journal is read from its end, where the newest lines are.
    LaunchedEffect(text) { scroll.scrollTo(Int.MAX_VALUE) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxHeight()
                .verticalScroll(scroll)
                .padding(start = 16.dp, end = 16.dp, top = top + 8.dp, bottom = bottom + 24.dp)
        ) {
            GlassCard(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                if (text.isBlank()) {
                    Text(
                        stringResource(R.string.logs_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                } else {
                    SelectionContainer {
                        Text(
                            text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
            Box(Modifier.height(8.dp))
        }
    }
}

package com.waytun.app.ui.settings

import com.waytun.app.res.Res
import com.waytun.app.res.action_back
import com.waytun.app.res.detail_label_name
import com.waytun.app.res.sub_auto
import com.waytun.app.res.sub_auto_hint
import com.waytun.app.res.sub_auto_hint_hours
import com.waytun.app.res.sub_client_id
import com.waytun.app.res.sub_device_info
import com.waytun.app.res.sub_device_info_hint
import com.waytun.app.res.sub_on_launch
import com.waytun.app.res.sub_on_launch_hint
import com.waytun.app.res.sub_order_alpha
import com.waytun.app.res.sub_order_panel
import com.waytun.app.res.sub_order_ping
import com.waytun.app.res.sub_ping_launch
import com.waytun.app.res.sub_seconds
import com.waytun.app.res.sub_section_network
import com.waytun.app.res.sub_section_order
import com.waytun.app.res.sub_section_privacy
import com.waytun.app.res.sub_section_sync
import com.waytun.app.res.sub_settings_title
import com.waytun.app.res.sub_wait
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.waytun.app.ui.LocalAppContainer
import com.waytun.app.ui.platform.PlatformBackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waytun.app.ui.components.GlassButton
import com.waytun.app.ui.components.GlassChip
import com.waytun.app.ui.components.GlassScaffold
import com.waytun.app.ui.components.GlassSegmented
import com.waytun.app.ui.components.GlassTextField
import com.waytun.app.ui.theme.Indigo
import kotlinx.coroutines.delay

private val WaitChoices = listOf(5, 10, 15)

/** Per-subscription options: how it syncs, what it tells the panel, how long to wait and how its keys are ordered. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubscriptionSettingsScreen(
    subscriptionId: String,
    onBack: () -> Unit,
    container: com.waytun.app.ui.AppContainer = LocalAppContainer.current,
    viewModel: SubscriptionSettingsViewModel = viewModel(key = subscriptionId) {
        SubscriptionSettingsViewModel(subscriptionId, container.subscriptionRepository)
    }
) {
    // A back gesture closes the screen exactly like the arrow does (same slide), not with the system's shrinking preview.
    PlatformBackHandler(onBack = onBack)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val defaultAgent = container.platform.defaultUserAgent
    val scroll = rememberScrollState()

    GlassScaffold(
        title = { Text(stringResource(Res.string.sub_settings_title), maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
            }
        }
    ) { padding ->
        val current = state ?: return@GlassScaffold
        val settings = current.settings
        // Text fields edit a local copy and save after a short pause, so typing is never fought by the database echo.
        var name by remember(current.id) { mutableStateOf(current.name) }
        LaunchedEffect(name) {
            if (name != current.name) {
                delay(450)
                viewModel.rename(name)
            }
        }
        // Empty means "the default", which is shown dimmed in the field: it can't be erased, only typed over.
        var agent by remember(current.id) { mutableStateOf(settings.userAgent ?: "") }
        LaunchedEffect(agent) {
            if (agent.trim() != (settings.userAgent ?: "")) {
                delay(450)
                viewModel.update { it.copy(userAgent = agent.trim().ifEmpty { null }) }
            }
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxHeight()
                    .verticalScroll(scroll)
                    .padding(
                        start = 16.dp, end = 16.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 24.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                GlassTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(Res.string.detail_label_name),
                    singleLine = true
                )

                SettingsCard(stringResource(Res.string.sub_section_sync), Indigo) {
                    val intervalHours = current.panelIntervalHours ?: 1
                    val autoHint = if (intervalHours == 1) {
                        stringResource(Res.string.sub_auto_hint)
                    } else {
                        stringResource(Res.string.sub_auto_hint_hours, intervalHours)
                    }
                    SwitchRow(stringResource(Res.string.sub_auto), autoHint, settings.autoUpdate, Indigo) { v ->
                        viewModel.update { it.copy(autoUpdate = v) }
                    }
                    SwitchRow(stringResource(Res.string.sub_on_launch), stringResource(Res.string.sub_on_launch_hint), settings.updateOnOpen, Indigo) { v ->
                        viewModel.update { it.copy(updateOnOpen = v) }
                    }
                    SwitchRow(stringResource(Res.string.sub_ping_launch), null, settings.pingOnOpen, Indigo) { v ->
                        viewModel.update { it.copy(pingOnOpen = v) }
                    }
                }

                SettingsCard(stringResource(Res.string.sub_section_privacy), Indigo) {
                    SwitchRow(stringResource(Res.string.sub_device_info), stringResource(Res.string.sub_device_info_hint), settings.sendHwid, Indigo) { v ->
                        viewModel.update { it.copy(sendHwid = v) }
                    }
                }

                SettingsCard(stringResource(Res.string.sub_section_network), Indigo) {
                    Text(
                        stringResource(Res.string.sub_wait),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )
                    GlassSegmented(
                        labels = WaitChoices.map { stringResource(Res.string.sub_seconds, it) },
                        selected = WaitChoices.indices.minBy { kotlin.math.abs(WaitChoices[it] - settings.timeoutSeconds) },
                        onSelect = { index -> viewModel.update { it.copy(timeoutSeconds = WaitChoices[index]) } }
                    )
                }

                SettingsCard(stringResource(Res.string.sub_client_id), Indigo) {
                    GlassTextField(
                        value = agent,
                        onValueChange = { agent = it },
                        label = null,
                        placeholder = defaultAgent,
                        singleLine = true
                    )
                }

                SettingsCard(stringResource(Res.string.sub_section_order), Indigo) {
                    GlassSegmented(
                        labels = listOf(
                            stringResource(Res.string.sub_order_panel),
                            stringResource(Res.string.sub_order_ping),
                            stringResource(Res.string.sub_order_alpha)
                        ),
                        selected = settings.sortMode.coerceIn(0, 2),
                        onSelect = { mode -> viewModel.update { it.copy(sortMode = mode) } }
                    )
                }
            }
        }
    }
}

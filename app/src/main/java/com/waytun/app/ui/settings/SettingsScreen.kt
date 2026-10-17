package com.waytun.app.ui.settings

import android.app.Activity
import android.os.Build
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import com.waytun.app.ui.components.ScrollToTopButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import com.waytun.app.ui.components.GlassButton
import com.waytun.app.ui.components.GlassCard
import com.waytun.app.ui.components.GlassSegmented
import com.waytun.app.ui.components.GlassStyle
import com.waytun.app.ui.components.glass
import com.waytun.app.ui.components.smoothScrollToTop
import com.waytun.app.ui.components.GlassDropdownMenu
import com.waytun.app.ui.components.GlassScaffold
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waytun.app.R
import com.waytun.app.core.device.DeviceIdentity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import com.waytun.app.ui.theme.InterfaceStyle
import com.waytun.app.ui.theme.InterfaceStyleStore
import com.waytun.app.ui.theme.AppThemeMode
import com.waytun.app.ui.theme.AppThemeStore
import com.waytun.app.core.language.AppLanguage
import com.waytun.app.core.obfuscation.ObfuscationLevel
import com.waytun.app.core.obfuscation.ObfuscationProfile
import com.waytun.app.core.obfuscation.ObfuscationSettings
import com.waytun.app.ui.components.WayTunSnackbarHost
import com.waytun.app.ui.components.showWayTun
import com.waytun.app.ui.theme.Amber
import com.waytun.app.ui.theme.Cyan
import com.waytun.app.ui.theme.Indigo
import com.waytun.app.ui.theme.Lime
import com.waytun.app.ui.theme.Mint
import com.waytun.app.ui.theme.Rose
import com.waytun.app.ui.theme.Sky

/** A titled card with a colored accent dot, so the sections are told apart at a glance. */
@Composable
internal fun SettingsCard(title: String, accent: Color, content: @Composable () -> Unit) {
    GlassCard(
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
internal fun SwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    accent: Color,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                uncheckedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
private fun InfoRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLogs: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    // A back gesture closes the screen exactly like the arrow does (same slide), not with the system's shrinking preview.
    androidx.activity.compose.BackHandler(onBack = onBack)
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val obfuscation by viewModel.obfuscationSettings.collectAsStateWithLifecycle()
    val profile by viewModel.obfuscationProfile.collectAsStateWithLifecycle()
    val activity = LocalContext.current as Activity
    var installRequested by remember(updateState) { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf(AppLanguage.current(activity)) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val listState = rememberLazyListState()
    val showScrollTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 500 } }

    GlassScaffold(
        title = { Text(stringResource(R.string.settings_title)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        },
        floatingActionButton = {
            ScrollToTopButton(visible = showScrollTop, onClick = { scope.launch { listState.smoothScrollToTop() } })
        }
    ) { innerPadding ->
        WayTunSnackbarHost(snackbarHostState)
        // On wide screens (tablets, landscape) the cards stay a readable column in the middle.
        // A lazy list: only the cards on screen are composed, so the screen opens without a long first frame.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxHeight(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "theme") {
                val themeMode by AppThemeStore.mode.collectAsStateWithLifecycle()
                SettingsCard(stringResource(R.string.settings_section_theme), Sky) {
                    GlassSegmented(
                        labels = listOf(stringResource(R.string.theme_light), stringResource(R.string.theme_dark)),
                        selected = if (themeMode == AppThemeMode.DARK) 1 else 0,
                        onSelect = { index -> AppThemeStore.set(if (index == 1) AppThemeMode.DARK else AppThemeMode.LIGHT) }
                    )
                }
            }

            item(key = "interface") {
                val interfaceStyle by InterfaceStyleStore.style.collectAsStateWithLifecycle()
                SettingsCard(stringResource(R.string.settings_section_interface), Sky) {
                    GlassSegmented(
                        labels = listOf(stringResource(R.string.interface_plain), stringResource(R.string.interface_glass)),
                        selected = if (interfaceStyle == InterfaceStyle.GLASS) 1 else 0,
                        onSelect = { index -> InterfaceStyleStore.set(if (index == 1) InterfaceStyle.GLASS else InterfaceStyle.PLAIN) }
                    )
                    Text(
                        stringResource(R.string.interface_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            item(key = "obfuscation") {
                ObfuscationSection(
                    settings = obfuscation,
                    profile = profile,
                    onUpdate = viewModel::updateObfuscation,
                    onRegenerate = viewModel::regenerateObfuscation
                )
            }

            item(key = "network") {
            SettingsCard(stringResource(R.string.settings_section_network), Amber) {
                SwitchRow(
                    stringResource(R.string.net_block_quic),
                    stringResource(R.string.net_block_quic_desc),
                    obfuscation.blockQuic,
                    Amber
                ) { v -> viewModel.updateObfuscation { it.copy(blockQuic = v) } }
            }
            }

            item(key = "language") {
            SettingsCard(stringResource(R.string.settings_section_language), Sky) {
                val tags = listOf(AppLanguage.AUTO, "en", "ru")
                GlassSegmented(
                    labels = listOf(
                        stringResource(R.string.settings_language_auto),
                        stringResource(R.string.settings_language_en),
                        stringResource(R.string.settings_language_ru)
                    ),
                    selected = tags.indexOf(selectedLanguage).coerceAtLeast(0),
                    onSelect = { index ->
                        selectedLanguage = tags[index]
                        AppLanguage.set(activity, tags[index])
                    }
                )
            }
            }

            item(key = "about") {
            SettingsCard(stringResource(R.string.settings_section_about), Rose) {
                InfoRow(
                    stringResource(R.string.app_name),
                    stringResource(R.string.settings_app_version, viewModel.appVersionName) + " · " +
                        stringResource(if (viewModel.isBetaBuild) R.string.settings_build_beta else R.string.settings_build_release)
                )
                InfoRow(stringResource(R.string.settings_os_label), "Android ${Build.VERSION.RELEASE}")
                val hwid = remember { DeviceIdentity.hwid(activity) }
                InfoRow(stringResource(R.string.settings_hwid), hwid)
                InfoRow(stringResource(R.string.settings_developer), stringResource(R.string.settings_developer_value))
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    stringResource(R.string.settings_section_updates),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                UpdateSection(updateState, installRequested, { installRequested = true }, viewModel)
            }
            }
            item(key = "diagnostics") {
            SettingsCard(stringResource(R.string.settings_section_diagnostics), Sky) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenLogs)
                        .padding(vertical = 10.dp)
                ) {
                    Text(stringResource(R.string.settings_logs_open), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ObfuscationSection(
    settings: ObfuscationSettings,
    profile: ObfuscationProfile,
    onUpdate: ((ObfuscationSettings) -> ObfuscationSettings) -> Unit,
    onRegenerate: () -> Unit
) {
    val on = settings.enabled
    SettingsCard(stringResource(R.string.settings_section_obfuscation), Indigo) {
        Text(
            stringResource(R.string.obf_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        SwitchRow(stringResource(R.string.obf_enable), null, on, Indigo) { v -> onUpdate { it.copy(enabled = v) } }

        AnimatedVisibility(
            visible = on,
            enter = expandVertically(tween(320)) + fadeIn(tween(320)),
            exit = shrinkVertically(tween(260)) + fadeOut(tween(200))
        ) {
          Column {
            Text(
                stringResource(R.string.obf_level),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
            )
            val levels = listOf(
                ObfuscationLevel.LIGHT to R.string.obf_level_light,
                ObfuscationLevel.MEDIUM to R.string.obf_level_medium,
                ObfuscationLevel.STRONG to R.string.obf_level_strong
            )
            GlassSegmented(
                labels = levels.map { stringResource(it.second) },
                selected = levels.indexOfFirst { it.first == settings.level }.coerceAtLeast(0),
                onSelect = { index -> onUpdate { it.copy(level = levels[index].first) } }
            )
            Text(
                stringResource(
                    when (settings.level) {
                        ObfuscationLevel.LIGHT -> R.string.obf_level_light_desc
                        ObfuscationLevel.MEDIUM -> R.string.obf_level_medium_desc
                        ObfuscationLevel.STRONG -> R.string.obf_level_strong_desc
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            SectionLabel(stringResource(R.string.obf_methods))
            SwitchRow(stringResource(R.string.obf_fingerprint), stringResource(R.string.obf_fingerprint_desc), settings.fingerprintRotation, Rose) { v -> onUpdate { it.copy(fingerprintRotation = v) } }
            SwitchRow(stringResource(R.string.obf_headers), stringResource(R.string.obf_headers_desc), settings.headerRotation, Sky) { v -> onUpdate { it.copy(headerRotation = v) } }
            SwitchRow(stringResource(R.string.obf_wg_junk), stringResource(R.string.obf_wg_junk_desc), settings.wireguardJunk, Lime) { v -> onUpdate { it.copy(wireguardJunk = v) } }

            SectionLabel(stringResource(R.string.obf_schedule))
            SwitchRow(stringResource(R.string.obf_auto_rotate), stringResource(R.string.obf_auto_rotate_desc), settings.autoRotate, Mint) { v -> onUpdate { it.copy(autoRotate = v) } }
            SwitchRow(stringResource(R.string.obf_auto_reconnect), stringResource(R.string.obf_auto_reconnect_desc), settings.autoReconnect, Mint) { v -> onUpdate { it.copy(autoReconnect = v) } }
            SwitchRow(
                stringResource(R.string.obf_apply_live), stringResource(R.string.obf_apply_live_desc),
                settings.applyRotationLive, Amber, enabled = settings.autoRotate
            ) { v -> onUpdate { it.copy(applyRotationLive = v) } }

            SectionLabel(stringResource(R.string.obf_current))
            val generated = DateUtils.getRelativeTimeSpanString(
                profile.generatedAtMillis, System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS
            ).toString()
            Text(
                stringResource(R.string.obf_generated, generated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                val onText = stringResource(R.string.obf_value_on)
                val offText = stringResource(R.string.obf_value_off)
                ValueChip(stringResource(R.string.obf_value_fingerprint, profile.fingerprint ?: offText), Rose)
                val junkCount = profile.awgJunkCount
                val junkMin = profile.awgJunkMin
                val junkMax = profile.awgJunkMax
                if (junkCount != null && junkMin != null && junkMax != null) {
                    ValueChip(stringResource(R.string.obf_value_junk, junkCount, junkMin, junkMax), Lime)
                }
                profile.userAgent?.let { ValueChip("UA: " + it.substringAfter("(").substringBefore(")").take(26), Sky) }
                if (profile.awgDecoyPacket != null) ValueChip("I1 $onText", Indigo)
            }
            GlassButton(onClick = onRegenerate, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(stringResource(R.string.obf_regenerate))
            }
          }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)
    )
}

@Composable
private fun ValueChip(text: String, @Suppress("UNUSED_PARAMETER") color: Color) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(scheme.onSurface.copy(alpha = 0.07f))
            .border(0.5.dp, scheme.onSurface.copy(alpha = 0.10f), RoundedCornerShape(50))
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun UpdateSection(
    state: UpdateUiState,
    installRequested: Boolean,
    onInstallRequested: () -> Unit,
    viewModel: SettingsViewModel
) {
    when (state) {
        is UpdateUiState.Idle, is UpdateUiState.Error, is UpdateUiState.UpToDate -> {
            Column {
                if (state is UpdateUiState.UpToDate) Text(stringResource(R.string.update_up_to_date), color = MaterialTheme.colorScheme.tertiary)
                if (state is UpdateUiState.Error) Text(state.message, color = MaterialTheme.colorScheme.error)
                GlassButton(onClick = { viewModel.checkForUpdates() }, primary = true, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.settings_check_updates))
                }
            }
        }

        UpdateUiState.Checking -> Column {
            CircularProgressIndicator()
            Text(stringResource(R.string.update_checking), modifier = Modifier.padding(top = 8.dp))
        }

        is UpdateUiState.Available -> Column {
            Text(stringResource(R.string.update_available_title, state.version), fontWeight = FontWeight.SemiBold)
            if (state.notes.isNotBlank()) Text(state.notes, modifier = Modifier.padding(top = 8.dp))
            GlassButton(onClick = { viewModel.downloadAndPrepareInstall() }, primary = true, modifier = Modifier.padding(top = 12.dp)) {
                Text(stringResource(R.string.update_download_install))
            }
            GlassButton(onClick = { viewModel.dismissUpdateDialog() }, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.action_cancel))
            }
        }

        is UpdateUiState.Downloading -> Column(modifier = Modifier.fillMaxWidth()) {
            if (state.percent != null) {
                LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.update_downloading, state.percent), modifier = Modifier.padding(top = 8.dp))
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.update_downloading_indeterminate), modifier = Modifier.padding(top = 8.dp))
            }
        }

        is UpdateUiState.ReadyToInstall -> GlassButton(
            primary = true,
            enabled = !installRequested,
            onClick = {
                onInstallRequested()
                viewModel.installUpdate(state.file)
            }
        ) { Text(stringResource(R.string.update_ready_install)) }
    }
}

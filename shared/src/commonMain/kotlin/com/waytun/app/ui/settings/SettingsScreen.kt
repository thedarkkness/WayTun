package com.waytun.app.ui.settings

import com.waytun.app.res.Res
import com.waytun.app.res.action_back
import com.waytun.app.res.action_cancel
import com.waytun.app.res.app_name
import com.waytun.app.res.interface_glass
import com.waytun.app.res.interface_hint
import com.waytun.app.res.interface_plain
import com.waytun.app.res.net_block_quic
import com.waytun.app.res.net_block_quic_desc
import com.waytun.app.res.obf_apply_live
import com.waytun.app.res.obf_apply_live_desc
import com.waytun.app.res.obf_auto_reconnect
import com.waytun.app.res.obf_auto_reconnect_desc
import com.waytun.app.res.obf_auto_rotate
import com.waytun.app.res.obf_auto_rotate_desc
import com.waytun.app.res.obf_current
import com.waytun.app.res.obf_enable
import com.waytun.app.res.obf_fingerprint
import com.waytun.app.res.obf_fingerprint_desc
import com.waytun.app.res.obf_generated
import com.waytun.app.res.obf_headers
import com.waytun.app.res.obf_headers_desc
import com.waytun.app.res.obf_intro
import com.waytun.app.res.obf_level
import com.waytun.app.res.obf_level_light
import com.waytun.app.res.obf_level_light_desc
import com.waytun.app.res.obf_level_medium
import com.waytun.app.res.obf_level_medium_desc
import com.waytun.app.res.obf_level_strong
import com.waytun.app.res.obf_level_strong_desc
import com.waytun.app.res.obf_methods
import com.waytun.app.res.obf_regenerate
import com.waytun.app.res.obf_schedule
import com.waytun.app.res.obf_value_fingerprint
import com.waytun.app.res.obf_value_junk
import com.waytun.app.res.obf_value_off
import com.waytun.app.res.obf_value_on
import com.waytun.app.res.obf_wg_junk
import com.waytun.app.res.obf_wg_junk_desc
import com.waytun.app.res.settings_app_version
import com.waytun.app.res.settings_build_beta
import com.waytun.app.res.settings_build_release
import com.waytun.app.res.settings_check_updates
import com.waytun.app.res.settings_developer
import com.waytun.app.res.settings_developer_value
import com.waytun.app.res.settings_hwid
import com.waytun.app.res.settings_language_auto
import com.waytun.app.res.settings_language_en
import com.waytun.app.res.settings_language_ru
import com.waytun.app.res.settings_logs_open
import com.waytun.app.res.settings_os_label
import com.waytun.app.res.settings_section_about
import com.waytun.app.res.settings_section_diagnostics
import com.waytun.app.res.settings_section_interface
import com.waytun.app.res.settings_section_language
import com.waytun.app.res.settings_section_network
import com.waytun.app.res.settings_section_obfuscation
import com.waytun.app.res.settings_section_theme
import com.waytun.app.res.settings_section_updates
import com.waytun.app.res.settings_title
import com.waytun.app.res.theme_dark
import com.waytun.app.res.theme_light
import com.waytun.app.res.update_available_title
import com.waytun.app.res.update_checking
import com.waytun.app.res.update_download_install
import com.waytun.app.res.update_downloading
import com.waytun.app.res.update_downloading_indeterminate
import com.waytun.app.res.update_ready_install
import com.waytun.app.res.update_up_to_date
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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.waytun.app.core.time.nowMillis
import com.waytun.app.ui.LocalAppContainer
import com.waytun.app.ui.asString
import com.waytun.app.ui.platform.PlatformBackHandler
import com.waytun.app.ui.platform.operatingSystemName
import com.waytun.app.ui.platform.relativeTimeSpan
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import com.waytun.app.ui.theme.InterfaceStyle
import com.waytun.app.ui.theme.InterfaceStyleStore
import com.waytun.app.ui.theme.AppThemeMode
import com.waytun.app.ui.theme.AppThemeStore
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
    container: com.waytun.app.ui.AppContainer = LocalAppContainer.current,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel(container) }
) {
    // A back gesture closes the screen exactly like the arrow does (same slide), not with the system's shrinking preview.
    PlatformBackHandler(onBack = onBack)
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val obfuscation by viewModel.obfuscationSettings.collectAsStateWithLifecycle()
    val profile by viewModel.obfuscationProfile.collectAsStateWithLifecycle()
    val platform = container.platform
    var installRequested by remember(updateState) { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf(platform.currentLanguage()) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val listState = rememberLazyListState()
    val showScrollTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 500 } }

    GlassScaffold(
        title = { Text(stringResource(Res.string.settings_title)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
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
                SettingsCard(stringResource(Res.string.settings_section_theme), Sky) {
                    GlassSegmented(
                        labels = listOf(stringResource(Res.string.theme_light), stringResource(Res.string.theme_dark)),
                        selected = if (themeMode == AppThemeMode.DARK) 1 else 0,
                        onSelect = { index -> AppThemeStore.set(if (index == 1) AppThemeMode.DARK else AppThemeMode.LIGHT) }
                    )
                }
            }

            item(key = "interface") {
                val interfaceStyle by InterfaceStyleStore.style.collectAsStateWithLifecycle()
                SettingsCard(stringResource(Res.string.settings_section_interface), Sky) {
                    GlassSegmented(
                        labels = listOf(stringResource(Res.string.interface_plain), stringResource(Res.string.interface_glass)),
                        selected = if (interfaceStyle == InterfaceStyle.GLASS) 1 else 0,
                        onSelect = { index -> InterfaceStyleStore.set(if (index == 1) InterfaceStyle.GLASS else InterfaceStyle.PLAIN) }
                    )
                    Text(
                        stringResource(Res.string.interface_hint),
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
            SettingsCard(stringResource(Res.string.settings_section_network), Amber) {
                SwitchRow(
                    stringResource(Res.string.net_block_quic),
                    stringResource(Res.string.net_block_quic_desc),
                    obfuscation.blockQuic,
                    Amber
                ) { v -> viewModel.updateObfuscation { it.copy(blockQuic = v) } }
            }
            }

            item(key = "language") {
            SettingsCard(stringResource(Res.string.settings_section_language), Sky) {
                val tags = listOf("auto", "en", "ru")
                GlassSegmented(
                    labels = listOf(
                        stringResource(Res.string.settings_language_auto),
                        stringResource(Res.string.settings_language_en),
                        stringResource(Res.string.settings_language_ru)
                    ),
                    selected = tags.indexOf(selectedLanguage).coerceAtLeast(0),
                    onSelect = { index ->
                        selectedLanguage = tags[index]
                        platform.setLanguage(tags[index])
                    }
                )
            }
            }

            item(key = "about") {
            SettingsCard(stringResource(Res.string.settings_section_about), Rose) {
                InfoRow(
                    stringResource(Res.string.app_name),
                    stringResource(Res.string.settings_app_version, viewModel.appVersionName) + " · " +
                        stringResource(if (viewModel.isBetaBuild) Res.string.settings_build_beta else Res.string.settings_build_release)
                )
                InfoRow(stringResource(Res.string.settings_os_label), operatingSystemName)
                val hwid = platform.deviceId
                InfoRow(stringResource(Res.string.settings_hwid), hwid)
                InfoRow(stringResource(Res.string.settings_developer), stringResource(Res.string.settings_developer_value))
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    stringResource(Res.string.settings_section_updates),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                UpdateSection(updateState, installRequested, { installRequested = true }, viewModel)
            }
            }
            item(key = "diagnostics") {
            SettingsCard(stringResource(Res.string.settings_section_diagnostics), Sky) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenLogs)
                        .padding(vertical = 10.dp)
                ) {
                    Text(stringResource(Res.string.settings_logs_open), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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
    SettingsCard(stringResource(Res.string.settings_section_obfuscation), Indigo) {
        Text(
            stringResource(Res.string.obf_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        SwitchRow(stringResource(Res.string.obf_enable), null, on, Indigo) { v -> onUpdate { it.copy(enabled = v) } }

        AnimatedVisibility(
            visible = on,
            enter = expandVertically(tween(320)) + fadeIn(tween(320)),
            exit = shrinkVertically(tween(260)) + fadeOut(tween(200))
        ) {
          Column {
            Text(
                stringResource(Res.string.obf_level),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
            )
            val levels = listOf(
                ObfuscationLevel.LIGHT to Res.string.obf_level_light,
                ObfuscationLevel.MEDIUM to Res.string.obf_level_medium,
                ObfuscationLevel.STRONG to Res.string.obf_level_strong
            )
            GlassSegmented(
                labels = levels.map { stringResource(it.second) },
                selected = levels.indexOfFirst { it.first == settings.level }.coerceAtLeast(0),
                onSelect = { index -> onUpdate { it.copy(level = levels[index].first) } }
            )
            Text(
                stringResource(
                    when (settings.level) {
                        ObfuscationLevel.LIGHT -> Res.string.obf_level_light_desc
                        ObfuscationLevel.MEDIUM -> Res.string.obf_level_medium_desc
                        ObfuscationLevel.STRONG -> Res.string.obf_level_strong_desc
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            SectionLabel(stringResource(Res.string.obf_methods))
            SwitchRow(stringResource(Res.string.obf_fingerprint), stringResource(Res.string.obf_fingerprint_desc), settings.fingerprintRotation, Rose) { v -> onUpdate { it.copy(fingerprintRotation = v) } }
            SwitchRow(stringResource(Res.string.obf_headers), stringResource(Res.string.obf_headers_desc), settings.headerRotation, Sky) { v -> onUpdate { it.copy(headerRotation = v) } }
            SwitchRow(stringResource(Res.string.obf_wg_junk), stringResource(Res.string.obf_wg_junk_desc), settings.wireguardJunk, Lime) { v -> onUpdate { it.copy(wireguardJunk = v) } }

            SectionLabel(stringResource(Res.string.obf_schedule))
            SwitchRow(stringResource(Res.string.obf_auto_rotate), stringResource(Res.string.obf_auto_rotate_desc), settings.autoRotate, Mint) { v -> onUpdate { it.copy(autoRotate = v) } }
            SwitchRow(stringResource(Res.string.obf_auto_reconnect), stringResource(Res.string.obf_auto_reconnect_desc), settings.autoReconnect, Mint) { v -> onUpdate { it.copy(autoReconnect = v) } }
            SwitchRow(
                stringResource(Res.string.obf_apply_live), stringResource(Res.string.obf_apply_live_desc),
                settings.applyRotationLive, Amber, enabled = settings.autoRotate
            ) { v -> onUpdate { it.copy(applyRotationLive = v) } }

            SectionLabel(stringResource(Res.string.obf_current))
            val generated = relativeTimeSpan(profile.generatedAtMillis, nowMillis(), 1_000L)
            Text(
                stringResource(Res.string.obf_generated, generated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                val onText = stringResource(Res.string.obf_value_on)
                val offText = stringResource(Res.string.obf_value_off)
                ValueChip(stringResource(Res.string.obf_value_fingerprint, profile.fingerprint ?: offText), Rose)
                val junkCount = profile.awgJunkCount
                val junkMin = profile.awgJunkMin
                val junkMax = profile.awgJunkMax
                if (junkCount != null && junkMin != null && junkMax != null) {
                    ValueChip(stringResource(Res.string.obf_value_junk, junkCount, junkMin, junkMax), Lime)
                }
                profile.userAgent?.let { ValueChip("UA: " + it.substringAfter("(").substringBefore(")").take(26), Sky) }
                if (profile.awgDecoyPacket != null) ValueChip("I1 $onText", Indigo)
            }
            GlassButton(onClick = onRegenerate, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(stringResource(Res.string.obf_regenerate))
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
                if (state is UpdateUiState.UpToDate) Text(stringResource(Res.string.update_up_to_date), color = MaterialTheme.colorScheme.tertiary)
                if (state is UpdateUiState.Error) Text(state.message.asString(), color = MaterialTheme.colorScheme.error)
                GlassButton(onClick = { viewModel.checkForUpdates() }, primary = true, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(Res.string.settings_check_updates))
                }
            }
        }

        UpdateUiState.Checking -> Column {
            CircularProgressIndicator()
            Text(stringResource(Res.string.update_checking), modifier = Modifier.padding(top = 8.dp))
        }

        is UpdateUiState.Available -> Column {
            Text(stringResource(Res.string.update_available_title, state.version), fontWeight = FontWeight.SemiBold)
            if (state.notes.isNotBlank()) Text(state.notes, modifier = Modifier.padding(top = 8.dp))
            GlassButton(onClick = { viewModel.downloadAndPrepareInstall() }, primary = true, modifier = Modifier.padding(top = 12.dp)) {
                Text(stringResource(Res.string.update_download_install))
            }
            GlassButton(onClick = { viewModel.dismissUpdateDialog() }, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(Res.string.action_cancel))
            }
        }

        is UpdateUiState.Downloading -> Column(modifier = Modifier.fillMaxWidth()) {
            if (state.percent != null) {
                LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(Res.string.update_downloading, state.percent), modifier = Modifier.padding(top = 8.dp))
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(stringResource(Res.string.update_downloading_indeterminate), modifier = Modifier.padding(top = 8.dp))
            }
        }

        is UpdateUiState.ReadyToInstall -> GlassButton(
            primary = true,
            enabled = !installRequested,
            onClick = {
                onInstallRequested()
                viewModel.installUpdate(state.path)
            }
        ) { Text(stringResource(Res.string.update_ready_install)) }
    }
}

package com.waytun.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waytun.app.core.obfuscation.ObfuscationSettings
import com.waytun.app.res.Res
import com.waytun.app.res.pairing_error_generic
import com.waytun.app.res.update_error_generic
import com.waytun.app.res.update_error_no_apk_asset
import com.waytun.app.ui.AppContainer
import com.waytun.app.ui.UiText
import com.waytun.app.update.GitHubReleaseApi
import com.waytun.app.update.ReleaseCheckResult
import com.waytun.app.update.ReleaseInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val version: String, val notes: String) : UpdateUiState
    data class Downloading(val percent: Int?) : UpdateUiState
    data class ReadyToInstall(val path: String) : UpdateUiState
    data class Error(val message: UiText) : UpdateUiState
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val obfuscationManager = container.obfuscation
    private val platform = container.platform

    val obfuscationSettings = obfuscationManager.settings
    val obfuscationProfile = obfuscationManager.profile

    /** A change reaches the running tunnel too: it restarts with the new values instead of waiting for a manual reconnect. */
    fun updateObfuscation(transform: (ObfuscationSettings) -> ObfuscationSettings) {
        obfuscationManager.update(transform)
        container.reapplier.requestReapply()
    }

    fun regenerateObfuscation() {
        obfuscationManager.rotateNow()
        container.reapplier.requestReapply()
    }

    val appVersionName: String = platform.appVersionName
    val isBetaBuild: Boolean = platform.isBetaBuild

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    private var pendingRelease: ReleaseInfo? = null

    // Guard against duplicate concurrent requests from repeated taps (e.g. double-tapping
    // "Check for updates" or "Download" before the UI has switched out of the idle/available
    // state). Cleared on completion - including failure - so a retry always starts clean.
    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    fun checkForUpdates() {
        if (checkJob?.isActive == true) return
        _updateState.value = UpdateUiState.Checking
        checkJob = viewModelScope.launch {
            var result = GitHubReleaseApi.fetchLatest()
            if (result is ReleaseCheckResult.Failure) {
                // Transient DNS/network hiccups (e.g. right after Wi-Fi reconnects) are common
                // and resolve themselves immediately, so retry once before surfacing an error.
                delay(1000)
                result = GitHubReleaseApi.fetchLatest()
            }
            when (result) {
                is ReleaseCheckResult.Success -> {
                    val release = result.release
                    _updateState.value = if (GitHubReleaseApi.isNewer(release.version, appVersionName)) {
                        pendingRelease = release
                        UpdateUiState.Available(release.version, release.releaseNotes)
                    } else {
                        UpdateUiState.UpToDate
                    }
                }
                ReleaseCheckResult.NoReleasesYet -> _updateState.value = UpdateUiState.UpToDate
                is ReleaseCheckResult.Failure ->
                    _updateState.value = UpdateUiState.Error(UiText.of(Res.string.update_error_generic, result.message))
            }
        }
        checkJob?.invokeOnCompletion { checkJob = null }
    }

    fun downloadAndPrepareInstall() {
        if (downloadJob?.isActive == true) return
        val release = pendingRelease ?: return
        val updater = platform.updater
        val asset = updater?.assetFor(release)
        if (updater == null || asset == null) {
            _updateState.value = UpdateUiState.Error(UiText.of(Res.string.update_error_no_apk_asset))
            return
        }
        downloadJob = viewModelScope.launch {
            _updateState.value = UpdateUiState.Downloading(null)
            updater.download(asset.first, asset.second) { percent -> _updateState.value = UpdateUiState.Downloading(percent) }
                .onSuccess { _updateState.value = UpdateUiState.ReadyToInstall(it) }
                .onFailure { e -> _updateState.value = UpdateUiState.Error(UiText.of(Res.string.update_error_generic, e.message ?: "")) }
        }
        downloadJob?.invokeOnCompletion { downloadJob = null }
    }

    fun installUpdate(path: String) {
        val updater = platform.updater ?: return
        viewModelScope.launch {
            updater.install(path).onFailure { e ->
                _updateState.value = UpdateUiState.Error(
                    UiText.of(Res.string.update_error_generic, e.message ?: UiText.of(Res.string.pairing_error_generic))
                )
            }
        }
    }

    fun dismissUpdateDialog() {
        checkJob?.cancel()
        downloadJob?.cancel()
        _updateState.value = UpdateUiState.Idle
        pendingRelease = null
    }
}

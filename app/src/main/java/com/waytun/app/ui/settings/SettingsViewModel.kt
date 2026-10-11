package com.waytun.app.ui.settings

import android.content.Context
import com.waytun.app.BuildConfig
import com.waytun.app.R
import com.waytun.app.core.obfuscation.ObfuscationManager
import com.waytun.app.ui.tunnels.ObfuscationReapplier
import com.waytun.app.core.obfuscation.ObfuscationSettings
import com.waytun.app.update.ApkInstaller
import com.waytun.app.update.DownloadProgress
import com.waytun.app.update.ApkDownloader
import com.waytun.app.update.GitHubReleaseApi
import com.waytun.app.update.ReleaseCheckResult
import com.waytun.app.update.ReleaseInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val version: String, val notes: String) : UpdateUiState
    data class Downloading(val percent: Int?) : UpdateUiState
    data class ReadyToInstall(val file: File) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val obfuscationManager: ObfuscationManager,
    private val reapplier: ObfuscationReapplier
) : ViewModel() {

    val obfuscationSettings = obfuscationManager.settings
    val obfuscationProfile = obfuscationManager.profile

    /** A change reaches the running tunnel too: it restarts with the new values instead of waiting for a manual reconnect. */
    fun updateObfuscation(transform: (ObfuscationSettings) -> ObfuscationSettings) {
        obfuscationManager.update(transform)
        reapplier.requestReapply()
    }

    fun regenerateObfuscation() {
        obfuscationManager.rotateNow()
        reapplier.requestReapply()
    }

    val appVersionName: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"

    // The app is only ever distributed as a debug build (no release signing config exists yet),
    // so DEBUG is an accurate stand-in for "not an official signed release".
    val isBetaBuild: Boolean = BuildConfig.DEBUG

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
            var result = withContext(Dispatchers.IO) { GitHubReleaseApi.fetchLatest() }
            if (result is ReleaseCheckResult.Failure) {
                // Transient DNS/network hiccups (e.g. right after Wi-Fi reconnects) are common
                // and resolve themselves immediately, so retry once before surfacing an error.
                delay(1000)
                result = withContext(Dispatchers.IO) { GitHubReleaseApi.fetchLatest() }
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
                    _updateState.value = UpdateUiState.Error(
                        context.getString(R.string.update_error_generic, result.message)
                    )
            }
        }
        checkJob?.invokeOnCompletion { checkJob = null }
    }

    fun downloadAndPrepareInstall() {
        if (downloadJob?.isActive == true) return
        val release = pendingRelease ?: return
        val url = release.apkDownloadUrl
        val name = release.apkFileName
        if (url == null || name == null) {
            _updateState.value = UpdateUiState.Error(context.getString(R.string.update_error_no_apk_asset))
            return
        }
        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            ApkDownloader.download(context, url, name) { progress ->
                _updateState.value = when (progress) {
                    is DownloadProgress.InProgress -> UpdateUiState.Downloading(progress.percent)
                    is DownloadProgress.Done -> UpdateUiState.ReadyToInstall(progress.file)
                    is DownloadProgress.Failed -> UpdateUiState.Error(
                        context.getString(R.string.update_error_generic, progress.message)
                    )
                }
            }
        }
        downloadJob?.invokeOnCompletion { downloadJob = null }
    }

    fun installUpdate(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            ApkInstaller.install(context, file).onFailure { e ->
                _updateState.value = UpdateUiState.Error(
                    context.getString(R.string.update_error_generic, e.message ?: context.getString(R.string.pairing_error_generic))
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

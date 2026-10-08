package com.waytun.app.ui.settings

import android.content.Context
import com.waytun.app.R
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
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val appVersionName: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    private var pendingRelease: ReleaseInfo? = null

    fun checkForUpdates() {
        _updateState.value = UpdateUiState.Checking
        viewModelScope.launch {
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
    }

    fun downloadAndPrepareInstall() {
        val release = pendingRelease ?: return
        val url = release.apkDownloadUrl
        val name = release.apkFileName
        if (url == null || name == null) {
            _updateState.value = UpdateUiState.Error(context.getString(R.string.update_error_no_apk_asset))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
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
    }

    fun dismissUpdateDialog() {
        _updateState.value = UpdateUiState.Idle
        pendingRelease = null
    }
}

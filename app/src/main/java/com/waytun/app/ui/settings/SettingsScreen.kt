package com.waytun.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waytun.app.R
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingInstallFile by remember { mutableStateOf<File?>(null) }

    fun startInstall(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
    }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        pendingInstallFile?.let { file ->
            if (context.packageManager.canRequestPackageInstalls()) startInstall(file)
        }
    }

    fun requestInstall(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            pendingInstallFile = file
            installPermissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                )
            )
        } else {
            startInstall(file)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.settings_app_version, viewModel.appVersionName))

            Box(modifier = Modifier.padding(top = 16.dp)) {
                when (val state = updateState) {
                    is UpdateUiState.Idle, is UpdateUiState.Error, is UpdateUiState.UpToDate -> {
                        Column {
                            if (state is UpdateUiState.UpToDate) {
                                Text(stringResource(R.string.update_up_to_date))
                            }
                            if (state is UpdateUiState.Error) {
                                Text(state.message)
                            }
                            Button(
                                onClick = { viewModel.checkForUpdates() },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text(stringResource(R.string.settings_check_updates))
                            }
                        }
                    }

                    UpdateUiState.Checking -> {
                        Column {
                            CircularProgressIndicator()
                            Text(
                                stringResource(R.string.update_checking),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }

                    is UpdateUiState.Available -> {
                        Column {
                            Text(stringResource(R.string.update_available_title, state.version))
                            if (state.notes.isNotBlank()) {
                                Text(state.notes, modifier = Modifier.padding(top = 8.dp))
                            }
                            Button(
                                onClick = { viewModel.downloadAndPrepareInstall() },
                                modifier = Modifier.padding(top = 12.dp)
                            ) {
                                Text(stringResource(R.string.update_download_install))
                            }
                            OutlinedButton(
                                onClick = { viewModel.dismissUpdateDialog() },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                    }

                    is UpdateUiState.Downloading -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (state.percent != null) {
                                LinearProgressIndicator(
                                    progress = { state.percent / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    stringResource(R.string.update_downloading, state.percent),
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(
                                    stringResource(R.string.update_downloading_indeterminate),
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }

                    is UpdateUiState.ReadyToInstall -> {
                        Column {
                            Button(onClick = { requestInstall(state.file) }) {
                                Text(stringResource(R.string.update_ready_install))
                            }
                        }
                    }
                }
            }
        }
    }
}

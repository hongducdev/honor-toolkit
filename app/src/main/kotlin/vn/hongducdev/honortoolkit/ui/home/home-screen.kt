package vn.hongducdev.honortoolkit.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.hongducdev.honortoolkit.R
import vn.hongducdev.honortoolkit.core.adb.AdbState
import vn.hongducdev.honortoolkit.core.adb.ShellResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.startPairing()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ConnectionStatusCard(state = state.adb, busy = state.busy, onReconnect = viewModel::reconnect)
            if (state.adb == AdbState.Connected) {
                ProbeCard(result = state.probe, busy = state.busy, onRun = viewModel::runProbe)
            } else {
                PairingCard(
                    pairingPortFound = state.pairingPort != null,
                    busy = state.busy,
                    onOpenDeveloperOptions = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                    },
                    onStartPairing = {
                        val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) viewModel.startPairing() else notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    onSubmitCode = viewModel::submitCode,
                )
            }
        }
    }
}

@Composable
private fun ProbeCard(result: ShellResult?, busy: Boolean, onRun: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.probe_heading), style = MaterialTheme.typography.titleMedium)
            Button(onClick = onRun, enabled = !busy) { Text(stringResource(R.string.probe_run)) }
            if (result != null) Text(result.output, style = MaterialTheme.typography.bodySmall)
        }
    }
}

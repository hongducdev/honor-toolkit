package vn.hongducdev.honortoolkit.feature.quickshare

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.hongducdev.honortoolkit.core.ui.NavRow
import vn.hongducdev.honortoolkit.core.ui.SettingsGroup
import vn.hongducdev.honortoolkit.core.ui.ToolkitTopBar

/** Opened from the system share sheet: pick a nearby receiver, compare the PIN and send [uris]. */
@Composable
fun QuickShareSendScreen(uris: List<Uri>, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: QuickShareSendViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(uris) { viewModel.start(uris) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            ToolkitTopBar(
                title = stringResource(R.string.quick_share_send_title),
                onBack = onClose,
                backDescription = stringResource(R.string.quick_share_back),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state.phase) {
                SendPhase.CHOOSING -> DevicePicker(state, viewModel::send)
                SendPhase.CONNECTING -> Text(stringResource(R.string.quick_share_send_connecting), style = MaterialTheme.typography.titleMedium)
                SendPhase.AWAITING_ANSWER -> {
                    Text(stringResource(R.string.quick_share_send_waiting), style = MaterialTheme.typography.titleMedium)
                    PinBlock(state.pin)
                    OutlinedButton(onClick = viewModel::cancel, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.quick_share_send_cancel))
                    }
                }
                SendPhase.SENDING -> {
                    Text(stringResource(R.string.quick_share_send_sending, state.percent), style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = viewModel::cancel, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.quick_share_send_cancel))
                    }
                }
                SendPhase.DONE -> Finished(R.string.quick_share_send_done, onClose)
                SendPhase.REJECTED -> Finished(R.string.quick_share_send_rejected, onClose)
                SendPhase.CANCELLED -> Finished(R.string.quick_share_send_cancelled, onClose)
                SendPhase.FAILED -> Finished(R.string.quick_share_send_failed, onClose)
            }
        }
    }
}

@Composable
private fun DevicePicker(state: SendUiState, onPick: (NearbyDevice) -> Unit) {
    Text(stringResource(R.string.quick_share_send_files, state.fileCount), style = MaterialTheme.typography.bodyLarge)
    if (state.devices.isEmpty()) {
        Text(stringResource(R.string.quick_share_send_searching), style = MaterialTheme.typography.titleMedium)
    } else {
        SettingsGroup(stringResource(R.string.quick_share_send_nearby)) {
            val unnamed = stringResource(R.string.quick_share_send_unnamed)
            state.devices.forEach { device ->
                NavRow(Icons.Rounded.Share, device.name ?: unnamed, device.address.hostAddress.orEmpty()) { onPick(device) }
            }
        }
    }
}

@Composable
private fun PinBlock(pin: String?) {
    if (pin == null) return
    Text(stringResource(R.string.quick_share_send_pin_hint), style = MaterialTheme.typography.bodyMedium)
    Text(pin, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Finished(messageRes: Int, onClose: () -> Unit) {
    Text(stringResource(messageRes), style = MaterialTheme.typography.titleMedium)
    Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.quick_share_send_close)) }
}

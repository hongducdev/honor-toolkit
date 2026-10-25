package vn.hongducdev.honortoolkit.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vn.hongducdev.honortoolkit.R
import vn.hongducdev.honortoolkit.core.adb.AdbState

@Composable
fun ConnectionStatusCard(state: AdbState, busy: Boolean, onReconnect: () -> Unit) {
    val connected = state == AdbState.Connected
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (connected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stateLabel(state), style = MaterialTheme.typography.titleMedium)
            if (state is AdbState.Failed) {
                Text(text = state.message, style = MaterialTheme.typography.bodyMedium)
            }
            if (!connected) {
                Button(onClick = onReconnect, enabled = !busy) {
                    Text(stringResource(R.string.status_reconnect))
                }
            }
        }
    }
}

@Composable
private fun stateLabel(state: AdbState): String = stringResource(
    when (state) {
        AdbState.Disconnected -> R.string.status_disconnected
        AdbState.Connecting -> R.string.status_connecting
        AdbState.NeedsPairing -> R.string.status_needs_pairing
        AdbState.Connected -> R.string.status_connected
        is AdbState.Failed -> R.string.status_failed
    },
)

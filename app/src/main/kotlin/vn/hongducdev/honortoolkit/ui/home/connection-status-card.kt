package vn.hongducdev.honortoolkit.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vn.hongducdev.honortoolkit.R
import vn.hongducdev.honortoolkit.core.adb.AdbState
import vn.hongducdev.honortoolkit.core.ui.CookieShape
import vn.hongducdev.honortoolkit.core.ui.WavyProgressIndicator

@Composable
fun ConnectionStatusCard(state: AdbState, busy: Boolean, onReconnect: () -> Unit) {
    val connected = state == AdbState.Connected
    val scheme = MaterialTheme.colorScheme
    val container = when {
        connected -> scheme.primaryContainer
        state is AdbState.Failed -> scheme.errorContainer
        else -> scheme.surfaceContainerHigh
    }
    val content = when {
        connected -> scheme.onPrimaryContainer
        state is AdbState.Failed -> scheme.onErrorContainer
        else -> scheme.onSurface
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.size(20.dp).background(if (connected) scheme.primary else scheme.outline, CookieShape))
                Text(text = stateLabel(state), style = MaterialTheme.typography.titleLarge)
            }
            if (state == AdbState.Connecting || busy) {
                WavyProgressIndicator(color = content, trackColor = content.copy(alpha = 0.18f))
            }
            if (state is AdbState.Failed) {
                Text(text = state.message, style = MaterialTheme.typography.bodyMedium)
            }
            if (!connected) {
                FilledTonalButton(onClick = onReconnect, enabled = !busy) {
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

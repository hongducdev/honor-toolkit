package vn.hongducdev.honortoolkit.feature.circlesearch

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import vn.hongducdev.honortoolkit.core.ui.SettingsGroup
import vn.hongducdev.honortoolkit.core.ui.SettingsNote
import vn.hongducdev.honortoolkit.core.ui.SettingsRow
import vn.hongducdev.honortoolkit.core.ui.SliderRow
import vn.hongducdev.honortoolkit.core.ui.SwitchRow

private const val STEP_MS = 50L

@Composable
internal fun TriggerPage(state: CircleSearchUiState, viewModel: CircleSearchViewModel) {
    val context = LocalContext.current
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    SettingsGroup(stringResource(R.string.cs_trigger_heading)) {
        SliderRow({ stringResource(R.string.cs_delay_label, it) }, state.delayMs, 0, TriggerConfig.MAX_DELAY_MS, STEP_MS, viewModel::setDelay)
        SliderRow({ stringResource(R.string.cs_tile_delay_label, it) }, state.tileDelayMs, 0, TriggerConfig.MAX_DELAY_MS, STEP_MS, viewModel::setTileDelay)
        SwitchRow(stringResource(R.string.cs_vibrate_label), state.vibrate, onChange = viewModel::setVibrate)
    }
    if (state.lastResult.isNotEmpty()) {
        Text(
            stringResource(R.string.cs_last_result, state.lastResult),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SettingsGroup(stringResource(R.string.cs_log_heading)) {
        SettingsRow {
            Text(
                state.diagnostics.ifEmpty { stringResource(R.string.cs_log_empty) },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingsRow {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = {
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Honor Toolkit", state.diagnostics))
                        Toast.makeText(context, R.string.cs_log_copied, Toast.LENGTH_SHORT).show()
                    },
                    enabled = state.diagnostics.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.cs_log_copy))
                }
                OutlinedButton(onClick = viewModel::clearLog, enabled = state.diagnostics.isNotEmpty()) {
                    Text(stringResource(R.string.cs_log_clear))
                }
            }
        }
    }
    SettingsGroup(stringResource(R.string.cs_assign_heading)) {
        SettingsNote(stringResource(R.string.cs_assign_body))
        SettingsNote(stringResource(R.string.cs_old_app_note))
    }
}

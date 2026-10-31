package vn.hongducdev.honortoolkit.feature.circlesearch

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.util.Date
import kotlinx.coroutines.delay
import vn.hongducdev.honortoolkit.core.ui.SettingsGroup
import vn.hongducdev.honortoolkit.core.ui.SettingsNote
import vn.hongducdev.honortoolkit.core.ui.SettingsRow
import vn.hongducdev.honortoolkit.core.ui.SwitchRow

private const val WAKE_STATUS_POLL_MS = 2_000L

@Composable
internal fun WakePage(state: CircleSearchUiState, viewModel: CircleSearchViewModel) {
    val context = LocalContext.current
    var enableAfterMic by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refresh() }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && enableAfterMic) {
            viewModel.setWakeEnabled(true)
        } else if (!granted && enableAfterMic) {
            Toast.makeText(context, R.string.cs_wake_no_mic_toast, Toast.LENGTH_SHORT).show()
            viewModel.setWakeEnabled(false)
        }
        enableAfterMic = false
        viewModel.refresh()
    }

    // The service writes its status to SharedPreferences; refresh while this page is open so the
    // model load, listening state and last wake are visible without leaving the screen.
    LaunchedEffect(state.wakeEnabled) {
        if (!state.wakeEnabled) return@LaunchedEffect
        while (true) {
            delay(WAKE_STATUS_POLL_MS)
            viewModel.refreshWakeStatus()
        }
    }

    SettingsGroup(stringResource(R.string.cs_wake_heading)) {
        SettingsNote(stringResource(R.string.cs_wake_body))
        SettingsNote(
            stringResource(
                R.string.cs_wake_status_line,
                state.wakeStatus.ifEmpty { stringResource(R.string.cs_wake_status_off) },
            ),
        )
        SwitchRow(stringResource(R.string.cs_wake_switch_label), state.wakeEnabled) { enable ->
            if (!enable) {
                viewModel.setWakeEnabled(false)
                return@SwitchRow
            }
            if (!state.micGranted) {
                enableAfterMic = true
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
                return@SwitchRow
            }
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !state.notificationsGranted
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            viewModel.setWakeEnabled(true)
        }
        if (!state.micGranted) {
            SettingsNote(stringResource(R.string.cs_wake_mic_missing))
            SettingsRow {
                Button(
                    onClick = {
                        enableAfterMic = false
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                ) {
                    Text(stringResource(R.string.cs_wake_grant_mic))
                }
            }
        }
        if (state.wakeEnabled && !state.notificationsGranted) {
            SettingsNote(stringResource(R.string.cs_wake_notification_denied))
        }
        if (state.wakeLastHeard.isNotEmpty() && state.wakeLastHeardAt > 0L) {
            val heardAt = DateFormat.getTimeFormat(context).format(Date(state.wakeLastHeardAt))
            SettingsNote(stringResource(R.string.cs_wake_last_heard, state.wakeLastHeard, heardAt))
        }
        if (state.wakeBatteryOptimized) {
            SettingsNote(stringResource(R.string.cs_wake_battery))
            SettingsRow {
                OutlinedButton(
                    onClick = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    },
                ) {
                    Text(stringResource(R.string.cs_wake_battery_button))
                }
            }
        }
        SettingsRow {
            FilledTonalButton(
                onClick = viewModel::testWake,
                enabled = state.wakeEnabled && state.micGranted,
            ) {
                Text(stringResource(R.string.cs_wake_test))
            }
        }
        SettingsNote(stringResource(R.string.cs_wake_test_hint))
    }
    SettingsGroup(stringResource(R.string.cs_wake_notes_heading)) {
        SettingsNote(stringResource(R.string.cs_wake_notes))
    }
}

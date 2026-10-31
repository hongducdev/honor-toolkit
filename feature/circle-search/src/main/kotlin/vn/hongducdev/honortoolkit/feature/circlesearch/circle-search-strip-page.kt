package vn.hongducdev.honortoolkit.feature.circlesearch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.core.content.ContextCompat
import vn.hongducdev.honortoolkit.core.ui.SettingsGroup
import vn.hongducdev.honortoolkit.core.ui.SettingsNote
import vn.hongducdev.honortoolkit.core.ui.SliderRow
import vn.hongducdev.honortoolkit.core.ui.SwitchRow

@Composable
internal fun StripPage(state: CircleSearchUiState, viewModel: CircleSearchViewModel) {
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refresh() }
    SettingsGroup(stringResource(R.string.cs_strip_heading)) {
        SettingsNote(
            stringResource(
                when {
                    !state.overlayGranted -> R.string.cs_strip_no_permission
                    state.stripEnabled -> R.string.cs_strip_on
                    else -> R.string.cs_strip_off
                },
            ),
        )
        SwitchRow(
            stringResource(R.string.cs_strip_switch_label),
            state.stripEnabled && state.overlayGranted,
        ) { enabled ->
            viewModel.setStripEnabled(enabled)
            if (!enabled) return@SwitchRow
            if (!state.overlayGranted) {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                )
            } else if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (state.stripEnabled && !state.notificationsGranted) {
            SettingsNote(stringResource(R.string.cs_strip_notification_denied))
        }
        SliderRow({ stringResource(R.string.cs_strip_width_label, it) }, state.stripWidthPercent, 5, TriggerConfig.MAX_STRIP_WIDTH_PERCENT, 5, viewModel::setStripWidth)
        SliderRow({ stringResource(R.string.cs_strip_height_label, it) }, state.stripHeightDp, 2, TriggerConfig.MAX_STRIP_HEIGHT_DP, 2, viewModel::setStripHeight)
        SliderRow(
            { stringResource(R.string.cs_strip_offset_label, it) },
            state.stripOffsetDp,
            TriggerConfig.MIN_STRIP_OFFSET_DP,
            TriggerConfig.MAX_STRIP_OFFSET_DP,
            2,
            viewModel::setStripOffset,
        )
        SwitchRow(stringResource(R.string.cs_strip_visible_label), state.stripVisible, onChange = viewModel::setStripVisible)
    }
    SettingsGroup(stringResource(R.string.cs_strip_gestures_heading)) {
        StripGesture.entries.forEach { gesture ->
            StripActionRow(
                gesture = gesture,
                action = state.stripActions[gesture] ?: StripAction.defaultFor(gesture),
                onChange = { viewModel.setStripAction(gesture, it) },
            )
        }
    }
}

@Composable
private fun StripActionRow(gesture: StripGesture, action: StripAction, onChange: (StripAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { open = true }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(gesture.labelRes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(stringResource(action.labelRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.cs_strip_gesture_dialog, stringResource(gesture.labelRes))) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    StripAction.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChange(option); open = false }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == action, onClick = { onChange(option); open = false })
                            Text(stringResource(option.labelRes), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.cs_close)) }
            },
        )
    }
}

package vn.hongducdev.honortoolkit.feature.automation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vn.hongducdev.honortoolkit.core.ui.SwitchRow
import vn.hongducdev.honortoolkit.feature.tune.TuneAutomation

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActionFields(action: Action, onChange: (Action) -> Unit) {
    when (action) {
        is Action.WriteSetting -> {
            Text(stringResource(R.string.automation_editor_scope), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingScope.entries.forEach { scope ->
                    FilterChip(
                        selected = action.scope == scope,
                        onClick = { onChange(action.copy(scope = scope)) },
                        label = { Text(scope.cli) },
                    )
                }
            }
            Field(
                label = stringResource(R.string.automation_editor_key),
                value = action.key,
                onChange = { onChange(action.copy(key = it.trim())) },
            )
            val keys = TuneAutomation.writableKeys()
                .filter { it.scope == action.scope.cli }
                .map { it.key }
                .distinct()
            if (keys.isNotEmpty()) {
                Text(stringResource(R.string.automation_editor_catalogue_keys), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    keys.forEach { key ->
                        FilterChip(
                            selected = action.key == key,
                            onClick = { onChange(action.copy(key = key)) },
                            label = { Text(key) },
                        )
                    }
                }
            }
            Field(
                label = stringResource(R.string.automation_editor_value),
                value = action.value,
                onChange = { onChange(action.copy(value = it)) },
            )
            var confirmAdvanced by remember { mutableStateOf(false) }
            SwitchRow(
                label = stringResource(R.string.automation_editor_advanced_on),
                description = stringResource(R.string.automation_editor_advanced_desc),
                checked = action.advanced,
                onChange = { on ->
                    if (on && !AutomationSettingsPolicy.isCatalogueKey(action.scope, action.key)) {
                        confirmAdvanced = true
                    } else {
                        onChange(action.copy(advanced = on))
                    }
                },
            )
            if (action.advanced && !AutomationSettingsPolicy.isCatalogueKey(action.scope, action.key)) {
                Text(
                    stringResource(R.string.automation_editor_key_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (action.key.isNotBlank()) {
                AutomationSettingsPolicy.refusal(action)?.let { refusal ->
                    Text(
                        refusal,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (confirmAdvanced) {
                AlertDialog(
                    onDismissRequest = { confirmAdvanced = false },
                    title = { Text(stringResource(R.string.automation_editor_advanced_on)) },
                    text = { Text(stringResource(R.string.automation_editor_confirm_advanced, action.key)) },
                    confirmButton = {
                        Button(onClick = {
                            confirmAdvanced = false
                            onChange(action.copy(advanced = true))
                        }) { Text(stringResource(R.string.automation_editor_pick)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmAdvanced = false }) { Text(stringResource(R.string.automation_cancel)) }
                    },
                )
            }
        }
        is Action.Ringer -> OptionChips(
            selected = action.mode,
            options = RingerMode.entries.map { it to ringerLabel(it) },
            onSelect = { onChange(action.copy(mode = it)) },
        )
        is Action.LaunchApp -> Field(
            label = stringResource(R.string.automation_editor_package),
            value = action.packageName,
            onChange = { onChange(action.copy(packageName = it.trim())) },
        )
        is Action.Notify -> {
            Field(
                label = stringResource(R.string.automation_editor_title),
                value = action.title,
                onChange = { onChange(action.copy(title = it)) },
            )
            Field(
                label = stringResource(R.string.automation_editor_message),
                value = action.message,
                onChange = { onChange(action.copy(message = it)) },
            )
        }
        is Action.Wait -> NumberField(
            label = stringResource(R.string.automation_editor_seconds),
            value = action.seconds,
            onValue = { onChange(action.copy(seconds = it)) },
            maxDigits = 4,
        )
        is Action.Shell -> {
            Field(
                label = stringResource(R.string.automation_editor_command),
                value = action.command,
                onChange = { onChange(action.copy(command = it)) },
            )
            Text(
                stringResource(R.string.automation_editor_shell_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is Action.TuneFeature -> {
            Text(stringResource(R.string.automation_editor_feature), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TuneAutomation.features().forEach { feature ->
                    FilterChip(
                        selected = action.featureId == feature.id,
                        onClick = { onChange(action.copy(featureId = feature.id)) },
                        label = { Text(stringResource(feature.titleRes)) },
                    )
                }
            }
            SwitchRow(
                label = stringResource(
                    if (action.enabled) R.string.automation_editor_feature_on else R.string.automation_editor_feature_off,
                ),
                checked = action.enabled,
                onChange = { onChange(action.copy(enabled = it)) },
            )
        }
        is Action.ToolkitStrip -> SwitchRow(
            label = stringResource(
                if (action.enabled) R.string.automation_editor_strip_on else R.string.automation_editor_strip_off,
            ),
            checked = action.enabled,
            onChange = { onChange(action.copy(enabled = it)) },
        )
        Action.CircleToSearch, Action.Screenshot, Action.ScreenOff -> Text(
            stringResource(ActionKind.of(action).label),
            style = MaterialTheme.typography.bodyMedium,
        )
        is Action.Flashlight -> OptionChips(
            selected = action.on,
            options = listOf(
                true to R.string.automation_flashlight_on,
                false to R.string.automation_flashlight_off,
            ),
            onSelect = { onChange(action.copy(on = it)) },
        )
        is Action.Media -> OptionChips(
            selected = action.button,
            options = listOf(
                MediaButton.PlayPause to R.string.automation_media_play_pause,
                MediaButton.Next to R.string.automation_media_next,
                MediaButton.Previous to R.string.automation_media_previous,
            ),
            onSelect = { onChange(action.copy(button = it)) },
        )
        is Action.Volume -> {
            OptionChips(
                selected = action.stream,
                options = listOf(
                    VolumeStream.Media to R.string.automation_volume_media,
                    VolumeStream.Ring to R.string.automation_volume_ring,
                    VolumeStream.Alarm to R.string.automation_volume_alarm,
                ),
                onSelect = { onChange(action.copy(stream = it)) },
            )
            NumberField(
                label = stringResource(R.string.automation_editor_volume_percent),
                value = action.percent,
                onValue = { onChange(action.copy(percent = it)) },
                maxDigits = 3,
            )
        }
        is Action.Vibrate -> OptionChips(
            selected = action.kind,
            options = listOf(
                HapticKind.Tick to R.string.automation_haptic_tick,
                HapticKind.Click to R.string.automation_haptic_click,
                HapticKind.DoubleClick to R.string.automation_haptic_double,
                HapticKind.Heavy to R.string.automation_haptic_heavy,
            ),
            onSelect = { onChange(action.copy(kind = it)) },
        )
        is Action.DoNotDisturbSwitch -> ToggleChips(action.mode) { onChange(action.copy(mode = it)) }
        is Action.MuteMedia -> ToggleChips(action.mode) { onChange(action.copy(mode = it)) }
        is Action.LowPower -> ToggleChips(action.mode) { onChange(action.copy(mode = it)) }
        is Action.AutoBrightness -> ToggleChips(action.mode) { onChange(action.copy(mode = it)) }
        is Action.KeepAwake -> {
            ToggleChips(action.mode, flip = true) { onChange(action.copy(mode = it)) }
            NumberField(
                label = stringResource(R.string.automation_editor_minutes),
                value = action.minutes,
                onValue = { onChange(action.copy(minutes = it)) },
                maxDigits = 3,
            )
        }
        is Action.NetworkSwitch -> {
            ToggleChips(action.mode) { onChange(action.copy(mode = it)) }
            AdbNote()
        }
        is Action.OpenActivity -> {
            PackageField(action.packageName) { onChange(action.copy(packageName = it)) }
            Field(
                label = stringResource(R.string.automation_editor_class),
                value = action.className,
                onChange = { onChange(action.copy(className = it.trim())) },
            )
        }
        is Action.OpenSettingsPage -> OptionChips(
            selected = action.page,
            options = SettingsPage.entries.map { it to settingsPageLabel(it) },
            onSelect = { onChange(action.copy(page = it)) },
        )
        is Action.FreezeApp -> {
            PackageField(action.packageName) { onChange(action.copy(packageName = it)) }
            OptionChips(
                selected = action.frozen,
                options = listOf(
                    true to R.string.automation_action_freeze_on,
                    false to R.string.automation_action_freeze_off,
                ),
                onSelect = { onChange(action.copy(frozen = it)) },
            )
            AdbNote()
        }
        Action.CycleRinger, Action.ToggleVibrate, is Action.OpenCamera, Action.KeyboardPicker, Action.VoiceAssistant,
        Action.LikeCurrentSong, Action.OpenNowPlaying, Action.SplitScreen,
        -> Text(
            stringResource(ActionKind.of(action).label),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ToggleChips(mode: Toggle, flip: Boolean = true, onChange: (Toggle) -> Unit) = OptionChips(
    selected = mode,
    options = buildList {
        add(Toggle.On to R.string.automation_toggle_on)
        add(Toggle.Off to R.string.automation_toggle_off)
        if (flip) add(Toggle.Flip to R.string.automation_toggle_flip)
    },
    onSelect = onChange,
)

@Composable
private fun AdbNote() = Text(
    stringResource(R.string.automation_editor_adb_only),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

private fun settingsPageLabel(page: SettingsPage): Int = when (page) {
    SettingsPage.Wifi -> R.string.automation_page_wifi
    SettingsPage.Bluetooth -> R.string.automation_page_bluetooth
    SettingsPage.Display -> R.string.automation_page_display
    SettingsPage.Sound -> R.string.automation_page_sound
    SettingsPage.Battery -> R.string.automation_page_battery
    SettingsPage.Location -> R.string.automation_page_location
    SettingsPage.Accessibility -> R.string.automation_page_accessibility
    SettingsPage.Date -> R.string.automation_page_date
    SettingsPage.Storage -> R.string.automation_page_storage
    SettingsPage.Apps -> R.string.automation_page_apps
}

internal fun ringerLabel(mode: RingerMode): Int = when (mode) {
    RingerMode.Silent -> R.string.automation_ringer_silent
    RingerMode.Vibrate -> R.string.automation_ringer_vibrate
    RingerMode.Normal -> R.string.automation_ringer_normal
}

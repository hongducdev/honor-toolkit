package vn.hongducdev.honortoolkit.feature.automation

import android.bluetooth.BluetoothManager
import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import vn.hongducdev.honortoolkit.core.adb.AdbSession
import vn.hongducdev.honortoolkit.core.adb.AdbState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import vn.hongducdev.honortoolkit.core.ui.LocalToolbarClearance
import vn.hongducdev.honortoolkit.core.ui.SettingsGroup
import vn.hongducdev.honortoolkit.core.ui.SettingsRow
import vn.hongducdev.honortoolkit.core.ui.SwitchRow
import vn.hongducdev.honortoolkit.feature.tune.TuneAutomation

/**
 * Rule editor. It keeps a local copy and only hands it to the view model on "Lưu", so a half-typed
 * rule never reaches the service. The settings action shows the Tune catalogue first; a key outside
 * it needs the "Nâng cao" switch and an explicit confirmation.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AutomationEditor(
    rule: AutomationRule,
    onSave: (AutomationRule) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember(rule.id) { mutableStateOf(rule.name) }
    var enabled by remember(rule.id) { mutableStateOf(rule.enabled) }
    var trigger by remember(rule.id) { mutableStateOf(rule.trigger) }
    var actions by remember(rule.id) { mutableStateOf(rule.actions) }
    var pickingTrigger by remember { mutableStateOf(false) }
    var addingAction by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = LocalToolbarClearance.current,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "name") {
            SettingsGroup(title = null) {
                SettingsRow {
                    Field(
                        label = stringResource(R.string.automation_editor_name),
                        value = name,
                        onChange = { name = it },
                    )
                }
                SwitchRow(
                    label = stringResource(R.string.automation_editor_enabled),
                    checked = enabled,
                    onChange = { enabled = it },
                )
            }
        }
        item(key = "trigger") {
            SettingsGroup(title = stringResource(R.string.automation_editor_trigger)) {
                SettingsRow {
                    Text(trigger.summary(), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { pickingTrigger = true }) {
                        Text(stringResource(R.string.automation_editor_choose_trigger))
                    }
                    TriggerFields(trigger) { trigger = it }
                }
            }
        }
        item(key = "actions") {
            SettingsGroup(title = stringResource(R.string.automation_editor_actions)) {
                if (actions.isEmpty()) {
                    SettingsRow {
                        Text(
                            stringResource(R.string.automation_editor_no_actions),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                actions.forEachIndexed { index, action ->
                    SettingsRow {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                action.summary(),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { actions = actions.filterIndexed { i, _ -> i != index } }) {
                                Text(stringResource(R.string.automation_editor_remove_action))
                            }
                        }
                    }
                }
                SettingsRow {
                    Button(onClick = { addingAction = true }) {
                        Text(stringResource(R.string.automation_editor_add_action))
                    }
                }
            }
        }
        item(key = "save") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onSave(rule.copy(name = name.trim(), enabled = enabled, trigger = trigger, actions = actions)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.automation_save))
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.automation_cancel))
                }
            }
        }
    }

    if (pickingTrigger) {
        TriggerKindDialog(
            onPick = {
                trigger = it.create()
                pickingTrigger = false
            },
            onDismiss = { pickingTrigger = false },
        )
    }

    if (addingAction) {
        AddActionDialog(
            initial = Action.Wait(5),
            onAdd = {
                actions = actions + it
                addingAction = false
            },
            onDismiss = { addingAction = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TriggerFields(trigger: Trigger, onChange: (Trigger) -> Unit) {
    when (trigger) {
        is Trigger.Charger -> OptionChips(
            selected = trigger.plugged,
            options = listOf(
                true to R.string.automation_trigger_plugged,
                false to R.string.automation_trigger_unplugged,
            ),
            onSelect = { onChange(trigger.copy(plugged = it)) },
        )
        is Trigger.BatteryLevel -> {
            OptionChips(
                selected = trigger.dropping,
                options = listOf(
                    true to R.string.automation_editor_battery_dropping,
                    false to R.string.automation_editor_battery_rising,
                ),
                onSelect = { onChange(trigger.copy(dropping = it)) },
            )
            NumberField(
                label = stringResource(R.string.automation_editor_percent),
                value = trigger.percent,
                onValue = { onChange(trigger.copy(percent = it)) },
                maxDigits = 3,
            )
        }
        is Trigger.ScreenOn, is Trigger.ScreenOff, is Trigger.DeviceUnlock -> Unit
        is Trigger.DoNotDisturb -> OptionChips(
            selected = trigger.on,
            options = listOf(
                true to R.string.automation_trigger_dnd_on,
                false to R.string.automation_trigger_dnd_off,
            ),
            onSelect = { onChange(trigger.copy(on = it)) },
        )
        is Trigger.Wifi -> {
            OptionChips(
                selected = trigger.connected,
                options = listOf(
                    true to R.string.automation_trigger_connected,
                    false to R.string.automation_trigger_disconnected,
                ),
                onSelect = { onChange(trigger.copy(connected = it)) },
            )
            Field(
                label = stringResource(R.string.automation_editor_ssid),
                value = trigger.ssid.orEmpty(),
                onChange = { onChange(trigger.copy(ssid = it.ifBlank { null })) },
            )
        }
        is Trigger.Bluetooth -> BluetoothFields(trigger, onChange)
        is Trigger.TimeOfDay -> {
            NumberField(
                label = stringResource(R.string.automation_editor_hour),
                value = trigger.hour,
                onValue = { onChange(trigger.copy(hour = it)) },
                maxDigits = 2,
            )
            NumberField(
                label = stringResource(R.string.automation_editor_minute),
                value = trigger.minute,
                onValue = { onChange(trigger.copy(minute = it)) },
                maxDigits = 2,
            )
        }
        is Trigger.AppOpened -> Field(
            label = stringResource(R.string.automation_editor_package),
            value = trigger.packageName,
            onChange = { onChange(trigger.copy(packageName = it.trim())) },
        )
        is Trigger.AppClosed -> Field(
            label = stringResource(R.string.automation_editor_package),
            value = trigger.packageName,
            onChange = { onChange(trigger.copy(packageName = it.trim())) },
        )
        is Trigger.Notification -> {
            Field(
                label = stringResource(R.string.automation_editor_package),
                value = trigger.packageName,
                onChange = { onChange(trigger.copy(packageName = it.trim())) },
            )
            Field(
                label = stringResource(R.string.automation_editor_keyword),
                value = trigger.keyword.orEmpty(),
                onChange = { onChange(trigger.copy(keyword = it.ifBlank { null })) },
            )
        }
        is Trigger.LocationRegion -> {
            OptionChips(
                selected = trigger.entering,
                options = listOf(
                    true to R.string.automation_editor_entering,
                    false to R.string.automation_editor_leaving,
                ),
                onSelect = { onChange(trigger.copy(entering = it)) },
            )
            Field(
                label = stringResource(R.string.automation_editor_latitude),
                value = trigger.latitude.toString(),
                onChange = { onChange(trigger.copy(latitude = it.toDoubleOrNull() ?: trigger.latitude)) },
                decimal = true,
            )
            Field(
                label = stringResource(R.string.automation_editor_longitude),
                value = trigger.longitude.toString(),
                onChange = { onChange(trigger.copy(longitude = it.toDoubleOrNull() ?: trigger.longitude)) },
                decimal = true,
            )
            Field(
                label = stringResource(R.string.automation_editor_radius),
                value = trigger.radiusMeters.toInt().toString(),
                onChange = { onChange(trigger.copy(radiusMeters = it.toFloatOrNull() ?: trigger.radiusMeters)) },
                decimal = true,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BluetoothFields(trigger: Trigger.Bluetooth, onChange: (Trigger.Bluetooth) -> Unit) {
    val context = LocalContext.current
    val devices = remember { bondedDevices(context) }
    OptionChips(
        selected = trigger.connected,
        options = listOf(
            true to R.string.automation_trigger_connected,
            false to R.string.automation_trigger_disconnected,
        ),
        onSelect = { onChange(trigger.copy(connected = it)) },
    )
    if (devices.isEmpty()) {
        Text(
            stringResource(R.string.automation_editor_bt_permission),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(stringResource(R.string.automation_editor_bt_devices), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            devices.forEach { (address, name) ->
                FilterChip(
                    selected = trigger.address == address,
                    onClick = { onChange(trigger.copy(address = address, name = name)) },
                    label = { Text(name) },
                )
            }
        }
    }
    Field(
        label = stringResource(R.string.automation_editor_bt_manual),
        value = trigger.address,
        onChange = { onChange(trigger.copy(address = it.trim())) },
    )
    Field(
        label = stringResource(R.string.automation_editor_bt_name),
        value = trigger.name,
        onChange = { onChange(trigger.copy(name = it)) },
    )
}

// BLUETOOTH_CONNECT is checked above; lint cannot follow the check through AutomationPermissions.
@SuppressLint("MissingPermission")
private fun bondedDevices(context: Context): List<Pair<String, String>> {
    if (!AutomationPermissions.granted(context, AutomationRequirement.BluetoothConnect)) return emptyList()
    return runCatching {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        adapter.bondedDevices.map { device -> device.address to (device.name ?: device.address) }
    }.getOrDefault(emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddActionDialog(initial: Action, onAdd: (Action) -> Unit, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(ActionKind.of(initial)) }
    var draft by remember { mutableStateOf(initial) }
    val context = LocalContext.current
    val adbState by AdbSession.get(context).state.collectAsState()
    val adbConnected = adbState == AdbState.Connected
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.automation_editor_choose_action)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionKind.entries.filter { !it.needsAdb || adbConnected }.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = {
                                kind = option
                                draft = option.create()
                            },
                            label = { Text(stringResource(option.label)) },
                        )
                    }
                }
                ActionFields(draft) { draft = it }
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(draft) }) { Text(stringResource(R.string.automation_editor_add_short)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.automation_cancel)) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionFields(action: Action, onChange: (Action) -> Unit) {
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
                label = stringResource(R.string.automation_editor_percent),
                value = action.percent,
                onValue = { onChange(action.copy(percent = it)) },
                maxDigits = 3,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> OptionChips(selected: T, options: List<Pair<T, Int>>, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

@Composable
private fun NumberField(label: String, value: Int, onValue: (Int) -> Unit, maxDigits: Int) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val filtered = input.filter(Char::isDigit).take(maxDigits)
            text = filtered
            filtered.toIntOrNull()?.let(onValue)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    numeric: Boolean = false,
    decimal: Boolean = false,
    maxLength: Int = 0,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text ->
            val allowed = when {
                decimal -> text.filter { it.isDigit() || it == '.' || it == '-' }
                numeric -> text.filter(Char::isDigit)
                else -> text
            }.let { if (maxLength > 0) it.take(maxLength) else it }
            onChange(allowed)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric || decimal) KeyboardType.Number else KeyboardType.Text,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriggerKindDialog(onPick: (TriggerKind) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.automation_editor_choose_trigger)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
            ) {
                TriggerKind.entries.forEach { kind ->
                    TextButton(onClick = { onPick(kind) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(kind.label), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.automation_cancel)) }
        },
    )
}

private enum class TriggerKind(val label: Int) {
    Charger(R.string.automation_trigger_charger),
    Battery(R.string.automation_trigger_battery),
    ScreenOn(R.string.automation_trigger_screen_on),
    ScreenOff(R.string.automation_trigger_screen_off),
    Wifi(R.string.automation_trigger_wifi),
    Bluetooth(R.string.automation_trigger_bluetooth),
    Time(R.string.automation_trigger_time),
    AppOpened(R.string.automation_trigger_app_opened),
    AppClosed(R.string.automation_trigger_app_closed),
    Notification(R.string.automation_trigger_notification),
    Location(R.string.automation_trigger_location),
    Unlock(R.string.automation_trigger_unlock),
    Dnd(R.string.automation_trigger_dnd),
    ;

    fun create(): Trigger = when (this) {
        Charger -> Trigger.Charger(plugged = true)
        Battery -> Trigger.BatteryLevel(percent = 20, dropping = true)
        ScreenOn -> Trigger.ScreenOn
        ScreenOff -> Trigger.ScreenOff
        Wifi -> Trigger.Wifi(connected = true)
        Bluetooth -> Trigger.Bluetooth(address = "", name = "", connected = true)
        Time -> Trigger.TimeOfDay(hour = 7, minute = 0)
        AppOpened -> Trigger.AppOpened(packageName = "")
        AppClosed -> Trigger.AppClosed(packageName = "")
        Notification -> Trigger.Notification(packageName = "")
        Location -> Trigger.LocationRegion(latitude = 0.0, longitude = 0.0, radiusMeters = 100f, entering = true)
        Unlock -> Trigger.DeviceUnlock
        Dnd -> Trigger.DoNotDisturb(on = true)
    }
}

private enum class ActionKind(val label: Int) {
    WriteSetting(R.string.automation_action_write_setting),
    Ringer(R.string.automation_action_ringer),
    LaunchApp(R.string.automation_action_launch_app),
    Notify(R.string.automation_action_notify),
    Wait(R.string.automation_action_wait),
    Shell(R.string.automation_action_shell),
    Tune(R.string.automation_action_tune),
    Strip(R.string.automation_action_strip),
    Circle(R.string.automation_action_circle),
    Flashlight(R.string.automation_action_flashlight),
    Media(R.string.automation_action_media),
    Volume(R.string.automation_action_volume),
    Screenshot(R.string.automation_action_screenshot),
    ScreenOff(R.string.automation_action_screen_off),
    ;

    /** These write protected settings or run ADB commands, so they are offered only while ADB is connected. */
    val needsAdb: Boolean get() = this == WriteSetting || this == Shell || this == Tune

    fun create(): Action = when (this) {
        WriteSetting -> Action.WriteSetting(SettingScope.Secure, key = "", value = "")
        Ringer -> Action.Ringer(RingerMode.Normal)
        LaunchApp -> Action.LaunchApp(packageName = "")
        Notify -> Action.Notify(title = "", message = "")
        Wait -> Action.Wait(seconds = 5)
        Shell -> Action.Shell(command = "")
        Tune -> Action.TuneFeature(featureId = TuneAutomation.features().firstOrNull()?.id.orEmpty(), enabled = true)
        Strip -> Action.ToolkitStrip(enabled = true)
        Circle -> Action.CircleToSearch
        Flashlight -> Action.Flashlight(on = true)
        Media -> Action.Media(MediaButton.PlayPause)
        Volume -> Action.Volume(VolumeStream.Media, percent = 50)
        Screenshot -> Action.Screenshot
        ScreenOff -> Action.ScreenOff
    }

    companion object {
        fun of(action: Action): ActionKind = when (action) {
            is Action.WriteSetting -> WriteSetting
            is Action.Ringer -> Ringer
            is Action.LaunchApp -> LaunchApp
            is Action.Notify -> Notify
            is Action.Wait -> Wait
            is Action.Shell -> Shell
            is Action.TuneFeature -> Tune
            is Action.ToolkitStrip -> Strip
            Action.CircleToSearch -> Circle
            is Action.Flashlight -> Flashlight
            is Action.Media -> Media
            is Action.Volume -> Volume
            Action.Screenshot -> Screenshot
            Action.ScreenOff -> ScreenOff
        }
    }
}

private fun ringerLabel(mode: RingerMode): Int = when (mode) {
    RingerMode.Silent -> R.string.automation_ringer_silent
    RingerMode.Vibrate -> R.string.automation_ringer_vibrate
    RingerMode.Normal -> R.string.automation_ringer_normal
}

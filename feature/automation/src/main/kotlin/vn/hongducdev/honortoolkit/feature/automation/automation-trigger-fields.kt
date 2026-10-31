package vn.hongducdev.honortoolkit.feature.automation

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** The inputs under the trigger or state summary in the editor. */
@Composable
internal fun TriggerFields(trigger: Trigger, onChange: (Trigger) -> Unit) {
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
        is Trigger.DoNotDisturb -> OnOffChips(trigger.on) { onChange(trigger.copy(on = it)) }
        is Trigger.PowerSaving -> OnOffChips(trigger.on) { onChange(trigger.copy(on = it)) }
        is Trigger.AndroidAuto -> ConnectedChips(trigger.connected) { onChange(trigger.copy(connected = it)) }
        is Trigger.Wifi -> {
            ConnectedChips(trigger.connected) { onChange(trigger.copy(connected = it)) }
            SsidField(trigger.ssid) { onChange(trigger.copy(ssid = it)) }
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
            DayChips(trigger.days) { onChange(trigger.copy(days = it)) }
        }
        is Trigger.AppOpened -> PackageField(trigger.packageName) { onChange(trigger.copy(packageName = it)) }
        is Trigger.AppClosed -> PackageField(trigger.packageName) { onChange(trigger.copy(packageName = it)) }
        is Trigger.Notification -> {
            PackageField(trigger.packageName) { onChange(trigger.copy(packageName = it)) }
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
        is Trigger.InState -> StateFields(trigger.state) { onChange(trigger.copy(state = it)) }
    }
}

@Composable
private fun StateFields(state: State, onChange: (State) -> Unit) {
    when (state) {
        State.Charging, State.ScreenOn, State.PowerSaving -> Unit
        is State.BatteryRange -> {
            NumberField(
                label = stringResource(R.string.automation_editor_min_percent),
                value = state.min,
                onValue = { onChange(state.copy(min = it)) },
                maxDigits = 3,
            )
            NumberField(
                label = stringResource(R.string.automation_editor_max_percent),
                value = state.max,
                onValue = { onChange(state.copy(max = it)) },
                maxDigits = 3,
            )
        }
        is State.TimePeriod -> {
            MinuteOfDayFields(
                minute = state.startMinute,
                hourLabel = R.string.automation_editor_start_hour,
                minuteLabel = R.string.automation_editor_start_minute,
                onChange = { onChange(state.copy(startMinute = it)) },
            )
            MinuteOfDayFields(
                minute = state.endMinute,
                hourLabel = R.string.automation_editor_end_hour,
                minuteLabel = R.string.automation_editor_end_minute,
                onChange = { onChange(state.copy(endMinute = it)) },
            )
            DayChips(state.days) { onChange(state.copy(days = it)) }
        }
        is State.WifiNetwork -> SsidField(state.ssid) { onChange(state.copy(ssid = it)) }
        is State.CalendarEvent -> Field(
            label = stringResource(R.string.automation_editor_calendar_keyword),
            value = state.keyword.orEmpty(),
            onChange = { onChange(state.copy(keyword = it.ifBlank { null })) },
        )
    }
}

/** The SSID box plus a button that fills it from the network the phone is on right now. */
@Composable
private fun SsidField(ssid: String?, onChange: (String?) -> Unit) {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    fun useCurrent() {
        val current = AutomationSnapshot.wifiSsid(context)
        failed = current == null
        if (current != null) onChange(current)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) useCurrent() else failed = true
    }
    Field(
        label = stringResource(R.string.automation_editor_ssid),
        value = ssid.orEmpty(),
        onChange = {
            failed = false
            onChange(it.ifBlank { null })
        },
    )
    TextButton(onClick = {
        if (AutomationLocation.hasPermission(context)) useCurrent() else launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }) {
        Text(stringResource(R.string.automation_editor_use_current_wifi))
    }
    if (failed) {
        Text(
            stringResource(R.string.automation_editor_current_wifi_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun OnOffChips(on: Boolean, onChange: (Boolean) -> Unit) = OptionChips(
    selected = on,
    options = listOf(true to R.string.automation_trigger_on, false to R.string.automation_trigger_off),
    onSelect = onChange,
)

@Composable
private fun ConnectedChips(connected: Boolean, onChange: (Boolean) -> Unit) = OptionChips(
    selected = connected,
    options = listOf(
        true to R.string.automation_trigger_connected,
        false to R.string.automation_trigger_disconnected,
    ),
    onSelect = onChange,
)

@Composable
internal fun PackageField(value: String, onChange: (String) -> Unit) = Field(
    label = stringResource(R.string.automation_editor_package),
    value = value,
    onChange = { onChange(it.trim()) },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BluetoothFields(trigger: Trigger.Bluetooth, onChange: (Trigger.Bluetooth) -> Unit) {
    val context = LocalContext.current
    val devices = remember { bondedDevices(context) }
    ConnectedChips(trigger.connected) { onChange(trigger.copy(connected = it)) }
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

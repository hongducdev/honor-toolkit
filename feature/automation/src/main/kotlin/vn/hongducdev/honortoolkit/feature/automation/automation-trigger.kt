package vn.hongducdev.honortoolkit.feature.automation

/**
 * What starts a rule. Values are plain data so a rule survives a reboot without a service running.
 *
 * The battery trigger only fires when the level crosses [BatteryLevel.percent] in the chosen
 * direction; sitting below the threshold does not fire again until the phone goes back above it.
 */
sealed interface Trigger {
    data class Charger(val plugged: Boolean) : Trigger

    data class BatteryLevel(val percent: Int, val dropping: Boolean) : Trigger

    data object ScreenOn : Trigger

    data object ScreenOff : Trigger

    /** The user got past the lock screen; fires on the unlock itself, not on screen-on. */
    data object DeviceUnlock : Trigger

    data class DoNotDisturb(val on: Boolean) : Trigger

    data class Wifi(val connected: Boolean, val ssid: String? = null) : Trigger

    data class Bluetooth(val address: String, val name: String, val connected: Boolean) : Trigger

    /** [days] uses ISO numbers (1 = Monday … 7 = Sunday); empty means every day. */
    data class TimeOfDay(val hour: Int, val minute: Int, val days: Set<Int> = emptySet()) : Trigger

    data class AppOpened(val packageName: String) : Trigger

    data class AppClosed(val packageName: String) : Trigger

    data class Notification(val packageName: String, val keyword: String? = null) : Trigger

    data class LocationRegion(
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Float,
        val entering: Boolean,
    ) : Trigger

    data class PowerSaving(val on: Boolean) : Trigger

    /** Car mode, which Android Auto turns on while it projects to a head unit. */
    data class AndroidAuto(val connected: Boolean) : Trigger

    /** A rule on a [State]; the engine evaluates it from snapshots, never from [TriggerEvent]s. */
    data class InState(val state: State) : Trigger
}

/**
 * Something that happened on the phone. [TimeReached] and [LocationChanged] carry a rule id because
 * their alarms and proximity intents are registered per rule; every other event is broadcast to all
 * matching rules.
 */
sealed interface TriggerEvent {
    data class ChargerChanged(val plugged: Boolean) : TriggerEvent

    data class BatteryChanged(val from: Int, val to: Int) : TriggerEvent

    data class ScreenChanged(val on: Boolean) : TriggerEvent

    data object DeviceUnlocked : TriggerEvent

    data class DndChanged(val on: Boolean) : TriggerEvent

    data class WifiChanged(val connected: Boolean, val ssid: String?) : TriggerEvent

    data class BluetoothChanged(val address: String, val name: String, val connected: Boolean) : TriggerEvent

    data class TimeReached(val hour: Int, val minute: Int, val ruleId: String) : TriggerEvent

    data class AppChanged(val packageName: String, val opened: Boolean) : TriggerEvent

    /** [text] exists only to match a keyword; it is never stored or logged. */
    data class NotificationPosted(val packageName: String, val text: String?) : TriggerEvent

    data class LocationChanged(val entering: Boolean, val ruleId: String) : TriggerEvent

    data class PowerSaveChanged(val on: Boolean) : TriggerEvent

    data class CarModeChanged(val connected: Boolean) : TriggerEvent
}

/** True when [event] is the moment this trigger describes. */
fun Trigger.matches(event: TriggerEvent): Boolean = when (this) {
    is Trigger.Charger -> event is TriggerEvent.ChargerChanged && event.plugged == plugged

    is Trigger.BatteryLevel -> event is TriggerEvent.BatteryChanged &&
        if (dropping) event.from > percent && event.to <= percent
        else event.from < percent && event.to >= percent

    Trigger.ScreenOn -> event is TriggerEvent.ScreenChanged && event.on

    Trigger.ScreenOff -> event is TriggerEvent.ScreenChanged && !event.on

    Trigger.DeviceUnlock -> event is TriggerEvent.DeviceUnlocked

    is Trigger.DoNotDisturb -> event is TriggerEvent.DndChanged && event.on == on

    is Trigger.Wifi -> event is TriggerEvent.WifiChanged &&
        event.connected == connected &&
        (ssid.isNullOrBlank() || event.ssid.equals(ssid, ignoreCase = true))

    is Trigger.Bluetooth -> event is TriggerEvent.BluetoothChanged &&
        event.connected == connected &&
        event.address.equals(address, ignoreCase = true)

    is Trigger.TimeOfDay -> event is TriggerEvent.TimeReached && event.hour == hour && event.minute == minute

    is Trigger.AppOpened -> event is TriggerEvent.AppChanged && event.opened && event.packageName == packageName

    is Trigger.AppClosed -> event is TriggerEvent.AppChanged && !event.opened && event.packageName == packageName

    is Trigger.Notification -> event is TriggerEvent.NotificationPosted &&
        event.packageName == packageName &&
        (keyword.isNullOrBlank() || event.text?.contains(keyword, ignoreCase = true) == true)

    is Trigger.LocationRegion -> event is TriggerEvent.LocationChanged && event.entering == entering

    is Trigger.PowerSaving -> event is TriggerEvent.PowerSaveChanged && event.on == on

    is Trigger.AndroidAuto -> event is TriggerEvent.CarModeChanged && event.connected == connected

    is Trigger.InState -> false
}

/** Short Vietnamese line for the rule list. */
fun Trigger.summary(): String = when (this) {
    is Trigger.Charger -> if (plugged) "Khi cắm sạc" else "Khi rút sạc"
    is Trigger.BatteryLevel -> if (dropping) "Khi pin còn $percent% trở xuống" else "Khi pin lên $percent% trở lên"
    Trigger.ScreenOn -> "Khi bật màn hình"
    Trigger.ScreenOff -> "Khi tắt màn hình"
    Trigger.DeviceUnlock -> "Khi mở khoá máy"
    is Trigger.DoNotDisturb -> if (on) "Khi bật Không làm phiền" else "Khi tắt Không làm phiền"
    is Trigger.Wifi -> buildString {
        append(if (connected) "Khi Wi-Fi kết nối" else "Khi Wi-Fi ngắt")
        if (!ssid.isNullOrBlank()) append(" ($ssid)")
    }
    is Trigger.Bluetooth -> buildString {
        append(if (connected) "Khi Bluetooth kết nối" else "Khi Bluetooth ngắt")
        if (name.isNotBlank()) append(" ($name)")
    }
    is Trigger.TimeOfDay -> "Vào %02d:%02d%s".format(hour, minute, daysSuffix(days))
    is Trigger.AppOpened -> "Khi mở $packageName"
    is Trigger.AppClosed -> "Khi đóng $packageName"
    is Trigger.Notification -> buildString {
        append("Khi có thông báo từ $packageName")
        if (!keyword.isNullOrBlank()) append(" chứa “$keyword”")
    }
    is Trigger.LocationRegion -> buildString {
        append(if (entering) "Khi vào vùng" else "Khi ra khỏi vùng")
        append(" (%.4f, %.4f, %.0f m)".format(java.util.Locale.US, latitude, longitude, radiusMeters))
    }
    is Trigger.PowerSaving -> if (on) "Khi bật tiết kiệm pin" else "Khi tắt tiết kiệm pin"
    is Trigger.AndroidAuto -> if (connected) "Khi kết nối Android Auto" else "Khi ngắt Android Auto"
    is Trigger.InState -> state.summary()
}

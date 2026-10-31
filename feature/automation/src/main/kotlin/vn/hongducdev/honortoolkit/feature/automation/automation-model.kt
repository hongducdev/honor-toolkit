package vn.hongducdev.honortoolkit.feature.automation

/** Which `Settings` table a key belongs to. [cli] is the name `settings` prints and stores. */
enum class SettingScope(val cli: String) {
    System("system"),
    Secure("secure"),
    Global("global"),
}

/** How the ringer should sound after an action. */
enum class RingerMode { Silent, Vibrate, Normal }

enum class MediaButton { PlayPause, Next, Previous }

enum class VolumeStream { Media, Ring, Alarm }

/**
 * What starts a rule. Values are plain data so a rule survives a reboot without a service running.
 *
 * The battery trigger only fires when the level crosses [percent] in the chosen direction; sitting
 * below the threshold does not fire again until the phone goes back above it.
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

    data class TimeOfDay(val hour: Int, val minute: Int) : Trigger

    data class AppOpened(val packageName: String) : Trigger

    data class AppClosed(val packageName: String) : Trigger

    data class Notification(val packageName: String, val keyword: String? = null) : Trigger

    data class LocationRegion(
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Float,
        val entering: Boolean,
    ) : Trigger
}

/** What a rule does. Actions run one after another, in list order. */
sealed interface Action {
    data class WriteSetting(
        val scope: SettingScope,
        val key: String,
        val value: String,
        /** A key outside the Tune catalogue; the editor asks for confirmation before setting it. */
        val advanced: Boolean = false,
    ) : Action

    data class Ringer(val mode: RingerMode) : Action

    data class LaunchApp(val packageName: String) : Action

    data class Notify(val title: String, val message: String) : Action

    data class Wait(val seconds: Int) : Action

    data class Shell(val command: String) : Action

    data class TuneFeature(val featureId: String, val enabled: Boolean) : Action

    /** Turns the Circle to Search navigation-bar strip on or off through its own service. */
    data class ToolkitStrip(val enabled: Boolean) : Action

    data object CircleToSearch : Action

    data class Flashlight(val on: Boolean) : Action

    data class Media(val button: MediaButton) : Action

    data class Volume(val stream: VolumeStream, val percent: Int) : Action

    /** Both of these go through the accessibility service's global actions. */
    data object Screenshot : Action

    data object ScreenOff : Action
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
}

data class AutomationRule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val trigger: Trigger,
    val actions: List<Action>,
) {
    /** Empty when the rule can run. Messages are shown as-is in the editor. */
    fun problems(): List<String> = buildList {
        if (name.isBlank()) add("Quy tắc chưa có tên")
        if (actions.isEmpty()) add("Quy tắc chưa có hành động")
        when (val t = trigger) {
            is Trigger.BatteryLevel -> if (t.percent !in 0..100) add("Mức pin phải trong khoảng 0–100")
            is Trigger.Wifi -> if (t.ssid != null && t.ssid.isBlank()) add("SSID đang trống")
            is Trigger.Bluetooth -> {
                if (t.address.isBlank()) add("Chưa chọn thiết bị Bluetooth")
                if (!t.address.matches(MAC_PATTERN)) add("Địa chỉ Bluetooth không hợp lệ")
            }
            is Trigger.TimeOfDay -> {
                if (t.hour !in 0..23) add("Giờ phải trong khoảng 0–23")
                if (t.minute !in 0..59) add("Phút phải trong khoảng 0–59")
            }
            is Trigger.AppOpened -> if (!t.packageName.isPackageName()) add("Tên gói ứng dụng không hợp lệ")
            is Trigger.AppClosed -> if (!t.packageName.isPackageName()) add("Tên gói ứng dụng không hợp lệ")
            is Trigger.Notification -> {
                if (!t.packageName.isPackageName()) add("Tên gói ứng dụng không hợp lệ")
                if (t.keyword != null && t.keyword.isBlank()) add("Từ khoá đang trống")
            }
            is Trigger.LocationRegion -> {
                if (t.latitude !in -90.0..90.0) add("Vĩ độ phải trong khoảng -90…90")
                if (t.longitude !in -180.0..180.0) add("Kinh độ phải trong khoảng -180…180")
                if (t.radiusMeters !in 1f..100_000f) add("Bán kính phải trong khoảng 1–100000 m")
            }
            else -> Unit
        }
        actions.forEachIndexed { index, action ->
            val at = "Hành động ${index + 1}: "
            when (action) {
                is Action.WriteSetting -> {
                    if (action.key.isBlank()) add(at + "chưa có khoá")
                    if (action.key.any { it.isWhitespace() }) add(at + "khoá không được chứa khoảng trắng")
                }
                is Action.LaunchApp -> if (!action.packageName.isPackageName()) add(at + "tên gói không hợp lệ")
                is Action.Notify -> {
                    if (action.title.isBlank()) add(at + "chưa có tiêu đề")
                    if (action.message.isBlank()) add(at + "chưa có nội dung")
                }
                is Action.Wait -> if (action.seconds !in 1..3_600) add(at + "thời gian chờ phải trong khoảng 1–3600 giây")
                is Action.Shell -> if (action.command.isBlank()) add(at + "lệnh đang trống")
                is Action.TuneFeature -> if (action.featureId.isBlank()) add(at + "chưa chọn tính năng")
                is Action.Volume -> if (action.percent !in 0..100) add(at + "âm lượng phải trong khoảng 0–100%")
                else -> Unit
            }
        }
    }

    val isValid: Boolean get() = problems().isEmpty()

    private companion object {
        val MAC_PATTERN = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
    }
}

internal fun String.isPackageName(): Boolean =
    isNotBlank() && matches(Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$"))

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
    is Trigger.TimeOfDay -> "Vào %02d:%02d".format(hour, minute)
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
}

/** Short Vietnamese line for the action list and the log. */
fun Action.summary(): String = when (this) {
    is Action.WriteSetting -> "Ghi ${scope.cli}/$key = $value"
    is Action.Ringer -> "Đổi chế độ âm thanh: " + when (mode) {
        RingerMode.Silent -> "im lặng"
        RingerMode.Vibrate -> "rung"
        RingerMode.Normal -> "chuông"
    }
    is Action.LaunchApp -> "Mở $packageName"
    is Action.Notify -> "Gửi thông báo “$title”"
    is Action.Wait -> "Chờ ${seconds}s"
    is Action.Shell -> "Chạy lệnh shell"
    is Action.TuneFeature -> (if (enabled) "Bật " else "Tắt ") + "tính năng Toolkit: $featureId"
    is Action.ToolkitStrip -> (if (enabled) "Bật " else "Tắt ") + "dải thanh điều hướng"
    Action.CircleToSearch -> "Mở Circle to Search"
    is Action.Flashlight -> if (on) "Bật đèn pin" else "Tắt đèn pin"
    is Action.Media -> when (button) {
        MediaButton.PlayPause -> "Phát / dừng nhạc"
        MediaButton.Next -> "Bài tiếp theo"
        MediaButton.Previous -> "Bài trước"
    }
    is Action.Volume -> "Âm lượng " + when (stream) {
        VolumeStream.Media -> "nhạc"
        VolumeStream.Ring -> "chuông"
        VolumeStream.Alarm -> "báo thức"
    } + " $percent%"
    Action.Screenshot -> "Chụp màn hình"
    Action.ScreenOff -> "Tắt màn hình"
}

sealed interface ActionResult {
    data object Done : ActionResult

    data class Skipped(val reason: String) : ActionResult

    data class Failed(val reason: String) : ActionResult
}

/** One action and what happened to it. */
data class AutomationStep(val action: Action, val result: ActionResult)

/** One rule and what its actions did for a trigger event. */
data class AutomationOutcome(val ruleId: String, val ruleName: String, val steps: List<AutomationStep>)

/** One line of the user-visible journal. Notification text never reaches this type. */
data class AutomationLogEntry(
    val at: Long,
    val ruleName: String,
    val action: String,
    val result: ActionResult,
)

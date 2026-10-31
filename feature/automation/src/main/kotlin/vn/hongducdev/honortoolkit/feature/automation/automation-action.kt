package vn.hongducdev.honortoolkit.feature.automation

import android.provider.Settings

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

/** [Flip] switches to the opposite of the current state. */
enum class Toggle { On, Off, Flip }

enum class NetworkKind { Wifi, MobileData }

enum class CameraKind { Photo, Video, QrScanner }

enum class HapticKind { Tick, Click, DoubleClick, Heavy }

/** Settings screens an action may open. Only public `Settings.ACTION_*` pages are listed. */
enum class SettingsPage(val intentAction: String) {
    Wifi(Settings.ACTION_WIFI_SETTINGS),
    Bluetooth(Settings.ACTION_BLUETOOTH_SETTINGS),
    Display(Settings.ACTION_DISPLAY_SETTINGS),
    Sound(Settings.ACTION_SOUND_SETTINGS),
    Battery(Settings.ACTION_BATTERY_SAVER_SETTINGS),
    Location(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
    Accessibility(Settings.ACTION_ACCESSIBILITY_SETTINGS),
    Date(Settings.ACTION_DATE_SETTINGS),
    Storage(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
    Apps(Settings.ACTION_APPLICATION_SETTINGS),
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

    data class Vibrate(val kind: HapticKind) : Action

    data class DoNotDisturbSwitch(val mode: Toggle) : Action

    data class MuteMedia(val mode: Toggle) : Action

    /** Normal → vibrate → silent → normal. */
    data object CycleRinger : Action

    /** Normal ↔ vibrate. */
    data object ToggleVibrate : Action

    data class LowPower(val mode: Toggle) : Action

    /** Holds the screen on for [minutes]; turning it off releases early. */
    data class KeepAwake(val mode: Toggle, val minutes: Int = 30) : Action

    data class AutoBrightness(val mode: Toggle) : Action

    /** Wi-Fi and mobile data have no public switch API, so these run `svc` over ADB. */
    data class NetworkSwitch(val kind: NetworkKind, val mode: Toggle) : Action

    data class OpenCamera(val kind: CameraKind) : Action

    data class OpenActivity(val packageName: String, val className: String) : Action

    data class OpenSettingsPage(val page: SettingsPage) : Action

    data object KeyboardPicker : Action

    data object VoiceAssistant : Action

    data object LikeCurrentSong : Action

    data object OpenNowPlaying : Action

    data object SplitScreen : Action

    /** Disables or re-enables a user-installed app over ADB (`pm disable-user`). */
    data class FreezeApp(val packageName: String, val frozen: Boolean) : Action
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
    is Action.Vibrate -> "Rung: " + when (kind) {
        HapticKind.Tick -> "nhẹ"
        HapticKind.Click -> "nhấn"
        HapticKind.DoubleClick -> "nhấn đôi"
        HapticKind.Heavy -> "mạnh"
    }
    is Action.DoNotDisturbSwitch -> mode.verb("Không làm phiền")
    is Action.MuteMedia -> mode.verb("tắt tiếng nhạc")
    Action.CycleRinger -> "Xoay vòng chế độ âm thanh"
    Action.ToggleVibrate -> "Chuyển chuông / rung"
    is Action.LowPower -> mode.verb("tiết kiệm pin")
    is Action.KeepAwake -> mode.verb("giữ màn hình sáng") + if (mode != Toggle.Off) " ($minutes phút)" else ""
    is Action.AutoBrightness -> mode.verb("độ sáng tự động")
    is Action.NetworkSwitch -> mode.verb(
        when (kind) {
            NetworkKind.Wifi -> "Wi-Fi"
            NetworkKind.MobileData -> "dữ liệu di động"
        },
    )
    is Action.OpenCamera -> when (kind) {
        CameraKind.Photo -> "Mở camera"
        CameraKind.Video -> "Mở quay video"
        CameraKind.QrScanner -> "Mở quét mã QR"
    }
    is Action.OpenActivity -> "Mở màn hình $className"
    is Action.OpenSettingsPage -> "Mở cài đặt: " + page.name
    Action.KeyboardPicker -> "Chọn bàn phím"
    Action.VoiceAssistant -> "Gọi trợ lý giọng nói"
    Action.LikeCurrentSong -> "Thích bài đang phát"
    Action.OpenNowPlaying -> "Mở ứng dụng đang phát nhạc"
    Action.SplitScreen -> "Chia đôi màn hình"
    is Action.FreezeApp -> (if (frozen) "Đóng băng " else "Bỏ đóng băng ") + packageName
}

private fun Toggle.verb(subject: String): String = when (this) {
    Toggle.On -> "Bật $subject"
    Toggle.Off -> "Tắt $subject"
    Toggle.Flip -> "Chuyển $subject"
}

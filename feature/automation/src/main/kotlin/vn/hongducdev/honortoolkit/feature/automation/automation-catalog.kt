package vn.hongducdev.honortoolkit.feature.automation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Battery5Bar
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MobileScreenShare
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VideoCameraBack
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.ui.graphics.vector.ImageVector
import vn.hongducdev.honortoolkit.feature.tune.TuneAutomation

/** What the editor offers when a rule starts from an event. */
internal enum class TriggerKind(val label: Int, val icon: ImageVector) {
    Charger(R.string.automation_trigger_charger, Icons.Rounded.BatteryChargingFull),
    Battery(R.string.automation_trigger_battery, Icons.Rounded.Battery5Bar),
    ScreenOn(R.string.automation_trigger_screen_on, Icons.Rounded.Smartphone),
    ScreenOff(R.string.automation_trigger_screen_off, Icons.Rounded.ScreenLockPortrait),
    Unlock(R.string.automation_trigger_unlock, Icons.Rounded.LockOpen),
    Time(R.string.automation_trigger_time, Icons.Rounded.Schedule),
    Wifi(R.string.automation_trigger_wifi, Icons.Rounded.Wifi),
    Bluetooth(R.string.automation_trigger_bluetooth, Icons.Rounded.Bluetooth),
    Location(R.string.automation_trigger_location, Icons.Rounded.Place),
    AppOpened(R.string.automation_trigger_app_opened, Icons.Rounded.Apps),
    AppClosed(R.string.automation_trigger_app_closed, Icons.Rounded.Apps),
    Notification(R.string.automation_trigger_notification, Icons.Rounded.Notifications),
    Dnd(R.string.automation_trigger_dnd, Icons.Rounded.DoNotDisturbOn),
    PowerSaving(R.string.automation_trigger_power_saving, Icons.Rounded.BatterySaver),
    AndroidAuto(R.string.automation_trigger_android_auto, Icons.Rounded.DirectionsCar),
    ;

    fun create(): Trigger = when (this) {
        Charger -> Trigger.Charger(plugged = true)
        Battery -> Trigger.BatteryLevel(percent = 20, dropping = true)
        ScreenOn -> Trigger.ScreenOn
        ScreenOff -> Trigger.ScreenOff
        Unlock -> Trigger.DeviceUnlock
        Time -> Trigger.TimeOfDay(hour = 7, minute = 0)
        Wifi -> Trigger.Wifi(connected = true)
        Bluetooth -> Trigger.Bluetooth(address = "", name = "", connected = true)
        Location -> Trigger.LocationRegion(latitude = 0.0, longitude = 0.0, radiusMeters = 100f, entering = true)
        AppOpened -> Trigger.AppOpened(packageName = "")
        AppClosed -> Trigger.AppClosed(packageName = "")
        Notification -> Trigger.Notification(packageName = "")
        Dnd -> Trigger.DoNotDisturb(on = true)
        PowerSaving -> Trigger.PowerSaving(on = true)
        AndroidAuto -> Trigger.AndroidAuto(connected = true)
    }

    companion object {
        fun of(trigger: Trigger): TriggerKind? = when (trigger) {
            is Trigger.Charger -> Charger
            is Trigger.BatteryLevel -> Battery
            Trigger.ScreenOn -> ScreenOn
            Trigger.ScreenOff -> ScreenOff
            Trigger.DeviceUnlock -> Unlock
            is Trigger.TimeOfDay -> Time
            is Trigger.Wifi -> Wifi
            is Trigger.Bluetooth -> Bluetooth
            is Trigger.LocationRegion -> Location
            is Trigger.AppOpened -> AppOpened
            is Trigger.AppClosed -> AppClosed
            is Trigger.Notification -> Notification
            is Trigger.DoNotDisturb -> Dnd
            is Trigger.PowerSaving -> PowerSaving
            is Trigger.AndroidAuto -> AndroidAuto
            is Trigger.InState -> null
        }
    }
}

internal enum class StateKind(val label: Int, val icon: ImageVector) {
    Charging(R.string.automation_state_charging, Icons.Rounded.BatteryChargingFull),
    Battery(R.string.automation_state_battery, Icons.Rounded.Battery5Bar),
    ScreenOn(R.string.automation_state_screen_on, Icons.Rounded.Smartphone),
    PowerSaving(R.string.automation_state_power_saving, Icons.Rounded.BatterySaver),
    Wifi(R.string.automation_state_wifi, Icons.Rounded.Wifi),
    TimePeriod(R.string.automation_state_time_period, Icons.Rounded.Schedule),
    Calendar(R.string.automation_state_calendar, Icons.Rounded.Event),
    ;

    fun create(): State = when (this) {
        Charging -> State.Charging
        Battery -> State.BatteryRange(min = 0, max = 20)
        ScreenOn -> State.ScreenOn
        PowerSaving -> State.PowerSaving
        Wifi -> State.WifiNetwork()
        TimePeriod -> State.TimePeriod(startMinute = 22 * 60, endMinute = 7 * 60)
        Calendar -> State.CalendarEvent()
    }

    companion object {
        fun of(state: State): StateKind = when (state) {
            State.Charging -> Charging
            is State.BatteryRange -> Battery
            State.ScreenOn -> ScreenOn
            State.PowerSaving -> PowerSaving
            is State.WifiNetwork -> Wifi
            is State.TimePeriod -> TimePeriod
            is State.CalendarEvent -> Calendar
        }
    }
}

/** The icon and label of whatever starts a rule, for the list card and the editor header. */
internal fun Trigger.icon(): ImageVector = when (this) {
    is Trigger.InState -> StateKind.of(state).icon
    else -> TriggerKind.of(this)?.icon ?: Icons.Rounded.Settings
}

internal fun Trigger.titleRes(): Int = when (this) {
    is Trigger.InState -> StateKind.of(state).label
    else -> TriggerKind.of(this)?.label ?: R.string.automation_editor_trigger
}

internal enum class ActionKind(val label: Int, val icon: ImageVector, val needsAdb: Boolean = false) {
    WriteSetting(R.string.automation_action_write_setting, Icons.Rounded.Settings, needsAdb = true),
    Ringer(R.string.automation_action_ringer, Icons.Rounded.VolumeUp),
    LaunchApp(R.string.automation_action_launch_app, Icons.Rounded.Apps),
    Notify(R.string.automation_action_notify, Icons.Rounded.NotificationsActive),
    Wait(R.string.automation_action_wait, Icons.Rounded.Timer),
    Shell(R.string.automation_action_shell, Icons.Rounded.Code, needsAdb = true),
    Tune(R.string.automation_action_tune, Icons.Rounded.Tune, needsAdb = true),
    Strip(R.string.automation_action_strip, Icons.Rounded.CropFree),
    Circle(R.string.automation_action_circle, Icons.Rounded.Search),
    Flashlight(R.string.automation_action_flashlight, Icons.Rounded.FlashlightOn),
    Media(R.string.automation_action_media, Icons.Rounded.PlayCircle),
    Volume(R.string.automation_action_volume, Icons.Rounded.VolumeUp),
    Screenshot(R.string.automation_action_screenshot, Icons.Rounded.Screenshot),
    ScreenOff(R.string.automation_action_screen_off, Icons.Rounded.PowerSettingsNew),
    Vibrate(R.string.automation_action_vibrate, Icons.Rounded.Vibration),
    DndSwitch(R.string.automation_action_dnd_switch, Icons.Rounded.DoNotDisturbOn),
    MuteMedia(R.string.automation_action_mute_media, Icons.Rounded.VolumeOff),
    CycleRinger(R.string.automation_action_cycle_ringer, Icons.Rounded.AutoMode),
    ToggleVibrate(R.string.automation_action_toggle_vibrate, Icons.Rounded.Vibration),
    LowPower(R.string.automation_action_low_power, Icons.Rounded.BatterySaver),
    KeepAwake(R.string.automation_action_keep_awake, Icons.Rounded.Brightness6),
    AutoBrightness(R.string.automation_action_auto_brightness, Icons.Rounded.BrightnessAuto),
    NetWifi(R.string.automation_action_net_wifi, Icons.Rounded.Wifi, needsAdb = true),
    NetData(R.string.automation_action_net_data, Icons.Rounded.SignalCellularAlt, needsAdb = true),
    Camera(R.string.automation_action_camera, Icons.Rounded.CameraAlt),
    Video(R.string.automation_action_video, Icons.Rounded.VideoCameraBack),
    Qr(R.string.automation_action_qr, Icons.Rounded.QrCodeScanner),
    OpenActivity(R.string.automation_action_open_activity, Icons.Rounded.MobileScreenShare),
    SettingsPage(R.string.automation_action_settings_page, Icons.Rounded.Settings),
    Keyboard(R.string.automation_action_keyboard, Icons.Rounded.Keyboard),
    VoiceAssistant(R.string.automation_action_voice_assistant, Icons.Rounded.Mic),
    LikeSong(R.string.automation_action_like_song, Icons.Rounded.Favorite),
    NowPlaying(R.string.automation_action_now_playing, Icons.Rounded.Audiotrack),
    SplitScreen(R.string.automation_action_split_screen, Icons.Rounded.ViewAgenda),
    FreezeApp(R.string.automation_action_freeze_app, Icons.Rounded.AcUnit, needsAdb = true),
    ;

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
        Vibrate -> Action.Vibrate(HapticKind.Click)
        DndSwitch -> Action.DoNotDisturbSwitch(Toggle.Flip)
        MuteMedia -> Action.MuteMedia(Toggle.Flip)
        CycleRinger -> Action.CycleRinger
        ToggleVibrate -> Action.ToggleVibrate
        LowPower -> Action.LowPower(Toggle.Flip)
        KeepAwake -> Action.KeepAwake(Toggle.On, minutes = 30)
        AutoBrightness -> Action.AutoBrightness(Toggle.Flip)
        NetWifi -> Action.NetworkSwitch(NetworkKind.Wifi, Toggle.Flip)
        NetData -> Action.NetworkSwitch(NetworkKind.MobileData, Toggle.Flip)
        Camera -> Action.OpenCamera(CameraKind.Photo)
        Video -> Action.OpenCamera(CameraKind.Video)
        Qr -> Action.OpenCamera(CameraKind.QrScanner)
        OpenActivity -> Action.OpenActivity(packageName = "", className = "")
        SettingsPage -> Action.OpenSettingsPage(vn.hongducdev.honortoolkit.feature.automation.SettingsPage.Wifi)
        Keyboard -> Action.KeyboardPicker
        VoiceAssistant -> Action.VoiceAssistant
        LikeSong -> Action.LikeCurrentSong
        NowPlaying -> Action.OpenNowPlaying
        SplitScreen -> Action.SplitScreen
        FreezeApp -> Action.FreezeApp(packageName = "", frozen = true)
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
            is Action.Vibrate -> Vibrate
            is Action.DoNotDisturbSwitch -> DndSwitch
            is Action.MuteMedia -> MuteMedia
            Action.CycleRinger -> CycleRinger
            Action.ToggleVibrate -> ToggleVibrate
            is Action.LowPower -> LowPower
            is Action.KeepAwake -> KeepAwake
            is Action.AutoBrightness -> AutoBrightness
            is Action.NetworkSwitch -> when (action.kind) {
                NetworkKind.Wifi -> NetWifi
                NetworkKind.MobileData -> NetData
            }
            is Action.OpenCamera -> when (action.kind) {
                CameraKind.Photo -> Camera
                CameraKind.Video -> Video
                CameraKind.QrScanner -> Qr
            }
            is Action.OpenActivity -> OpenActivity
            is Action.OpenSettingsPage -> SettingsPage
            Action.KeyboardPicker -> Keyboard
            Action.VoiceAssistant -> VoiceAssistant
            Action.LikeCurrentSong -> LikeSong
            Action.OpenNowPlaying -> NowPlaying
            Action.SplitScreen -> SplitScreen
            is Action.FreezeApp -> FreezeApp
        }
    }
}

internal fun Action.icon(): ImageVector = ActionKind.of(this).icon

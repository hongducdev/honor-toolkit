package vn.hongducdev.honortoolkit.feature.automation

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** A capability a rule needs before it can run. Shown as a card on the automation page. */
enum class AutomationRequirement {
    Accessibility,
    NotificationAccess,
    Location,
    Overlay,
    PostNotifications,
    BluetoothConnect,
    ExactAlarm,
    DndAccess,
    Calendar,
}

object AutomationPermissions {
    fun requirements(rule: AutomationRule): Set<AutomationRequirement> = buildSet {
        when (val trigger = rule.trigger) {
            is Trigger.AppOpened, is Trigger.AppClosed -> add(AutomationRequirement.Accessibility)
            is Trigger.Notification -> add(AutomationRequirement.NotificationAccess)
            is Trigger.Bluetooth -> add(AutomationRequirement.BluetoothConnect)
            is Trigger.LocationRegion -> add(AutomationRequirement.Location)
            is Trigger.Wifi -> if (!trigger.ssid.isNullOrBlank()) add(AutomationRequirement.Location)
            is Trigger.TimeOfDay -> add(AutomationRequirement.ExactAlarm)
            is Trigger.InState -> when (trigger.state) {
                is State.CalendarEvent -> add(AutomationRequirement.Calendar)
                is State.WifiNetwork -> if (!trigger.state.ssid.isNullOrBlank()) add(AutomationRequirement.Location)
                is State.TimePeriod -> add(AutomationRequirement.ExactAlarm)
                else -> Unit
            }
            else -> Unit
        }
        (rule.actions + rule.exitActions).forEach { action ->
            when (action) {
                is Action.OpenActivity, is Action.OpenSettingsPage, Action.KeyboardPicker, Action.VoiceAssistant,
                is Action.OpenCamera,
                -> add(AutomationRequirement.Overlay)
                Action.OpenNowPlaying -> {
                    add(AutomationRequirement.Overlay)
                    add(AutomationRequirement.NotificationAccess)
                }
                is Action.DoNotDisturbSwitch, Action.CycleRinger, Action.ToggleVibrate -> add(AutomationRequirement.DndAccess)
                Action.LikeCurrentSong -> add(AutomationRequirement.NotificationAccess)
                Action.SplitScreen -> add(AutomationRequirement.Accessibility)
                is Action.LaunchApp -> add(AutomationRequirement.Overlay)
                is Action.ToolkitStrip -> if (action.enabled) add(AutomationRequirement.Overlay)
                is Action.Notify -> add(AutomationRequirement.PostNotifications)
                is Action.Ringer -> if (action.mode == RingerMode.Silent) add(AutomationRequirement.DndAccess)
                Action.Screenshot, Action.ScreenOff -> add(AutomationRequirement.Accessibility)
                else -> Unit
            }
        }
    }

    fun missing(context: Context, rule: AutomationRule): Set<AutomationRequirement> =
        requirements(rule).filterNot { granted(context, it) }.toSet()

    fun missing(context: Context, rules: List<AutomationRule>): Set<AutomationRequirement> =
        rules.flatMap { missing(context, it) }.toSet()

    fun granted(context: Context, requirement: AutomationRequirement): Boolean = when (requirement) {
        AutomationRequirement.Accessibility ->
            enabledComponents(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        AutomationRequirement.NotificationAccess ->
            enabledComponents(context, "enabled_notification_listeners")
        AutomationRequirement.Location ->
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        AutomationRequirement.Overlay -> Settings.canDrawOverlays(context)
        AutomationRequirement.PostNotifications ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        AutomationRequirement.BluetoothConnect ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        AutomationRequirement.ExactAlarm -> Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        AutomationRequirement.DndAccess ->
            context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        AutomationRequirement.Calendar ->
            context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    /** Runtime permissions are requested from Compose; everything else opens the matching settings page. */
    fun runtimePermission(requirement: AutomationRequirement): String? = when (requirement) {
        AutomationRequirement.Location -> Manifest.permission.ACCESS_FINE_LOCATION
        AutomationRequirement.PostNotifications ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
        AutomationRequirement.BluetoothConnect ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else null
        AutomationRequirement.Calendar -> Manifest.permission.READ_CALENDAR
        AutomationRequirement.DndAccess -> null
        else -> null
    }

    fun settingsIntent(context: Context, requirement: AutomationRequirement): Intent? = when (requirement) {
        AutomationRequirement.Accessibility -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        AutomationRequirement.NotificationAccess -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        AutomationRequirement.Location -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        AutomationRequirement.Overlay -> Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
        AutomationRequirement.ExactAlarm ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) else null
        AutomationRequirement.DndAccess -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        AutomationRequirement.PostNotifications,
        AutomationRequirement.BluetoothConnect,
        AutomationRequirement.Calendar,
        -> null
    }

    private fun enabledComponents(context: Context, key: String): Boolean {
        val value = Settings.Secure.getString(context.contentResolver, key).orEmpty()
        return value.split(':').any { it.startsWith("${context.packageName}/") }
    }
}

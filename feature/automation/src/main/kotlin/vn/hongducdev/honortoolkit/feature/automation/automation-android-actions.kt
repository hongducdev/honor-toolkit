package vn.hongducdev.honortoolkit.feature.automation

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Rating
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.PowerManager
import android.os.UserManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.net.wifi.WifiManager
import vn.hongducdev.honortoolkit.core.adb.AdbShell
import vn.hongducdev.honortoolkit.core.adb.AdbState

internal fun Context.isUserUnlocked(): Boolean = getSystemService(UserManager::class.java).isUserUnlocked

internal fun Context.isKeyguardLocked(): Boolean = getSystemService(KeyguardManager::class.java).isKeyguardLocked

/** Holds the screen on for the keep-awake action; the wake lock lives as long as the service process. */
private object KeepAwakeLock {
    private var lock: PowerManager.WakeLock? = null

    @Suppress("DEPRECATION")
    @Synchronized
    fun hold(context: Context, minutes: Int) {
        release()
        val power = context.getSystemService(PowerManager::class.java)
        lock = power.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "honortoolkit:automation-keep-awake",
        ).also { it.acquire(minutes * 60_000L) }
    }

    @Synchronized
    fun release() {
        lock?.takeIf { it.isHeld }?.release()
        lock = null
    }

    @Synchronized
    fun isHeld(): Boolean = lock?.isHeld == true
}

/**
 * The actions built on ordinary Android APIs, plus the ones that go through ADB because Android has
 * no public switch for them. Same contract as [AndroidActionExecutor]: expected problems come back
 * as [ActionResult.Skipped], a refusal from the ROM as [ActionResult.Failed].
 */
internal class AndroidSystemActions(
    private val context: Context,
    private val shell: () -> AdbShell,
    private val adbState: () -> AdbState,
) {
    suspend fun run(action: Action): ActionResult = when (action) {
        is Action.Vibrate -> vibrate(action.kind)
        is Action.DoNotDisturbSwitch -> doNotDisturb(action.mode)
        is Action.MuteMedia -> muteMedia(action.mode)
        Action.CycleRinger -> cycleRinger()
        Action.ToggleVibrate -> toggleVibrate()
        is Action.LowPower -> lowPower(action.mode)
        is Action.KeepAwake -> keepAwake(action)
        is Action.AutoBrightness -> autoBrightness(action.mode)
        is Action.NetworkSwitch -> network(action)
        is Action.OpenCamera -> openCamera(action.kind)
        is Action.OpenActivity -> startWhenAllowed {
            Intent().setComponent(ComponentName(action.packageName, action.className))
        }
        is Action.OpenSettingsPage -> startWhenAllowed { Intent(action.page.intentAction) }
        Action.KeyboardPicker -> keyboardPicker()
        Action.VoiceAssistant -> startWhenAllowed {
            val voice = Intent(Intent.ACTION_VOICE_COMMAND)
            if (voice.resolveActivity(context.packageManager) != null) voice else Intent(Intent.ACTION_ASSIST)
        }
        Action.LikeCurrentSong -> likeCurrentSong()
        Action.OpenNowPlaying -> openNowPlaying()
        Action.SplitScreen -> splitScreen()
        is Action.FreezeApp -> freezeApp(action)
        else -> ActionResult.Failed("Hành động không thuộc nhóm hệ thống")
    }

    private fun vibrate(kind: HapticKind): ActionResult {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return ActionResult.Failed("Máy không có động cơ rung")
        if (!vibrator.hasVibrator()) return ActionResult.Failed("Máy không có động cơ rung")
        val effect = VibrationEffect.createPredefined(
            when (kind) {
                HapticKind.Tick -> VibrationEffect.EFFECT_TICK
                HapticKind.Click -> VibrationEffect.EFFECT_CLICK
                HapticKind.DoubleClick -> VibrationEffect.EFFECT_DOUBLE_CLICK
                HapticKind.Heavy -> VibrationEffect.EFFECT_HEAVY_CLICK
            },
        )
        val started = runCatching {
            // A background app only gets to vibrate with an alarm-like usage.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            }
        }.isSuccess
        return if (started) ActionResult.Done else ActionResult.Failed("Android từ chối rung")
    }

    private fun hasPolicyAccess(): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    private fun doNotDisturb(mode: Toggle): ActionResult {
        if (!hasPolicyAccess()) return ActionResult.Skipped("cần quyền Chế độ không làm phiền")
        val manager = context.getSystemService(NotificationManager::class.java)
        val currentlyOn = manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        val turnOn = when (mode) {
            Toggle.On -> true
            Toggle.Off -> false
            Toggle.Flip -> !currentlyOn
        }
        val target = if (turnOn) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL
        return if (runCatching { manager.setInterruptionFilter(target) }.isSuccess) {
            ActionResult.Done
        } else {
            ActionResult.Failed("Android từ chối đổi Không làm phiền")
        }
    }

    private fun muteMedia(mode: Toggle): ActionResult {
        val audio = context.getSystemService(AudioManager::class.java)
            ?: return ActionResult.Failed("Máy không có dịch vụ âm thanh")
        val direction = when (mode) {
            Toggle.On -> AudioManager.ADJUST_MUTE
            Toggle.Off -> AudioManager.ADJUST_UNMUTE
            Toggle.Flip -> AudioManager.ADJUST_TOGGLE_MUTE
        }
        return if (runCatching { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0) }.isSuccess) {
            ActionResult.Done
        } else {
            ActionResult.Failed("Android từ chối đổi tiếng nhạc")
        }
    }

    private fun cycleRinger(): ActionResult {
        val audio = context.getSystemService(AudioManager::class.java)
            ?: return ActionResult.Failed("Máy không có dịch vụ âm thanh")
        val silentAllowed = hasPolicyAccess()
        val next = when (audio.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE ->
                if (silentAllowed) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        return setRinger(audio, next)
    }

    private fun toggleVibrate(): ActionResult {
        val audio = context.getSystemService(AudioManager::class.java)
            ?: return ActionResult.Failed("Máy không có dịch vụ âm thanh")
        val next = if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            AudioManager.RINGER_MODE_VIBRATE
        } else {
            AudioManager.RINGER_MODE_NORMAL
        }
        return setRinger(audio, next)
    }

    private fun setRinger(audio: AudioManager, mode: Int): ActionResult =
        if (runCatching { audio.ringerMode = mode }.isSuccess) {
            ActionResult.Done
        } else {
            ActionResult.Failed("Máy không đổi được chế độ âm thanh")
        }

    private fun lowPower(mode: Toggle): ActionResult {
        val current = context.getSystemService(PowerManager::class.java).isPowerSaveMode
        val turnOn = when (mode) {
            Toggle.On -> true
            Toggle.Off -> false
            Toggle.Flip -> !current
        }
        return if (SettingsAccess.write(context, SettingScope.Global, "low_power", if (turnOn) "1" else "0")) {
            ActionResult.Done
        } else {
            ActionResult.Failed("Máy không lưu low_power; thiếu quyền WRITE_SECURE_SETTINGS hoặc ROM bỏ qua")
        }
    }

    private fun keepAwake(action: Action.KeepAwake): ActionResult {
        val turnOn = when (action.mode) {
            Toggle.On -> true
            Toggle.Off -> false
            Toggle.Flip -> !KeepAwakeLock.isHeld()
        }
        if (turnOn) KeepAwakeLock.hold(context, action.minutes.coerceIn(1, 720)) else KeepAwakeLock.release()
        return ActionResult.Done
    }

    private suspend fun autoBrightness(mode: Toggle): ActionResult {
        val resolver = context.contentResolver
        val key = Settings.System.SCREEN_BRIGHTNESS_MODE
        val current = Settings.System.getInt(resolver, key, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) ==
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        val value = if (when (mode) {
                Toggle.On -> true
                Toggle.Off -> false
                Toggle.Flip -> !current
            }
        ) {
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } else {
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        }
        if (Settings.System.canWrite(context) && runCatching { Settings.System.putInt(resolver, key, value) }.isSuccess) {
            return ActionResult.Done
        }
        return runShell("settings put system screen_brightness_mode $value", "cần quyền Sửa cài đặt hệ thống hoặc ADB để đổi độ sáng")
    }

    private suspend fun network(action: Action.NetworkSwitch): ActionResult {
        val on = when (action.kind) {
            NetworkKind.Wifi -> resolve(action.mode) { context.getSystemService(WifiManager::class.java).isWifiEnabled }
            NetworkKind.MobileData -> resolve(action.mode) { Settings.Global.getInt(context.contentResolver, "mobile_data", 1) == 1 }
        }
        val command = when (action.kind) {
            NetworkKind.Wifi -> "svc wifi " + if (on) "enable" else "disable"
            NetworkKind.MobileData -> "svc data " + if (on) "enable" else "disable"
        }
        return runShell(command, "cần kết nối ADB để đổi mạng")
    }

    private inline fun resolve(mode: Toggle, current: () -> Boolean): Boolean = when (mode) {
        Toggle.On -> true
        Toggle.Off -> false
        Toggle.Flip -> !current()
    }

    private suspend fun runShell(command: String, needAdb: String): ActionResult {
        if (!context.isUserUnlocked()) return ActionResult.Skipped("chưa mở khoá lần đầu")
        if (adbState() != AdbState.Connected) return ActionResult.Skipped(needAdb)
        val result = runCatching { shell().run(command) }.getOrNull()
            ?: return ActionResult.Failed("Không chạy được lệnh ADB")
        return if (result.isSuccess) {
            ActionResult.Done
        } else {
            ActionResult.Failed(result.output.take(200).ifBlank { "Lệnh trả mã ${result.exitCode}" })
        }
    }

    /** Null when an activity may start now; otherwise the reason it cannot. */
    private fun startBlocker(): String? = when {
        !context.isUserUnlocked() -> "chưa mở khoá lần đầu"
        context.isKeyguardLocked() -> "máy đang khoá"
        !Settings.canDrawOverlays(context) -> "cần quyền hiển thị trên ứng dụng khác để mở màn hình từ nền"
        else -> null
    }

    private fun startWhenAllowed(build: () -> Intent): ActionResult {
        startBlocker()?.let { return ActionResult.Skipped(it) }
        val started = runCatching { context.startActivity(build().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return if (started.isSuccess) ActionResult.Done else ActionResult.Failed("Android từ chối mở màn hình này")
    }

    private suspend fun openCamera(kind: CameraKind): ActionResult = when (kind) {
        CameraKind.Photo -> {
            // The secure camera opens over the lock screen; the ordinary one cannot.
            val locked = context.isKeyguardLocked()
            val blocker = if (locked) lockedCameraBlocker() else startBlocker()
            if (blocker != null) {
                ActionResult.Skipped(blocker)
            } else {
                val action = if (locked) {
                    MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE
                } else {
                    MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
                }
                startWhenAllowedNoLockCheck(Intent(action))
            }
        }
        CameraKind.Video -> startWhenAllowed { Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA) }
        CameraKind.QrScanner -> openQrScanner()
    }

    private fun lockedCameraBlocker(): String? = when {
        !context.isUserUnlocked() -> "chưa mở khoá lần đầu"
        !Settings.canDrawOverlays(context) -> "cần quyền hiển thị trên ứng dụng khác để mở màn hình từ nền"
        else -> null
    }

    private fun startWhenAllowedNoLockCheck(intent: Intent): ActionResult {
        val started = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return if (started.isSuccess) ActionResult.Done else ActionResult.Failed("Android từ chối mở camera")
    }

    /**
     * System UI keeps the component of its QR scanner in a framework resource. Google's scanner
     * closes itself for ordinary callers, so it is started as the shell user when ADB is connected.
     */
    private suspend fun openQrScanner(): ActionResult {
        val resource = Resources.getSystem().getIdentifier("config_defaultQrCodeComponent", "string", "android")
        val component = if (resource != 0) {
            ComponentName.unflattenFromString(Resources.getSystem().getString(resource).orEmpty())
        } else {
            null
        }
        if (component != null && adbState() == AdbState.Connected) {
            return runShell("am start -n ${component.flattenToShortString()}", "cần kết nối ADB")
        }
        return startWhenAllowed {
            if (component != null) Intent().setComponent(component) else Intent("com.google.android.gms.mlkit_barcode_ui.SCAN_QR_CODE")
        }
    }

    private fun keyboardPicker(): ActionResult {
        startBlocker()?.let { return ActionResult.Skipped(it) }
        val picker = context.getSystemService(InputMethodManager::class.java)
            ?: return ActionResult.Failed("Máy không có dịch vụ bàn phím")
        return if (runCatching { picker.showInputMethodPicker() }.isSuccess) {
            ActionResult.Done
        } else {
            ActionResult.Failed("Android từ chối mở bộ chọn bàn phím")
        }
    }

    private fun activeSession(): MediaController? {
        val manager = context.getSystemService(MediaSessionManager::class.java) ?: return null
        val sessions = manager.getActiveSessions(ComponentName(context, AutomationNotificationListener::class.java))
        return sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
            ?: sessions.firstOrNull()
    }

    private fun likeCurrentSong(): ActionResult {
        val session = try {
            activeSession()
        } catch (_: SecurityException) {
            return ActionResult.Skipped("cần quyền Truy cập thông báo để điều khiển trình phát")
        } ?: return ActionResult.Skipped("không có trình phát nhạc đang hoạt động")
        val custom = session.playbackState?.customActions.orEmpty().firstOrNull { candidate ->
            val text = (candidate.action + " " + candidate.name).lowercase()
            LIKE_WORDS.any { it in text }
        }
        return when {
            custom != null -> {
                session.transportControls.sendCustomAction(custom, null)
                ActionResult.Done
            }
            else -> {
                val rated = runCatching { session.transportControls.setRating(Rating.newThumbRating(true)) }.isSuccess
                if (rated) ActionResult.Done else ActionResult.Skipped("ứng dụng phát nhạc không có nút thích")
            }
        }
    }

    private fun openNowPlaying(): ActionResult {
        startBlocker()?.let { return ActionResult.Skipped(it) }
        val session = try {
            activeSession()
        } catch (_: SecurityException) {
            return ActionResult.Skipped("cần quyền Truy cập thông báo để biết ứng dụng đang phát")
        } ?: return ActionResult.Skipped("không có trình phát nhạc đang hoạt động")
        val intent = context.packageManager.getLaunchIntentForPackage(session.packageName)
            ?: return ActionResult.Failed("Không tìm thấy ứng dụng ${session.packageName}")
        val started = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return if (started.isSuccess) ActionResult.Done else ActionResult.Failed("Android từ chối mở ${session.packageName}")
    }

    private fun splitScreen(): ActionResult {
        if (context.isKeyguardLocked()) return ActionResult.Skipped("máy đang khoá")
        return when (AutomationAccessibilityService.performGlobal(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)) {
            null -> ActionResult.Skipped("cần bật dịch vụ Trợ năng của Honor Toolkit để chia đôi màn hình")
            true -> ActionResult.Done
            false -> ActionResult.Failed("Android từ chối chia đôi màn hình")
        }
    }

    /** Only apps the user installed, and never this app or the launcher: freezing those would strand the phone. */
    private suspend fun freezeApp(action: Action.FreezeApp): ActionResult {
        val pm = context.packageManager
        val info = runCatching { pm.getApplicationInfo(action.packageName, PackageManager.MATCH_DISABLED_COMPONENTS) }
            .getOrNull() ?: return ActionResult.Failed("Không tìm thấy ứng dụng ${action.packageName}")
        if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0) {
            return ActionResult.Failed("Chỉ đóng băng được ứng dụng do người dùng cài")
        }
        val home = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
        if (action.packageName == context.packageName || action.packageName == home) {
            return ActionResult.Failed("Không đóng băng ứng dụng này vì sẽ làm mất điều khiển máy")
        }
        val command = (if (action.frozen) "pm disable-user --user 0 " else "pm enable --user 0 ") + action.packageName
        return runShell(command, "cần kết nối ADB để đóng băng ứng dụng")
    }

    private companion object {
        val LIKE_WORDS = listOf("like", "heart", "favorite", "favourite", "thumb", "love", "yêu thích", "thích")
    }
}

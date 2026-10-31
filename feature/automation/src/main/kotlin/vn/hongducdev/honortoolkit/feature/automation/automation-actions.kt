package vn.hongducdev.honortoolkit.feature.automation

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.view.KeyEvent
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import vn.hongducdev.honortoolkit.core.adb.AdbSession
import vn.hongducdev.honortoolkit.core.adb.AdbShell
import vn.hongducdev.honortoolkit.core.adb.AdbState
import vn.hongducdev.honortoolkit.feature.circlesearch.NavBarStripService
import vn.hongducdev.honortoolkit.feature.circlesearch.TriggerConfig
import vn.hongducdev.honortoolkit.feature.circlesearch.TriggerRunner
import vn.hongducdev.honortoolkit.feature.circlesearch.TriggerSource
import vn.hongducdev.honortoolkit.feature.tune.TuneAutomation

/**
 * Runs actions against the phone. Every failure that the user can act on comes back as
 * [ActionResult.Skipped] with a reason (ADB off, phone locked, permission missing); a write the ROM
 * refuses comes back as [ActionResult.Failed].
 */
internal class AndroidActionExecutor(
    private val context: Context,
    private val store: AutomationStore,
    private val shell: () -> AdbShell = { AdbSession.get(context) },
    private val adbState: () -> AdbState = { AdbSession.get(context).state.value },
) : ActionExecutor {
    private val notificationIds = AtomicInteger(1_000)

    override suspend fun run(action: Action): ActionResult = when (action) {
        is Action.WriteSetting -> writeSetting(action)
        is Action.Ringer -> ringer(action)
        is Action.LaunchApp -> launchApp(action)
        is Action.Notify -> notify(action)
        is Action.Wait -> {
            delay(action.seconds.coerceIn(1, 3_600) * 1_000L)
            ActionResult.Done
        }
        is Action.Shell -> shell(action)
        is Action.TuneFeature -> tuneFeature(action)
        is Action.ToolkitStrip -> toolkitStrip(action)
        Action.CircleToSearch -> circleToSearch()
        is Action.Flashlight -> flashlight(action)
        is Action.Media -> media(action)
        is Action.Volume -> volume(action)
        Action.Screenshot -> globalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, "chụp màn hình", needsUnlocked = true)
        Action.ScreenOff -> globalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, "tắt màn hình", needsUnlocked = false)
    }

    private fun writeSetting(action: Action.WriteSetting): ActionResult {
        AutomationSettingsPolicy.refusal(action)?.let { return ActionResult.Failed(it) }
        val previous = SettingsAccess.read(context, action.scope, action.key)
        store.recordUndo(action.scope, action.key, previous)
        return if (SettingsAccess.write(context, action.scope, action.key, action.value)) {
            ActionResult.Done
        } else {
            ActionResult.Failed("Máy không lưu ${action.scope.cli}/${action.key}; có thể thiếu quyền hoặc ROM bỏ qua khoá này")
        }
    }

    private fun ringer(action: Action.Ringer): ActionResult {
        val audio = context.getSystemService(AudioManager::class.java)
            ?: return ActionResult.Failed("Máy không có dịch vụ âm thanh")
        val target = when (action.mode) {
            RingerMode.Silent -> AudioManager.RINGER_MODE_SILENT
            RingerMode.Vibrate -> AudioManager.RINGER_MODE_VIBRATE
            RingerMode.Normal -> AudioManager.RINGER_MODE_NORMAL
        }
        if (target == AudioManager.RINGER_MODE_SILENT &&
            !context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        ) {
            return ActionResult.Skipped("cần quyền Chế độ không làm phiền để đặt im lặng")
        }
        val changed = runCatching { audio.ringerMode = target }.isSuccess
        return if (changed) ActionResult.Done else ActionResult.Failed("Máy không đổi được chế độ âm thanh")
    }

    private fun launchApp(action: Action.LaunchApp): ActionResult {
        if (!unlocked()) return ActionResult.Skipped("chưa mở khoá lần đầu")
        if (locked()) return ActionResult.Skipped("máy đang khoá")
        if (!Settings.canDrawOverlays(context)) {
            return ActionResult.Skipped("cần quyền hiển thị trên ứng dụng khác để mở app từ nền")
        }
        val intent = context.packageManager.getLaunchIntentForPackage(action.packageName)
            ?: return ActionResult.Failed("Không tìm thấy ứng dụng ${action.packageName}")
        val started = runCatching {
            context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        return if (started) ActionResult.Done else ActionResult.Failed("Android từ chối mở ${action.packageName}")
    }

    private fun notify(action: Action.Notify): ActionResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return ActionResult.Skipped("chưa cấp quyền thông báo")
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ACTIONS, context.getString(R.string.automation_action_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val notification = Notification.Builder(context, CHANNEL_ACTIONS)
            .setSmallIcon(R.drawable.ic_automation)
            .setContentTitle(action.title)
            .setContentText(action.message)
            .setAutoCancel(true)
            .build()
        val posted = runCatching { manager.notify(notificationIds.incrementAndGet(), notification) }.isSuccess
        return if (posted) ActionResult.Done else ActionResult.Failed("Không gửi được thông báo")
    }

    private suspend fun shell(action: Action.Shell): ActionResult {
        if (!unlocked()) return ActionResult.Skipped("chưa mở khoá lần đầu")
        if (adbState() != AdbState.Connected) return ActionResult.Skipped("ADB chưa kết nối")
        val result = runCatching { shell().run(action.command) }.getOrNull()
            ?: return ActionResult.Failed("Không chạy được lệnh ADB")
        return if (result.isSuccess) {
            ActionResult.Done
        } else {
            ActionResult.Failed(result.output.take(200).ifBlank { "Lệnh trả mã ${result.exitCode}" })
        }
    }

    private fun tuneFeature(action: Action.TuneFeature): ActionResult {
        val feature = TuneAutomation.feature(action.featureId)
            ?: return ActionResult.Failed("Tính năng Toolkit “${action.featureId}” không còn trong danh mục")
        val failures = mutableListOf<String>()
        feature.writes.forEach { write ->
            val scope = SettingScope.entries.firstOrNull { it.cli == write.scope }
            if (scope == null) {
                failures += write.key
                return@forEach
            }
            store.recordUndo(scope, write.key, SettingsAccess.read(context, scope, write.key))
            val value = if (action.enabled) write.onValue else write.offValue
            if (!SettingsAccess.write(context, scope, write.key, value)) failures += write.key
        }
        return if (failures.isEmpty()) {
            ActionResult.Done
        } else {
            ActionResult.Failed("Máy không lưu ${failures.size}/${feature.writes.size} khoá của tính năng này")
        }
    }

    private fun circleToSearch(): ActionResult {
        if (locked()) return ActionResult.Skipped("máy đang khoá")
        TriggerRunner.run(context, 0, TriggerSource.Automation)
        return ActionResult.Done
    }

    /**
     * The strip keeps its setting in the app's CE prefs and runs as an overlay, so it needs an
     * unlocked phone and the overlay permission. [NavBarStripService.sync] starts or stops it.
     */
    private fun toolkitStrip(action: Action.ToolkitStrip): ActionResult {
        if (!unlocked()) return ActionResult.Skipped("chưa mở khoá lần đầu")
        if (action.enabled && !Settings.canDrawOverlays(context)) {
            return ActionResult.Skipped("cần quyền hiển thị trên ứng dụng khác để bật dải")
        }
        TriggerConfig(context).stripEnabled = action.enabled
        NavBarStripService.sync(context)
        return ActionResult.Done
    }

    private fun flashlight(action: Action.Flashlight): ActionResult {
        val cameras = context.getSystemService(CameraManager::class.java)
            ?: return ActionResult.Failed("Máy không có dịch vụ camera")
        val id = runCatching {
            cameras.cameraIdList.firstOrNull { cameraId ->
                val info = cameras.getCameraCharacteristics(cameraId)
                info.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    info.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        }.getOrNull() ?: return ActionResult.Failed("Máy không có đèn flash sau")
        val changed = runCatching { cameras.setTorchMode(id, action.on) }.isSuccess
        return if (changed) ActionResult.Done else ActionResult.Failed("Không bật được đèn pin; camera có thể đang được dùng")
    }

    private fun media(action: Action.Media): ActionResult {
        val audio = context.getSystemService(AudioManager::class.java)
            ?: return ActionResult.Failed("Máy không có dịch vụ âm thanh")
        val code = when (action.button) {
            MediaButton.PlayPause -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            MediaButton.Next -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaButton.Previous -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        val sent = runCatching {
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }.isSuccess
        return if (sent) ActionResult.Done else ActionResult.Failed("Không gửi được phím media")
    }

    private fun volume(action: Action.Volume): ActionResult {
        val audio = context.getSystemService(AudioManager::class.java)
            ?: return ActionResult.Failed("Máy không có dịch vụ âm thanh")
        val stream = when (action.stream) {
            VolumeStream.Media -> AudioManager.STREAM_MUSIC
            VolumeStream.Ring -> AudioManager.STREAM_RING
            VolumeStream.Alarm -> AudioManager.STREAM_ALARM
        }
        val level = audio.getStreamMaxVolume(stream) * action.percent.coerceIn(0, 100) / 100
        val changed = runCatching { audio.setStreamVolume(stream, level, 0) }.isSuccess
        return if (changed) ActionResult.Done else ActionResult.Failed("Android từ chối đổi âm lượng (có thể do Không làm phiền)")
    }

    private fun globalAction(action: Int, label: String, needsUnlocked: Boolean): ActionResult {
        if (needsUnlocked && locked()) return ActionResult.Skipped("máy đang khoá")
        return when (AutomationAccessibilityService.performGlobal(action)) {
            null -> ActionResult.Skipped("cần bật dịch vụ Trợ năng của Honor Toolkit để $label")
            true -> ActionResult.Done
            false -> ActionResult.Failed("Android từ chối $label")
        }
    }

    private fun unlocked(): Boolean = context.getSystemService(UserManager::class.java).isUserUnlocked

    private fun locked(): Boolean = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private companion object {
        const val CHANNEL_ACTIONS = "automation_actions"
    }
}

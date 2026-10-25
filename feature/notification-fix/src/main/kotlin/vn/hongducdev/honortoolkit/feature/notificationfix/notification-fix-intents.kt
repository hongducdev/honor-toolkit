package vn.hongducdev.honortoolkit.feature.notificationfix

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Best-effort deep links into OEM settings screens. MagicOS does not expose stable public intents
 * for its autostart/launch-management screen, so [openAppLaunchManagement] tries a few known
 * component names (carried over from Huawei's EMUI lineage) before giving up.
 */
internal object NotificationFixIntents {

    fun openBatterySettings(context: Context): Boolean =
        tryStart(context, Intent(Intent.ACTION_POWER_USAGE_SUMMARY), Intent(Settings.ACTION_SETTINGS))

    fun openAppLaunchManagement(context: Context): Boolean =
        tryStart(
            context,
            Intent().setClassName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            Intent().setClassName("com.hihonor.systemmanager", "com.hihonor.systemmanager.appcontrol.activity.StartupAppControlActivity"),
            Intent().setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            Intent().setClassName("com.hihonor.systemmanager", "com.hihonor.systemmanager.optimize.process.ProtectActivity"),
            Intent(Settings.ACTION_APPLICATION_SETTINGS),
        )

    private fun tryStart(context: Context, vararg intents: Intent): Boolean {
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
                // Not resolvable on this device, fall through to the next candidate.
            }
        }
        return false
    }
}

package vn.hongducdev.honortoolkit.feature.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the runtime after boot (including before the first unlock) and after an app update. */
class AutomationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ALLOWED) return
        AutomationForegroundService.sync(context)
    }

    private companion object {
        val ALLOWED = setOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}

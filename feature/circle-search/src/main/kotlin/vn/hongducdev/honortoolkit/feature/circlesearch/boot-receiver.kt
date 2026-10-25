package vn.hongducdev.honortoolkit.feature.circlesearch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NavBarStripService.sync(context)
    }
}

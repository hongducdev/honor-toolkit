package vn.hongducdev.honortoolkit.feature.automation

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Reports posted notifications for [Trigger.Notification]. The title and text are read only to test
 * the keyword and are handed to the engine in memory; the engine never writes notification content
 * to the log or to preferences. One-time codes must not end up in a file the user may share.
 */
class AutomationNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (notification.packageName == packageName) return
        val wanted = AutomationRuntime.hasEnabledRule(this) {
            it is Trigger.Notification && it.packageName == notification.packageName
        }
        if (!wanted) return
        AutomationRuntime.dispatch(
            this,
            TriggerEvent.NotificationPosted(notification.packageName, extractText(notification.notification)),
        )
    }

    private fun extractText(notification: Notification): String? {
        val extras = notification.extras ?: return null
        val parts = listOf(
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUMMARY_TEXT,
        ).mapNotNull { key -> extras.getCharSequence(key)?.toString()?.takeIf { it.isNotBlank() } }
        return parts.joinToString(" ").takeIf { it.isNotBlank() }
    }
}

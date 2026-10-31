package vn.hongducdev.honortoolkit.feature.automation

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/**
 * Reports app switches for [Trigger.AppOpened] and [Trigger.AppClosed]. The service only listens to
 * window-state changes and never reads window content.
 *
 * The system event filter is left open on purpose. Restricting `packageNames` to the packages that
 * have rules would hide the transition to any other app, so the next return to a tracked app would
 * not look like an open; the tracking in [onAccessibilityEvent] filters by package instead.
 */
class AutomationAccessibilityService : AccessibilityService() {
    private var currentPackage: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        if (packageName == this.packageName || isTransientWindow(packageName)) return
        if (packageName == currentPackage) return
        val previous = currentPackage
        currentPackage = packageName

        if (AutomationRuntime.hasEnabledRule(this) { it is Trigger.AppOpened && it.packageName == packageName }) {
            AutomationRuntime.dispatch(this, TriggerEvent.AppChanged(packageName, opened = true))
        }
        if (previous != null &&
            AutomationRuntime.hasEnabledRule(this) { it is Trigger.AppClosed && it.packageName == previous }
        ) {
            AutomationRuntime.dispatch(this, TriggerEvent.AppChanged(previous, opened = false))
        }
    }

    /** The shade, the keyboard and system dialogs are windows over an app, not a switch away from it. */
    private fun isTransientWindow(packageName: String): Boolean {
        if (packageName == SYSTEM_UI || packageName == "android") return true
        val methods = getSystemService(InputMethodManager::class.java)?.enabledInputMethodList.orEmpty()
        return methods.any { it.packageName == packageName }
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val SYSTEM_UI = "com.android.systemui"

        @Volatile
        private var instance: AutomationAccessibilityService? = null

        /** Null when the service is off, so the caller can report the missing permission. */
        fun performGlobal(action: Int): Boolean? = instance?.performGlobalAction(action)
    }
}

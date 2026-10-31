package vn.hongducdev.honortoolkit.feature.automation

import android.content.Context

/** Which rule ids already have an alarm or proximity alert registered, so a resync can cancel them. */
internal class AutomationScheduleStore(context: Context) {
    private val prefs = context.applicationContext
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun ids(kind: Kind): Set<String> = prefs.getStringSet(kind.key, emptySet()).orEmpty()

    fun save(kind: Kind, ids: Set<String>) {
        prefs.edit().putStringSet(kind.key, ids).apply()
    }

    enum class Kind(val key: String) {
        Alarms("alarms"),
        Locations("locations"),
    }

    private companion object {
        const val PREFS = "automation_schedule"
    }
}

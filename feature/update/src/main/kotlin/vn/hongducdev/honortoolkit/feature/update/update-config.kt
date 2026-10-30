package vn.hongducdev.honortoolkit.feature.update

import android.content.Context

/** Update settings and the last check time, kept in their own preferences file. */
class UpdateConfig(context: Context) {
    private val prefs = context.getSharedPreferences("update", Context.MODE_PRIVATE)

    var autoCheck: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CHECK, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CHECK, value).apply()

    var lastCheckedAt: Long
        get() = prefs.getLong(KEY_LAST_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHECK, value).apply()

    var skippedVersion: String?
        get() = prefs.getString(KEY_SKIPPED, null)
        set(value) = prefs.edit().putString(KEY_SKIPPED, value).apply()

    private companion object {
        const val KEY_AUTO_CHECK = "auto_check"
        const val KEY_LAST_CHECK = "last_check"
        const val KEY_SKIPPED = "skipped_version"
    }
}

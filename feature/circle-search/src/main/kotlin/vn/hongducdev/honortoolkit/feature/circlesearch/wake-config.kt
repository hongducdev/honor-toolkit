package vn.hongducdev.honortoolkit.feature.circlesearch

import android.content.Context
import android.os.PowerManager

/**
 * Persistent state for the wake-word service. The service writes [status] and [lastHeard] so the
 * screen can show what happened even after MagicOS killed the process (logcat is filtered on this
 * ROM, so SharedPreferences is the only trail).
 */
class WakeConfig(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val power = context.getSystemService(PowerManager::class.java)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var status: String
        get() = prefs.getString(KEY_STATUS, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_STATUS, value).apply()

    var lastHeard: String
        get() = prefs.getString(KEY_LAST_HEARD, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_LAST_HEARD, value).apply()

    var lastHeardAt: Long
        get() = prefs.getLong(KEY_LAST_HEARD_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_HEARD_AT, value).apply()

    /** Screen-on is more sensitive because the microphone is farther from the user at night. */
    fun sensitivity(): Int = if (power.isInteractive) SENSITIVITY_SCREEN_ON else SENSITIVITY_SCREEN_OFF

    private companion object {
        const val PREFS = "wake_word"
        const val KEY_ENABLED = "enabled"
        const val KEY_STATUS = "status"
        const val KEY_LAST_HEARD = "last_heard"
        const val KEY_LAST_HEARD_AT = "last_heard_at"
        const val SENSITIVITY_SCREEN_ON = 80
        const val SENSITIVITY_SCREEN_OFF = 75
    }
}

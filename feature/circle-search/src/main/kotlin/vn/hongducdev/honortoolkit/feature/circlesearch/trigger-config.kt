package vn.hongducdev.honortoolkit.feature.circlesearch

import android.content.Context

class TriggerConfig(context: Context) {
    private val prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE)

    var delayMs: Long
        get() = prefs.getLong(KEY_DELAY, DEFAULT_DELAY_MS)
        set(value) = prefs.edit().putLong(KEY_DELAY, value).apply()

    var tileDelayMs: Long
        get() = prefs.getLong(KEY_TILE_DELAY, DEFAULT_TILE_DELAY_MS)
        set(value) = prefs.edit().putLong(KEY_TILE_DELAY, value).apply()

    var vibrate: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATE, value).apply()

    var lastResult: String
        get() = prefs.getString(KEY_LAST_RESULT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_RESULT, value).apply()

    var stripEnabled: Boolean
        get() = prefs.getBoolean(KEY_STRIP_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_STRIP_ENABLED, value).apply()

    var stripWidthPercent: Long
        get() = prefs.getLong(KEY_STRIP_WIDTH, DEFAULT_STRIP_WIDTH_PERCENT)
        set(value) = prefs.edit().putLong(KEY_STRIP_WIDTH, value).apply()

    var stripHeightDp: Long
        get() = prefs.getLong(KEY_STRIP_HEIGHT, DEFAULT_STRIP_HEIGHT_DP)
        set(value) = prefs.edit().putLong(KEY_STRIP_HEIGHT, value).apply()

    var stripOffsetDp: Long
        get() = prefs.getLong(KEY_STRIP_OFFSET, DEFAULT_STRIP_OFFSET_DP)
        set(value) = prefs.edit().putLong(KEY_STRIP_OFFSET, value).apply()

    var stripVisible: Boolean
        get() = prefs.getBoolean(KEY_STRIP_VISIBLE, false)
        set(value) = prefs.edit().putBoolean(KEY_STRIP_VISIBLE, value).apply()

    fun stripAction(gesture: StripGesture): StripAction {
        val stored = prefs.getString(KEY_STRIP_ACTION + gesture.key, null)
        return if (stored == null) StripAction.defaultFor(gesture) else StripAction.fromKey(stored)
    }

    fun setStripAction(gesture: StripGesture, action: StripAction) {
        prefs.edit().putString(KEY_STRIP_ACTION + gesture.key, action.key).apply()
    }

    companion object {
        const val MAX_DELAY_MS = 1000L
        const val MAX_STRIP_WIDTH_PERCENT = 100L
        const val MAX_STRIP_HEIGHT_DP = 60L
        const val MAX_STRIP_OFFSET_DP = 80L
        const val MIN_STRIP_OFFSET_DP = -30L
        private const val DEFAULT_STRIP_OFFSET_DP = -18L
        private const val DEFAULT_DELAY_MS = 150L
        private const val DEFAULT_TILE_DELAY_MS = 350L
        private const val DEFAULT_STRIP_WIDTH_PERCENT = 40L
        private const val DEFAULT_STRIP_HEIGHT_DP = 22L
        private const val KEY_DELAY = "delay_ms"
        private const val KEY_TILE_DELAY = "tile_delay_ms"
        private const val KEY_VIBRATE = "vibrate"
        private const val KEY_LAST_RESULT = "last_result"
        private const val KEY_STRIP_ENABLED = "strip_enabled"
        private const val KEY_STRIP_WIDTH = "strip_width_percent"
        private const val KEY_STRIP_HEIGHT = "strip_height_dp"
        private const val KEY_STRIP_OFFSET = "strip_offset_dp"
        private const val KEY_STRIP_VISIBLE = "strip_visible"
        private const val KEY_STRIP_ACTION = "strip_action_"
    }
}

package vn.hongducdev.honortoolkit.feature.circlesearch

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Where a Circle to Search request came from; the label is shown in the diagnostic log. */
enum class TriggerSource(val labelRes: Int) {
    Shortcut(R.string.source_shortcut),
    Tile(R.string.source_tile),
    Strip(R.string.source_strip),
    GeminiIcon(R.string.source_gemini_icon),
    Wake(R.string.source_wake),
    Test(R.string.source_test),
    Automation(R.string.source_automation),
}

/**
 * The last 20 launch attempts, newest first, kept in SharedPreferences. MagicOS hides third-party
 * logcat lines and [TriggerConfig.lastResult] holds a single string, so this is the only trail a
 * user can copy when a launch is refused.
 */
internal object TriggerLog {
    private const val PREFS = "circle_search_diag"
    private const val KEY = "log"
    private const val MAX_LINES = 20
    private val time = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    fun add(context: Context, source: TriggerSource, action: String, detail: String, ok: Boolean) {
        val line = buildString {
            append(time.format(Date()))
            append(" | ")
            append(context.getString(source.labelRes))
            append(" → ")
            append(action)
            append(" | ")
            append(detail.replace('\n', ' '))
            append(" | ")
            append(if (ok) "OK" else "FAIL")
        }
        synchronized(this) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY, append(prefs.getString(KEY, null), line, MAX_LINES)).apply()
        }
    }

    fun text(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    /** Newest line first, blank lines dropped, at most [maxLines] lines kept. */
    internal fun append(existing: String?, line: String, maxLines: Int): String {
        val kept = existing.orEmpty().lineSequence().filter { it.isNotBlank() }.take(maxLines - 1).toList()
        return (listOf(line) + kept).joinToString("\n")
    }
}

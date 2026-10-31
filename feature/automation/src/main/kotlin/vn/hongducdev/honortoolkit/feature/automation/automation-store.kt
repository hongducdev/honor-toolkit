package vn.hongducdev.honortoolkit.feature.automation

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** The previous value of a key before an automation write, used by "Hoàn tác lần ghi gần nhất". */
data class SettingSnapshot(val scope: SettingScope, val key: String, val value: String?)

/**
 * Rules, the journal and the undo snapshots live in device-protected storage so the engine can read
 * them before the first unlock after a reboot (direct boot). Nothing in here is a secret: rule names,
 * actions and log lines only; notification text is never stored.
 */
class AutomationStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val rulesLock = Any()
    private var cachedRaw: String? = null
    private var cachedRules: List<AutomationRule> = emptyList()

    /** Decoding is skipped while the stored JSON is unchanged; every accessibility and notification event calls this. */
    fun rules(): List<AutomationRule> = synchronized(rulesLock) {
        val raw = prefs.getString(KEY_RULES, null)
        if (raw != cachedRaw) {
            cachedRules = AutomationJson.decodeRules(raw)
            cachedRaw = raw
        }
        cachedRules
    }

    fun rule(id: String): AutomationRule? = rules().firstOrNull { it.id == id }

    fun saveRules(rules: List<AutomationRule>) = synchronized(rulesLock) {
        val raw = AutomationJson.encodeRules(rules)
        prefs.edit().putString(KEY_RULES, raw).apply()
        cachedRaw = raw
        cachedRules = rules
    }

    fun upsert(rule: AutomationRule) = synchronized(rulesLock) {
        val current = rules()
        val next = if (current.any { it.id == rule.id }) {
            current.map { if (it.id == rule.id) rule else it }
        } else {
            current + rule
        }
        saveRules(next)
    }

    fun remove(id: String) = synchronized(rulesLock) {
        saveRules(rules().filterNot { it.id == id })
    }

    fun setEnabled(id: String, enabled: Boolean) = synchronized(rulesLock) {
        saveRules(rules().map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    fun disableAll() = synchronized(rulesLock) {
        saveRules(rules().map { it.copy(enabled = false) })
    }

    fun hasEnabledRules(): Boolean = rules().any { it.enabled && it.isValid }

    fun appendLog(entry: AutomationLogEntry) {
        val line = format(entry)
        synchronized(this) {
            val existing = prefs.getString(KEY_LOG, "").orEmpty()
            val kept = existing.lineSequence().filter { it.isNotBlank() }.take(MAX_LOG_LINES - 1).toList()
            prefs.edit().putString(KEY_LOG, (listOf(line) + kept).joinToString("\n")).apply()
        }
    }

    fun log(): String = prefs.getString(KEY_LOG, "").orEmpty()

    fun clearLog() {
        prefs.edit().remove(KEY_LOG).apply()
    }

    /** Keeps the value the first automation write replaced, so a later undo restores the pre-rule state. */
    fun recordUndo(scope: SettingScope, key: String, previous: String?) {
        synchronized(this) {
            val current = undoEntries()
            if (current.any { it.scope == scope && it.key == key }) return
            val json = JSONArray().also { array ->
                (current + SettingSnapshot(scope, key, previous)).forEach { snapshot ->
                    array.put(
                        JSONObject()
                            .put("scope", snapshot.scope.cli)
                            .put("key", snapshot.key)
                            .put("value", snapshot.value ?: JSONObject.NULL),
                    )
                }
            }
            prefs.edit().putString(KEY_UNDO, json.toString()).apply()
        }
    }

    fun undoEntries(): List<SettingSnapshot> {
        val raw = prefs.getString(KEY_UNDO, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            val scope = SettingScope.entries.firstOrNull { it.cli == json.optString("scope") } ?: return@mapNotNull null
            SettingSnapshot(
                scope = scope,
                key = json.optString("key"),
                value = if (json.isNull("value")) null else json.optString("value"),
            )
        }
    }

    fun clearUndo() {
        prefs.edit().remove(KEY_UNDO).apply()
    }

    private fun format(entry: AutomationLogEntry): String {
        val result = when (val r = entry.result) {
            ActionResult.Done -> "OK"
            is ActionResult.Skipped -> "BỎ QUA: ${r.reason}"
            is ActionResult.Failed -> "LỖI: ${r.reason}"
        }
        val time = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(entry.at))
        return "$time | ${entry.ruleName} → ${entry.action} | $result"
    }

    private companion object {
        const val PREFS = "automation"
        const val KEY_RULES = "rules_json"
        const val KEY_LOG = "log"
        const val KEY_UNDO = "undo_json"
        const val MAX_LOG_LINES = 60
    }
}

object AutomationStorage {
    private var cached: AutomationStore? = null

    /** One store per process, always on the device-protected context created from [context]. */
    fun store(context: Context): AutomationStore {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: AutomationStore(context.applicationContext.createDeviceProtectedStorageContext())
                .also { cached = it }
        }
    }
}

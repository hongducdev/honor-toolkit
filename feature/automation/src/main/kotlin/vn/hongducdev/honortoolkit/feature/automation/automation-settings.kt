package vn.hongducdev.honortoolkit.feature.automation

import android.content.Context
import android.provider.Settings
import vn.hongducdev.honortoolkit.feature.tune.TuneAutomation

/** Reads and writes one settings key. The write is always read back before it is reported as done. */
internal object SettingsAccess {
    fun read(context: Context, scope: SettingScope, key: String): String? = runCatching {
        when (scope) {
            SettingScope.System -> Settings.System.getString(context.contentResolver, key)
            SettingScope.Secure -> Settings.Secure.getString(context.contentResolver, key)
            SettingScope.Global -> Settings.Global.getString(context.contentResolver, key)
        }
    }.getOrNull()

    /** True when the value read back equals [value]; the ROM may keep its old value instead. */
    fun write(context: Context, scope: SettingScope, key: String, value: String): Boolean {
        val written = runCatching {
            when (scope) {
                SettingScope.System -> Settings.System.putString(context.contentResolver, key, value)
                SettingScope.Secure -> Settings.Secure.putString(context.contentResolver, key, value)
                SettingScope.Global -> Settings.Global.putString(context.contentResolver, key, value)
            }
        }.getOrDefault(false)
        return written && read(context, scope, key) == value
    }

    fun delete(context: Context, scope: SettingScope, key: String): Boolean {
        val deleted = runCatching {
            when (scope) {
                SettingScope.System -> Settings.System.putString(context.contentResolver, key, null)
                SettingScope.Secure -> Settings.Secure.putString(context.contentResolver, key, null)
                SettingScope.Global -> Settings.Global.putString(context.contentResolver, key, null)
            }
        }.getOrDefault(false)
        return deleted && read(context, scope, key) == null
    }
}

/**
 * Decides which settings keys a rule may write.
 *
 * The default allowlist is the keys the tune page already exposes. A key outside it can still be
 * written after the user turns on "Nâng cao" in the editor, but the keys below are refused in every
 * case: changing them can cut the app off from its own ADB connection or leave the phone with a
 * security setting it cannot restore.
 */
object AutomationSettingsPolicy {
    private val blockedKeys = setOf(
        "adb_enabled",
        "adb_wifi_enabled",
        "enabled_accessibility_services",
        "enabled_notification_listeners",
        "default_input_method",
        "package_verifier_enable",
        "install_non_market_apps",
        "device_provisioned",
        "user_setup_complete",
        "development_settings_enabled",
    )

    private val catalogue: Set<Pair<String, String>> by lazy {
        TuneAutomation.writableKeys().map { it.scope to it.key }.toSet()
    }

    fun isBlocked(key: String): Boolean = key in blockedKeys

    fun isCatalogueKey(scope: SettingScope, key: String): Boolean = (scope.cli to key) in catalogue

    /** Null when the action may run, otherwise the Vietnamese reason shown in the log. */
    fun refusal(action: Action.WriteSetting): String? = when {
        action.key.isBlank() -> "Khoá thiết lập đang trống"
        isBlocked(action.key) -> "Khoá “${action.key}” bị chặn để tránh hỏng kết nối hoặc bảo mật"
        !action.advanced && !isCatalogueKey(action.scope, action.key) ->
            "Khoá ngoài danh mục Tune; hãy bật “Nâng cao” nếu thật sự cần"
        else -> null
    }
}

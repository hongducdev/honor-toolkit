package vn.hongducdev.honortoolkit.feature.circlesearch

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Optional extra launcher icons, each starting one action directly. All are disabled until the user turns them on. */
enum class LauncherEntry(internal val aliasName: String) {
    CircleSearch("CircleSearchLauncherAlias"),
    Gemini("GeminiLauncherAlias"),
    SearchLive("SearchLiveLauncherAlias"),
}

object LauncherAlias {
    private const val PACKAGE = "vn.hongducdev.honortoolkit.feature.circlesearch"

    private fun component(context: Context, entry: LauncherEntry) = ComponentName(context.packageName, "$PACKAGE.${entry.aliasName}")

    fun isEnabled(context: Context, entry: LauncherEntry): Boolean =
        context.packageManager.getComponentEnabledSetting(component(context, entry)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun enabledEntries(context: Context): Set<LauncherEntry> = LauncherEntry.entries.filter { isEnabled(context, it) }.toSet()

    fun setEnabled(context: Context, entry: LauncherEntry, enabled: Boolean) {
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        context.packageManager.setComponentEnabledSetting(component(context, entry), state, PackageManager.DONT_KILL_APP)
    }
}

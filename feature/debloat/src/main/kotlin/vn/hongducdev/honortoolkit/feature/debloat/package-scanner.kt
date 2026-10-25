package vn.hongducdev.honortoolkit.feature.debloat

import android.content.Context
import android.content.pm.PackageManager
import vn.hongducdev.honortoolkit.core.adb.AdbShell

internal class PackageScanner(private val context: Context, private val shell: AdbShell) {

    /** Returns null when the shell could not list packages (not connected). */
    suspend fun scan(): List<PackageEntry>? {
        val all = listPackages("pm list packages -s") ?: return null
        val disabled = listPackages("pm list packages -s -d") ?: return null
        val inUse = resolveInUsePackages() ?: return null
        return all.map { name ->
            PackageEntry(
                packageName = name,
                label = labelOf(name),
                enabled = name !in disabled,
                protection = DebloatDenylist.protectionOf(name, inUse),
                verified = name in verifiedOnMagic7Pro,
            )
        }.sortedWith(compareBy({ it.label.lowercase() }, { it.packageName }))
    }

    /** Returns null when any lookup fails, so callers refuse to disable instead of guessing. */
    suspend fun resolveInUsePackages(): Set<String>? {
        val holders = mutableSetOf<String>()
        for (role in ROLES) {
            val result = shell.run("cmd role get-role-holders --user 0 $role")
            if (!result.isSuccess) return null
            holders += result.output.split(Regex("[;\\s]+"))
        }
        val ime = shell.run("settings get secure default_input_method")
        if (!ime.isSuccess) return null
        holders += ime.output.substringBefore('/').trim()
        holders += context.packageName
        return holders.filter(::isValidPackageName).toSet()
    }

    private suspend fun listPackages(command: String): Set<String>? {
        val result = shell.run(command)
        if (!result.isSuccess) return null
        return result.output.lineSequence()
            .filter { it.startsWith(PACKAGE_PREFIX) }
            .map { it.removePrefix(PACKAGE_PREFIX).trim() }
            .filter(::isValidPackageName)
            .toSet()
    }

    private fun labelOf(packageName: String): String = try {
        context.packageManager.getApplicationInfo(packageName, 0).loadLabel(context.packageManager).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }

    private companion object {
        const val PACKAGE_PREFIX = "package:"
        val ROLES = listOf(
            "android.app.role.HOME",
            "android.app.role.SMS",
            "android.app.role.DIALER",
            "android.app.role.ASSISTANT",
            "android.app.role.BROWSER",
        )
    }
}

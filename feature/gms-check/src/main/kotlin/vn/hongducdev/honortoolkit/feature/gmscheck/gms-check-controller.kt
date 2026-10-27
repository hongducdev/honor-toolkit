package vn.hongducdev.honortoolkit.feature.gmscheck

import vn.hongducdev.honortoolkit.core.adb.AdbShell

enum class GmsPackage(val packageName: String) {
    PlayServices("com.google.android.gms"),
    ServicesFramework("com.google.android.gsf"),
    PlayStore("com.android.vending"),
}

enum class GmsPackageStatus { Missing, Disabled, Enabled }

data class GmsPackageState(val target: GmsPackage, val status: GmsPackageStatus, val version: String?)

data class GmsReport(val packages: List<GmsPackageState>, val googleAccounts: Int?) {
    val isReady: Boolean get() = packages.all { it.status == GmsPackageStatus.Enabled } && (googleAccounts ?: 0) > 0
}

internal class GmsCheckController(private val shell: AdbShell) {
    /** Null when the shell could not list packages; read-only, nothing is installed or changed. */
    suspend fun check(): GmsReport? {
        val all = shell.run("pm list packages")
        val disabled = shell.run("pm list packages -d")
        if (!all.isSuccess || !disabled.isSuccess) return null
        val installed = parsePackageNames(all.output)
        val off = parsePackageNames(disabled.output)
        val states = GmsPackage.entries.map { target ->
            val name = target.packageName
            when {
                name !in installed -> GmsPackageState(target, GmsPackageStatus.Missing, null)
                name in off -> GmsPackageState(target, GmsPackageStatus.Disabled, version(name))
                else -> GmsPackageState(target, GmsPackageStatus.Enabled, version(name))
            }
        }
        val accounts = shell.run("dumpsys account").takeIf { it.isSuccess }?.let { countGoogleAccounts(it.output) }
        return GmsReport(states, accounts)
    }

    private suspend fun version(packageName: String): String? =
        shell.run("dumpsys package $packageName").takeIf { it.isSuccess }?.let { parseVersionName(it.output, packageName) }
}

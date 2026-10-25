package vn.hongducdev.honortoolkit.feature.debloat

import vn.hongducdev.honortoolkit.core.adb.AdbShell

sealed interface DebloatOutcome {
    data object Done : DebloatOutcome
    data class Blocked(val reason: ProtectionReason) : DebloatOutcome
    data object InvalidName : DebloatOutcome
    data class Failed(val output: String) : DebloatOutcome
}

internal class DebloatController(
    private val shell: AdbShell,
    private val scanner: PackageScanner,
    private val journal: DebloatJournal,
) {
    suspend fun disable(packageName: String): DebloatOutcome {
        if (!isValidPackageName(packageName)) return DebloatOutcome.InvalidName
        val inUse = scanner.resolveInUsePackages() ?: return DebloatOutcome.Failed("Không xác định được ứng dụng mặc định")
        val protection = DebloatDenylist.protectionOf(packageName, inUse)
        if (protection != null) return DebloatOutcome.Blocked(protection)
        return change(JournalAction.Disable, "pm disable-user --user 0 $packageName", packageName, "disabled-user")
    }

    suspend fun enable(packageName: String): DebloatOutcome {
        if (!isValidPackageName(packageName)) return DebloatOutcome.InvalidName
        return change(JournalAction.Enable, "pm enable --user 0 $packageName", packageName, "enabled")
    }

    private suspend fun change(action: JournalAction, command: String, packageName: String, expectedState: String): DebloatOutcome {
        val result = shell.run(command)
        if (!result.isSuccess || !result.output.contains("new state: $expectedState")) return DebloatOutcome.Failed(result.output)
        journal.append(action, packageName)
        return DebloatOutcome.Done
    }
}

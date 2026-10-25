package vn.hongducdev.honortoolkit.feature.permissions

import vn.hongducdev.honortoolkit.core.adb.AdbShell

sealed interface PermissionOutcome {
    data object Done : PermissionOutcome
    data class Blocked(val reason: BlockReason) : PermissionOutcome
    data class Failed(val output: String) : PermissionOutcome
}

data class AppPermissions(val permissions: List<PermissionEntry>, val appOps: List<AppOpEntry>, val blocked: BlockReason?)

internal class PermissionsController(private val shell: AdbShell, private val journal: PermissionsJournal) {
    /** Third-party packages on user 0, or null when the shell could not list them. */
    suspend fun userPackages(): Set<String>? {
        val result = shell.run("pm list packages -3 --user 0")
        if (!result.isSuccess) return null
        return result.output.lineSequence().map { it.trim().removePrefix("package:") }.filter(::isValidPackageName).toSet()
    }

    /** Null when the shell failed. A non-null result with [AppPermissions.blocked] set is shown read-only. */
    suspend fun load(packageName: String): AppPermissions? {
        val packages = userPackages() ?: return null
        val dump = readDump(packageName) ?: return null
        val ops = shell.run("cmd appops get --user 0 $packageName")
        return AppPermissions(
            permissions = dump.permissions,
            appOps = if (ops.isSuccess) parseAppOps(ops.output) else emptyList(),
            blocked = PermissionGuard.checkApp(packageName, packages, dump.sharedUid),
        )
    }

    suspend fun setGranted(packageName: String, permission: String, grant: Boolean): PermissionOutcome {
        val dump = when (val app = guardApp(packageName)) {
            is Guard.Stop -> return app.outcome
            is Guard.Ok -> app.dump
        }
        val entry = dump.permissions.firstOrNull { it.name == permission }
        PermissionGuard.checkPermission(permission, entry)?.let { return PermissionOutcome.Blocked(it) }
        if (entry!!.granted == grant) return PermissionOutcome.Done
        val verb = if (grant) "grant" else "revoke"
        val result = shell.run("pm $verb --user 0 $packageName $permission")
        if (!result.isSuccess || result.output.contains("Exception")) return PermissionOutcome.Failed(result.output.trim())
        // The ROM can silently ignore the change, so only report success when the read-back matches.
        val after = readDump(packageName)?.permissions?.firstOrNull { it.name == permission }
        if (after?.granted != grant) return PermissionOutcome.Failed("Hệ thống không áp dụng thay đổi")
        journal.append(ChangeKind.Permission, packageName, permission, if (grant) "revoked" else "granted", if (grant) "granted" else "revoked")
        return PermissionOutcome.Done
    }

    suspend fun setAppOp(packageName: String, op: String, mode: AppOpMode): PermissionOutcome {
        PermissionGuard.checkAppOp(op)?.let { return PermissionOutcome.Blocked(it) }
        (guardApp(packageName) as? Guard.Stop)?.let { return it.outcome }
        val before = currentMode(packageName, op)
        val result = shell.run("cmd appops set --user 0 $packageName $op ${mode.argument}")
        if (!result.isSuccess || result.output.contains("Exception")) return PermissionOutcome.Failed(result.output.trim())
        // After "default" the effective mode is whatever the ROM picks, so there is nothing to compare against.
        if (mode != AppOpMode.Default && currentMode(packageName, op) != mode) {
            return PermissionOutcome.Failed("Hệ thống không áp dụng thay đổi")
        }
        journal.append(ChangeKind.AppOp, packageName, op, (before ?: AppOpMode.Default).argument, mode.argument)
        return PermissionOutcome.Done
    }

    private suspend fun currentMode(packageName: String, op: String): AppOpMode? {
        val result = shell.run("cmd appops get --user 0 $packageName $op")
        if (!result.isSuccess) return null
        return parseAppOps(result.output).firstOrNull { it.name == op }?.mode
    }

    private suspend fun readDump(packageName: String): PackageDump? {
        if (!isValidPackageName(packageName)) return null
        val result = shell.run("dumpsys package $packageName")
        return if (result.isSuccess) parsePackageDump(result.output) else null
    }

    private sealed interface Guard {
        data class Ok(val dump: PackageDump) : Guard
        data class Stop(val outcome: PermissionOutcome) : Guard
    }

    private suspend fun guardApp(packageName: String): Guard {
        if (!isValidPackageName(packageName)) return Guard.Stop(PermissionOutcome.Blocked(BlockReason.InvalidName))
        val packages = userPackages() ?: return Guard.Stop(PermissionOutcome.Failed("Không đọc được danh sách ứng dụng"))
        val dump = readDump(packageName) ?: return Guard.Stop(PermissionOutcome.Failed("Không đọc được quyền của ứng dụng"))
        PermissionGuard.checkApp(packageName, packages, dump.sharedUid)?.let { return Guard.Stop(PermissionOutcome.Blocked(it)) }
        return Guard.Ok(dump)
    }
}
